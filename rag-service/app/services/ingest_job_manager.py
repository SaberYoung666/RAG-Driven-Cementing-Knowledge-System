"""文档入库任务状态管理，支持本地持久化。"""

from __future__ import annotations

from datetime import datetime, timezone
import json
from pathlib import Path
import threading
from typing import Any, Dict


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


class IngestJobManager:
    def __init__(self, store_path: Path):
        self.store_path = store_path
        self.store_path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self.run_lock = threading.Lock()
        # 正在处理的文档
        self._jobs: Dict[str, Dict[str, Any]] = {}
        self._load()

    def _load(self) -> None:
        if not self.store_path.exists():
            return
        try:
            data = json.loads(self.store_path.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                self._jobs = data
        except Exception:
            self._jobs = {}

    # 记录入库任务日志
    def _save(self) -> None:
        self.store_path.write_text(
            json.dumps(self._jobs, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    # 入库任务入队
    def queue(
        self, doc_id: str, enable_ocr: str = "auto", message: str = "queued"
    ) -> Dict[str, Any]:
        with self._lock:
            row = {
                "doc_id": doc_id,
                "status": "queued",
                "pages_processed": 0,
                "ocr_pages": 0,
                "chunk_count": 0,
                "enable_ocr": enable_ocr,
                "message": message,
                "error": None,
                "failed_pages": [],
                "started_at": None,
                "finished_at": None,
                "updated_at": _utc_now(),
                "elapsed_ms": None,
            }
            self._jobs[doc_id] = row
            self._save()
            return dict(row)

    # 入库任务开始，仅记录开始时间
    def start(self, doc_id: str) -> Dict[str, Any]:
        return self.update(doc_id, started_at=_utc_now())

    # 切换文档处理阶段，并同步进度统计
    def set_stage(
        self, doc_id: str, status: str, message: str | None = None, **kwargs: Any
    ) -> Dict[str, Any]:
        return self.update(doc_id, status=status, message=message or status, **kwargs)

    def update(self, doc_id: str, **kwargs: Any) -> Dict[str, Any]:
        with self._lock:
            row = dict(self._jobs.get(doc_id) or {"doc_id": doc_id})
            row.update(kwargs)
            row["updated_at"] = _utc_now()
            self._jobs[doc_id] = row
            self._save()
            return dict(row)

    def done(
        self,
        doc_id: str,
        pages_processed: int,
        ocr_pages: int,
        chunk_count: int,
        message: str = "done",
        failed_pages: list[int] | None = None,
        elapsed_ms: int | None = None,
    ) -> Dict[str, Any]:
        return self.update(
            doc_id,
            status="done",
            pages_processed=pages_processed,
            ocr_pages=ocr_pages,
            chunk_count=chunk_count,
            message=message,
            failed_pages=failed_pages or [],
            error=None,
            finished_at=_utc_now(),
            elapsed_ms=elapsed_ms,
        )

    def fail(
        self,
        doc_id: str,
        message: str,
        error: str | None = None,
        pages_processed: int | None = None,
        ocr_pages: int | None = None,
        failed_pages: list[int] | None = None,
        elapsed_ms: int | None = None,
    ) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "status": "failed",
            "message": message,
            "error": error or message,
            "finished_at": _utc_now(),
            "elapsed_ms": elapsed_ms,
            "failed_pages": failed_pages or [],
        }
        if pages_processed is not None:
            payload["pages_processed"] = pages_processed
        if ocr_pages is not None:
            payload["ocr_pages"] = ocr_pages
        return self.update(doc_id, **payload)

    # 获取文档处理状态
    def get(self, doc_id: str) -> Dict[str, Any] | None:
        with self._lock:
            row = self._jobs.get(doc_id)
            return dict(row) if row else None
