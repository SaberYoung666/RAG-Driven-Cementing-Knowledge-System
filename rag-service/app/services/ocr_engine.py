"""OCR 引擎抽象与 PaddleOCR 实现。"""

from __future__ import annotations

import inspect
from dataclasses import dataclass
import logging
import os
import time
from typing import Any, List, Mapping, Protocol

logger = logging.getLogger(__name__)


class OCREngine(Protocol):
    def recognize(self, image: Any) -> "OCRPageResult":
        """对单页图像进行 OCR 并返回结构化结果。"""


@dataclass
class OCRBlock:
    text: str
    confidence: float
    bbox: List[float]


@dataclass
class OCRPageResult:
    text: str
    confidence_avg: float
    blocks: List[OCRBlock]
    timing: Mapping[str, float] | None = None


class OCREngineUnavailable(RuntimeError):
    """OCR 依赖不可用时抛出。"""


class PaddleOCREngine:
    """PaddleOCR 封装，使用懒加载减少启动开销。"""

    def __init__(
        self,
        lang: str = "ch",
        use_angle_cls: bool = True,
        enable_mkldnn: bool = False,
        cpu_threads: int | None = None,
        init_kwargs: Mapping[str, Any] | None = None,
    ):
        # OCR语言
        self.lang = lang
        # 是否启用文字方向分类器，默认为 True 可提升竖排文本的识别效果
        self.use_angle_cls = use_angle_cls
        # Windows/CPU + Paddle 3.3.x 在部分 OCR 模型上会触发 oneDNN/PIR 兼容问题，默认关闭 MKL-DNN。
        self.enable_mkldnn = enable_mkldnn
        self.cpu_threads = cpu_threads
        self.init_kwargs = dict(init_kwargs or {})
        # 内部 OCR 模型实例，初始为 None，首次调用时加载
        self._ocr = None

    @staticmethod
    def _prepare_environment() -> None:
        # 某些环境下 PaddleX 会在初始化时检查模型源连通性，导致启动长时间阻塞。
        os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")

    # 确保OCR模型已加载
    def _ensure_model(self) -> None:
        if self._ocr is not None:
            return
        self._prepare_environment()
        try:
            from paddleocr import PaddleOCR
        except Exception as exc:
            logger.error(
                "PaddleOCR 模块加载失败，OCR 功能不可用。请确保已正确安装 paddleocr 包及其依赖。错误详情：%s",
                str(exc),
            )
            raise OCREngineUnavailable(
                "PaddleOCR 不可用，请确保已正确安装 paddleocr 包及其依赖"
            ) from exc
        try:
            self._ocr = PaddleOCR(**self._build_init_kwargs(PaddleOCR))
        except Exception as exc:
            logger.error("PaddleOCR 初始化失败：%s", str(exc))
            raise OCREngineUnavailable(f"PaddleOCR 初始化失败: {exc}") from exc

    def _build_init_kwargs(self, paddle_ocr_cls: Any) -> dict[str, Any]:
        params = inspect.signature(paddle_ocr_cls.__init__).parameters
        kwargs: dict[str, Any] = {
            "lang": self.lang,
            "enable_mkldnn": self.enable_mkldnn,
        }
        if self.cpu_threads is not None:
            kwargs["cpu_threads"] = self.cpu_threads
        for key, value in self.init_kwargs.items():
            if value is not None:
                kwargs[key] = value
        if "show_log" in params:
            kwargs["show_log"] = False
        if "use_angle_cls" in params:
            kwargs["use_angle_cls"] = self.use_angle_cls
        elif "use_textline_orientation" in params:
            kwargs["use_textline_orientation"] = self.use_angle_cls
        return kwargs

    def _run_ocr(self, image: Any) -> Any:
        predict = getattr(self._ocr, "predict", None)
        if callable(predict):
            params = inspect.signature(predict).parameters
            kwargs: dict[str, Any] = {}
            if "use_textline_orientation" in params:
                kwargs["use_textline_orientation"] = self.use_angle_cls
            return predict(image, **kwargs)
        return self._ocr.ocr(image, cls=self.use_angle_cls)

    @staticmethod
    def _get_value(payload: Any, key: str) -> Any:
        if isinstance(payload, Mapping):
            return payload.get(key)
        getter = getattr(payload, "get", None)
        if callable(getter):
            try:
                return getter(key)
            except Exception:
                pass
        try:
            return payload[key]
        except Exception:
            return getattr(payload, key, None)

    @staticmethod
    def _flatten_bbox(points: Any) -> List[float]:
        bbox: List[float] = []
        if points is None:
            return bbox
        for point in points:
            if isinstance(point, (list, tuple)) and len(point) >= 2:
                bbox.extend([float(point[0]), float(point[1])])
                continue
            try:
                x = point[0]
                y = point[1]
            except Exception:
                continue
            bbox.extend([float(x), float(y)])
        return bbox

    def _parse_legacy_lines(self, lines: Any) -> List[OCRBlock]:
        blocks: List[OCRBlock] = []
        for line in lines or []:
            if not line or len(line) < 2:
                continue
            points = line[0] or []
            payload = line[1] or ("", 0.0)
            txt = str(payload[0] or "").strip()
            conf = float(payload[1] or 0.0)
            if not txt:
                continue
            blocks.append(
                OCRBlock(
                    text=txt,
                    confidence=conf,
                    bbox=self._flatten_bbox(points),
                )
            )
        return blocks

    def _parse_result_item(self, item: Any) -> List[OCRBlock]:
        rec_texts = self._get_value(item, "rec_texts")
        if rec_texts is None:
            if isinstance(item, list):
                return self._parse_legacy_lines(item)
            return []

        rec_scores = self._get_value(item, "rec_scores") or []
        rec_polys = self._get_value(item, "rec_polys")
        if rec_polys is None:
            rec_polys = self._get_value(item, "dt_polys") or []

        blocks: List[OCRBlock] = []
        for idx, raw_text in enumerate(rec_texts):
            txt = raw_text[0] if isinstance(raw_text, tuple) else raw_text
            txt = str(txt or "").strip()
            if not txt:
                continue
            conf = float(rec_scores[idx] or 0.0) if idx < len(rec_scores) else 0.0
            points = rec_polys[idx] if idx < len(rec_polys) else []
            blocks.append(
                OCRBlock(
                    text=txt,
                    confidence=conf,
                    bbox=self._flatten_bbox(points),
                )
            )
        return blocks

    # OCR识别图像的方法
    def recognize(self, image: Any) -> OCRPageResult:
        t0 = time.perf_counter()
        cold_start = self._ocr is None

        ensure_t0 = time.perf_counter()
        self._ensure_model()
        ensure_ms = (time.perf_counter() - ensure_t0) * 1000

        infer_t0 = time.perf_counter()
        raw = self._run_ocr(image)
        infer_ms = (time.perf_counter() - infer_t0) * 1000

        parse_t0 = time.perf_counter()
        blocks: List[OCRBlock] = []
        if isinstance(raw, list):
            if raw and isinstance(raw[0], list) and raw[0] and isinstance(raw[0][0], list):
                blocks = self._parse_legacy_lines(raw[0])
            else:
                for item in raw:
                    blocks.extend(self._parse_result_item(item))
        else:
            blocks = self._parse_result_item(raw)
        parse_ms = (time.perf_counter() - parse_t0) * 1000

        text_parts = [block.text for block in blocks]
        conf_sum = sum(block.confidence for block in blocks)
        avg = conf_sum / len(blocks) if blocks else 0.0
        total_ms = (time.perf_counter() - t0) * 1000
        image_h = float(getattr(image, "shape", [0, 0])[0]) if hasattr(image, "shape") else 0.0
        image_w = float(getattr(image, "shape", [0, 0])[1]) if hasattr(image, "shape") else 0.0
        timing = {
            "cold_start": 1.0 if cold_start else 0.0,
            "ensure_ms": round(ensure_ms, 2),
            "infer_ms": round(infer_ms, 2),
            "parse_ms": round(parse_ms, 2),
            "total_ms": round(total_ms, 2),
            "image_h": image_h,
            "image_w": image_w,
            "block_count": float(len(blocks)),
        }
        logger.info(
            "OCR阶段耗时 cold_start=%s ensure_ms=%.2f infer_ms=%.2f parse_ms=%.2f total_ms=%.2f blocks=%s size=%sx%s",
            cold_start,
            ensure_ms,
            infer_ms,
            parse_ms,
            total_ms,
            len(blocks),
            int(image_w),
            int(image_h),
            extra={"ocr_timing": timing},
        )
        return OCRPageResult(
            text="\n".join(text_parts).strip(),
            confidence_avg=avg,
            blocks=blocks,
            timing=timing,
        )
