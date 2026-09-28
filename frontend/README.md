# 前端

React 19 / TypeScript / Vite 的基础页，只展示同源后端健康状态。注册、登录和预约界面属于后续功能。

```sh
npm ci
npm run dev
npm run typecheck
npm test
npm run build
npm run lint
```

开发服务器在 `http://localhost:5173`，将 `/api/actuator/*` 转发至本机 `localhost:8080`；先按[部署说明](../deploy/README.md)启动后端容器，开发环境需保留默认 `V2_BACKEND_PORT=8080`。生产静态页由本地 Compose 中的 NGINX 提供，`/api/` 在 NGINX 中转发给后端。
