# 本地环境

AWS 服务器的规格、控制台创建、Session Manager 登录及既有 EC2 的实际准备结果见 [EC2 准备指南](EC2_SETUP.md)。2026-10-04 已按 [0010 revision 3](../ai-docs/implement-plan/0010-production-delivery.md) 完成生产技术发布：[https://52.60.174.156](https://52.60.174.156)；main 自动发布、HTTPS 续期、每日私有 S3 备份已启用，真实异机独立恢复通过。账号与完整业务/媒体测试按用户要求自行进行；使用方式、实际证据、旧根盘/快照保留和费用见 [生产运维手册](PRODUCTION_RUNBOOK.md) 及配对计划。下文为独立本地环境。

`compose.yaml` 在本机启动 MySQL 8.4、Java 后端、NGINX 前端、S3Mock 本地对象存储和 [Mailpit 邮件沙箱](https://mailpit.axllent.org/docs/install/docker/)。未设置 `MAIL_*` 参数时默认发送到 Mailpit；在被 Git 忽略的 `deploy/.env` 配置真实 SMTP 后，注册验证、密码找回和预约通知共用该发信账号。本配置未部署到 AWS。

## 首次启动

复制 `deploy/.env.example` 为被 Git 忽略的 `deploy/.env`，把数据库密码改为本地独立密码，并用 `openssl rand -hex 32` 生成 `VERIFICATION_KEY`。在仓库根目录运行：

```sh
docker-compose -f deploy/compose.yaml config --quiet
docker-compose -f deploy/compose.yaml up --build -d --wait
python3 deploy/smoke.py
```

仅在 Mailpit 模式下运行 `python3 deploy/smoke_identity.py`、`python3 deploy/smoke_password_recovery.py`。预约邮件的 Mailpit 联调还需已有已验证的测试教练账号：运行 `SMOKE_COACH_EMAIL=教练邮箱 python3 deploy/smoke_booking_mail.py`，按提示输入本地测试密码。该脚本会注册一次性 `example.test` 学员，完成申请、教练收信与确认、学员收信、授权链接检查，并撤销测试预约、下架测试课程、停用测试雪场。**真实 SMTP 模式下不要运行这些使用 `example.test` 地址的 Mailpit 冒烟脚本。**

若安装的是 `docker compose` 子命令，可等价替换 `docker-compose`。`smoke.py` 只读检查静态页、照片、健康端点、CSRF 和匿名权限；`smoke_identity.py` 会在本地数据库创建一个 `smoke-…@example.test` 学员，经过 Mailpit 验证、登录、退出；`smoke_password_recovery.py` 会创建一次性学员，经 Mailpit 收取验证码并检查旧密码和两个旧会话失效。设置 `SMOKE_RESTART_BACKEND=1` 可在登录后重启后端容器，检查 Session 在 MySQL 中恢复。

修改前端或后端代码后，已运行的容器不会自动更新。若使用 Vite 的 `http://localhost:5173`，也要在后端 API 变更后重建 8080 容器；若使用下表的 `http://localhost:8088`，前后端镜像都需重建。在仓库根目录运行 `docker-compose --env-file deploy/.env -f deploy/compose.yaml up -d --build backend frontend`，并核对两个容器健康状态。此操作保留本地 MySQL 卷。

真实 SMTP 使用 `MAIL_HOST`、`MAIL_PORT`、`MAIL_USER`、`MAIL_PASSWORD`、`MAIL_FROM`、`MAIL_SMTP_AUTH`、`MAIL_STARTTLS`，均由 `deploy/.env` 传给后端；示例见 `.env.example`。更改这些参数后运行 `docker-compose --env-file deploy/.env -f deploy/compose.yaml up -d --no-deps --force-recreate --wait backend`。该操作保留数据库，并让后端容器读取新参数；应用密码不要提交到 Git 或复制到命令输出。移除这些 SMTP 参数并重新创建后端可恢复 Mailpit 模式。

预约邮件链接使用 `V2_PUBLIC_URL`（映射到后端 `APP_PUBLIC_URL`），无需改代码。默认 `http://localhost:8088` 仅能在运行应用的电脑上打开；手机或其他电脑访问时，在 `deploy/.env` 中填写这些设备可访问的站点根地址。修改 `V2_HTTP_PORT` 时也要同步修改 `V2_PUBLIC_URL`。正式环境应使用已配置 HTTPS 的公开域名。Mailpit 不会把本地测试邮件送到外部邮箱。

| 服务 | 本机地址 |
|---|---|
| 前端与同源 API | `http://localhost:8088` |
| Mailpit 收件箱 | `http://localhost:8025` |
| 后端，供 Vite 代理 | `http://127.0.0.1:8080` |
| Docker MySQL，供 IDEA 连接 | `127.0.0.1:3307` |
| Mailpit SMTP，供 IDEA 后端连接 | `127.0.0.1:1025` |
| S3Mock 本地媒体，供 IDEA 与浏览器 | `http://localhost:9090` |

在 IDEA 开发后端时，可保留 `db` 与 `mailpit` 并停掉占用 8080 的容器后端及前端：

```sh
docker-compose -f deploy/compose.yaml stop backend frontend
```

IDEA 所需环境变量与启动类见[后端说明](../backend/README.md)。Mac 自身的 MySQL 若占用 3306，不影响此处的 3307。`docker-compose -f deploy/compose.yaml down` 会停止容器但保留 MySQL 命名卷；命名卷不是备份，删除卷会丢数据。

## 验证边界

Mailpit 模式的本地冒烟覆盖邮箱验证、登录、Session、退出、密码找回及预约两类邮件；实际执行结果见相应功能计划。真实 SMTP 模式需使用本人控制的邮箱单独验证，SMTP 接受也不等于收件箱最终送达。本地检查不证明生产 HTTPS Cookie、EC2 性能或备份恢复。生产部署、实际付费资源和 v1 数据迁移均需另行批准。

## IDEA 开发“关于 GEER”

保留 `db`、`mailpit`、`s3mock`，运行 `docker-compose -f deploy/compose.yaml up -d db mailpit s3mock`。S3Mock 5.2.3 仅绑定 `127.0.0.1:9090`，数据保存在 `media_v2_data` 命名卷；后端由 IDEA 启动在 8080，前端由 Vite 启动在 5173。ffprobe 安装、上传上限和生产配置见[后端媒体说明](../backend/README.md)。修改后需重新加载 Maven 并重启 IDEA 后端。

容器内 S3 endpoint 为 `http://s3mock:9090`，浏览器 URL 默认使用 `http://localhost:9090`；若从其他设备查看本地页面，需为 `MEDIA_PUBLIC_ENDPOINT` 配置该设备能访问的开发存储地址，并调整本地端口绑定，不能把 localhost 作为远程可达地址。生产使用 HTTPS 的 CloudFront URL。

构建镜像包含固定 ffprobe 9.0.2，运行账号非 root；构建不等于启动后端或生产部署。S3Mock 不校验预签名有效性，private S3/OAC/可信 key group、存储生命周期和费用须在实际 AWS 部署另验。
