"""构建并配置 FastAPI 主应用，挂载中间件、路由与异常处理。"""
from __future__ import annotations

from fastapi import FastAPI

from app.core.config import settings
from app.core.errors import install_exception_handlers
from app.core.lifecycle import close_state, init_state
from app.core.logging import configure_logging
from app.routers.chat import router as chat_router
from app.routers.doc import router as doc_router
from app.routers.eval import router as eval_router
from app.routers.health import router as health_router
from app.routers.index import router as index_router
from app.routers.ingest import router as ingest_router


def create_app() -> FastAPI:
    configure_logging()
    app = FastAPI(title=settings.app_name, version=settings.app_version)
    install_exception_handlers(app)

    @app.on_event("startup")
    def _startup() -> None:
        init_state(app)

    @app.on_event("shutdown")
    def _shutdown() -> None:
        close_state(app)

    app.include_router(health_router)
    app.include_router(chat_router)
    app.include_router(ingest_router)
    app.include_router(index_router)
    app.include_router(doc_router)
    app.include_router(eval_router)
    return app


app = create_app()

