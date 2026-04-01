"""实时日志流接口。"""
from __future__ import annotations

import json
import queue
from typing import Iterator

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.core.live_logs import heartbeat, subscribe, unsubscribe

router = APIRouter(prefix="/rag/v1", tags=["logs"])


@router.get("/logs/stream")
def stream_logs() -> StreamingResponse:
    subscriber = subscribe()

    def event_stream() -> Iterator[str]:
        try:
            while True:
                try:
                    payload = subscriber.get(timeout=15)
                except queue.Empty:
                    payload = heartbeat()
                yield json.dumps(payload, ensure_ascii=False) + "\n"
        finally:
            unsubscribe(subscriber)

    return StreamingResponse(
        event_stream(),
        media_type="application/x-ndjson",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )
