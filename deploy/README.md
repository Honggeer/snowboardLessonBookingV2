# 部署

`compose.yaml` 建立 MySQL 8.4、Java 后端和 NGINX/前端三个容器。数据库使用独立 `snowboard-v2` 命名卷；此卷不是备份。此配置用于本地验证，不构成 EC2 生产部署方案。

## 启动

需要 Docker Compose、可运行的 Docker 守护进程。先复制 `deploy/.env.example` 为 `deploy/.env`，将两个示例密码改为仅本地使用的不同密码，再在仓库根运行：

```sh
docker compose -f deploy/compose.yaml config --quiet
docker compose -f deploy/compose.yaml up --build -d --wait
python3 deploy/smoke.py
```

默认入口是 `http://localhost:8088`。前端请求同源 `/api/actuator/health`，NGINX 转发为后端 `/actuator/health`。后端另仅在本机 `127.0.0.1:8080` 开放，供 Vite 开发服务器代理；可通过 `V2_BACKEND_PORT` 调整，但使用默认 Vite 配置时保持 8080。若本机只有独立的 `docker-compose` 命令，可把上述 `docker compose` 换成 `docker-compose`。

停止容器用 `docker compose -f deploy/compose.yaml down`，这会保留 MySQL 数据卷。重新构建时 Flyway 不重复执行已应用的 V1。需要检查迁移记录时，用 `docker compose -f deploy/compose.yaml exec db mysql -u snowboard_v2 -p snowboard_v2` 登录后查询 `flyway_schema_history`。

## 验证与边界

`smoke.py` 默认检查静态页和同源健康接口；若设置 `SMOKE_BASIC_PASSWORD` 为后端本地启动日志给出的开发用密码，还会以本地 Basic 认证验证演示 API 的路由、CSRF token、创建与读取。`backend/` 的 Testcontainers 测试另用真实 MySQL 8.4 验证空业务库的 V1 迁移。容器有健康检查、重启策略和日志轮转。本计划不创建 AWS 资源、不迁移 v1 数据、不验证生产容量或恢复；这些需要单独批准和验证。
