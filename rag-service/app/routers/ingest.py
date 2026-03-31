"""数据导入接口路由，触发文档清洗、切分与入库流程。"""
from __future__ import annotations

from fastapi import APIRouter, Request

from app.schemas.ingest import IngestRunRequest, IngestRunResponse


router = APIRouter(prefix="/rag/v1/ingest", tags=["ingest"])


@router.post("/run", response_model=IngestRunResponse)
def ingest_run(req: IngestRunRequest, request: Request):
    svc = request.app.state.services["ingest_service"]
    idx = request.app.state.services["index_service"]
    rag = request.app.state.services["rag_service"]

    result = svc.run(
        input_dir=req.input_dir,
        strategy=req.strategy,
        incremental=req.incremental,
        file_paths=req.file_paths,
        enable_ocr=req.enable_ocr,
        strict_ocr=req.strict_ocr,
    )
    faiss_built = False
    bm25_built = False
    if req.rebuild_faiss and result.get("total_chunks", 0) > 0:
        idx.build_faiss(chunks_path=result["chunks_path"])
        faiss_built = True
    if req.rebuild_bm25 and result.get("total_chunks", 0) > 0:
        idx.build_bm25(chunks_path=result["chunks_path"])
        bm25_built = True
    if faiss_built or bm25_built:
        rag.reload_assets(silent=False)
    return IngestRunResponse(
        chunks_path=result["chunks_path"],
        files=result["files"],
        chunks=result["chunks"],
        total_chunks=result.get("total_chunks", 0),
        chunks_by_source=result.get("chunks_by_source", {}),
        doc_stats=result.get("doc_stats", {}),
        faiss_built=faiss_built,
        bm25_built=bm25_built,
    )

