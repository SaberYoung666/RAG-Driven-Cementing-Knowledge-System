"""定义索引接口相关的数据模型与参数结构。"""
from __future__ import annotations

from pydantic import BaseModel, Field


class BuildFaissRequest(BaseModel):
    chunks_path: str | None = Field(default=None, validation_alias="chunksPath")


class BuildBm25Request(BaseModel):
    chunks_path: str | None = Field(default=None, validation_alias="chunksPath")


class IndexBuildResponse(BaseModel):
    ok: bool
    message: str


class IndexStatusResponse(BaseModel):
    index_ready: bool
    bm25_ready: bool
    meta: dict = Field(default_factory=dict)

