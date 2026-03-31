"""健康检查路由，用于服务存活与就绪状态探测。"""
from __future__ import annotations

from fastapi import APIRouter, Request

router = APIRouter(prefix="/rag/v1", tags=["health"])

@router.get("/health")
def health(request: Request):
    rag_service = request.app.state.services["rag_service"]
    return rag_service.health()

@router.get("/config")
def config_view(request: Request):
    settings = request.app.state.services["settings"]
    return {"config_path": str(settings.config_path), "config": settings.config}

