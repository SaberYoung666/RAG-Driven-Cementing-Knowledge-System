# RAG-Driven-Cementing-Knowledge-System

基于 RAG 的固井领域智能问答系统，采用三模块解耦架构：

- `rag-service`（Python/FastAPI）：文档处理、索引构建、检索重排、回答生成、评测
- `backend`（Spring Boot）：统一业务 API、鉴权会话、文档管理、日志反馈、评测触发
- `frontend`（Vue3 + Vite + Ant Design Vue）：问答、知识库管理、日志、评测页面

## 1. 对齐成果目标

系统满足《成果预期.md》中的核心链路：

1. 多格式文档入库（PDF/DOCX/TXT/MD）
2. 双检索（Dense + BM25 Hybrid，`alpha` 可调）
3. 可选重排（Cross-Encoder）
4. 可追溯证据输出（`source/page/section/chunk_id/score`）
5. 证据不足拒答（`min_score` 阈值）
6. 离线评测与自动汇总（batch + summarize）

## 2. 目录结构

```text
RAG-Driven-Cementing-Knowledge-System/
├─ rag-service/   # Python 微服务（智能核心）
├─ backend/       # Spring Boot 业务后端
└─ frontend/      # Vue 前端
```

## 3. 微服务（rag-service）

### 3.1 关键能力

- 文档抽取与清洗
- `structure_then_window` / `window_only` 切分
- FAISS 索引构建与落盘：
  - `faiss.index`
  - `docstore.jsonl`
  - `chunk_ids.json`
  - `index_meta.json`
- BM25 语料落盘：
  - `bm25_corpus.jsonl`
- 在线问答：`dense` / `hybrid` + `rerank` + `LLM(可关)`
- 文档增量处理：支持按文件列表增量替换同 `source` 的 chunk

### 3.2 核心接口

- `GET /rag/v1/health`
- `POST /rag/v1/chat`
- `POST /rag/v1/retrieve`
- `POST /rag/v1/rerank`
- `POST /rag/v1/ingest/run`
- `POST /rag/v1/docs/process`
- `GET /rag/v1/index/status`
- `POST /rag/v1/index/build/faiss`
- `POST /rag/v1/index/build/bm25`
- `POST /rag/v1/eval/batch`
- `POST /rag/v1/eval/summarize`

### 3.3 启动

```bash
cd rag-service
uvicorn main:app --reload --port 8000
```

## 4. 后端（backend）

### 4.1 职责

- 统一 API 入口与参数校验
- 用户鉴权、会话管理
- 转发问答到 `rag-service`
- 文档上传/处理状态管理
- 日志与反馈接口
- 评测触发接口（异步）

### 4.2 关键接口

- `POST /api/v1/chat`
- `POST /api/v1/chat/sessions`
- `GET /api/v1/chat/sessions`
- `GET /api/v1/chat/sessions/{sessionId}`
- `PATCH /api/v1/chat/sessions/{sessionId}`
- `DELETE /api/v1/chat/sessions/{sessionId}`
- `POST /api/v1/ingest`
- `POST /api/v1/docs/{docId}/process`
- `GET /api/v1/docs/{docId}/process`
- `GET /api/v1/logs`
- `POST /api/v1/feedback`
- `POST /api/v1/eval/run`

### 4.3 启动

```bash
cd backend
./mvnw spring-boot:run
```

默认端口 `8080`，OpenAPI：`http://localhost:8080/swagger-ui/index.html`

## 5. 前端（frontend）

### 5.1 页面

- 登录/注册
- 问答页（模式、参数、证据展示）
- 文档管理页（上传、处理、状态）
- 日志页（历史问答、反馈）
- 评测页（触发离线评测）

### 5.2 启动

```bash
cd frontend
npm install
npm run dev
```

默认端口 `8090`，开发代理 `/api -> http://localhost:8080`

## 6. 联调顺序（推荐）

1. 启动 `rag-service`（8000）
2. 启动 `backend`（8080）
3. 启动 `frontend`（8090）
4. 登录后进入文档管理，上传文档并触发处理
5. 进入问答页提问，观察 `answer + citations + refused`
6. 进入日志/评测页验证记录与评测触发

## 7. Docker 部署

1. 复制环境变量模板

```bash
cp .env.example .env
```

2. 按需修改 `.env` 中的数据库密码、`AUTH_TOKEN_SECRET` 和 `OPENAI_API_KEY`

3. 在仓库根目录启动全部服务

```bash
docker compose up -d --build
```

4. 访问入口

- 前端：`http://localhost`
- 后端 OpenAPI：`http://localhost:8080/swagger-ui/index.html`
- RAG 健康检查：`http://localhost:8000/rag/v1/health`

5. 常用命令

```bash
docker compose logs -f
docker compose down
docker compose down -v
// 若修改了代码
git pull
docker compose up -d --build
```

说明：

- `postgres` 首次启动会自动执行 `docker/postgres/init/01-init.sql`
- `backend` 与 `rag-service` 通过共享卷 `/data/uploads/docs` 访问上传文档
- `rag-service` 会把索引和中间数据持久化到命名卷，首次启动可能需要下载 OCR、Embedding、Rerank 模型，耗时会明显更长
- 如需启用 LLM 生成，除设置 `OPENAI_API_KEY` 外，还需要把 `rag-service/config/config.yaml` 中的 `llm.enabled` 改为 `true`

## 8. 关键配置

- `backend/src/main/resources/application.yaml`
  - `rag.base-url`
  - `rag.docs-process-path`
  - `docs.storage-dir`
- `rag-service/config/config.yaml`
  - `ingestion.*`
  - `indexing.*`
  - `retrieval.*`
  - `rerank.*`
  - `llm.*`

## 9. 当前验证情况

- 前端生产构建已通过（`npm run build`）
- Python 关键改动文件已做语法编译校验
- 后端在当前环境无法通过 Maven Wrapper 启动（本机 `mvn` 缺失，`mvnw` 执行异常），建议在具备 Maven/正常 Wrapper 的环境执行一次 `compile` 或 `test`
