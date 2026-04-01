"""文档处理接口路由，支持异步 OCR 入库与状态查询。"""

from __future__ import annotations

from pathlib import Path
import time
from typing import Any

from fastapi import APIRouter, BackgroundTasks, HTTPException, Request

from app.core.errors import ServiceError
from app.schemas.doc import DocsProcessRequest, DocsProcessResponse, IngestDocStatus

import logging

router = APIRouter(prefix="/rag/v1", tags=["docs"])

logger = logging.getLogger(__name__)

_STAGE_PROGRESS_RANGES: dict[str, tuple[int, int]] = {
    "queued": (0, 5),
    "processing": (5, 35),
    "ocr_processing": (5, 35),
    "cleaning": (35, 55),
    "splitting": (55, 80),
    "indexing": (80, 99),
    "done": (100, 100),
}

# 文档处理接口
@router.post("/docs/process", response_model=DocsProcessResponse)
def process_docs(
    req: DocsProcessRequest, request: Request, background_tasks: BackgroundTasks
):
    logger.info("收到处理请求")
    # 获取入库任务状态管理器
    job_manager = request.app.state.services["ingest_job_manager"]
    docs = req.docs or []
    if not docs:
        raise ServiceError(
            "RAG文档处理模块收到空文档列表处理请求", status_code=400, code="BAD_REQUEST"
        )

    # 将每个文档入库任务入队，并记录状态
    statuses: list[IngestDocStatus] = []
    for item in docs:
        queued = job_manager.queue(
            doc_id=item.doc_id, enable_ocr=req.enable_ocr, message="queued"
        )
        statuses.append(IngestDocStatus(**queued))

    # 实际开始执行入库逻辑
    background_tasks.add_task(_run_docs_job, request.app, req)

    first = statuses[0] if statuses else None
    return DocsProcessResponse(
        ok=True,
        files=len(docs),
        chunks=0,
        total_chunks=0,
        chunks_by_source={},
        processed_doc_ids=[x.doc_id for x in docs],
        statuses=statuses,
        doc_id=first.doc_id if first else None,
        status=first.status if first else None,
        pages_processed=first.pages_processed if first else 0,
        ocr_pages=first.ocr_pages if first else 0,
        message="queued",
    )


# 获取文档处理状态的接口
@router.get("/ingest/{doc_id}/status", response_model=IngestDocStatus)
def ingest_status(doc_id: str, request: Request):
    job_manager = request.app.state.services["ingest_job_manager"]
    row = job_manager.get(doc_id)
    if not row:
        raise HTTPException(status_code=404, detail=f"doc_id not found: {doc_id}")
    return IngestDocStatus(**row)


def _elapsed_ms(started_at: float | None) -> int | None:
    if started_at is None:
        return None
    return int((time.perf_counter() - started_at) * 1000)


def _row_metric(row: dict[str, Any] | None, key: str) -> int | None:
    if not row or row.get(key) is None:
        return None
    return int(row[key])


def _clamp_percent(value: int | float | None) -> int:
    if value is None:
        return 0
    return max(0, min(100, int(round(value))))


def _resolve_stage_progress(stage: str, payload: dict[str, Any] | None = None) -> int:
    progress = dict(payload or {})
    if progress.get("stage_progress") is not None:
        return _clamp_percent(progress.get("stage_progress"))
    total_pages = progress.get("total_pages")
    current_page = progress.get("current_page")
    if total_pages and current_page is not None:
        return _clamp_percent(current_page * 100 / max(int(total_pages), 1))
    if stage == "done":
        return 100
    return 0


def _resolve_progress(stage: str, payload: dict[str, Any] | None = None) -> int:
    progress = dict(payload or {})
    if stage == "failed":
        return _clamp_percent(progress.get("progress"))
    if stage == "done":
        return 100
    start, end = _STAGE_PROGRESS_RANGES.get(stage, (0, 0))
    stage_progress = _resolve_stage_progress(stage, progress)
    return _clamp_percent(start + (end - start) * stage_progress / 100)


