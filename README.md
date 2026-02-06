# AI 教育辅助学习系统（aismate.tech）

- 在线地址：https://aismate.tech
- 技术关键词：RAG | 流式对话 | 异步入库（Kafka + Outbox）| 可观测性（Prometheus/Grafana）| 限流/重试/熔断

## 项目亮点

- RAG 知识库问答：PDF 上传 → 分块/向量化入库（Chroma）→ 检索增强生成
- 流式对话体验：后端流式返回，前端逐段渲染
- 长任务异步化：入库任务返回 taskId，支持进度查询与事件流（SSE）
- 可靠投递设计：Kafka(KRaft) + Outbox Pattern，失败可重试、可追溯
- 可观测性：Actuator/Micrometer 指标 + Prometheus 抓取 + Grafana 看板
- 一键运行：Docker Compose 编排 MySQL/Redis/Chroma/Kafka/后端/前端/监控组件

## 技术栈

- 后端：Java 17 + Spring Boot 3 + Spring Security(JWT) + JPA/MySQL + Redis + Kafka + Flyway
- AI/RAG：LangChain4j + Chroma + WebClient（对接大模型）
- 观测：Actuator + Micrometer + Prometheus + Grafana + 结构化日志
- 工程化：Docker Compose + GitHub Actions + Testcontainers

## Performance（压测基线）

- k6（20 VU / 30s）覆盖链路：注册 / 鉴权会话列表 / 创建会话
- 压测结果（端到端）：成功率 100%，吞吐 180 req/s，P95 延迟 24ms
- 观测指标（服务端）：req/s 峰值约 160+，P95 处理耗时约 9ms

![reqs](docs/perf/reqs.png)
![p95](docs/perf/p95.png)

本仓库包含一个前后端分离的 Web 应用：
- **后端**：`ai-chat/`（Spring Boot，提供鉴权、对话、知识库、错题本、题目生成、统计等 API）
- **前端**：`ai-chat-frontend/`（React + Vite，调用后端 API）

## 目录结构

```text
ai-chat/                 # Java 后端（Spring Boot）
ai-chat-frontend/        # 前端（React + Vite）
SSL_Nginx_aismate.tech_部署流程.md  # Nginx + SSL 部署记录
```

## 本地运行（快速）

### 1) 启动后端

要求：Java 17+、Maven 3.6+、MySQL、Redis（以及可选的 Chroma / 向量化与大模型相关服务）。

```bash
cd ai-chat
mvn spring-boot:run
```

默认端口：`8081`（可用环境变量 `APP_PORT` 覆盖）。

### 2) 启动前端

```bash
cd ai-chat-frontend
npm install
npm run dev -- --host
```

默认端口：`5174`。

## Docker Compose 一键启动

仓库根目录提供 `docker-compose.yml`，包含 MySQL / Redis / Chroma / Kafka / 后端 / 前端 / Prometheus / Grafana。

```bash
docker compose up -d --build
```

启动后可访问：

- 前端：http://localhost:5174/
- Swagger：http://localhost:8081/swagger-ui/index.html
- Prometheus：http://localhost:9090/
- Grafana：http://localhost:3000/（默认 admin/admin）

### 入库队列（Redis Streams / Kafka）

- 默认：Redis Streams（`INGEST_QUEUE=redis`）
- 可选：Kafka + Outbox（`INGEST_QUEUE=kafka`，并配置 `KAFKA_BOOTSTRAP_SERVERS`）

Docker Compose 默认会启用 Kafka 以便本地演示。

如果你的网络访问 Docker Hub 不稳定，可复制 `docker-compose.env.example` 为 `.env` 后再启动（会改用镜像站前缀拉取镜像）：

```bash
copy docker-compose.env.example .env
docker compose up -d --build
```

## 部署与反向代理（Nginx）

- 推荐 Nginx 托管前端静态文件，并将 `/api/` 反向代理到后端 `127.0.0.1:8081`
- HTTPS 证书与 Nginx 配置流程见：`SSL_Nginx_aismate.tech_部署流程.md`

## 文档

- 后端快速上手：`ai-chat/QUICKSTART.md`
- 后端架构说明：`ai-chat/ARCHITECTURE.md`
