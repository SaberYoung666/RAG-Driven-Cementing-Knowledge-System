"""索引管理接口路由，处理索引构建与检索配置相关请求。"""
from __future__ import annotations

from fastapi import APIRouter, Request

from app.schemas.index import BuildBm25Request, BuildFaissRequest, IndexBuildResponse, IndexStatusResponse


router = APIRouter(prefix="/rag/v1/index", tags=["index"])


@router.get("/status", response_model=IndexStatusResponse)
def index_status(request: Request):
    svc = request.app.state.services["index_service"]
    return svc.status()


@router.post("/build/faiss", response_model=IndexBuildResponse)
def build_faiss(req: BuildFaissRequest, request: Request):
    svc = request.app.state.services["index_service"]
    rag = request.app.state.services["rag_service"]
    result = svc.build_faiss(chunks_path=req.chunks_path)
    rag.reload_assets(silent=False)
    return IndexBuildResponse(**result)


@router.post("/build/bm25", response_model=IndexBuildResponse)
def build_bm25(req: BuildBm25Request, request: Request):
    svc = request.app.state.services["index_service"]
    rag = request.app.state.services["rag_service"]
    result = svc.build_bm25(chunks_path=req.chunks_path)
    rag.reload_assets(silent=False)
    return IndexBuildResponse(**result)

