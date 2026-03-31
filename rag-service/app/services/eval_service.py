"""实现检索与生成效果评测的核心业务逻辑。"""
from __future__ import annotations

import csv
from datetime import datetime
import json
from pathlib import Path
from typing import Any, Dict, List, Tuple

from app.core.config import PROJECT_ROOT, Settings
from app.schemas.chat import ChatRequest
from app.services.rag_service import RAGService


class EvalService:
    def __init__(self, settings: Settings):
        self.settings = settings

    def batch_eval(self, rag_service: RAGService, questions_path: str, top_k: int, candidate_k: int, alpha: float) -> Dict[str, Any]:
        qpath = PROJECT_ROOT / questions_path
        questions = self._load_questions(qpath)

        out_dir = PROJECT_ROOT / "evaluation"
        out_dir.mkdir(parents=True, exist_ok=True)
        out_path = out_dir / f"results_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"

        fields = [
            "qid",
            "type",
            "question",
            "mode",
            "alpha",
            "rerank_on",
            "candidate_k",
            "top_k",
            "retrieval_ms",
            "top1_score",
            "top1_source",
            "top1_page",
            "top1_section",
            "top1_chunk_id",
        ]

        rows = 0
        with out_path.open("w", encoding="utf-8", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=fields)
            writer.writeheader()
            for q in questions:
                for mode in ("dense", "hybrid"):
                    for rerank_on in (False, True):
                        req = ChatRequest(
                            query=q["question"],
                            mode=mode,
                            topR=top_k,
                            candidateK=candidate_k,
                            alpha=alpha,
                            rerankOn=rerank_on,
                            useLlm=False,
                            returnChunks=True,
                        )
                        resp = rag_service.chat(req)
                        top1 = resp.retrieved[0] if resp.retrieved else None
                        writer.writerow(
                            {
                                "qid": q.get("id", ""),
                                "type": q.get("type", ""),
                                "question": q.get("question", ""),
                                "mode": mode,
                                "alpha": alpha if mode == "hybrid" else "",
                                "rerank_on": rerank_on,
                                "candidate_k": candidate_k,
                                "top_k": top_k,
                                "retrieval_ms": resp.debug.retrieval_ms,
                                "top1_score": top1.score if top1 else "",
                                "top1_source": (top1.metadata or {}).get("source") if top1 else "",
                                "top1_page": (top1.metadata or {}).get("page") if top1 else "",
                                "top1_section": (top1.metadata or {}).get("section") if top1 else "",
                                "top1_chunk_id": top1.chunk_id if top1 else "",
                            }
                        )
                        rows += 1
        return {"result_csv": str(out_path), "rows": rows}

    def summarize(self, results_path: str | None = None) -> Dict[str, Any]:
        rpath = Path(results_path) if results_path else self._latest_results()
        with rpath.open("r", encoding="utf-8") as fh:
            reader = csv.DictReader(fh)
            rows = list(reader)

        overall = self._aggregate(rows, group_keys=("mode", "rerank_on"))
        by_type = self._aggregate(rows, group_keys=("type", "mode", "rerank_on"))

        out_dir = PROJECT_ROOT / "evaluation"
        out_dir.mkdir(parents=True, exist_ok=True)
        overall_csv = out_dir / "summary_overall.csv"
        by_type_csv = out_dir / "summary_by_type.csv"
        summary_md = out_dir / "summary_text.md"

        self._write_csv(overall_csv, overall)
        self._write_csv(by_type_csv, by_type)
        summary_md.write_text(self._to_markdown(rpath.name, overall), encoding="utf-8")
        return {"overall_csv": str(overall_csv), "by_type_csv": str(by_type_csv), "summary_md": str(summary_md)}

    @staticmethod
    def _load_questions(path: Path) -> List[Dict[str, Any]]:
        out = []
        with path.open("r", encoding="utf-8") as fh:
            for line in fh:
                if line.strip():
                    out.append(json.loads(line))
        return out

    @staticmethod
    def _write_csv(path: Path, rows: List[Dict[str, Any]]) -> None:
        if not rows:
            path.write_text("", encoding="utf-8")
            return
        with path.open("w", encoding="utf-8", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
            writer.writeheader()
            writer.writerows(rows)

    @staticmethod
    def _aggregate(rows: List[Dict[str, str]], group_keys: Tuple[str, ...]) -> List[Dict[str, Any]]:
        groups: Dict[Tuple[str, ...], List[Dict[str, str]]] = {}
        for row in rows:
            key = tuple(str(row.get(k, "")) for k in group_keys)
            groups.setdefault(key, []).append(row)

        result: List[Dict[str, Any]] = []
        for key, items in groups.items():
            scores = []
            retrievals = []
            hit = 0
            for r in items:
                if str(r.get("top1_chunk_id", "")):
                    hit += 1
                try:
                    scores.append(float(r.get("top1_score", "")))
                except Exception:
                    pass
                try:
                    retrievals.append(float(r.get("retrieval_ms", "")))
                except Exception:
                    pass
            row: Dict[str, Any] = {k: v for k, v in zip(group_keys, key)}
            row["n"] = len(items)
            row["mean_top1"] = round(sum(scores) / len(scores), 4) if scores else ""
            row["has_top1_rate"] = round(hit / len(items), 4) if items else 0.0
            row["mean_retrieval_ms"] = round(sum(retrievals) / len(retrievals), 2) if retrievals else ""
            result.append(row)
        result.sort(key=lambda x: tuple(str(x.get(k, "")) for k in group_keys))
        return result

    @staticmethod
    def _to_markdown(src_name: str, overall: List[Dict[str, Any]]) -> str:
        lines = [f"# 自动评测汇总（基于 {src_name}）", "", "## 总体对比（mode x rerank_on）", ""]
        for row in overall:
            lines.append(
                f"- mode={row.get('mode')}, rerank_on={row.get('rerank_on')}, "
                f"n={row.get('n')}, mean_top1={row.get('mean_top1')}, "
                f"has_top1_rate={row.get('has_top1_rate')}, mean_retrieval_ms={row.get('mean_retrieval_ms')}"
            )
        lines.append("")
        return "\n".join(lines)

    @staticmethod
    def _latest_results() -> Path:
        eval_dir = PROJECT_ROOT / "evaluation"
        files = sorted(eval_dir.glob("results_*.csv"), key=lambda p: p.stat().st_mtime, reverse=True)
        if not files:
            raise FileNotFoundError(f"No results_*.csv under {eval_dir}")
        return files[0]

