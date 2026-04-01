"""配置结构化日志与日志上下文，供全局服务复用。"""
from __future__ import annotations

import json
import logging
import sys
import traceback
from typing import Any, Dict

from app.core.live_logs import publish

_STANDARD_LOG_RECORD_FIELDS = set(vars(logging.makeLogRecord({})).keys())

class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: Dict[str, Any] = {
            "level": record.levelname,
            "name": record.name,
            "message": record.getMessage(),
        }
        for key, value in record.__dict__.items():
            if key in _STANDARD_LOG_RECORD_FIELDS or key.startswith("_"):
                continue
            payload[key] = value
        return json.dumps(payload, ensure_ascii=False)


class LiveLogHandler(logging.Handler):
    def emit(self, record: logging.LogRecord) -> None:
        details = None
        if record.exc_info:
            details = "".join(traceback.format_exception(*record.exc_info))
        publish(
            {
                "type": "log",
                "source": "rag",
                "level": record.levelname,
                "logger": record.name,
                "thread": getattr(record, "threadName", None),
                "message": record.getMessage(),
                "timestamp": self.formatTime(record),
                "details": details,
            }
        )

    def formatTime(self, record: logging.LogRecord) -> str:
        return self.formatter.formatTime(record) if self.formatter else logging.Formatter().formatTime(record)


def configure_logging() -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    live_handler = LiveLogHandler()
    live_handler.setFormatter(logging.Formatter())

    root = logging.getLogger()
    root.setLevel(logging.INFO)
    root.handlers.clear()
    root.addHandler(handler)
    root.addHandler(live_handler)

