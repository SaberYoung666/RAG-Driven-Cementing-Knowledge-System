"""注册应用生命周期钩子，管理启动与关闭阶段的资源初始化和释放。"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict

from fastapi import FastAPI

from app.core.config import settings
from app.services.eval_service import EvalService
from app.services.backend_callback_client import BackendCallbackClient
from app.services.ingest_job_manager import IngestJobManager
from app.services.index_service import IndexService
from app.services.ingest_service import IngestService
from app.services.model_registry import get_cross_encoder, get_sentence_transformer
from app.services.rag_service import RAGService

# 创建日志记录器，记录器名称为当前模块名，日志级别和格式由全局配置决定。
logger = logging.getLogger(__name__)


def _prewarm_models() -> None:
    if settings.model_cache_dir is not None:
        settings.model_cache_dir.mkdir(parents=True, exist_ok=True)

    get_sentence_transformer(
        settings.embedding_model,
        cache_dir=settings.model_cache_dir,
        local_files_only=settings.hf_local_files_only,
    )

    if settings.rerank_enabled:
        get_cross_encoder(
            settings.rerank_model,
            cache_dir=settings.model_cache_dir,
            local_files_only=settings.hf_local_files_only,
        )

# 资源初始化。在应用启动时创建并初始化各种服务实例，并将它们存储在 app.state.services 中，供后续请求处理使用。
def init_state(app: FastAPI) -> Dict[str, Any]:
    if settings.prewarm_models:
        try:
            _prewarm_models()
            logger.info(
                "模型预热完成: embedding=%s rerank=%s offline=%s cache_dir=%s",
                settings.embedding_model,
                settings.rerank_model if settings.rerank_enabled else "<disabled>",
                settings.hf_local_files_only,
                settings.model_cache_dir or "<default>",
            )
        except Exception:
            logger.exception("模型预热失败")
            if settings.prewarm_fail_fast:
                raise

    index_service = IndexService(settings)
    ingest_service = IngestService(settings)
    job_store_path = Path(settings.config.get("ingestion", {}).get("job_store_path", "data/ingest_jobs.json"))
    if not job_store_path.is_absolute():
        job_store_path = (Path(__file__).resolve().parents[2] / job_store_path).resolve()
    ingest_job_manager = IngestJobManager(job_store_path)
    eval_service = EvalService(settings)
    rag_service = RAGService(settings)
    backend_callback_client = BackendCallbackClient(settings)
    rag_service.reload_assets(silent=True)

    state: Dict[str, Any] = {
        "settings": settings,
        "index_service": index_service,
        "ingest_service": ingest_service,
        "ingest_job_manager": ingest_job_manager,
        "eval_service": eval_service,
        "rag_service": rag_service,
        "backend_callback_client": backend_callback_client,
    }
    app.state.services = state
    logger.info("资源初始化完成")
    return state


def close_state(app: FastAPI) -> None:
    services = getattr(app.state, "services", {}) or {}
    ingest_service = services.get("ingest_service")
    if ingest_service is not None and hasattr(ingest_service, "close"):
        try:
            ingest_service.close()
        except Exception:
            logger.exception("释放OCR资源失败")
    backend_callback_client = services.get("backend_callback_client")
    if backend_callback_client is not None and hasattr(backend_callback_client, "close"):
        try:
            backend_callback_client.close()
        except Exception:
            logger.exception("释放backend回调客户端失败")
