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
- `SiliconFlow_Api_Key` / `SILICONFLOW_API_KEY`（可选）
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

## 相关文档

- 快速上手：`QUICKSTART.md`
- 架构说明：`ARCHITECTURE.md`
