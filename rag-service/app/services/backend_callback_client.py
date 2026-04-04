"""向 backend 推送文档处理状态的内部回调客户端。"""

from __future__ import annotations

import logging
from typing import Any

import httpx

from app.core.config import Settings

logger = logging.getLogger(__name__)

CALLBACK_SECRET_HEADER = "X-Rag-Callback-Secret"


class BackendCallbackClient:
    def __init__(self, settings: Settings):
        self._base_url = settings.backend_callback_base_url
        self._path = settings.backend_docs_status_callback_path
        self._secret = settings.backend_callback_secret
        timeout = settings.backend_callback_timeout_seconds
        self._client = httpx.Client(timeout=timeout)

    @property
    def enabled(self) -> bool:
        return bool(self._base_url and self._path and self._secret)

    def close(self) -> None:
        self._client.close()

    def publish_status(self, row: dict[str, Any]) -> None:
        if not self.enabled:
            return

        payload = self._build_payload(row)
        url = f"{str(self._base_url).rstrip('/')}/{str(self._path).lstrip('/')}"
        try:
            response = self._client.post(
                url,
                headers={
                    CALLBACK_SECRET_HEADER: str(self._secret),
                    "Content-Type": "application/json",
                },
                json=payload,
            )
            response.raise_for_status()
        except Exception:
            logger.exception(
                "backend 状态回调失败 doc_id=%s status=%s trace_id=%s backend_callback_url=%s",
                row.get("doc_id"),
                row.get("status"),
                row.get("trace_id"),
                url,
                extra={
                    "doc_id": row.get("doc_id"),
                    "status": row.get("status"),
                    "trace_id": row.get("trace_id"),
                    "backend_callback_url": url,
                },
            )

    @staticmethod
    def _build_payload(row: dict[str, Any]) -> dict[str, Any]:
        return {
            "docId": row.get("doc_id"),
            "status": row.get("status"),
            "progress": row.get("progress"),
            "stageProgress": row.get("stage_progress"),
            "pagesProcessed": row.get("pages_processed"),
            "totalPages": row.get("total_pages"),
            "currentPage": row.get("current_page"),
            "ocrPages": row.get("ocr_pages"),
            "chunkCount": row.get("chunk_count"),
            "message": row.get("message"),
            "error": row.get("error"),
            "traceId": row.get("trace_id"),
            "errorType": row.get("error_type"),
            "failedStage": row.get("failed_stage"),
            "debugDetail": row.get("debug_detail"),
            "failedPages": list(row.get("failed_pages") or []),
            "startedAt": row.get("started_at"),
            "finishedAt": row.get("finished_at"),
            "updatedAt": row.get("updated_at"),
            "elapsedMs": row.get("elapsed_ms"),
        }
