# 贡献指南（Contributing）

感谢你愿意为本项目贡献代码。为了保证主干稳定与提交可追溯，请遵循以下约定。

## 开发准备

- 推荐使用 Docker Compose 一键启动依赖与服务（MySQL/Redis/Chroma/Kafka/监控等）
- 本地启动成功后优先用 Swagger 完成接口回归

## Git 工作流

### 1) 分支策略

- `main`：稳定分支，只允许通过 Pull Request 合并
- 功能分支命名建议：
  - `feature/<short-desc>`（新功能）
  - `fix/<short-desc>`（Bug 修复）
  - `docs/<short-desc>`（文档/截图）
  - `chore/<short-desc>`（构建/依赖/CI）

示例：
- `feature/rag-doc-scope`
- `feature/ui-theme-redesign`

### 2) 提交信息规范（Conventional Commits）

推荐使用以下前缀：
- `feat:` 新功能
- `fix:` 修复
- `docs:` 文档
- `test:` 测试
- `db:` 数据库迁移（Flyway）
- `chore:` 构建/CI/依赖

示例：
- `feat: add document-scoped rag retrieval`
- `db: add flyway migration for outbox`

### 3) PR 合并策略

- 推荐使用 GitHub 的 **Squash and merge** 合并到 `main`
- PR 必须包含清晰的变更说明与验证方式

## PR 要求（Checklist）

提交 PR 前请确认：

- 代码可编译/可运行（至少完成本地冒烟回归）
- 不提交敏感信息（`.env`、token、证书、密钥等）
- 若涉及数据库变更，必须提供 Flyway 迁移脚本
- 若涉及接口变更，Swagger/OpenAPI 可正常查看并能调用
- 若涉及 UI 变更，附截图或录屏

## 建议的验证方式

- `docker compose up -d --build` 一键启动
- Swagger：注册/登录 → Authorize → 关键接口回归
- 文档入库：upload-async → 查询任务状态 → documents READY

