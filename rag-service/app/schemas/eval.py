"""定义评测接口使用的请求与结果数据模型。"""
from __future__ import annotations

from pydantic import BaseModel, Field


class EvalBatchRequest(BaseModel):
    questions_path: str = Field(default="evaluation/questions.jsonl", validation_alias="questionsPath")
    top_k: int = Field(default=6, validation_alias="topK", ge=1, le=50)
    candidate_k: int = Field(default=20, validation_alias="candidateK", ge=1, le=200)
    alpha: float = Field(default=0.5, ge=0.0, le=1.0)


class EvalBatchResponse(BaseModel):
    result_csv: str
    rows: int


class EvalSummarizeRequest(BaseModel):
    results_path: str | None = Field(default=None, validation_alias="resultsPath")


class EvalSummarizeResponse(BaseModel):
    overall_csv: str
    by_type_csv: str
    summary_md: str

