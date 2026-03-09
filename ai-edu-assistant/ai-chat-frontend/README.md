# ai-chat-frontend（前端）

React + Vite 前端项目，默认调用同源的后端 API（`/api/...`），也支持通过 `public/config.js` 或 LocalStorage 覆盖 API_BASE。

## 开发

```bash
npm install
npm run dev -- --host
```

默认访问：`http://localhost:5174`

## 配置后端地址

优先级从高到低：

1. `window.API_BASE`（可在页面运行时注入）
2. `localStorage["API_BASE"]`
3. `window.config.apiBaseUrl`（`public/config.js`）
4. 自动推导：
   - localhost 开发：`http(s)://localhost:8081`
   - 非 localhost：使用 `window.location.origin`（适合 Nginx 同源反代 `/api`）

