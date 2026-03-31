"""OCR 子进程执行器，支持超时后强制终止并重建 worker。"""

from __future__ import annotations

from dataclasses import asdict, dataclass
import logging
import multiprocessing as mp
from queue import Empty
import threading
import time
from typing import Any, Mapping
from uuid import uuid4

from app.services.ocr_engine import OCRBlock, OCREngineUnavailable, OCRPageResult, PaddleOCREngine

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class OCRProcessConfig:
    lang: str = "ch"
    use_angle_cls: bool = True
    enable_mkldnn: bool = False
    cpu_threads: int | None = None
    init_kwargs: dict[str, Any] | None = None
    prewarm: bool = False


def _serialize_result(result: OCRPageResult) -> dict[str, Any]:
    return {
        "text": result.text,
        "confidence_avg": result.confidence_avg,
        "blocks": [asdict(block) for block in result.blocks],
        "timing": dict(result.timing or {}),
    }


def _deserialize_result(payload: Mapping[str, Any]) -> OCRPageResult:
    blocks = [OCRBlock(**block) for block in payload.get("blocks", [])]
    timing = payload.get("timing")
    return OCRPageResult(
        text=str(payload.get("text", "")),
        confidence_avg=float(payload.get("confidence_avg", 0.0)),
        blocks=blocks,
        timing=dict(timing) if isinstance(timing, Mapping) else None,
    )


def _worker_main(
    request_queue: Any,
    response_queue: Any,
    config: OCRProcessConfig,
) -> None:
    engine = PaddleOCREngine(
        lang=config.lang,
        use_angle_cls=config.use_angle_cls,
        enable_mkldnn=config.enable_mkldnn,
        cpu_threads=config.cpu_threads,
        init_kwargs=dict(config.init_kwargs or {}),
    )
    if config.prewarm:
        engine._ensure_model()

    while True:
        payload = request_queue.get()
        if payload is None:
            break

        request_id = str(payload["request_id"])
        image = payload["image"]
        try:
            result = engine.recognize(image)
            response_queue.put(
                {
                    "request_id": request_id,
                    "ok": True,
                    "result": _serialize_result(result),
                }
            )
        except Exception as exc:
            response_queue.put(
                {
                    "request_id": request_id,
                    "ok": False,
                    "error_type": exc.__class__.__name__,
                    "error": str(exc),
                }
            )


class OCRProcessRunner:
    def __init__(self, config: OCRProcessConfig):
        self._config = config
        self._ctx = mp.get_context("spawn")
        self._lock = threading.RLock()
        self._process: mp.Process | None = None
        self._request_queue: Any = None
        self._response_queue: Any = None

    def start(self) -> None:
        with self._lock:
            self._ensure_started_locked()

    def close(self) -> None:
        with self._lock:
            self._stop_locked()

    def recognize(self, image: Any, timeout_s: float) -> OCRPageResult:
        with self._lock:
            self._ensure_started_locked()
            request_id = uuid4().hex
            submit_t0 = time.perf_counter()
            self._request_queue.put({"request_id": request_id, "image": image})
            try:
                payload = self._response_queue.get(timeout=timeout_s)
            except Empty as exc:
                running_ms = (time.perf_counter() - submit_t0) * 1000
                logger.error(
                    "OCR子进程执行超时 running_ms=%.2f timeout_s=%.2f，准备重启worker",
                    running_ms,
                    timeout_s,
                    extra={
                        "ocr_timeout_stage": "running",
                        "ocr_running_ms": round(running_ms, 2),
                        "timeout_s": timeout_s,
                        "ocr_backend": "process_worker",
                    },
                )
                self._restart_locked()
                raise TimeoutError(f"ocr 在执行阶段超过 {timeout_s} 秒超时") from exc

            if str(payload.get("request_id")) != request_id:
                logger.error(
                    "OCR子进程响应request_id不匹配 expected=%s actual=%s，准备重启worker",
                    request_id,
                    payload.get("request_id"),
                )
                self._restart_locked()
                raise RuntimeError("ocr worker returned mismatched response")

            if not bool(payload.get("ok")):
                error_type = str(payload.get("error_type") or "RuntimeError")
                error_message = str(payload.get("error") or "unknown ocr error")
                if error_type == "OCREngineUnavailable":
                    raise OCREngineUnavailable(error_message)
                raise RuntimeError(error_message)

            return _deserialize_result(payload["result"])

    def _ensure_started_locked(self) -> None:
        if self._process is not None and self._process.is_alive():
            return

        self._request_queue = self._ctx.Queue(maxsize=1)
        self._response_queue = self._ctx.Queue(maxsize=1)
        self._process = self._ctx.Process(
            target=_worker_main,
            args=(self._request_queue, self._response_queue, self._config),
            daemon=True,
            name="ocr-worker",
        )
        self._process.start()
        logger.info(
            "OCR子进程worker已启动 pid=%s prewarm=%s",
            self._process.pid,
            self._config.prewarm,
            extra={
                "ocr_backend": "process_worker",
                "ocr_worker_pid": self._process.pid,
                "ocr_prewarm": self._config.prewarm,
            },
        )

    def _restart_locked(self) -> None:
        self._stop_locked()
        self._ensure_started_locked()

    def _stop_locked(self) -> None:
        process = self._process
        request_queue = self._request_queue
        response_queue = self._response_queue

        self._process = None
        self._request_queue = None
        self._response_queue = None

        if request_queue is not None:
            try:
                request_queue.put_nowait(None)
            except Exception:
                pass

        if process is not None:
            process.join(timeout=1)
            if process.is_alive():
                process.terminate()
                process.join(timeout=5)
            if process.is_alive():
                process.kill()
                process.join(timeout=5)

        for queue_obj in (request_queue, response_queue):
            if queue_obj is None:
                continue
            try:
                queue_obj.close()
            except Exception:
                pass
            try:
                queue_obj.join_thread()
            except Exception:
                pass
