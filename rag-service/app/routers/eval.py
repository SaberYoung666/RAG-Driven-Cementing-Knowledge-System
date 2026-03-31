"""评测相关接口路由，提供评测任务触发与结果查询能力。"""
from __future__ import annotations

from fastapi import APIRouter, Request

from app.schemas.eval import EvalBatchRequest, EvalBatchResponse, EvalSummarizeRequest, EvalSummarizeResponse


router = APIRouter(prefix="/rag/v1/eval", tags=["eval"])


@router.post("/batch", response_model=EvalBatchResponse)
def batch_eval(req: EvalBatchRequest, request: Request):
    eval_service = request.app.state.services["eval_service"]
    rag_service = request.app.state.services["rag_service"]
    result = eval_service.batch_eval(
        rag_service=rag_service,
        questions_path=req.questions_path,
        top_k=req.top_k,
        candidate_k=req.candidate_k,
        alpha=req.alpha,
    )
    return EvalBatchResponse(**result)


@router.post("/summarize", response_model=EvalSummarizeResponse)
def summarize_eval(req: EvalSummarizeRequest, request: Request):
    eval_service = request.app.state.services["eval_service"]
    result = eval_service.summarize(results_path=req.results_path)
    return EvalSummarizeResponse(**result)

