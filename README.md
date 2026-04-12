RAG-Driven-Cementing-Knowledge-System



基于 RAG 的固井领域智能问答系统

面向固井专业场景的工程化智能问答系统，支持
知识库构建 · 混合检索 · 证据重排 · 引用回答 · 日志评测 · 后台管理


Java
Spring Boot
Vue
FastAPI
PostgreSQL
FAISS
License





项目简介

固井是油气井建井过程中的关键工序，其质量直接影响井筒封隔效果、套管稳定性、后续作业安全性及油气井全生命周期的维护成本。固井相关知识通常分散在标准规范、作业手册、现场记录、事故案例、培训资料和学术文献中，传统检索方式难以高效定位证据，而纯大模型回答又容易产生幻觉。


本项目以 RAG（Retrieval-Augmented Generation） 为核心技术路线，围绕固井领域构建一套集 知识入库、检索召回、证据重排、答案生成、引用溯源、日志评测、系统管理 于一体的工程化智能问答系统，实现“先检索证据，再基于证据回答”的专业问答能力。



项目亮点


垂直领域聚焦：面向固井知识问答，不是通用闲聊系统

证据驱动回答：回答建立在检索到的知识片段之上，并返回引用

支持混合检索：Dense + BM25 + Hybrid 检索策略

支持重排优化：接入 Cross-Encoder 提升召回结果相关性

多格式知识接入：支持 PDF / DOCX / TXT / MD 等格式文档

可追踪可评测：支持日志、状态、离线评测与性能分析

前后端分离：Web 前端、Spring Boot 后端、Python RAG 微服务解耦部署

可扩展：便于后续接入知识图谱、多模态文档处理、异步任务等能力



在线展示


当前仓库未提供公开在线演示地址，可在本地或服务器部署运行。



你可以在这里补充：



前端地址：http://your-domain

后端 Swagger：http://your-domain/swagger-ui/index.html

RAG 健康检查：http://your-domain/rag/v1/health



项目结构

RAG-Driven-Cementing-Knowledge-System/
├─ frontend/               # Vue3 前端
├─ backend/                # Spring Boot 后端
├─ rag-service/            # FastAPI RAG 微服务
├─ docker-compose.yml      # 容器编排（按实际情况完善）
└─ README.md

模块职责

模块	技术栈	主要职责
frontend	Vue 3 + Vite + TypeScript + Ant Design Vue	用户交互、页面展示、问答结果与引用展示
backend	Java 21 + Spring Boot + MyBatis-Plus + PostgreSQL	鉴权、业务编排、文档管理、日志与评测接口
rag-service	FastAPI + FAISS + BM25 + Transformers	文档处理、检索、重排、生成、评测


系统架构

flowchart LR
    A[前端 Frontend<br/>Vue3 + Vite + Ant Design Vue] --> B[后端 Backend<br/>Spring Boot]
    B --> C[RAG 微服务<br/>FastAPI]
    B --> D[(PostgreSQL)]
    C --> E[(知识库文件/索引)]
    C --> F[Embedding Model]
    C --> G[Rerank Model]
    C --> H[LLM / OpenAI Compatible API]

    E --> C
    D --> B

架构说明


前端 负责用户交互、登录注册、问答界面、文档管理、日志和评测页面展示

后端 作为统一业务入口，负责鉴权、参数校验、会话管理、文档管理、日志记录与评测触发

RAG 微服务 负责文档解析、切分、索引构建、检索、重排、提示构建、答案生成与评测

数据库 用于存储用户、会话、文档记录、日志与系统状态等业务数据

知识库文件与索引 用于持久化 chunk、FAISS 索引、BM25 语料等中间结果



核心功能

1. 用户与认证


用户注册

用户登录

用户信息查询

基于角色的页面与接口访问控制


2. 智能问答


提交问题并返回答案

支持多轮会话

返回证据引用与来源信息

支持 Dense / Hybrid 检索

支持重排与阈值控制

