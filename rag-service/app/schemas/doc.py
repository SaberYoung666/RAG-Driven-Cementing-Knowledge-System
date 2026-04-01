"""定义文档处理接口的数据模型。"""
from __future__ import annotations

from typing import Literal

from pydantic import AliasChoices, BaseModel, Field


IngestStatus = Literal[
    "queued",
    "processing",
    "ocr_processing",
    "cleaning",
    "splitting",
    "indexing",
    "done",
    "failed",
]


# 定义文档类
class ProcessDocItem(BaseModel):
    doc_id: str = Field(validation_alias=AliasChoices("doc_id", "docId"))
    file_path: str = Field(validation_alias=AliasChoices("file_path", "filePath"))
    source_name: str | None = Field(default=None, validation_alias=AliasChoices("source_name", "sourceName"))


# 定义文档处理状态类
class IngestDocStatus(BaseModel):
    doc_id: str
    status: IngestStatus
    progress: int = 0
    stage_progress: int = 0
    pages_processed: int = 0
    total_pages: int | None = None
    current_page: int | None = None
    ocr_pages: int = 0
    message: str = ""
    chunk_count: int = 0
    error: str | None = None
    failed_pages: list[int] = Field(default_factory=list)
    started_at: str | None = None
    finished_at: str | None = None
    updated_at: str | None = None
    elapsed_ms: int | None = None


# 定义文档处理请求
class DocsProcessRequest(BaseModel):
    user_id: int | None = Field(default=None, validation_alias=AliasChoices("user_id", "userId"))
    doc_ids: list[str] = Field(default_factory=list, validation_alias=AliasChoices("doc_ids", "docIds"))
    docs: list[ProcessDocItem] = Field(default_factory=list)
    # TODO：后端无该参数
    strategy: str | None = None
    enable_ocr: Literal["auto", "on", "off"] = Field(default="auto", validation_alias=AliasChoices("enable_ocr", "enableOcr"))
    strict_ocr: bool = Field(default=False, validation_alias=AliasChoices("strict_ocr", "strictOcr"))
    rebuild_faiss: bool = Field(default=True, validation_alias=AliasChoices("rebuild_faiss", "rebuildFaiss"))
    rebuild_bm25: bool = Field(default=True, validation_alias=AliasChoices("rebuild_bm25", "rebuildBm25"))


# 定义文档处理响应类
class DocsProcessResponse(BaseModel):
    ok: bool = True
    # 兼容旧结构字段
    files: int = 0
    chunks: int = 0
    total_chunks: int = 0
    chunks_by_source: dict[str, int] = Field(default_factory=dict)
    processed_doc_ids: list[str] = Field(default_factory=list)
    # 新结构字段
    statuses: list[IngestDocStatus] = Field(default_factory=list)
    doc_id: str | None = None
    status: str | None = None
    pages_processed: int = 0
    ocr_pages: int = 0
    message: str = "queued"
