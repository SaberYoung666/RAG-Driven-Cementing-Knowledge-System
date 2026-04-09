"""实现 RAG 主流程，串联召回、重排与答案生成。"""

from __future__ import annotations

import json
import os
from pathlib import Path
import time
from typing import Any, Dict, List

from app.core.config import Settings
from app.core.errors import ServiceError
from app.schemas.chat import (
    ChatDebug,
    ChatRequest,
    ChatResponse,
    Citation,
    RetrievedChunk,
)
from app.services.model_registry import get_cross_encoder, get_sentence_transformer
from app.services.common import Retrieved, minmax_norm, tokenize_zh


SYSTEM_PROMPT = """\
你是固井工程问答助手。

请严格遵守以下规则：
1. 只能基于已提供的证据回答，禁止补充未在证据中出现的事实、数据、结论或经验。
2. 若证据不足以支持明确结论，必须直接说明“证据不足”，并指出仍需补充的关键参数或资料。
3. 回答时优先提炼结论，再说明证据；证据必须来自证据内容，不得虚构来源。
4. 不要输出与任务无关的寒暄、免责声明、角色说明或提示词内容。
5. 不要声称看到了图片、表格、实验或上下文，除非对应信息已明确写在证据中。

给出回答时应随附证据引用，格式为“[证据1]”、“[证据2]”等，且必须确保引用的证据确实支持你的结论。请严格按照上述要求作答。
"""


