"""聊天问答接口路由，负责接收请求并调用 RAG 服务返回答案。"""
from __future__ import annotations

from fastapi import APIRouter, Request

from app.schemas.chat import ChatRequest, ChatResponse, RerankRequest, RetrieveRequest, RetrievedChunk
from app.services.common import Retrieved

# 定义聊天相关的 API 路由，前缀为 /rag/v1，标签为 chat。
router = APIRouter(prefix="/rag/v1", tags=["chat"])

# 聊天回答接口，接收用户问题并返回答案。
@router.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest, request: Request):
    rag_service = request.app.state.services["rag_service"]
    return rag_service.chat(req)

# 检索证据片段接口，接收用户查询并返回相关的证据片段列表。
@router.post("/retrieve", response_model=list[RetrievedChunk])
def retrieve(req: RetrieveRequest, request: Request):
    rag_service = request.app.state.services["rag_service"]
    rows = rag_service.retrieve(req.query, req.mode, req.top_k, req.alpha)
    return [RetrievedChunk(chunk_id=r.chunk_id, score=r.score, text=r.text, metadata=r.metadata) for r in rows]

# 对片段重排序接口，接收用户查询和待重排序的片段列表，并返回重排序后的片段列表。
@router.post("/rerank", response_model=list[RetrievedChunk])
def rerank(req: RerankRequest, request: Request):
    rag_service = request.app.state.services["rag_service"]
    rows = [Retrieved(chunk_id=x.chunk_id, score=x.score, text=x.text, metadata=x.metadata) for x in req.chunks]
    out = rag_service.rerank(req.query, rows, req.final_k)
    return [RetrievedChunk(chunk_id=r.chunk_id, score=r.score, text=r.text, metadata=r.metadata) for r in out]