证据不足时拒答或提示补充信息


3. 文档管理


上传领域文档

触发文档处理

查看处理状态

删除文档

重建索引 / 重建知识库


4. 知识处理


文档解析

文本清洗

结构化切分

向量化

FAISS 索引构建

BM25 索引构建


5. 评测与日志


记录问答链路日志

查看系统日志概览

触发离线评测

汇总评测结果

分析响应时延、召回质量和生成质量



核心流程

文档入库流程

flowchart TD
    A[上传文档] --> B[文档解析]
    B --> C[文本清洗]
    C --> D[结构化切分 Chunk]
    D --> E[生成 Metadata]
    E --> F[向量化]
    F --> G[构建 FAISS 索引]
    E --> H[构建 BM25 语料]
    G --> I[持久化索引]
    H --> I

在线问答流程

flowchart TD
    A[用户提问] --> B[后端接收请求]
    B --> C[RAG 微服务处理]
    C --> D[Query 向量化]
    D --> E[Dense / BM25 / Hybrid 检索]
    E --> F[Cross-Encoder 重排]
    F --> G[构建 Prompt]
    G --> H[调用 LLM]
    H --> I[生成答案与引用]
    I --> J[返回前端并写入日志]


技术栈

前端


Vue 3

TypeScript

Vite

Ant Design Vue

Pinia

Vue Router

Axios

Markdown-It

Highlight.js


后端


Java 21

Spring Boot 4

Spring MVC

Spring Validation

Spring WebFlux

Spring Boot Actuator

MyBatis-Plus

PostgreSQL

Lombok

SpringDoc OpenAPI


RAG 微服务


Python 3

FastAPI

Uvicorn

FAISS

rank-bm25

sentence-transformers

transformers

PaddleOCR / PaddlePaddle

PyMuPDF / pypdf / python-docx

OpenAI Compatible API



页面预览


这里建议后续补充实际截图，GitHub 首页观感会明显更好。



登录 / 注册页

建议插入截图：docs/images/auth.png

智能问答页

建议插入截图：docs/images/chat.png

文档管理页

建议插入截图：docs/images/docs.png

日志与评测页

建议插入截图：docs/images/logs.png
docs/images/eval.png


已实现页面


/auth：登录 / 注册页

/chat：问答页

/profile：个人中心页

/admin/docs：文档管理页

/admin/logs/overview：日志概览页

/admin/logs/list：日志列表页

/admin/eval：评测页

/admin/console：控制台页



接口概览

后端接口

认证相关


GET /api/v1/auth/check-username

POST /api/v1/auth/register

POST /api/v1/auth/login

GET /api/v1/auth/me


问答与会话


POST /api/v1/chat

POST /api/v1/chat/sessions

GET /api/v1/chat/sessions

GET /api/v1/chat/sessions/{sessionId}

PATCH /api/v1/chat/sessions/{sessionId}

DELETE /api/v1/chat/sessions/{sessionId}


文档管理


GET /api/v1/docs

DELETE /api/v1/docs/{docId}

POST /api/v1/ingest

POST /api/v1/docs/{docId}/process

GET /api/v1/docs/{docId}/process

POST /api/v1/docs/reindex


状态与评测


GET /api/v1/rag/status

POST /api/v1/eval/run


日志相关


GET /api/v1/system-logs

GET /api/v1/system-logs/overview


RAG 微服务接口


GET /rag/v1/health

GET /rag/v1/config

POST /rag/v1/chat

POST /rag/v1/retrieve

POST /rag/v1/rerank

POST /rag/v1/ingest/run

POST /rag/v1/docs/process

GET /rag/v1/ingest/{doc_id}/status

GET /rag/v1/index/status

POST /rag/v1/index/build/faiss

POST /rag/v1/index/build/bm25

POST /rag/v1/eval/batch

POST /rag/v1/eval/summarize



运行环境

建议环境如下：