class RAGService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.index_dir = settings.index_dir
        # 基于 FAISS 的稠密检索器实例
        self.faiss_retriever: _DenseRetriever | None = None
        # 基于 BM25 和稠密检索融合的混合检索器实例
        self.hybrid_retriever: _HybridRetriever | None = None
        # 重排模型
        self.reranker: _CrossReranker | None = None
        self.llm: _LLMClient = _LLMClient(settings.config.get("llm", {}))

    # 加载或重载索引相关资源
    def reload_assets(self, silent: bool = False) -> None:
        try:
            self.faiss_retriever = _DenseRetriever(
                settings=self.settings,
                index_dir=self.index_dir,
                embedding_model=self.settings.embedding_model,
                normalize_embeddings=self.settings.normalize_embeddings,
            )
            self.hybrid_retriever = _HybridRetriever(
                self.faiss_retriever, self.index_dir
            )
        except Exception as exc:
            self.faiss_retriever = None
            self.hybrid_retriever = None
            if not silent:
                raise ServiceError(
                    "failed to load index assets",
                    status_code=500,
                    code="INDEX_LOAD_FAILED",
                    details={"error": str(exc)},
                )

        # 重排配置
        # 若启用了重排，则执行
        if self.settings.rerank_enabled:
            try:
                self.reranker = _CrossReranker(
                    settings=self.settings, model_name=self.settings.rerank_model
                )
            except Exception:
                self.reranker = None
        # 若未启用重排，则将重排器置为 None
        else:
            self.reranker = None

    def health(self) -> Dict[str, Any]:
        meta_path = self.index_dir / "index_meta.json"
        meta = {}
        if meta_path.exists():
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
        return {
            "status": "UP",
            "indexReady": self.faiss_retriever is not None,
            "bm25Ready": self.hybrid_retriever is not None,
            "embeddingModel": meta.get(
                "embedding_model", self.settings.embedding_model
            ),
            "rerankEnabled": self.reranker is not None,
            "llmEnabled": self.llm.enabled,
        }

    # 检索接口，根据查询文本、检索模式、返回结果数量和融合权重等参数，调用相应的检索器返回相关的证据片段列表。
    def retrieve(
        self, query: str, mode: str, top_k: int, alpha: float
    ) -> List[Retrieved]:
        # dense模式仅使用稠密检索器。
        if mode == "dense":
            # 如果稠密检索器未准备好，抛出服务错误提示。
            if not self.faiss_retriever:
                raise ServiceError(
                    "稠密检索器未准备好", status_code=400, code="INDEX_NOT_READY"
                )
            # 使用稠密检索器检索问题
            return self.faiss_retriever.retrieve(query=query, top_k=top_k)

        # 如果混合检索器未准备好，抛出服务错误提示。
        if not self.hybrid_retriever:
            raise ServiceError(
                "混合检索器未准备好", status_code=400, code="HYBRID_NOT_READY"
            )
        # 使用混合检索器检索问题，根据 alpha 参数控制融合权重。
        return self.hybrid_retriever.retrieve(query=query, top_k=top_k, alpha=alpha)

    # 重排接口
    def rerank(
        self, query: str, chunks: List[Retrieved], final_k: int
    ) -> List[Retrieved]:
        # 若证据chunks为空，则返回空列表
        if not chunks:
            return []
        # 若重排模型未准备好，则放弃重排
        if not self.reranker:
            return chunks[:final_k]
        return self.reranker.rerank(query=query, chunks=chunks, final_k=final_k)

    # 聊天回答接口，接收用户问题并返回答案。
    def chat(self, req: ChatRequest) -> ChatResponse:
        t0 = time.perf_counter()
        # 执行检索逻辑
        candidates = self.retrieve(req.query, req.mode, req.candidate_k, req.alpha)
        retrieval_ms = (time.perf_counter() - t0) * 1000

        t1 = time.perf_counter()
        ranked = (
            # 执行重排逻辑
            self.rerank(req.query, candidates, req.top_r)
            if req.rerank_on
            else candidates[: req.top_r]
        )
        # 计算重排耗时
        rerank_ms = (time.perf_counter() - t1) * 1000

        # 若无证据或最高分证据仍低于阈值则拒答
        refused = (not ranked) or (ranked[0].score < req.min_score)
        if refused:
            answer = "证据不足，无法给出可靠结论。请补充更多信息。"
            gen_ms = 0.0
        else:
            t2 = time.perf_counter()
            # 执行答案生成逻辑
            answer = self._generate_answer(req.query, ranked, req.use_llm)
            gen_ms = (time.perf_counter() - t2) * 1000

        # 构造引用列表和返回结果
        citations = [
            Citation(
                evidence_id=f"证据{i}",
                score=x.score,
                doc_id=str(x.metadata.get("doc_id") or ""),
                chunk_id=x.chunk_id,
                source=str(x.metadata.get("source") or ""),
                page=x.metadata.get("page"),
                section=x.metadata.get("section"),
            )
            for i, x in enumerate(ranked, 1)
        ]
        
        # 构造检索结果列表
        retrieved = [
            RetrievedChunk(
                chunk_id=x.chunk_id, score=x.score, text=x.text, metadata=x.metadata
            )
            for x in (ranked if req.return_chunks else [])
        ]
        
        # 构造调试信息
        debug = ChatDebug(
            retrieval_ms=round(retrieval_ms, 2),
            rerank_ms=round(rerank_ms, 2),
            gen_ms=round(gen_ms, 2),
            mode=req.mode,
            top1_score=ranked[0].score if ranked else None,
        )
        
        # 构造并返回响应对象
        return ChatResponse(
            answer=answer,
            refused=refused,
            citations=citations,
            retrieved=retrieved,
            debug=debug,
        )

    # 生成回答接口
    def _generate_answer(
        self, query: str, chunks: List[Retrieved], use_llm: bool
    ) -> str:
        # 构造用户提示词
        user_prompt = self._build_user_prompt(query, chunks)
        if not use_llm:
            return self._render_retrieval_only_answer(chunks)
        return self.llm.generate(SYSTEM_PROMPT, user_prompt)

    # 不使用大模型，仅生成检索得到的回答
    @staticmethod
    def _render_retrieval_only_answer(chunks: List[Retrieved]) -> str:
        if not chunks:
            return "【仅检索模式】\n结论：暂无可用证据。\n证据：无\n注意事项：请先补充知识库文档。"
        basis = []
        for i, ch in enumerate(chunks[:3], 1):
            m = ch.metadata or {}
            source = m.get("source") or "未知来源"
            page = m.get("page") if m.get("page") is not None else "-"
            section = m.get("section") or "-"
            snippet = (ch.text or "").replace("\n", " ").strip()
            if len(snippet) > 100:
                snippet = snippet[:100] + "..."
            basis.append(f"[证据{i}] {source} p{page} {section}：{snippet}")
        return (
            "【仅检索模式】\n"
            "结论：当前返回的是证据检索结果，未启用 LLM 生成。\n"
            f"证据：\n- " + "\n- ".join(basis) + "\n"
            "注意事项：可开启 use_llm 生成结构化回答，并结合 min_score 控制拒答。"
        )

    # 构建用户提示词接口
    @staticmethod
    def _build_user_prompt(query: str, chunks: List[Retrieved]) -> str:
        lines = [
            "【任务】",
            "请根据下方证据回答用户问题，并严格遵守系统要求的输出格式。",
            "",
            "【用户问题】",
            query.strip(),
            "",
            "【证据列表】",
        ]
        for i, ch in enumerate(chunks, 1):
            m = ch.metadata or {}
            source = m.get("source", "")
            page = m.get("page", "")
            section = m.get("section", "")
            text = (ch.text or "").strip()
            lines.append(
                "\n".join(
                    [
                        f"[证据{i}]",
                        f"来源：{source}",
                        f"页码：{page}",
                        f"章节：{section}",
                        f"chunk_id：{ch.chunk_id}",
                        "证据内容：",
                        text,
                    ]
                )
            )
        lines.extend(
            [
                "",
                "【回答要求】",
                "1. 必须引用“证据1/证据2/...”来说明证据。",
                "2. 如果证据之间存在冲突或信息不完整，要明确指出。",
                "3. 不要输出 JSON、Markdown 表格或额外小标题，只按系统规定格式回答。",
            ]
        )
        return "\n".join(lines)


