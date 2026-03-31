"""提供服务层通用工具、共享数据结构与辅助函数。"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List


@dataclass
# 定义检索结果的数据结构
class Retrieved:
    chunk_id: str
    score: float
    text: str
    metadata: Dict[str, Any]


def tokenize_zh(text: str) -> List[str]:
    try:
        import jieba  # type: ignore
    except Exception:
        return [t for t in text.replace("\n", " ").split(" ") if t]
    return [tok.strip() for tok in jieba.lcut((text or "").replace("\n", " ")) if tok.strip()]


def minmax_norm(scores: List[float]) -> List[float]:
    if not scores:
        return []
    lo, hi = min(scores), max(scores)
    if abs(hi - lo) < 1e-9:
        return [0.0 for _ in scores]
    return [(x - lo) / (hi - lo) for x in scores]

