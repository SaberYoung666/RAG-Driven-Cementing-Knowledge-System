"""实现向量索引与检索索引的构建、加载与维护逻辑。"""
from __future__ import annotations

import json
from pathlib import Path
import tempfile
from typing import Any, Dict, List

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

        cp = Path(chunks_path) if chunks_path else self.settings.chunks_path
        self.index_dir.mkdir(parents=True, exist_ok=True)
        batch_size = int(
            (self.settings.config.get("indexing", {}) or {}).get("batch_size", 8)
        )

        model = get_sentence_transformer(
            self.settings.embedding_model,
            cache_dir=self.settings.model_cache_dir,
            local_files_only=self.settings.hf_local_files_only,
        )
        dim = int(model.get_sentence_embedding_dimension())
        index = faiss.IndexFlatIP(dim)
        chunk_ids: List[str] = []
        count = 0

        index_tmp = self._make_temp_path("faiss-", ".index")
        docstore_tmp = self._make_temp_path("docstore-", ".jsonl")
        chunk_ids_tmp = self._make_temp_path("chunk-ids-", ".json")
        meta_tmp = self._make_temp_path("index-meta-", ".json")

        batch_ids: List[str] = []
        batch_texts: List[str] = []
        batch_metas: List[Dict[str, Any]] = []

        try:
            with docstore_tmp.open("w", encoding="utf-8") as docstore_fh:
                for chunk_id, text, meta in self._iter_chunks(cp):
                    batch_ids.append(chunk_id)
                    batch_texts.append(text)
                    batch_metas.append(meta)
                    if len(batch_texts) < max(1, batch_size):
                        continue
                    count += self._flush_faiss_batch(
                        index=index,
                        model=model,
                        normalize=self.settings.normalize_embeddings,
                        batch_ids=batch_ids,
                        batch_texts=batch_texts,
                        batch_metas=batch_metas,
                        docstore_fh=docstore_fh,
                        chunk_ids=chunk_ids,
                    )
                    batch_ids = []
                    batch_texts = []
                    batch_metas = []

                if batch_texts:
                    count += self._flush_faiss_batch(
                        index=index,
                        model=model,
                        normalize=self.settings.normalize_embeddings,
                        batch_ids=batch_ids,
                        batch_texts=batch_texts,
                        batch_metas=batch_metas,
                        docstore_fh=docstore_fh,
                        chunk_ids=chunk_ids,
                    )

            faiss.write_index(index, str(index_tmp))
            chunk_ids_tmp.write_text(
                json.dumps(chunk_ids, ensure_ascii=False), encoding="utf-8"
            )
            meta_tmp.write_text(
                json.dumps(
                    {
                        "embedding_model": self.settings.embedding_model,
                        "normalize_embeddings": self.settings.normalize_embeddings,
                        "dim": dim,
                        "count": count,
                    },
                    ensure_ascii=False,
                    indent=2,
                ),
                encoding="utf-8",
            )

            index_tmp.replace(self.index_dir / "faiss.index")
            docstore_tmp.replace(self.index_dir / "docstore.jsonl")
            chunk_ids_tmp.replace(self.index_dir / "chunk_ids.json")
            meta_tmp.replace(self.index_dir / "index_meta.json")
        finally:
            self._safe_unlink(index_tmp)
            self._safe_unlink(docstore_tmp)
            self._safe_unlink(chunk_ids_tmp)
            self._safe_unlink(meta_tmp)

        return {"ok": True, "message": f"faiss built with {count} chunks"}

    def build_bm25(self, chunks_path: str | None = None) -> Dict[str, Any]:
        cp = Path(chunks_path) if chunks_path else self.settings.chunks_path
        self.index_dir.mkdir(parents=True, exist_ok=True)

        try:
            import jieba  # type: ignore
            tokenizer = lambda text: [tok.strip() for tok in jieba.lcut(text.replace("\n", " ")) if tok.strip()]
        except Exception:
            tokenizer = lambda text: [tok for tok in text.replace("\n", " ").split(" ") if tok]

        corpus_tmp = self._make_temp_path("bm25-", ".jsonl")
        count = 0
        try:
            with corpus_tmp.open("w", encoding="utf-8") as fh:
                for cid, text, meta in self._iter_chunks(cp):
                    fh.write(
                        json.dumps(
                            {
                                "chunk_id": cid,
                                "tokens": tokenizer(text),
                                "metadata": meta,
                            },
                            ensure_ascii=False,
                        )
                        + "\n"
                    )
                    count += 1
            corpus_tmp.replace(self.index_dir / "bm25_corpus.jsonl")
        finally:
            self._safe_unlink(corpus_tmp)

        return {"ok": True, "message": f"bm25 corpus built with {count} chunks"}

    def _flush_faiss_batch(
            self,
            *,
            index: Any,
            model: Any,
            normalize: bool,
            batch_ids: List[str],
            batch_texts: List[str],
            batch_metas: List[Dict[str, Any]],
            docstore_fh: Any,
            chunk_ids: List[str],
    ) -> int:
        import numpy as np  # type: ignore

        embeds = model.encode(
            batch_texts,
            convert_to_numpy=True,
            show_progress_bar=False,
            batch_size=max(1, len(batch_texts)),
            normalize_embeddings=normalize,
        ).astype(np.float32)
        index.add(embeds)
        for cid, txt, meta in zip(batch_ids, batch_texts, batch_metas):
            docstore_fh.write(
                json.dumps(
                    {"chunk_id": cid, "text": txt, "metadata": meta},
                    ensure_ascii=False,
                )
                + "\n"
            )
            chunk_ids.append(cid)
        return len(batch_ids)

    @staticmethod
    def _iter_chunks(path: Path):
        with path.open("r", encoding="utf-8") as fh:
            for line in fh:
                if not line.strip():
                    continue
                rec = json.loads(line)
                yield (
                    rec["chunk_id"],
                    rec["text"],
                    rec.get("metadata", {}),
                )

    def _make_temp_path(self, prefix: str, suffix: str) -> Path:
        with tempfile.NamedTemporaryFile(
                delete=False,
                dir=str(self.index_dir),
                prefix=prefix,
                suffix=suffix,
        ) as tmp:
            return Path(tmp.name)

    @staticmethod
    def _safe_unlink(path: Path | None) -> None:
        if path is None:
            return
        try:
            path.unlink(missing_ok=True)
        except Exception:
            pass
