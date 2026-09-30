# 本地环境

`compose.yaml` 在本机启动 MySQL 8.4、Java 后端、NGINX 前端和 [Mailpit 邮件沙箱](https://mailpit.axllent.org/docs/install/docker/)。Mailpit 只用于本地测试，不是正式 Gmail 发信服务。本配置未部署到 AWS。

## 首次启动

复制 `deploy/.env.example` 为被 Git 忽略的 `deploy/.env`，把数据库密码改为本地独立密码，并用 `openssl rand -hex 32` 生成 `VERIFICATION_KEY`。在仓库根目录运行：

```sh
docker-compose -f deploy/compose.yaml config --quiet
docker-compose -f deploy/compose.yaml up --build -d --wait
python3 deploy/smoke.py
python3 deploy/smoke_identity.py
python3 deploy/smoke_password_recovery.py
```

若安装的是 `docker compose` 子命令，可等价替换 `docker-compose`。`smoke.py` 只读检查静态页、照片、健康端点、CSRF 和匿名权限；`smoke_identity.py` 会在本地数据库创建一个 `smoke-…@example.test` 学员，经过 Mailpit 验证、登录、退出；`smoke_password_recovery.py` 会创建一次性学员，经 Mailpit 收取验证码并检查旧密码和两个旧会话失效。设置 `SMOKE_RESTART_BACKEND=1` 可在登录后重启后端容器，检查 Session 在 MySQL 中恢复。

| 服务 | 本机地址 |
|---|---|
| 前端与同源 API | `http://localhost:8088` |
| Mailpit 收件箱 | `http://localhost:8025` |
| 后端，供 Vite 代理 | `http://127.0.0.1:8080` |
| Docker MySQL，供 IDEA 连接 | `127.0.0.1:3307` |
| Mailpit SMTP，供 IDEA 后端连接 | `127.0.0.1:1025` |

在 IDEA 开发后端时，可保留 `db` 与 `mailpit` 并停掉占用 8080 的容器后端及前端：

```sh
docker-compose -f deploy/compose.yaml stop backend frontend
```

IDEA 所需环境变量与启动类见[后端说明](../backend/README.md)。Mac 自身的 MySQL 若占用 3306，不影响此处的 3307。`docker-compose -f deploy/compose.yaml down` 会停止容器但保留 MySQL 命名卷；命名卷不是备份，删除卷会丢数据。

## 验证边界

本地冒烟覆盖 Mailpit SMTP 投递、邮箱验证、登录、Session、退出及密码找回；实际执行结果见相应功能计划。它不证明 Gmail 真实邮箱送达、生产 HTTPS Cookie、EC2 性能或备份恢复。生产部署、实际付费资源和 v1 数据迁移均需另行批准。
