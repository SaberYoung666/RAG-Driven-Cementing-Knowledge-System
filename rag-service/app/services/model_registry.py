"""共享模型注册表，避免重复初始化远程依赖模型。"""

from __future__ import annotations

from threading import RLock
from typing import Any


_LOCK = RLock()
_SENTENCE_MODELS: dict[str, Any] = {}
_CROSS_ENCODERS: dict[str, Any] = {}


def get_sentence_transformer(model_name: str) -> Any:
    from sentence_transformers import SentenceTransformer  # type: ignore

    with _LOCK:
        model = _SENTENCE_MODELS.get(model_name)
        if model is None:
            model = SentenceTransformer(model_name)
            _SENTENCE_MODELS[model_name] = model
        return model


def get_cross_encoder(model_name: str) -> Any:
    from sentence_transformers import CrossEncoder  # type: ignore

    with _LOCK:
        model = _CROSS_ENCODERS.get(model_name)
        if model is None:
            model = CrossEncoder(model_name)
            _CROSS_ENCODERS[model_name] = model
        return model