# 文档后台入库接口
def _run_docs_job(app: Any, req: DocsProcessRequest) -> None:
    ingest_service = app.state.services["ingest_service"]
    index_service = app.state.services["index_service"]
    rag_service = app.state.services["rag_service"]
    job_manager = app.state.services["ingest_job_manager"]

    docs = req.docs or []
    processed_docs = []
    started_at_map: dict[str, float] = {}
    try:
        with job_manager.run_lock:
            for item in docs:
                doc_id = item.doc_id
                source_name = item.source_name or Path(item.file_path).name
                file_path = Path(item.file_path)
                t0 = time.perf_counter()
                started_at_map[doc_id] = t0
                job_manager.start(doc_id)

                def _stage_update(
                    stage: str,
                    payload: dict[str, Any] | None = None,
                    *,
                    _doc_id: str = doc_id,
                    _t0: float = t0,
                ) -> None:
                    progress = dict(payload or {})
                    progress["stage_progress"] = _resolve_stage_progress(stage, progress)
                    progress["progress"] = _resolve_progress(stage, progress)
                    progress["elapsed_ms"] = _elapsed_ms(_t0)
                    job_manager.set_stage(_doc_id, stage, **progress)

                try:
                    # 执行文档导入逻辑
                    doc_chunks = ingest_service.process_document(
                        doc_id=doc_id,
                        file_path=file_path,
                        source_file_name=source_name,
                        strategy=req.strategy,
                        enable_ocr=req.enable_ocr,
                        strict_ocr=req.strict_ocr,
                        status_callback=_stage_update,
                    )
                    processed_docs.append(doc_chunks)
                    job_manager.set_stage(
                        doc_id,
                        "splitting",
                        progress=_resolve_progress("splitting", {"stage_progress": 100}),
                        stage_progress=100,
                        pages_processed=doc_chunks.pages_processed,
                        total_pages=doc_chunks.pages_processed,
                        current_page=doc_chunks.pages_processed,
                        ocr_pages=doc_chunks.ocr_pages,
                        chunk_count=len(doc_chunks.records),
                        failed_pages=doc_chunks.failed_pages,
                        elapsed_ms=_elapsed_ms(t0),
                    )
                except ServiceError as exc:
                    row = job_manager.get(doc_id)
                    job_manager.fail(
                        doc_id,
                        message=exc.message,
                        error=str(exc.details or exc.message),
                        pages_processed=_row_metric(row, "pages_processed"),
                        ocr_pages=_row_metric(row, "ocr_pages"),
                        failed_pages=list((row or {}).get("failed_pages") or []),
                        elapsed_ms=_elapsed_ms(t0),
                    )
                except Exception as exc:
                    row = job_manager.get(doc_id)
                    job_manager.fail(
                        doc_id,
                        message="document processing failed",
                        error=str(exc),
                        pages_processed=_row_metric(row, "pages_processed"),
                        ocr_pages=_row_metric(row, "ocr_pages"),
                        failed_pages=list((row or {}).get("failed_pages") or []),
                        elapsed_ms=_elapsed_ms(t0),
                    )

            if not processed_docs:
                return

            for doc in processed_docs:
                indexing_steps = 1
                if req.rebuild_faiss and doc.records:
                    indexing_steps += 1
                if req.rebuild_bm25 and doc.records:
                    indexing_steps += 1
                if doc.records and (req.rebuild_faiss or req.rebuild_bm25):
                    indexing_steps += 1
                doc.__dict__["indexing_steps"] = indexing_steps
                doc.__dict__["completed_indexing_steps"] = 0
                job_manager.set_stage(
                    doc.doc_id,
                    "indexing",
                    progress=_resolve_progress("indexing", {"stage_progress": 0}),
                    stage_progress=0,
                    pages_processed=doc.pages_processed,
                    total_pages=doc.pages_processed,
                    current_page=doc.pages_processed,
                    ocr_pages=doc.ocr_pages,
                    chunk_count=len(doc.records),
                    failed_pages=doc.failed_pages,
                    elapsed_ms=_elapsed_ms(started_at_map.get(doc.doc_id)),
                )

            result = ingest_service.persist_documents(
                docs=processed_docs, incremental=True
            )
            for doc in processed_docs:
                doc.__dict__["completed_indexing_steps"] += 1
                stage_progress = round(
                    doc.__dict__["completed_indexing_steps"] * 100
                    / max(doc.__dict__["indexing_steps"], 1)
                )
                job_manager.set_stage(
                    doc.doc_id,
                    "indexing",
                    progress=_resolve_progress("indexing", {"stage_progress": stage_progress}),
                    stage_progress=stage_progress,
                    pages_processed=doc.pages_processed,
                    total_pages=doc.pages_processed,
                    current_page=doc.pages_processed,
                    ocr_pages=doc.ocr_pages,
                    chunk_count=len(doc.records),
                    failed_pages=doc.failed_pages,
                    elapsed_ms=_elapsed_ms(started_at_map.get(doc.doc_id)),
                )
            if req.rebuild_faiss and result.get("total_chunks", 0) > 0:
                index_service.build_faiss(chunks_path=result["chunks_path"])
                for doc in processed_docs:
                    doc.__dict__["completed_indexing_steps"] += 1
                    stage_progress = round(
                        doc.__dict__["completed_indexing_steps"] * 100
                        / max(doc.__dict__["indexing_steps"], 1)
                    )
                    job_manager.set_stage(
                        doc.doc_id,
                        "indexing",
                        progress=_resolve_progress("indexing", {"stage_progress": stage_progress}),
                        stage_progress=stage_progress,
                        pages_processed=doc.pages_processed,
                        total_pages=doc.pages_processed,
                        current_page=doc.pages_processed,
                        ocr_pages=doc.ocr_pages,
                        chunk_count=len(doc.records),
                        failed_pages=doc.failed_pages,
                        elapsed_ms=_elapsed_ms(started_at_map.get(doc.doc_id)),
                    )
            if req.rebuild_bm25 and result.get("total_chunks", 0) > 0:
                index_service.build_bm25(chunks_path=result["chunks_path"])
                for doc in processed_docs:
                    doc.__dict__["completed_indexing_steps"] += 1
                    stage_progress = round(
                        doc.__dict__["completed_indexing_steps"] * 100
                        / max(doc.__dict__["indexing_steps"], 1)
                    )
                    job_manager.set_stage(
                        doc.doc_id,
                        "indexing",
                        progress=_resolve_progress("indexing", {"stage_progress": stage_progress}),
                        stage_progress=stage_progress,
                        pages_processed=doc.pages_processed,
                        total_pages=doc.pages_processed,
                        current_page=doc.pages_processed,
                        ocr_pages=doc.ocr_pages,
                        chunk_count=len(doc.records),
                        failed_pages=doc.failed_pages,
                        elapsed_ms=_elapsed_ms(started_at_map.get(doc.doc_id)),
                    )
            if result.get("total_chunks", 0) > 0 and (
                req.rebuild_faiss or req.rebuild_bm25
            ):
                rag_service.reload_assets(silent=False)
                for doc in processed_docs:
                    doc.__dict__["completed_indexing_steps"] += 1
                    stage_progress = round(
                        doc.__dict__["completed_indexing_steps"] * 100
                        / max(doc.__dict__["indexing_steps"], 1)
                    )
                    job_manager.set_stage(
                        doc.doc_id,
                        "indexing",
                        progress=_resolve_progress("indexing", {"stage_progress": stage_progress}),
                        stage_progress=stage_progress,
                        pages_processed=doc.pages_processed,
                        total_pages=doc.pages_processed,
                        current_page=doc.pages_processed,
                        ocr_pages=doc.ocr_pages,
                        chunk_count=len(doc.records),
                        failed_pages=doc.failed_pages,
                        elapsed_ms=_elapsed_ms(started_at_map.get(doc.doc_id)),
                    )

            doc_stats = result.get("doc_stats", {})
            for doc in processed_docs:
                stat = doc_stats.get(doc.doc_id, {})
                elapsed_ms = _elapsed_ms(started_at_map.get(doc.doc_id))
                job_manager.done(
                    doc.doc_id,
                    pages_processed=int(
                        stat.get("pages_processed", doc.pages_processed)
                    ),
                    ocr_pages=int(stat.get("ocr_pages", doc.ocr_pages)),
                    chunk_count=int(stat.get("chunk_count", len(doc.records))),
                    failed_pages=list(stat.get("failed_pages", doc.failed_pages)),
                    elapsed_ms=elapsed_ms
                    if elapsed_ms is not None
                    else int(stat.get("elapsed_ms", doc.elapsed_ms)),
                    message=str(stat.get("message", "done")),
                    progress=100,
                    stage_progress=100,
                    total_pages=doc.pages_processed,
                    current_page=doc.pages_processed,
                )
    except Exception as exc:
        for item in docs:
            row = job_manager.get(item.doc_id)
            if row and row.get("status") in {"done", "failed"}:
                continue
            job_manager.fail(
                item.doc_id,
                message="ingest pipeline failed",
                error=str(exc),
                pages_processed=_row_metric(row, "pages_processed"),
                ocr_pages=_row_metric(row, "ocr_pages"),
                failed_pages=list((row or {}).get("failed_pages") or []),
                elapsed_ms=_elapsed_ms(started_at_map.get(item.doc_id)),
            )
