"""运行时原始控制台输出订阅中心。"""
from __future__ import annotations

import queue
import sys
import threading
from datetime import datetime, timezone
from typing import Any

MAX_QUEUE_SIZE = 2000
_subscribers: set[queue.Queue[dict[str, Any]]] = set()
_subscribers_lock = threading.Lock()
_installed = False


def publish(entry: dict[str, Any]) -> None:
    with _subscribers_lock:
        subscribers = list(_subscribers)
    for subscriber in subscribers:
        if subscriber.full():
            try:
                subscriber.get_nowait()
            except queue.Empty:
                pass
        try:
            subscriber.put_nowait(entry)
        except queue.Full:
            continue


def publish_raw(raw: str) -> None:
    if not raw:
        return
    publish(
        {
            "type": "chunk",
            "source": "rag",
            "raw": raw,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
    )


def subscribe() -> queue.Queue[dict[str, Any]]:
    subscriber: queue.Queue[dict[str, Any]] = queue.Queue(maxsize=MAX_QUEUE_SIZE)
    with _subscribers_lock:
        _subscribers.add(subscriber)
    return subscriber


def unsubscribe(subscriber: queue.Queue[dict[str, Any]]) -> None:
    with _subscribers_lock:
        _subscribers.discard(subscriber)
    while not subscriber.empty():
        try:
            subscriber.get_nowait()
        except queue.Empty:
            break


def heartbeat() -> dict[str, Any]:
    return {
        "type": "heartbeat",
        "source": "rag",
        "raw": None,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


class MirroredTextStream:
    def __init__(self, target: Any) -> None:
        self._target = target
        self._buffer = ""
        self._lock = threading.Lock()
        self.encoding = getattr(target, "encoding", "utf-8")
        self.errors = getattr(target, "errors", "strict")

    def write(self, data: str) -> int:
        with self._lock:
            written = self._target.write(data)
            self._buffer += data
            self._publish_completed_chunks(flush_partial=False)
        return written

    def flush(self) -> None:
        with self._lock:
            self._target.flush()
            self._publish_completed_chunks(flush_partial=True)

    def isatty(self) -> bool:
        return bool(getattr(self._target, "isatty", lambda: False)())

    def fileno(self) -> int:
        return self._target.fileno()

    def writable(self) -> bool:
        return True

    def __getattr__(self, item: str) -> Any:
        return getattr(self._target, item)

    def _publish_completed_chunks(self, flush_partial: bool) -> None:
        start = 0
        for idx, char in enumerate(self._buffer):
            if char == "\n":
                publish_raw(self._buffer[start : idx + 1])
                start = idx + 1
        if start > 0:
            self._buffer = self._buffer[start:]
        if flush_partial and self._buffer:
            publish_raw(self._buffer)
            self._buffer = ""


def install_console_capture() -> None:
    global _installed
    if _installed:
        return
    sys.stdout = MirroredTextStream(sys.stdout)
    sys.stderr = MirroredTextStream(sys.stderr)
    _installed = True
