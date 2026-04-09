"""共享模型注册表，避免重复初始化远程依赖模型。"""

from __future__ import annotations

from pathlib import Path
from threading import RLock
from typing import Any


_LOCK = RLock()
_SENTENCE_MODELS: dict[tuple[str, str | None, bool], Any] = {}
_CROSS_ENCODERS: dict[tuple[str, str | None, bool], Any] = {}


def _cache_key(
    model_name: str, cache_dir: str | Path | None, local_files_only: bool
) -> tuple[str, str | None, bool]:
    cache_dir_str = str(cache_dir) if cache_dir else None
    return (model_name, cache_dir_str, local_files_only)


def get_sentence_transformer(
    model_name: str,
    *,
    cache_dir: str | Path | None = None,
    local_files_only: bool = False,
) -> Any:
    from sentence_transformers import SentenceTransformer  # type: ignore

    with _LOCK:
        key = _cache_key(model_name, cache_dir, local_files_only)
        model = _SENTENCE_MODELS.get(key)
        if model is None:
            try:
                model = SentenceTransformer(
                    model_name,
                    cache_folder=str(cache_dir) if cache_dir else None,
                    local_files_only=local_files_only,
                )
            except Exception as exc:
                mode = "offline" if local_files_only else "online"
                cache_hint = (
                    f", cache_dir={cache_dir}" if cache_dir else ", cache_dir=<default>"
                )
                raise RuntimeError(
                    f"加载 embedding 模型失败: model={model_name}, mode={mode}{cache_hint}"
                ) from exc
            _SENTENCE_MODELS[key] = model
        return model


def get_cross_encoder(
    model_name: str,
    *,
    cache_dir: str | Path | None = None,
    local_files_only: bool = False,
) -> Any:
    from sentence_transformers import CrossEncoder  # type: ignore

    with _LOCK:
        key = _cache_key(model_name, cache_dir, local_files_only)
        model = _CROSS_ENCODERS.get(key)
        if model is None:
            try:
                model = CrossEncoder(
                    model_name,
                    cache_folder=str(cache_dir) if cache_dir else None,
                    local_files_only=local_files_only,
                )
            except Exception as exc:
                mode = "offline" if local_files_only else "online"
                cache_hint = (
                    f", cache_dir={cache_dir}" if cache_dir else ", cache_dir=<default>"
                )
                raise RuntimeError(
                    f"加载 rerank 模型失败: model={model_name}, mode={mode}{cache_hint}"
                ) from exc
            _CROSS_ENCODERS[key] = model
        return model
