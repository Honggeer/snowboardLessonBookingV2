# v2 生产交付运维手册

对应 [0010 revision 3 首发 review](../ai-docs/implement-plan/0010-production-delivery.md)。revision 2 的 P-02 至 P-07 已获批并完成本地交付/隔离验证；本手册的 AWS 配置、正式存储处理、生产初始化及首次发布属于 P-08，用户已批准执行和 commit/push；云资源/主机准备已完成，应用首发尚未执行，详见计划实际证据。用户已明确跳过买域名，本次提议现有 EC2 + 一个 EIP + 可信 IP HTTPS、同容量加密根盘；具体资源、换盘/回退/清理、费用和步骤见计划第 1/6 节。Git commit/push 默认由用户负责，仅明确要求时 Codex 执行。自动发布变量默认不启用。

## 1. 已交付与本地检查

- `compose.production.yaml`：三容器、独立数据库卷、只读密钥文件、TLS、内存和日志上限、健康检查及重启策略。
- `.github/workflows/release.yml`：可信 main/push/CI success、准确受测 SHA、原生 ARM64 构建、ECR digest、版本化配置包、OIDC、SSM 结果检查。并发串行，过时版本在 CI 和服务器各检查一次；无需每次手动发布。
- `dispatch.py`：预安装入口，只接收 SHA/checksum/digest；固定私有桶、2 MiB 文件白名单、不允许链接或任意 shell；主机非阻塞排他文件锁覆盖发布。执行同一个版本包里的发布程序。
- `release.py`：预检后启动、HTTPS/数据库检查、保存当前/上一版；健康失败仅在迁移历史及数据库结构指纹都兼容时回退。失败迁移、指纹改变、首次发布没有兼容回退点时停止应用，保留数据库。
- `backup.py`：每日一致逻辑导出、压缩校验、S3 加密上传、最后上传成功标记；记录失败及超过 24 小时的备份。恢复只创建隔离容器，核对全部表、快照行数和迁移版本，清理自己的临时数据库。
- `renew_certificate.py`：校验证书/私钥及有效期，原子替换文件、NGINX 测试与重载；重载失败恢复文件。

在仓库根目录执行，均不读取 `deploy/.env`：

```sh
python3 -m unittest discover -s deploy -p 'test_*.py'
python3 ai-docs/check_docs.py
git diff --check
python3 deploy/render_aws_templates.py --parameters deploy/aws/parameters.example.json --output /tmp/geer-aws-review
```

真实隔离演练需 Docker Compose ≥ 2.24.4、OpenSSL、原生 ARM64 Docker，先构建本地镜像：

```sh
docker build --platform linux/arm64 -t snowboard-v2-delivery-backend:local backend
docker build --platform linux/arm64 -t snowboard-v2-delivery-frontend:local frontend
python3 deploy/verify_delivery.py
```

演练使用 `.local/` 下新临时目录、`geer-delivery-check-<随机值>` 项目、新数据库卷、测试专用密钥/证书及回环端口。数据库和应用网络禁止外网访问，只有 NGINX 加入入口网络；不会发外部邮件或调用 AWS。结束仅清理该隔离项目、卷和独立恢复容器。镜像由本机构建，正式 ECR/OIDC/SSM 仍需 P-08 验收。

## 2. P-08 前须定稿的资源与成本

