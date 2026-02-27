# 🎓 AI-Chat (Edu Assistant)

> **你的 AI 备考搭子** —— 基于 RAG 检索增强生成的智能教育辅助系统。

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![LangChain4j](https://img.shields.io/badge/LangChain4j-0.29-blue?style=flat-square)
![Chroma](https://img.shields.io/badge/Chroma-Vector%20DB-cc5500?style=flat-square)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=flat-square&logo=docker&logoColor=white)

## 📖 项目简介

**AI-Chat** 是一个面向备考学习场景的 AI 辅助系统。它不仅仅是一个聊天机器人，更是一个能够理解你复习资料的智能助教。

通过上传 PDF 教材或笔记，系统会自动解析并构建向量知识库。当你提问时，AI 会基于你的资料进行回答（RAG），确保答案的准确性和相关性。此外，它还能根据知识点自动生成练习题，并提供错题管理功能，帮助你高效备考。

### ✨ 核心功能

- 🧠 **知识库构建 (RAG)**：上传 PDF/笔记，自动切分、向量化入库，让 AI "读懂" 你的教材。
- 💬 **智能问答**：基于上下文的流式对话，支持引用溯源，拒绝 AI 幻觉。
- 📝 **智能出题**：根据指定知识点生成选择题/简答题，实时检验学习成果。
- ❌ **错题本管理**：自动记录错题，提供 AI 解析与复习建议。
- 📊 **学习统计**：可视化展示学习进度与知识点掌握情况。
- 🛡️ **高可靠架构**：基于 Kafka + Outbox 模式的异步入库流程，确保数据零丢失。

---

## 🛠️ 技术架构

- **后端框架**: Spring Boot 3 + Spring WebFlux
- **AI 框架**: LangChain4j (整合 OpenAI/SiliconFlow/BigModel API)
- **向量数据库**: Chroma
- **关系型数据库**: MySQL 8
- **缓存/消息**: Redis + Kafka
- **监控**: Prometheus + Grafana + OpenTelemetry
- **安全**: Spring Security + JWT
- **详细设计：[架构图 (Mermaid)
  ](docs/async-ingest-diagram.md)**

---

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- Docker & Docker Compose (推荐)

### 本地启动

1. **克隆仓库**

   ```bash
   git clone https://github.com/Decadesice/ai-edu-assistant.git
   cd ai-edu-assistant
   ```
2. **配置环境变量**
   复制 `ai-chat/src/main/resources/application.properties` 或直接设置环境变量：

   ```bash
   export MYSQL_PASSWORD=your_password
   export SILICONFLOW_API_KEY=sk-xxxx  # 用于 AI 模型服务
   ```
3. **启动依赖服务** (MySQL, Redis, Chroma)

   ```bash
   docker-compose up -d mysql redis chroma
   ```
4. **运行后端**

   ```bash
   cd ai-chat
   mvn spring-boot:run
   ```

### Docker 部署

```bash
# 完整一键部署
docker-compose -f docker-compose.prod.yml up -d
```

---

## ✅ 可靠性验证

本项目实现了高可靠的异步文档入库流程（Upload -> Kafka -> Consumer -> Vector DB），并包含完整的测试验证。

- **设计文档**: [异步入库可靠性设计](ai-chat/docs/reliability.md)
- **测试证据**: [查看测试截图](docs/证据截图/)

---

## 📄 License

MIT License © 2024 Decadesice
