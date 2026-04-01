"""运行时实时日志订阅中心，仅保留内存中的短时日志流。"""
from __future__ import annotations

import queue
import threading
from datetime import datetime, timezone
from typing import Any

MAX_QUEUE_SIZE = 500
_subscribers: set[queue.Queue[dict[str, Any]]] = set()
_subscribers_lock = threading.Lock()


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
        "level": "INFO",
        "logger": "rag.heartbeat",
        "thread": None,
        "message": "heartbeat",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "details": None,
    }