| 项目 | 当前事实 / 需要确认 | 费用与清理 |
|---|---|---|
| EC2 | 已有 Canada Central t4g.small / ARM64 / Standard / AL2023；Docker/Compose 已准备 | 已有实例 + 一个公网 IPv4 的参考基础成本约 24.33 CAD/月，来源与口径见 0010；不含下列费用及税 |
| EBS | 当前 20 GiB gp3 **未加密**，TODO-0024 OPEN；revision 3 提议停机快照、同 AZ 同容量加密新根盘、保留旧盘回退 | 常态约 2.51 CAD/月；旧盘/快照最多保留 48 小时临时约 0.27 CAD，失败保留并报告；实际换盘/清理待 review |
| 地址 / 域名 | 用户跳过域名；revision 3 提议一个 EIP 替代自动公网 IPv4，用 IP HTTPS | 同时仅一个 IPv4；不买域名、不建 Route 53/ALB/NAT；EIP/证书仍未创建 |
| 媒体 | staging/frozen 两个独立私有 S3 桶，staging 开启 versioning；CloudFront OAC + viewer key group | 统计媒体大小、版本、播放/请求量和区域价格，不能只算逻辑配额或免费字样 |
| 运维 | 第三个私有桶，可共用 `releases/` 与 `db-backups/`；不连接 CloudFront | 备份当前对象保留 7 天，非当前版本另保留 1 天，删除可能异步；发布包保留当前/上一版及重建所需版本 |
| ECR | 两个限定仓库，SHA tag IMMUTABLE，部署使用 digest | 清理超过 7 天的 untagged 镜像；tagged 镜像先核对当前/上一版，不能用最近 N 个规则误删回退点 |
| SMTP / 日志 | 沿用用户发信服务；容器日志每服务 10 MiB × 3 | SMTP 实际送达及限额待正式验证；新付费监控/日志服务不在默认范围 |

AWS 总预算目标仍是 30 CAD/月。revision 3 的明确低用量场景合计约 **27.66 CAD/月未计税**，包含现有 EC2、一个 IPv4、20 GiB gp3、ECR 2 GiB、S3 全部版本 11 GiB 及请求；CloudFront 在账户共享免费额度内。详见计划第 6 节，实际使用量/旧资源/税/SMTP 账单另核对；提醒不是硬费用上限。t4g.small 的应用容量尚未验收。初始内存限额为 backend 768 MiB（heap 384 MiB）、MySQL 640 MiB、NGINX 64 MiB；发布要求至少 4 GiB 磁盘可用，备份要求至少 5 GiB，单次导出硬限制 2 GiB。独立恢复另占 640 MiB，本次在本机受控独立 Docker 主机恢复真实 S3 备份；不把本地 4 GiB Colima 结果当作 EC2 容量证据。

## 3. AWS 模板的使用顺序（待具体操作授权）

复制 `aws/parameters.example.json` 到仓库外，用真实账户、桶、origin、实例和身份参数重新 render。示例账户、桶和 immutable owner/repo ID 都是虚构值。

1. 核对已有网络、SSM、IMDSv2 Required/hop 2 和 Standard。确定存储方案；不直接把未加密卷记为已加密。网站入口按首次受控验收方案开放 80/443，3306/8080/22 保持不公开。
2. 在 `ca-central-1` 创建两个 ECR 仓库 `snowboard-v2-backend` / `snowboard-v2-frontend`，tag IMMUTABLE；应用 `ecr-lifecycle.json`。CI 的重试会复用已有 SHA 的 digest，权限错误不会被当成镜像不存在。
3. 创建三个独立 S3 桶；应用 `bucket-privacy.json`、`bucket-encryption.json` 和 HTTPS-only bucket policy。staging 与 operations 开启 versioning；operations 应用 `operations-lifecycle.json`；staging 设置精确 `staging-cors.json` 及 `staging-lifecycle.json`。后者仅清理未完成 multipart，不自动删除已引用或待处理版本。应用已有媒体 worker 负责按记录清理孤立对象；定期核对非当前版本存储，新增过期策略先确认业务影响。
4. 为 CloudFront 生成 RSA 2048 签名 key；私钥只放安全密钥存储及服务器只读文件。上传**公钥**，用其 ID render `cloudfront-key-group.json`；创建 key group、OAC，再填写 `oac_id`、`key_group_id` 和 `public_key_id` 重新 render `cloudfront-distribution.json`。创建 distribution 后填写真实 `distribution_id`，应用 `frozen-bucket-policy.json`。使用默认 `*.cloudfront.net` HTTPS 域名，viewer 必须提供有效签名；OAC 只允许指定 distribution 读 frozen 路径。
5. EC2 保留 AmazonSSMManagedInstanceCore，额外添加 `ec2-permissions.json`：只读限定 ECR、限定 staging/frozen 操作、运维桶指定前缀。CI 使用单独 OIDC role，`ci-trust.json` + `ci-permissions.json`；只允许限定仓库推镜像、写发布包、调用指定实例及专用 SSM document，不授权任意 AWS-RunShellScript。
6. OIDC `sub` 必须按本仓库实际声明核验，aud 固定 `sts.amazonaws.com`，subject 精确匹配 `environment:production`；新仓库可能带 immutable owner/repo ID。GitHub `production` environment 限制 main，配置为自动部署所需规则，不添加每次人工审批。核验只记录非密钥声明，不在日志/文件保存 JWT。
7. 使用 `ssm-document.json` 创建 Command document `snowboard-v2-release`。确认 SSM Agent 支持 `interpolationType: ENV_VAR`；旧 agent 不做字符串插值降级，文档会拒绝运行。实例预安装入口见下一节。