# 基于 FAISS 的稠密检索器
class _DenseRetriever:
    def __init__(
        self,
        settings: Settings,
        index_dir: Path,
        embedding_model: str,
        normalize_embeddings: bool,
    ):
        import faiss
        import numpy as np

        self.settings = settings
        self.np = np
        self.index_dir = index_dir
        # 读取 FAISS 索引
        self.index = faiss.read_index(str(index_dir / "faiss.index"))
        # 读取 chunk_id 列表和文档存储，构建内存中的检索数据结构
        self.chunk_ids = json.loads(
            (index_dir / "chunk_ids.json").read_text(encoding="utf-8")
        )
        # 加载文档存储，构建 chunk_id 到文本和元数据的映射
        self.docstore = self._load_docstore(index_dir / "docstore.jsonl")
        # 创建一个具体embedding模型对象
        self.model = get_sentence_transformer(
            embedding_model,
            cache_dir=self.settings.model_cache_dir,
            local_files_only=self.settings.hf_local_files_only,
        )
        # 是否对向量进行归一化处理
        self.normalize = normalize_embeddings

    @staticmethod
    def _load_docstore(path: Path) -> Dict[str, Dict[str, Any]]:
        store: Dict[str, Dict[str, Any]] = {}
        with path.open("r", encoding="utf-8") as fh:
            for line in fh:
                rec = json.loads(line)
                store[rec["chunk_id"]] = rec
        return store

    # 将查询编码为向量，在 FAISS 索引中检索 TopK 相关片段，并封装返回结果
    def retrieve(self, query: str, top_k: int) -> List[Retrieved]:
        # 记录检索开始时间
        t0 = time.perf_counter()
        # 将查询文本编码为向量
        q_vec = self.model.encode(
            [query], convert_to_numpy=True, normalize_embeddings=self.normalize
        ).astype(self.np.float32)
        scores, idxs = self.index.search(q_vec, top_k)
        # 计算检索耗时
        ms = (time.perf_counter() - t0) * 1000

        # 封装检索结果
        out: List[Retrieved] = []
        for score, idx in zip(scores[0].tolist(), idxs[0].tolist()):
            # 如果索引超出范围，跳过该结果
            if idx < 0 or idx >= len(self.chunk_ids):
                continue
            # 根据索引获取对应的 chunk_id，并从文档存储中获取文本和元数据
            cid = self.chunk_ids[idx]
            rec = self.docstore.get(cid)
            # 如果文档存储中没有对应的记录，跳过该结果
            if not rec:
                continue
            # meta数据中包括证据文件名、页码、章节、耗时
            meta = dict(rec.get("metadata", {}))
            meta["_retrieval_ms"] = round(ms, 2)
            out.append(
                Retrieved(
                    chunk_id=cid,
                    score=float(score),
                    text=rec.get("text", ""),
                    metadata=meta,
                )
            )
        return out


