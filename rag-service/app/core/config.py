"""负责读取与解析系统配置，提供统一的配置对象访问接口。"""

from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
from typing import Any, Dict

import yaml


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG_PATH = PROJECT_ROOT / "config" / "config.yaml"
LEGACY_CONFIG_PATH = PROJECT_ROOT / "configs" / "config.yaml"


def _deep_get(data: Dict[str, Any], path: str, default: Any) -> Any:
    cur: Any = data
    for part in path.split("."):
        if not isinstance(cur, dict) or part not in cur:
            return default
        cur = cur[part]
    return cur


def _to_bool(value: Any, default: bool) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


def _resolve_path(value: str | None) -> Path | None:
    if not value:
        return None
    path = Path(value)
    if not path.is_absolute():
        path = PROJECT_ROOT / path
    return path.resolve()


@dataclass(frozen=True)
class Settings:
    app_name: str
    app_version: str
    config_path: Path
    config: Dict[str, Any]

    @property
    def index_dir(self) -> Path:
        return PROJECT_ROOT / str(
            _deep_get(self.config, "indexing.index_dir", "data/index")
        )

    @property
    def chunks_path(self) -> Path:
        return PROJECT_ROOT / str(
            _deep_get(self.config, "indexing.chunks_path", "data/chunks.jsonl")
        )

    @property
    def embedding_model(self) -> str:
        return str(
            _deep_get(
                self.config,
                "indexing.embedding_model",
                "sentence-transformers/all-MiniLM-L6-v2",
            )
        )

    @property
    def normalize_embeddings(self) -> bool:
        return bool(_deep_get(self.config, "indexing.normalize_embeddings", True))

    @property
    def rerank_enabled(self) -> bool:
        return bool(_deep_get(self.config, "rerank.enabled", True))

    @property
    def rerank_model(self) -> str:
        return str(
            _deep_get(
                self.config,
                "rerank.model",
                "cross-encoder/ms-marco-MiniLM-L-6-v2",
            )
        )

    @property
    def model_cache_dir(self) -> Path | None:
        value = os.getenv("RAG_MODEL_CACHE_DIR")
        if value:
            return _resolve_path(value.strip())
        cfg_value = str(_deep_get(self.config, "models.cache_dir", "")).strip()
        return _resolve_path(cfg_value)

    @property
    def hf_local_files_only(self) -> bool:
        value = os.getenv("RAG_HF_LOCAL_FILES_ONLY")
        if value is not None:
            return _to_bool(value, False)
        return _to_bool(_deep_get(self.config, "models.local_files_only", False), False)

    @property
    def prewarm_models(self) -> bool:
        value = os.getenv("RAG_PREWARM_MODELS")
        if value is not None:
            return _to_bool(value, True)
        return _to_bool(_deep_get(self.config, "models.prewarm_on_startup", True), True)

    @property
    def prewarm_fail_fast(self) -> bool:
        value = os.getenv("RAG_PREWARM_FAIL_FAST")
        if value is not None:
            return _to_bool(value, False)
        return _to_bool(_deep_get(self.config, "models.prewarm_fail_fast", False), False)

    @property
    def expose_error_details(self) -> bool:
        return bool(_deep_get(self.config, "debug.expose_error_details", False))

    @property
    def backend_callback_base_url(self) -> str | None:
        value = os.getenv("BACKEND_CALLBACK_BASE_URL")
        if value:
            return value.strip()
        cfg_value = _deep_get(self.config, "backend.callback_base_url", "")
        return str(cfg_value).strip() or None

    @property
    def backend_docs_status_callback_path(self) -> str:
        value = os.getenv("BACKEND_DOCS_STATUS_CALLBACK_PATH")
        if value:
            return value.strip()
        return str(
            _deep_get(
                self.config,
                "backend.docs_status_callback_path",
                "/api/internal/rag/docs/status",
            )
        ).strip()

    @property
    def backend_callback_secret(self) -> str | None:
        value = os.getenv("BACKEND_CALLBACK_SECRET")
        if value:
            return value.strip()
        cfg_value = _deep_get(self.config, "backend.callback_secret", "")
        return str(cfg_value).strip() or None

    @property
    def backend_callback_timeout_seconds(self) -> float:
        value = os.getenv("BACKEND_CALLBACK_TIMEOUT_SECONDS")
        if value:
            try:
                return float(value)
            except ValueError:
                pass
        return float(
            _deep_get(self.config, "backend.callback_timeout_seconds", 5.0)
        )

# 载入设置，该设置将用于全局
def load_settings() -> Settings:
    default_path = DEFAULT_CONFIG_PATH if DEFAULT_CONFIG_PATH.exists() else LEGACY_CONFIG_PATH
    cfg_path = Path(os.getenv("RAG_CONFIG_PATH", str(default_path)))
    if cfg_path.exists():
        cfg = yaml.safe_load(cfg_path.read_text(encoding="utf-8")) or {}
    else:
        cfg = {}

    return Settings(
        app_name=os.getenv("RAG_APP_NAME", "RAG Microservice"),
        app_version=os.getenv("RAG_APP_VERSION", "2.0.0"),
        config_path=cfg_path,
        config=cfg,
    )


settings = load_settings()