JSON 文件是 API 输入片段：IAM 用 `--policy-document`，SSM 用 `--content`，OAC/distribution/key group 用 `--cli-input-json`，S3 CORS/lifecycle 分别用 `--cors-configuration` / `--lifecycle-configuration`；Bucket/Policy 参数按对应真实对象单独提供。模板生成不执行 AWS 操作。正式调用前核对精确 ARN、ID、权限和费用。

参考：[GitHub AWS OIDC](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)、[workflow_run](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_run)、[SSM 参数环境插值](https://docs.aws.amazon.com/systems-manager/latest/userguide/documents-command-ssm-plugin-reference.html)、[CloudFront OAC](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html)、[S3 生命周期](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-configuration-examples.html)。

## 4. 实例文件与密钥（待配置）

安装 Python 3、AWS CLI v2、OpenSSL，核验现有 Docker/Compose；避免重复安装已准备环境。入口程序仅 root 可写，工作目录与配置仅 root 可管理。

| 位置 | 内容与权限 |
|---|---|
| `/opt/snowboard-v2/bin/` | 经 review 的 `dispatch.py`、`release.py`、`run_backup.py`、`renew_certificate.py`、`renew-certificate.sh`；root 拥有，脚本 0755 / Python 0644，目录 0755 |
| `/opt/snowboard-v2/releases/<SHA>/` | 已验证 checksum 的发布包，包含生产 Compose/NGINX/发布及备份程序；上层目录 root 0700；不得覆盖已有不同 checksum 的同 SHA |
| `/etc/snowboard-v2/host.json` | 根据 `aws/host.example.json` 填真实参数，root 0600；production.env 同样 0600，使用 `.env.production.example` 的字段 |
| `/etc/snowboard-v2/secrets/spring/` | `spring.datasource.password`、`identity.verification-key`（至少 32 UTF-8 字节）、`spring.mail.password`；文件 root:101 / 0640，目录 root:101 / 0750；backend 当前 UID 100/GID 101 已实测，镜像变更时重新核对 |
| `/etc/snowboard-v2/secrets/media/cloudfront.pem` | CloudFront RSA 私钥，root:101 / 0640，media 目录 root:101 / 0750 |
| `/etc/snowboard-v2/secrets/mysql.root.password` | 与应用密码不同；root:root / 0600。MySQL 使用专用 `snowboard_v2` 账号连接应用 |
| `/etc/snowboard-v2/tls/` | `fullchain.pem` 0644、`privkey.pem` 0600，NGINX root 启动读取；整个目录只读挂载，续期原子替换可被容器看到 |
| `/var/lib/snowboard-v2/` | state、backup-work、acme 目录；state/backup-work root 0700。acme 的 challenge 文件需要 NGINX 可读 |

在目标服务器用安全工具生成/录入实际秘密，验证**可读性及字节长度**而不打印内容。生产密码、验证码密钥、SMTP 密码、CloudFront 私钥都不进入 Git、镜像、聊天或 CI。容器的 `configtree:/run/secrets/spring/` 直接覆盖 Spring 相应属性，环境文件只存非密钥参数及路径。TLS 下 Secure/HttpOnly/SameSite=Lax Cookie 始终开启，不为公网 HTTP 关闭。

NGINX 信任入口自身观察到的客户端 IP；覆盖 X-Real-IP/X-Forwarded-For/X-Forwarded-Proto，应用仅信任 frontend 的容器地址。健康检查包含应用及数据库，SMTP health indicator 关闭；邮件必须在正式业务验收中实际送达，并检查持久化任务失败/积压，不能把 UP 当成发信成功。

## 5. IP HTTPS 与续期（本次跳过域名）

本次首发采用一个实际分配的 EIP；`APP_PUBLIC_URL`、host.json public_url、staging CORS origin 都为 `https://<实际EIP>`。当前自动公网 IP 不是已保留的 EIP，不能提前把它写为最终地址。生产不使用测试证书。

[Let's Encrypt 官方 IP 证书指南](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)确认 Certbot ≥5.4 的 `--ip-address <实际EIP>`、`--preferred-profile shortlived` 与 webroot 支持。沿用 Certbot 5.8.0；初次先 staging standalone HTTP-01，再正式申请，证书约六日有效。NGINX 启动后使用 `certbot reconfigure` 转为 webroot 和既有 deploy hook，核对保存的 renewal 配置与 lineage `/etc/letsencrypt/live/<实际EIP>`；daily twice timer 必须实际启用并 dry-run/reload 通过。不要用 `-d <IP>` 冒充域名或使用暂不支持 IP 的 nginx installer。

AL2023 采用 Python 3.11 venv 固定安装 Certbot 5.8.0（2026-10-04 已核对 PyPI，要求 Python ≥3.10）；在 P-08 再核对系统包与版本后安装。路径 `/opt/snowboard-v2/certbot`，不升级业务依赖。首次 standalone 要求 80 空闲；随后把证书复制到配置的 TLS 目录并填写 `certificate_lineage`。webroot authenticator 指向 `/var/lib/snowboard-v2/acme`，由 NGINX 80 的 `/.well-known/acme-challenge/` 提供文件，其余 HTTP 请求跳转 HTTPS。

安装 `systemd/snowboard-v2-certbot.service` 和 `.timer`，首次配置完通过 Certbot staging/dry-run 验证 webroot，启用 timer。每天 00/12 UTC 检查，deploy hook 验证新 key pair、有效期、重载 NGINX；不会重启数据库。记录首次续期/dry-run 结果和证书 expiry，`systemctl status` / journal 可查看失败。Certbot 包及 ACME 正式网络验证尚未执行。

## 6. 首发及自动部署启用

仓库变量填写 `BACKEND_ECR_REPOSITORY`、`FRONTEND_ECR_REPOSITORY`、`RELEASE_BUCKET`、`PRODUCTION_INSTANCE_ID`、`PRODUCTION_SSM_DOCUMENT`、`PRODUCTION_CI_ROLE_ARN`。只有首次云端配置与生产操作授权覆盖后，才设 `PRODUCTION_DELIVERY_ENABLED=true`。未设置时 gate/publish 都跳过，不请求 AWS 发布凭证、不推镜像、不发 SSM 命令。

首次先在受控入口验收，明确 SHA、镜像 digest、配置包 checksum、迁移 V1–V10。启动空生产库会执行 Flyway，这属于首次上线授权。教练使用既有私下初始化命令；不复制本地测试用户、邮件任务、媒体或 v1 数据。初始化说明见 [backend README](../backend/README.md)。

用户 commit/push 后，CI 全部成功触发 release；从该 run 的 head_sha 构建/复用镜像，检查 ARM64 ffprobe，再上传同 SHA 的配置包，SSM 调用固定入口并等待真实完成。云端结果不因 `send-command` 返回而提前算成功。服务器也拒绝已被新 main 替代的 SHA。GitHub concurrency + 文件锁串行；workflow 取消不代表远程命令已停止，超时后先查 CommandId/主机状态再重试。

首次 P-08 验收涵盖注册/验证/登录/找回、CSRF/越权、申请/确认/取消、SMTP 实收和正确 HTTPS 链接、匿名 S3/无签名 CDN 拒绝、媒体上传/发布、同一约 65 秒视频播至 ended 与 Range、容器重启/session 恢复。执行事先定稿的并发和媒体负载，检查 CPU credits、内存/OOM、磁盘及备份期间余量；不满足容量/预算时交用户取舍。业务验证通过后按公网授权开放入口，再更新 RELEASED。

## 7. 回退、每日备份与恢复

发布保持数据库卷；不会逆向 DDL、覆盖数据库或执行生产 `down --volumes`。兼容失败恢复上一版 image digest、Compose 和 NGINX 文件。`state/current.json` / `previous.json` / `last-release.json` 记录成功版本及失败情况；首发或不兼容失败保留数据库、停止应用、报告无自动回退点。预检失败不会停止已运行的应用。手动回退先核对上一版 schema/指纹、当前唯一 worker 与授权，再使用保存的完整 manifest；下次自动发布仍由 main 决定。

安装 `snowboard-v2-backup.service` / `.timer`，首发成功且真实备份演练通过后启用 timer，每日 06:00 UTC，Persistent 补执行漏掉的时点。launcher 使用当前成功版本的备份程序；备份与发布共用同一锁，导出期间不做迁移。导出采用 MySQL 8.4 `--single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --hex-blob --skip-extended-insert`，快照每行计数写 manifest；S3 上传 AES256、payload/manifest 后最后上传 SUCCESS.json，导出/上传失败记录 failed。工作文件在成功或失败后清理；2 GiB 导出限制在写文件时执行。单进程有界，超时 30 分钟。

每次检查 `backup.py status --config /etc/snowboard-v2/host.json`（使用当前版本包的脚本）和 systemd/journal。status 要求最近成功快照不超过 24 小时；缺失、失败或超期明确失败。RPO 24 小时是目标，备份故障会超过目标。7 天生命周期覆盖整个 `db-backups/` 前缀及非当前版本；S3 实际删除异步，短暂额外存储计入账单。发布包/媒体不适用此过期规则。

恢复演练先在受控主机下载**同一备份目录的三个文件**，使用 `restore-check.sh --backup <目录> --schema-version 10`。脚本验证 SUCCESS/manifest/checksum、展开大小与 schema；只创建新的 `geer-restore-<随机值>` MySQL 8.4 容器，network none、640 MiB、不发布端口，不接受既有/正式容器。恢复核对全部表集合、行数及成功迁移版本，报告耗时，最后仅删除该临时容器及其匿名卷。生产库恢复另需具体计划与授权，本脚本不支持覆盖正式库。

数据库备份之外，安全保管验证密钥、SMTP/数据库秘密、CloudFront 私钥、TLS/配置恢复方法；这些不放运维桶的公开发布包。灾难重建需要实例角色、网络、加密卷方案、配置、密钥和异机备份；RTO 4 小时仍是待评估目标，不以本地 8 秒恢复当成真实灾难 RTO。

## 8. 清理与后续证据

每次发布核对镜像缓存、tagged 镜像、配置包与磁盘余量；清理前保护 current/previous 对应 image ID/digest、配置和回退点。ECR 仅自动清理 untagged；tagged/主机旧缓存/发布包清理按库存与授权执行，发布前 4 GiB 门禁阻止继续压满磁盘。媒体库存由应用清理任务与 S3 版本清单核对；不能用全桶过期误删已发布内容。

取消上线/替换资源前列清单及数据保留；EC2 停机仍有 EBS/存储费用，地址/卷/快照/S3/ECR/CloudFront/DNS 都需核对并按授权处理，不自动删除。

P-08 应记录真实资源 ID、精确费用/使用量、IAM 正反例、OIDC subject、首次 SHA/digest/CommandId、TLS/邮件/完整视频、备份异机恢复及容量结果。当前本地交付通过不满足整体 VERIFIED/RELEASED；TODO-0024 与本地视频 TODO-0022 仍待各自完成判定。
