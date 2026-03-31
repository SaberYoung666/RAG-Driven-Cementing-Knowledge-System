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
