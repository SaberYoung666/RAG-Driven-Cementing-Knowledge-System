"""定义数据导入流程使用的请求参数与返回模型。"""
from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field


class IngestRunRequest(BaseModel):
    input_dir: Optional[str] = Field(default=None, validation_alias="inputDir")
    file_paths: list[str] = Field(default_factory=list, validation_alias="filePaths")
    strategy: Optional[Literal["window_only", "structure_then_window"]] = None
    enable_ocr: Literal["auto", "on", "off"] = Field(default="auto", validation_alias="enableOcr")
    strict_ocr: bool = Field(default=False, validation_alias="strictOcr")
    rebuild_faiss: bool = Field(default=True, validation_alias="rebuildFaiss")
    rebuild_bm25: bool = Field(default=True, validation_alias="rebuildBm25")
    incremental: bool = Field(default=False, validation_alias="incremental")


class IngestRunResponse(BaseModel):
    chunks_path: str
    files: int
    chunks: int
    total_chunks: int = 0
    chunks_by_source: dict[str, int] = Field(default_factory=dict)
    doc_stats: dict[str, dict] = Field(default_factory=dict)
    faiss_built: bool
    bm25_built: bool

