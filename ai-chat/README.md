# ai-chat（后端）

`ai-chat` 是 AI 教育辅助学习系统的后端服务（Spring Boot），提供鉴权、对话、知识库、错题本、题目生成、学习统计等 API。

## 环境要求

- Java 17+
- Maven 3.6+
- MySQL（必需）
- Redis（必需）
- Chroma（可选，用于向量库/检索增强）

## 配置（环境变量）

后端默认从 `src/main/resources/application.properties` 读取配置，并支持用环境变量覆盖。常用环境变量：

- `APP_PORT`：服务端口（默认 8081）
- `APP_ADDRESS`：绑定地址（默认 0.0.0.0）
- `MYSQL_URL` / `MYSQL_USER` / `MYSQL_PASSWORD`
- `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB`
- `CHROMA_BASE_URL`
- `BIGMODEL_API_KEY`（可选）
- `SILICONFLOW_API_KEY`（可选）
- `CORS_ALLOWED_ORIGINS` / `CORS_ALLOWED_ORIGIN_PATTERNS`（当你前后端跨域部署时需要）

## 启动

```bash
mvn spring-boot:run
```

打包：

```bash
mvn clean package
java -jar target/*.jar
```

## Quick Verify

### 1) 跑测试

```bash
mvn test
```

说明：
- Docker 可用时会跑 Testcontainers 集成测试（例如 `IngestRedisStreamReliabilityIT`）。

### 2) 验证异步入库可靠性（失败→重试→死信）

- 文档与复现步骤见：`docs/reliability.md`
- 典型验证入口：
  - `POST /api/knowledge/documents/upload-async`（拿到 taskId）
  - `GET /api/knowledge/tasks/{taskId}`
  - `GET /api/knowledge/tasks/{taskId}/events`（SSE）
  - `/actuator/prometheus`（指标）

### 3) 证据截图（验真）

仓库根目录 `docs/证据截图` 已包含“测试通过 / outbox 指标 / DEAD 状态 / DLQ+transition”四张验真截图（本 README 直接展示如下）：

![IngestRedisStreamReliabilityIT](../docs/%E8%AF%81%E6%8D%AE%E6%88%AA%E5%9B%BE/IngestRedisStreamReliabilityIT.png)
![outbox_publish_total](../docs/%E8%AF%81%E6%8D%AE%E6%88%AA%E5%9B%BE/outbox_publish_total.png)
![DEAD + attemptCount=2](../docs/%E8%AF%81%E6%8D%AE%E6%88%AA%E5%9B%BE/DEAD%20%2B%20attemptCount%3D2.png)
![XLEN ingesttasksdlq = 4 + ingest_task_transition](../docs/%E8%AF%81%E6%8D%AE%E6%88%AA%E5%9B%BE/XLEN%20ingesttasksdlq%20%3D%204%20%2B%20ingest_task_transition.png)

## 相关文档

- 快速上手：`QUICKSTART.md`
- 架构说明：`ARCHITECTURE.md`
- 异步入库可靠性：`docs/reliability.md`
