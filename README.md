# AI 教育辅助学习系统（aismate.tech）

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

## 部署与反向代理（Nginx）

- 推荐 Nginx 托管前端静态文件，并将 `/api/` 反向代理到后端 `127.0.0.1:8081`
- HTTPS 证书与 Nginx 配置流程见：`SSL_Nginx_aismate.tech_部署流程.md`

## 文档

- 后端快速上手：`ai-chat/QUICKSTART.md`
- 后端架构说明：`ai-chat/ARCHITECTURE.md`

