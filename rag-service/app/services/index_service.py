"""实现向量索引与检索索引的构建、加载与维护逻辑。"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, List, Tuple

from app.core.config import Settings
from app.services.model_registry import get_sentence_transformer


class IndexService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.index_dir = settings.index_dir

    def status(self) -> Dict[str, Any]:
        faiss_ok = (self.index_dir / "faiss.index").exists() and (self.index_dir / "docstore.jsonl").exists() and (self.index_dir / "chunk_ids.json").exists()
        bm25_ok = (self.index_dir / "bm25_corpus.jsonl").exists()
        meta_path = self.index_dir / "index_meta.json"
        meta = json.loads(meta_path.read_text(encoding="utf-8")) if meta_path.exists() else {}
        return {"index_ready": faiss_ok, "bm25_ready": bm25_ok, "meta": meta}

    def build_faiss(self, chunks_path: str | None = None) -> Dict[str, Any]:
        import faiss  # type: ignore
        import numpy as np  # type: ignore

        cp = Path(chunks_path) if chunks_path else self.settings.chunks_path
        chunk_ids, texts, metas = self._read_chunks(cp)
        self.index_dir.mkdir(parents=True, exist_ok=True)

        model = get_sentence_transformer(
            self.settings.embedding_model,
            cache_dir=self.settings.model_cache_dir,
            local_files_only=self.settings.hf_local_files_only,
        )
        embeds = model.encode(
            texts,
            convert_to_numpy=True,
            show_progress_bar=False,
            normalize_embeddings=self.settings.normalize_embeddings,
        ).astype(np.float32)

        index = faiss.IndexFlatIP(embeds.shape[1])
        index.add(embeds)
        faiss.write_index(index, str(self.index_dir / "faiss.index"))

        with (self.index_dir / "docstore.jsonl").open("w", encoding="utf-8") as fh:
            for cid, txt, meta in zip(chunk_ids, texts, metas):
                fh.write(json.dumps({"chunk_id": cid, "text": txt, "metadata": meta}, ensure_ascii=False) + "\n")
        (self.index_dir / "chunk_ids.json").write_text(json.dumps(chunk_ids, ensure_ascii=False), encoding="utf-8")
        (self.index_dir / "index_meta.json").write_text(
            json.dumps(
                {
                    "embedding_model": self.settings.embedding_model,
                    "normalize_embeddings": self.settings.normalize_embeddings,
                    "dim": embeds.shape[1],
                    "count": len(chunk_ids),
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
        return {"ok": True, "message": f"faiss built with {len(chunk_ids)} chunks"}

    def build_bm25(self, chunks_path: str | None = None) -> Dict[str, Any]:
        cp = Path(chunks_path) if chunks_path else self.settings.chunks_path
        chunk_ids, texts, metas = self._read_chunks(cp)
        self.index_dir.mkdir(parents=True, exist_ok=True)

        try:
            import jieba  # type: ignore
            tokenizer = lambda text: [tok.strip() for tok in jieba.lcut(text.replace("\n", " ")) if tok.strip()]
        except Exception:
            tokenizer = lambda text: [tok for tok in text.replace("\n", " ").split(" ") if tok]

        with (self.index_dir / "bm25_corpus.jsonl").open("w", encoding="utf-8") as fh:
            for cid, text, meta in zip(chunk_ids, texts, metas):
                fh.write(json.dumps({"chunk_id": cid, "tokens": tokenizer(text), "metadata": meta}, ensure_ascii=False) + "\n")
        return {"ok": True, "message": f"bm25 corpus built with {len(chunk_ids)} chunks"}

    @staticmethod
    def _read_chunks(path: Path) -> Tuple[List[str], List[str], List[Dict[str, Any]]]:
        chunk_ids: List[str] = []
        texts: List[str] = []
        metas: List[Dict[str, Any]] = []
        with path.open("r", encoding="utf-8") as fh:
            for line in fh:
                if not line.strip():
                    continue
                rec = json.loads(line)
                chunk_ids.append(rec["chunk_id"])
                texts.append(rec["text"])
                metas.append(rec.get("metadata", {}))
        return chunk_ids, texts, metas

