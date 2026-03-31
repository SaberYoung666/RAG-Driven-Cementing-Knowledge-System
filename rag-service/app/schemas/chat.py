"""定义聊天接口的请求体与响应体数据模型。"""
from __future__ import annotations

from typing import Any, Dict, List, Literal, Optional

from pydantic import AliasChoices, BaseModel, Field


class RetrievedChunk(BaseModel):
    chunk_id: str
    score: float
    text: str
    metadata: Dict[str, Any] = Field(default_factory=dict)


class Citation(BaseModel):
    evidence_id: str
    score: float
    doc_id: Optional[str] = None
    chunk_id: str
    source: Optional[str] = None
    page: Optional[int] = None
    section: Optional[str] = None


class ChatRequest(BaseModel):
    query: str = Field(min_length=1)
    mode: Literal["dense", "hybrid"] = "hybrid"
    # top_r的值决定了最终用于生成答案的证据片段数量
    top_r: int = Field(default=6, validation_alias=AliasChoices("top_r", "topR"), ge=1, le=50)
    candidate_k: int = Field(default=20, validation_alias=AliasChoices("candidate_k", "candidateK"), ge=1, le=200)
    alpha: float = Field(default=0.5, ge=0.0, le=1.0)
    rerank_on: bool = Field(default=True, validation_alias=AliasChoices("rerank_on", "rerankOn"))
    min_score: float = Field(default=0.2, validation_alias=AliasChoices("min_score", "minScore"))
    use_llm: bool = Field(default=True, validation_alias=AliasChoices("use_llm", "useLlm"))
    return_chunks: bool = Field(default=True, validation_alias=AliasChoices("return_chunks", "returnChunks"))


class RetrieveRequest(BaseModel):
    query: str = Field(min_length=1)
    mode: Literal["dense", "hybrid"] = "hybrid"
    top_k: int = Field(default=20, validation_alias=AliasChoices("top_k", "topK"), ge=1, le=200)
    alpha: float = Field(default=0.5, ge=0.0, le=1.0)


class RerankRequest(BaseModel):
    query: str = Field(min_length=1)
    final_k: int = Field(default=6, validation_alias=AliasChoices("final_k", "finalK"), ge=1, le=50)
    chunks: List[RetrievedChunk] = Field(default_factory=list)


class ChatDebug(BaseModel):
    retrieval_ms: float = 0.0
    rerank_ms: float = 0.0
    gen_ms: float = 0.0
    mode: str = ""
    top1_score: Optional[float] = None


class ChatResponse(BaseModel):
    answer: str
    refused: bool
    citations: List[Citation] = Field(default_factory=list)
    retrieved: List[RetrievedChunk] = Field(default_factory=list)
    debug: ChatDebug

