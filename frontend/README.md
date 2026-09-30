# 前端

React 19 / TypeScript / Vite。已实现 GEER 响应式登录、学员注册、邮箱验证、待验证与登录后状态。身份页面使用已批准蓝色概念稿的雪道、锐角 GEER 字标与刻滑线；原始照片保留在 `ai-docs/design/` 作为素材来源。约课界面尚未实现。

```sh
npm ci
npm run dev
npm test
npm run typecheck
npm run build
npm run lint
```

开发服务器在 `http://localhost:5173`，将同源 `/api` 转发至本机 8080。先按[后端说明](../backend/README.md)用 IDEA 或 Docker 启动后端。邮件沙箱在 `http://localhost:8025`；注册后从收件箱打开验证链接。页面只在内存中保存当前身份，刷新时调用 `/api/auth/me` 恢复有效会话。

身份页面在约 360、390、768、1440、1672 像素宽度下经 Chrome 检查；桌面及手机截图见[实施计划 0002](../ai-docs/implement-plan/0002-student-identity.md)。注册密码长度按 Unicode 码点校验为 8–128。若更换 GEER 图片，请确认容器中的文件可由 NGINX 读取，并运行 `python3 deploy/smoke.py` 检查资源可访问。