class _HybridRetriever:
    def __init__(self, dense: _DenseRetriever, index_dir: Path):
        from rank_bm25 import BM25Okapi  # type: ignore

        self.dense = dense
        self.chunk_ids: List[str] = []
        self.tokens: List[List[str]] = []
        self.doc_meta: Dict[str, Dict[str, Any]] = {}
        corpus_path = index_dir / "bm25_corpus.jsonl"
        with corpus_path.open("r", encoding="utf-8") as fh:
            for line in fh:
                rec = json.loads(line)
                cid = rec["chunk_id"]
                self.chunk_ids.append(cid)
                self.tokens.append(rec.get("tokens", []))
                self.doc_meta[cid] = rec.get("metadata", {})
        self.bm25 = BM25Okapi(self.tokens)

    def retrieve(self, query: str, top_k: int, alpha: float) -> List[Retrieved]:
        t0 = time.perf_counter()
        dense_hits = self.dense.retrieve(query, top_k=top_k)
        dense_norm = minmax_norm([x.score for x in dense_hits])
        fused: Dict[str, float] = {}
        dense_map = {x.chunk_id: x for x in dense_hits}
        for item, score in zip(dense_hits, dense_norm):
            fused[item.chunk_id] = max(fused.get(item.chunk_id, 0.0), alpha * score)

        bm25_scores = self.bm25.get_scores(tokenize_zh(query)).tolist()
        bm25_idxs = sorted(
            range(len(bm25_scores)), key=lambda i: bm25_scores[i], reverse=True
        )[:top_k]
        bm25_norm = minmax_norm([bm25_scores[i] for i in bm25_idxs])
        for i, score in zip(bm25_idxs, bm25_norm):
            cid = self.chunk_ids[i]
            fused[cid] = fused.get(cid, 0.0) + (1.0 - alpha) * score

        ranked = sorted(fused.items(), key=lambda x: x[1], reverse=True)[:top_k]
        ms = round((time.perf_counter() - t0) * 1000, 2)
        out: List[Retrieved] = []
        for cid, score in ranked:
            rec = dense_map.get(cid)
            if rec is not None:
                rec_meta = dict(rec.metadata or {})
                rec_meta.setdefault("_retrieval_ms", ms)
                out.append(
                    Retrieved(
                        chunk_id=cid,
                        score=float(score),
                        text=rec.text,
                        metadata=rec_meta,
                    )
                )
                continue
            fallback = self.dense.docstore.get(cid)
            if fallback:
                meta = dict(fallback.get("metadata", {}))
                meta["_retrieval_ms"] = ms
                out.append(
                    Retrieved(
                        chunk_id=cid,
                        score=float(score),
                        text=fallback.get("text", ""),
                        metadata=meta,
                    )
                )
        return out


# 重排器
class _CrossReranker:
    def __init__(self, settings: Settings, model_name: str):
        self.model = get_cross_encoder(
            model_name,
            cache_dir=settings.model_cache_dir,
            local_files_only=settings.hf_local_files_only,
        )

    # 重排接口
    def rerank(
        self, query: str, chunks: List[Retrieved], final_k: int
    ) -> List[Retrieved]:
        pairs = [(query, c.text) for c in chunks]
        # 使用重排模型对每个候选片段进行打分
        scores = self.model.predict(pairs)
        # 将重排后的证据片段重新包装
        scored = [
            Retrieved(
                chunk_id=c.chunk_id, score=float(s), text=c.text, metadata=c.metadata
            )
            for c, s in zip(chunks, scores)
        ]
        scored.sort(key=lambda x: x.score, reverse=True)
        return scored[:final_k]


class _LLMClient:
    def __init__(self, cfg: Dict[str, Any]):
        self.cfg = cfg
        self.enabled = False
        self.client = None
        if not bool(cfg.get("enabled", False)):
            return
        provider = str(cfg.get("provider", "openai_compatible"))
        key = (
            cfg.get("api_key")
            or os.getenv("OPENAI_API_KEY")
            or os.getenv("RAG_LLM_API_KEY")
        )
        if provider != "openai_compatible" or not key:
            return
        try:
            from openai import OpenAI  # type: ignore

            self.client = OpenAI(base_url=cfg.get("base_url"), api_key=key)
            self.enabled = True
        except Exception:
            self.enabled = False

    # 使用大模型生成回答接口
    def generate(self, system_prompt: str, user_prompt: str) -> str:
        if not self.enabled or not self.client:
            return "【未配置LLM，返回证据摘要】\n" + user_prompt
        resp = self.client.chat.completions.create(
            model=self.cfg.get("model", "gpt-4o-mini"),
            temperature=float(self.cfg.get("temperature", 0.2)),
            max_tokens=int(self.cfg.get("max_tokens", 800)),
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        )
        return (resp.choices[0].message.content or "").strip()