组件	版本建议
Node.js	22+
npm	10+
Java	21
Maven	3.9+
Python	3.10+
PostgreSQL	14+


快速开始

推荐启动顺序：



启动 PostgreSQL

启动 rag-service

启动 backend

启动 frontend


1）启动 RAG 微服务

cd rag-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8000

默认地址：


http://localhost:8000

健康检查：


http://localhost:8000/rag/v1/health


2）启动后端

cd backend
./mvnw spring-boot:run

Windows：


mvnw.cmd spring-boot:run

默认地址：


http://localhost:8080

Swagger：


http://localhost:8080/swagger-ui/index.html


3）启动前端

cd frontend
npm install
npm run dev

默认地址：


http://localhost:8090


配置说明

后端配置

配置文件位置：


backend/src/main/resources/application.yaml

通常需要配置：



PostgreSQL 数据源

服务端口

Token / 鉴权参数

文件上传限制

文档存储路径

RAG 微服务地址

日志与监控配置


RAG 微服务配置

配置文件位置：


rag-service/config/config.yaml

通常需要配置：



原始文档目录

Chunk 输出路径

OCR 参数

索引路径

Embedding 模型

Retrieval 参数

Rerank 模型

LLM 服务地址与模型名



建议不要将 API Key、Token、回调密钥等敏感信息直接提交到仓库中，优先改为环境变量注入。




Docker 部署

项目支持容器化部署，三个模块均可单独构建镜像。


构建前端镜像

cd frontend
docker build -t cement-frontend .

构建后端镜像

cd backend
docker build -t cement-backend .

构建 RAG 微服务镜像

cd rag-service
docker build -t cement-rag-service .

如需联动部署，可基于根目录 docker-compose.yml 继续完善：



PostgreSQL 服务

数据卷挂载

环境变量

网络配置

Nginx 反向代理



评测与日志

系统支持记录问答链路日志，并支持离线评测，用于分析系统质量与性能。


可关注指标

检索指标


Recall@K

MRR

TopR 证据覆盖率


生成指标


正确性

完整性

可解释性

拒答准确率


性能指标


平均响应时延

P50 / P95 时延

检索耗时

生成耗时



Roadmap


 增加项目截图与演示 GIF

 完善 Docker Compose 一键部署

 增加 CI/CD 配置

 接入知识图谱增强检索

 支持表格 / 图表 / 多模态资料处理

 增加异步任务队列

 增强系统监控、审计与限流

 优化引用可视化与可解释性展示

 增加自动化测试



常见问题

1. 这个项目是通用聊天机器人吗？

不是。它定位为 固井领域知识问答系统，重点是专业知识检索、证据约束回答和工程化管理能力。


2. 为什么要用 RAG，而不是直接调用大模型？

因为固井场景要求回答准确、可追溯、可更新。RAG 通过外部知识库提供证据，能明显降低幻觉风险。


3. 为什么拆成 Spring Boot + Python 微服务？


Java 更适合业务系统开发、鉴权、日志、权限与管理能力建设

Python 更适合快速实现 RAG、模型调用、检索与评测链路

两者解耦后更便于迭代和部署


4. 目前是否适合生产环境直接使用？

目前更适合毕业设计、原型验证和研究实验场景。若用于生产环境，还需要补齐安全、监控、并发、运维治理等能力。



贡献说明

当前项目以毕业设计 / 研究原型为主。


如需开放协作，建议后续补充：



CONTRIBUTING.md

CODE_OF_CONDUCT.md

Issue / PR 模板



免责声明

本项目主要用于毕业设计、教学研究与原型验证，重点在于验证固井领域 RAG 智能问答系统的技术路线与工程可行性。若用于真实生产环境，还应进一步完善：



权限治理

敏感信息保护

安全审计

并发与稳定性设计

监控报警

自动化运维



License

当前仓库建议补充 LICENSE 文件后再正式声明许可证类型。





如果这个项目对你有帮助，欢迎 Star ⭐


