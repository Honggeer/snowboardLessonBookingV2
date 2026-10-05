# 部署与运维入口

`deploy/` 负责本地开发环境、GitHub 自动发布、EC2 上的发布与回退，以及数据库备份、独立恢复检查和 HTTPS 证书续期。业务代码在 `backend/` 和 `frontend/`。

生产站点：[https://52.60.174.156](https://52.60.174.156)。`main` 的 CI 成功后自动部署；当前运行证据、备份和待验证事项见 [生产运维手册](docs/PRODUCTION_RUNBOOK.md) 与 [0010 交付记录](../ai-docs/implement-plan/0010-production-delivery.md)。

## 目录职责

| 位置 | 用途 | 什么时候看 |
|---|---|---|
| `compose.yaml`、`.env.example` | 本地 MySQL、后端、前端、S3Mock 与 Mailpit 环境 | 本地启动、IDEA 联调 |
| `compose.production.yaml`、`.env.production.example` | EC2 三容器配置及非密钥参数示例 | 核对生产容器和参数 |
| `ci/` | 发布条件检查、复用 ECR 镜像、打包和等待 SSM 发布结果 | 修改自动部署流程；由 GitHub Actions 调用 |
| `runtime/` | EC2 发布、回退、备份、独立恢复检查、证书续期程序 | 排查线上发布或维护服务器 |
| `aws/` | AWS 参数示例、主机配置示例及 IAM/S3/CloudFront/SSM 模板生成器 | 核对云资源配置；生成器只输出文件 |
| `systemd/` | 数据库每日备份和证书续期的 service/timer | 检查或安装 EC2 定时任务 |
| `tests/` | 自动化回归、本地冒烟与隔离发布演练 | 验证部署配置和维护改动 |
| `docs/` | 本地开发、EC2 准备、生产运维手册 | 查操作步骤 |

本地私密参数继续放在被 Git 忽略的 `deploy/.env`；示例文件只说明需要哪些字段。生产密钥保存在 EC2 的 `/etc/snowboard-v2/`，具体位置见运维手册。

## 常用入口

以下命令在仓库根目录执行。

本地首次启动前，按 [本地开发指南](docs/LOCAL_DEVELOPMENT.md) 创建 `deploy/.env`：

```sh
docker-compose -f deploy/compose.yaml up --build -d --wait
python3 deploy/tests/smoke.py
```

若安装的是 `docker compose` 子命令，可等价替换 `docker-compose`。IDEA、Vite、Mailpit、真实 SMTP 和 S3Mock 的使用方式也在本地开发指南中。

部署回归检查不读取本地 `deploy/.env`：

```sh
python3 -m unittest discover -s deploy/tests -p 'test_*.py'
python3 ai-docs/check_docs.py
```

隔离发布演练的镜像准备见 [生产运维手册](docs/PRODUCTION_RUNBOOK.md)：

```sh
python3 deploy/tests/verify_delivery.py
```

该演练创建自己的临时 Docker 项目和数据库卷，检查 HTTPS、发布失败回退及独立恢复，结束后清理该项目。`smoke_identity.py`、`smoke_password_recovery.py`、`smoke_booking_mail.py` 会创建测试数据，只按本地指南在 Mailpit 环境中使用。

生成可 review 的 AWS 配置文件：

```sh
python3 deploy/aws/render_aws_templates.py --parameters deploy/aws/parameters.example.json --output /tmp/geer-aws-review
```

## 仓库路径与服务器路径

GitHub workflow 调用 `ci/` 中的脚本；发布包从 `runtime/` 读取程序，再打包为既有的 `deploy/release.py`、`deploy/backup.py` 等包内路径。EC2 的 `/opt/snowboard-v2/bin/` 安装入口与 `/opt/snowboard-v2/releases/<SHA>/` 版本目录沿用现有约定。整理仓库目录后，下次发布仍与已安装的 dispatcher 和备份定时任务兼容。

服务器操作和备份恢复步骤见 [生产运维手册](docs/PRODUCTION_RUNBOOK.md)；服务器配置背景见 [EC2 准备指南](docs/EC2_SETUP.md)。
