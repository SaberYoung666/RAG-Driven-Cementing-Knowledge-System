# RAG 微服务

## 项目简介
该服务按文档定义重构为 FastAPI 分层工程，覆盖完整 RAG 链路：
文档入库、索引构建（FAISS/BM25）、检索（dense/hybrid）、重排、生成、评测。

## 目录结构
```text
rag-service/
  app/
    main.py
    routers/
    schemas/
    services/
    core/
  config/
    config.yaml
  src/
    ingestion/
    retrieval/
    generation/
  scripts/
    build_chunks.py
    build_index.py
    build_bm25.py
    batch_eval.py
    summarize_eval.py
  data/
    raw_docs/
    chunks.jsonl
    index/
  evaluation/
  main.py                      # 兼容入口
```

## 启动
```bash
uvicorn main:app --reload --port 8000
```

## 核心接口
- `GET /rag/v1/health`
- `GET /rag/v1/config`
- `POST /rag/v1/chat`
- `POST /rag/v1/retrieve`
- `POST /rag/v1/rerank`
- `POST /rag/v1/ingest/run`
- `POST /rag/v1/docs/process`
- `GET /rag/v1/ingest/{doc_id}/status`
- `GET /rag/v1/index/status`
- `POST /rag/v1/index/build/faiss`
- `POST /rag/v1/index/build/bm25`
- `POST /rag/v1/eval/batch`
- `POST /rag/v1/eval/summarize`

## OCR 入库说明
- `enable_ocr`: `auto`(默认) / `on` / `off`
  - `auto`：仅对抽取文本不足页做 OCR
  - `on`：所有 PDF 页/图片走 OCR
  - `off`：禁用 OCR，扫描页会返回可读错误
- `POST /rag/v1/docs/process` 为异步触发，返回 `queued` 状态
- 通过 `GET /rag/v1/ingest/{doc_id}/status` 轮询 `queued/ocr_processing/cleaning/splitting/indexing/done/failed`
- 扫描件/图片通常经过 `queued -> ocr_processing -> cleaning -> splitting -> indexing -> done`
- 可直接抽取文本的文档会跳过 OCR，通常经过 `queued -> cleaning -> splitting -> indexing -> done`
- 多文档批量提交时，只有整批进入真正的持久化与索引构建阶段后，成功文档才会统一切换到 `indexing`
- 实际发生 OCR 的文档会额外保存到 `data/ocr_texts/<doc_id>.txt`，目录可通过 `config/config.yaml` 中的 `ocr.output_dir` 调整

## 扫描 PDF 示例
```bash
curl -X POST http://localhost:8000/rag/v1/docs/process \
  -H "Content-Type: application/json" \
  -d '{
    "enableOcr": "auto",
    "strictOcr": false,
    "docs": [
      {
        "docId": "demo-scan-001",
        "filePath": "D:/codes/RAG-Driven-Cementing-Knowledge-System/backend/data/uploads/docs/xxx.pdf",
        "sourceName": "xxx.pdf"
      }
    ]
  }'
```

```bash
curl http://localhost:8000/rag/v1/ingest/demo-scan-001/status
```

当状态为 `done` 后，即可在 `POST /rag/v1/chat` 中检索到该文档，引用信息包含 `doc_id + page_no + chunk_id`。
