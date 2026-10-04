---
id: "0010"
title: "首次生产上线与 CI/CD"
status: IMPLEMENTING
revision: 3
approved_revision: 3
created: 2026-10-04
updated: 2026-10-04
feature: "../features/0010-production-delivery.md"
---

# 0010 — 首次生产上线与 CI/CD 实施计划

## 1. Review 摘要

让目前仅在本地验证的网站以 HTTPS 正式运行，并用 GitHub Actions 构建版本化镜像、发布到单台 EC2、验证健康状态和恢复兼容的上一版本。关联[功能 0010](../features/0010-production-delivery.md)与 [ADR 0001](../decisions/0001-v2-baseline.md)。revision 2 的本地实现已完成；本次 revision 3 具体化 P-08 首发，等待该范围 review，不撤销已有实现证据。

用户本次确认：AWS 总预算仍希望约 30 CAD/月，区域 `ca-central-1`，尚无域名，已能正常对外发邮件，v2 不需要数据迁移。业务时间继续是 `America/Toronto`，AWS 区域为 Canada (Central)。

2026-10-04 用户进一步要求：“域名先放一下，我想先搞一个AWS ec2服务器……使用量应该没那么大……你教我一步一步怎么搞”。当前先提供[EC2 创建与登录指南](../../deploy/EC2_SETUP.md)，推荐 t4g.small、ARM64 Amazon Linux 2023、20 GiB gp3 与 Session Manager。这是对用户手动准备服务器的指导，不是本计划整体获批，也不是 Codex 已创建资源或容量验收通过。域名和其余上线工作后置，不取消最终 HTTPS/备份验收。

用户回传服务器基础检查后，要求 Codex 远程操作、提供实例 `i-0c7978984740cbd58` 并完成 AWS 浏览器认证。已通过 SSM 独立核对 t4g.small/ARM64、AL2023.12、Standard、入站为空、IMDSv2，安装和验证 Docker Engine 25.0.16 与 Compose v5.6.0。根盘是 20 GiB gp3，但未启用加密，记录 TODO-0024。该独立服务器准备范围不代表批准整体生产配置或 CI/CD 实现。

当前已有 `.github/workflows/ci.yml`，覆盖后端、前端、文档及本地 Compose。2026-10-04 恢复会话后已查询最新 main 提交 `448b5c51e4c708edc4bd2fe28191aa7b651abd5b` 的 [CI run 37176068847](https://github.com/Honggeer/snowboardLessonBookingV2/actions/runs/37176068847)：backend/frontend 均 success，docs-and-compose 在 Compose configuration 步骤 failure。干净本地环境仍因缺少 `VERIFICATION_KEY` 而 exit 1；远端详细日志受连接重新认证限制，未把本地错误文本写成远端日志。现有 Compose 的 local、Mailpit、S3Mock 和源码构建配置不能直接作为生产配置。

提议延续单机三容器，选择具备实测容量的低成本实例，CI 构建并推送 ECR，EC2 通过 IAM Role 拉取，GitHub OIDC + SSM Run Command 触发已安装的部署程序。首次上线前完成恢复演练；单机接受短暂中断。

2026-10-04 用户在收到 revision 2 P-02 至 P-07 review 范围后明确要求“开始实现”。本地交付实现与隔离验证已获批并完成；自动发布与每日 7 天备份按此前选择实现。域名按此前要求后置。首次云资源配置与发布准备完成后，后续可信 main 提交按该自动发布规则执行，无需逐次手动点击。Git commit/push 仍由用户决定。

### revision 2 已批准的本地范围

本次提交 review 的是 **P-02 至 P-07 的本地实现与隔离验证**：修复 CI 配置检查，准备生产 Compose/TLS、ARM64 镜像工作流、SSM 发布/回退程序、每日备份/独立恢复程序、IAM 与运维模板。先测试 RED，再实现 GREEN 和适用回归。现有空 EC2 的可访问性已复核，不需重复安装 Docker。

真实域名、正式存储加密、实际媒体用量/完整费用和首次云资源操作仍在 P-08 前定稿；这些事项不阻塞使用测试专用参数编写与验证上述交付文件。测试使用本机隔离 Compose 项目、新命名卷和临时证书，禁止复用本地业务卷或正式数据库；不发送外部邮件。

云端发布 job 以仓库变量 `PRODUCTION_DELIVERY_ENABLED=true` 为启用条件，未设置时不申请 AWS 发布权限、不推镜像、不发送部署命令。该开关在首次配置与发布授权覆盖后启用，之后 main 成功提交自动发布；不是每次发布的人工审批。实际资源创建、更换/停止 EC2 根盘、初始化生产库、首发和开放公网入口均不在本次本地实现批准范围内。

本阶段不增加 AWS 资源费用。不包含 v1 导入、RDS/多机、ALB/NAT Gateway、付费转码或业务改造；改变这些范围须重新 review。预算与容量不能以本地实现通过代替。

| 用户待决定 | 具体提议/选项 | 影响与阻塞 |
|---|---|---|
| 实例、存储和总价 | 沿用已核实 t4g.small/ARM64、20 GiB gp3；revision 3 提议在正式数据落盘前换为同容量加密根盘，低用量完整估算约 27.66 CAD/月 | 不再重复询问已有实例规格；具体换盘和新资源待本次 review |
| 域名 | 2026-10-04 用户明确“还没买域名，先跳过”；revision 3 提议固定 IPv4 + 可信 IP HTTPS 证书 | 无需购买域名即可首发；实际 EIP、证书和续期仍需执行与验收 |
| CD 触发 | 已确认：main 分支测试通过后自动更新 EC2 | 仅可信 push/main 且 CI 全成功；首次云端配置完成后启用，无逐次人工发布门槛 |
| 恢复目标 | 已确认每日异机备份、保留 7 日，RPO 目标 24 小时；RTO 4 小时仍是待评估目标 | 本次可实现频率/保留/独立恢复；实际 RPO/RTO 须在上线前演练，不保证备份故障时仍满足 24 小时 |
| 使用量和预算口径 | 沿用约 30 CAD/月目标；第 6 节给出明确低用量场景，税和其他旧资源另列 | 不把估算场景写成用户已保证的使用量；持续账单不能仅按 EC2 价格判断 |

### revision 3：P-08 首次生产发布 review

2026-10-04 用户要求“现在是什么情况，能上线了吗，我想上线”，随后明确“还没买域名，先跳过”，并提醒实例已运行、规格已知。本次不再选购域名或重建实例，按已有预算准备以下具体首发方案；上述方向要求不冒充用户已 review 并批准全部新增操作。

| 对象 | 本次具体资源 / 操作 |
|---|---|
| 已有服务器 | 仅操作 `ca-central-1` 的 `i-0c7978984740cbd58`，保留 t4g.small / ARM64 / AL2023 / CPU Standard、现有实例角色及 SSM 通道 |
| 固定地址与 TLS | 为该实例分配并关联 **一个** EIP，替代当前自动分配公网 IP `15.223.235.98`；首发地址为 `https://<实际EIP>`。不保留第二个闲置 EIP，不购买域名，不创建 Route 53 hosted zone |
| 正式存储 | 在无业务容器/正式数据时，停机后给现有根盘 `vol-01afc752b9b52dd07` 做快照；由该快照直接创建同 AZ `ca-central-1d`、20 GiB gp3、3000 IOPS / 125 MiB/s 的加密卷，使用 AWS 托管 EBS key；保留旧卷，替换 `/dev/xvda` 根盘并恢复启动/SSM/Docker。失败重新挂回旧卷，不删除数据，不改变整个账户的默认加密配置 |
| 换盘临时资源 | 仅本次生成的快照、旧根盘在新盘、SSM 和 Docker 复核成功后保留至 48 小时；本次 review 包含届时清理这张快照和旧根盘的授权。失败不清理回退资源，先报告其 ID 与费用；实际新根盘不列入清理。新根盘核对 DeleteOnTermination 设置与原实例一致 |
| ECR | 两个 private / immutable SHA tag 仓库：`snowboard-v2-backend`、`snowboard-v2-frontend`；ARM64 镜像使用 digest；只自动清理超过 7 天的 untagged 镜像，保留当前/上一版及受保护 tagged 版本 |
| S3 | 三个新私有桶：`snowboard-v2-481604401994-ca-central-1-staging`、`snowboard-v2-481604401994-ca-central-1-frozen`、`snowboard-v2-481604401994-ca-central-1-operations`；SSE-S3、Block Public Access、HTTPS-only；staging/operations versioning，按已实现模板配置精确 IP origin 的 CORS、7 天备份和 multipart 清理。名字若已被其他账户占用，不操作其桶；仅在同一前缀加随机后缀并记录实际名称 |
| 媒体分发 | 一个 CloudFront **pay-as-you-go** distribution，默认 CloudFront HTTPS 域名、PriceClass_100、一个 OAC、一个 RSA 公钥/可信 key group；frozen 只允许该 distribution，viewer 必须签名。不启用新付费 WAF、日志或转码服务 |
| 权限与发布 | 现有 `snowboard-v2-ec2-role` 增加模板限定 ECR/S3 权限；一个 GitHub OIDC provider（已有则核验后复用）、角色 `snowboard-v2-github-release`、SSM Command document `snowboard-v2-release`；CI 不能调用任意远程 shell |
| GitHub | `Honggeer/snowboardLessonBookingV2` 的 `production` environment 限定 main，无逐次人工审批；填写既有 workflow 所需非密钥变量。启用前保持 `PRODUCTION_DELIVERY_ENABLED` 关闭；核对受测 SHA、资源和首发准备后启用。**commit/push 须用户单独明确允许**，拟将本次交付文件按 `ci: add production delivery and first release configuration` 提交并推到现有 main，不 amend/force push |
| 主机配置与数据 | 预装已验证的发布/备份/续期入口和 root 保护配置；生成新数据库、验证和签名密钥，不复制本地/v1 数据；沿用现有可发信 SMTP 配置，经安全文件传输，不进聊天、Git 或 SSM 日志。启动已有 Flyway V1–V10，仅初始化空生产库；正式教练使用用户指定邮箱，初始密码安全生成并通过找回密码设置，不在聊天显示 |
| 公网与首发 | 80 对公网用于 ACME 和 HTTPS 重定向；初次 443 先限验收端公网 IPv4 `/32`，通过后改为公网 443。22/8080/3306 不公开；首发、空库迁移、受控真实邮件验收及最终开放公网都纳入本次 review |

**IP TLS 的具体方式**：官方 [Let's Encrypt IP/六日证书说明](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)确认 Certbot ≥5.4 支持 `--ip-address`、`--preferred-profile shortlived` 和 webroot；证书约六日有效，不使用自签名证书。沿用已核验 Certbot 5.8.0 / Python 3.11 venv，先 staging standalone 验证 HTTP-01，再申请正式 IP 证书并装入现有 TLS 目录；生产 NGINX 启动后 reconfigure 为 webroot，使用已有 deploy hook 校验并 reload，每日两次自动续期。验证 staging/dry-run、正式信任链/IP SAN、Secure Cookie 与续期 hook，保存实际到期时间。更换 IP 后须重新签证书并同步 public URL/CORS，不将旧公网 IP 当固定地址。

**执行顺序及停止条件**：

1. 获批后记录 revision 3 原话与范围；复核账户/实例/无正式数据、GitHub 写入身份及所需非密钥教练邮箱。当前 SSH `git ls-remote` 为 Permission denied (publickey)，见 TODO-0025；需要一次性恢复用户的 GitHub 授权，不索取长期 key/token 到聊天。
2. 固定 EIP；无业务数据时停机、快照、新加密卷、换根盘，核对系统/SSM/加密/容量/CPU Standard；失败先恢复旧根盘。此过程有服务器短暂停机。
3. 创建以上限定 ECR/S3/CloudFront/IAM/SSM 对象；按真实 IDs render，核验 OIDC 的 aud/sub。仓库公开 API 的 owner ID `60202820`、repo ID `1391576226`、创建于 2026-09-28；默认 immutable ID subject 的候选为 `repo:Honggeer@60202820/snowboardLessonBookingV2@1391576226:environment:production`，最终以实际非密钥声明为准，不凭候选放宽 trust。
4. 装保护配置/密钥和 IP 证书；执行本地文档/交付门禁后，在**明确 commit/push 授权**下提交并推送。先确认同一 main SHA 的 CI 全部 success，再启用自动发布；触发方式须确保是可信 push/main，远端受测 SHA 才可成为首发 SHA，不以未提交工作区冒充已受测版本。
5. 首发到限制入口，真实验证邮箱注册/找回、教练/学员登录、预约申请/确认/取消和实际邮件链接、权限/CSRF、容器重启后会话；重新上传现有宣传素材。正式媒体核对私有 S3、无签名 CloudFront 拒绝、Range 206、同一 65 秒视频从头到 ended，不把本地 S3Mock 故障直接移作 AWS 结论。
6. 从真实 EC2 上传备份到 operations，下载到本机受控独立 Docker 主机恢复，核对校验和/全部表/行数/schema；记录实际恢复耗时和完整步骤，以 4 小时 RTO 为评估目标，RPO 目标 24 小时。不在 2 GiB 生产机上额外并行启动恢复库；测试实际 timer 与最新成功快照，密钥另存仓库外受控恢复副本。
7. 容量验收场景先按小规模候选执行：10 个并发用户、15 分钟浏览/查询/受控预约，视频流经 CloudFront；观察 CPU credits、内存/OOM、数据库连接、应用错误和磁盘至少 5 GiB 余量。此场景是首发验收范围提议，不声称用户保证只有 10 人；不满足则报告并暂停开放，不自动加大实例或新增付费服务。
8. 以上门禁通过才开放 443、记录实际 SHA/digest/证书/备份/资源 IDs，进入 RELEASED；不能用 health UP 或本地测试数量代替首发验收。首次无上一版，失败停止应用并保留库；已有兼容上一版时执行已实现的回退。不逆向迁移，不覆盖/删除生产库。

用户已明确批准 revision 3 并允许 commit/push。P-08 正在执行；实际云资源/主机准备已完成的部分见第 8 节，应用首发尚未执行；GitHub 登录已成功，正在提交与核对远端 CI。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 初始调查（未批准） | 用户提出上线与 CI/CD，并确认预算、区域、邮件和无迁移；当时尚未 review 本计划 | N/A | 仅需求调查与草稿编写 |
| 2026-10-04 | “你不能远程连接我的服务器然后帮我做吗？”；随后提供实例 ID 并完成浏览器登录 | N/A（独立服务器准备授权） | 已有实例的配置检查和 Docker/Compose 安装验证；不批准整体 revision 1、业务部署、生产初始化或新增 AWS 资源 |
| 2026-10-04 | “接着做”；选择“自动发布：main 分支测试通过后，自动更新 EC2”和“每日备份、保留 7 天：最多可能丢失 24 小时数据” | N/A（继续调查及需求决定） | 恢复上线准备，确认自动发布与备份目标；revision 2 本地实现仍需 review，未把继续调查当成具体计划批准 |
| 2026-10-04 | “开始实现”（紧接 revision 2 P-02 至 P-07 review 提交） | 2 | 批准 P-02 至 P-07 本地交付实现及隔离验证；不包含 P-08 真实资源创建、生产初始化、发布、Git commit/push |
| 2026-10-04 | “按 revision 3 上线，并允许你 commit/push。我有个问题，不需要我提供任何key认证吗？如果需要我亲手做的跟我说，我本地varification key是以传参给到idea的” | 3 | 明确批准第 1 节 P-08 现有实例/EIP/IP TLS、加密换盘与限定临时资源清理、云资源、空库初始化、首发验收与公网开放；明确允许 Codex commit/push，禁止 amend/force push。生产密钥独立生成；需要用户亲手认证或非密钥账号信息时说明，不索取本地 verification key |

revision 2 的 P-02 至 P-07 已完成测试先行、本地实现及隔离验证，证据保留。用户已明确批准 revision 3 并允许 commit/push；当前 `approved_revision: 3` / IMPLEMENTING，按 P-08 具体范围执行，整体真实环境验收未完成，不进入 VERIFIED/RELEASED。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | 本计划、功能、必要 ADR | 确认 CD/备份目标；分阶段明确本地实现范围与首次云端上线前置条件 | 本地实现 revision review 获批；P-08 前另定域名/存储/完整费用与容量/RTO | 本地范围已批准；P-08 前置事项待定 |
| P-02 | 预计 `deploy/test_ci_config.py`、`deploy/test_production_config.py`、`deploy/test_release.py`、`deploy/test_backup_restore.py`、`.github/workflows/ci.yml` | 先写行为检查，复现干净环境缺 key、生产配置缺失、发布失败/并发及损坏备份未正确拒绝；CI 注入明确无效的测试专用 key | 有效 RED 记录，修复后同用例 GREEN | 完成，RED/GREEN 见第 8 节 |
| P-03 | 预计 `deploy/compose.production.yaml`、`deploy/.env.production.example`、`frontend/nginx.production.conf`、`deploy/README.md` | 独立生产 Compose：版本镜像、TLS/证书只读挂载、无 local/沙箱、MySQL 仅内网、密钥只读注入、日志轮转、健康检查、资源限制与重启策略 | 与本地配置隔离；校验及隔离启动通过 | 本地完成 / 隔离通过 |
| P-04 | 预计 `.github/workflows/release.yml`、CI 工作流、必要 Dockerfile 修改 | 可信 main 的 CI 全成功后构建 linux/arm64 镜像；记录准确受测 SHA/digest；OIDC 推 ECR，再通过受控 SSM 自动部署；首次启用前云端 job 禁用 | 失败/PR/其他分支/旧提交不发布；平台镜像实际启动及 ffprobe 通过 | 本地完成；云端待 P-08 |
| P-05 | 预计 `deploy/release.sh`、SSM 部署文档及 IAM 策略模板 | 部署入口只接收受控仓库与校验过的 commit SHA/digest；主机锁与 workflow concurrency 串行；拉取、启动、等待、检查、记版本；兼容时失败回退 | 成功、重复调用、故障、并发和回退演练通过；保留 DB 卷 | 本地完成 / 实际失败回退通过 |
| P-06 | 预计 `deploy/backup.sh`、`deploy/restore-check.sh`、`deploy/systemd/`、定时任务说明 | 每日 06:00 UTC 导出 MySQL 一致备份、校验和、加密上传独立私有 S3、保留 7 天与失败记录；独立目标库恢复并核对 | 本地独立恢复通过；P-08 真实异机恢复证据及达成的 RPO/RTO | 本地完成 / 独立恢复通过 |
| P-07 | 预计 `deploy/PRODUCTION_RUNBOOK.md`、资源/IAM 配置模板 | 整理 EC2/EBS/IP/ECR、私有 S3/staging versioning、CloudFront OAC/key group、CORS、SSM、TLS 续期、密钥注入、容量/费用及清理步骤 | 用户可照清单配置；权限边界和准确费用可 review | 本地完成；revision 3 补齐首发资源/报价，实际 IDs 待 P-08 |
| P-08 | 同上及本计划证据 | revision 3 明确现有实例、EIP/IP TLS、加密换盘、云资源及费用；获批后先限制入口验收，测容量与恢复，初始化正式教练，重新上传媒体，验证后开放入口 | 发布后验收及账单观察有实际证据 | 已批准 / 云资源及主机准备完成；首发待 GitHub 认证 |
| P-09 | 功能、计划、索引、相关说明 | 更新实际文件、RED/GREEN、限制、资源账单、发布 SHA、回退点与日期 | IMPLEMENTED/VERIFIED/RELEASED 依据分别完整 | 本地记录已同步；正式发布后补齐 |

- [x] P-01 调查部分：恢复会话、复核 EC2/最新 CI，确认自动发布与每日备份目标，形成 revision 2 本地实施范围。
- [x] P-01 review：用户批准 revision 2 的 P-02 至 P-07；revision 3 的具体 P-08 方案已形成，等待该范围批准。
- [x] P-02 至 P-07：批准后目标测试 RED → 实现 GREEN → 适用回归与交付文档。
- [ ] P-08：在具体云资源/生产操作授权覆盖后执行首次上线。
- [x] P-09 本地范围：同步实现文件、RED/GREEN、隔离验收、限制及 ticket；正式发布证据在 P-08 后补齐。

预计文件不是已经存在的实现；实际文件名可以在不改变范围时局部调整并记录。

### P-02 至 P-07 的具体交付约束

1. **CI**：新增 `deploy/test_ci_config.py`，使用仅用于配置校验的虚构数据库密码与至少 32 字节虚构验证 key；在空 env-file 下实际解析 Compose。目标用例须证明 workflow 提供完整参数，缺失真实必填 key 的正常部署仍失败。新增交付检查接入 docs-and-compose；保留既有后端/前端测试，不升级业务依赖。
2. **生产配置**：`deploy/compose.production.yaml` 仅定义 frontend/backend/db；前后端必须引用限定 ECR 仓库的 digest，MySQL 沿用 8.4。只有 NGINX 暴露 80/443，后端/DB 不发布 host 端口；无 local/Mailpit/S3Mock、无源码构建、无默认真实密钥。Host 上配置文件权限 0600；CloudFront 私钥与证书只读挂载。明确 Secure Cookie、可信代理、HTTPS public URL、真实 SMTP/TLS 和媒体参数。初始容器内存限额候选为 backend 768 MiB（Java heap 384 MiB）、MySQL 640 MiB（buffer pool 128 MiB）、NGINX 64 MiB，剩余约 368 MiB 留给 OS/SSM/Docker；这些是隔离测试起点，目标 EC2 的负载验收可要求调整，不能先认定 2 GiB 足够。
3. **TLS**：生产 NGINX 配置含 HTTP 跳转 HTTPS、受控 ACME challenge 路径及证书挂载；revision 2 以隔离临时证书验证。P-08 按 revision 3 配置正式 EIP/IP 证书与续期，不关闭 Secure Cookie。
4. **镜像与触发**：现有 CI 保持只读权限；release workflow 使用 `workflow_run` 监听 CI completed，显式要求 conclusion=success、event=push、head_branch=main、head_repository 为本仓库。检出 `workflow_run.head_sha`，构建和部署引用同一 SHA；构建前及提交部署前检查它仍是当前 main，跳过已被更新取代的 run。构建采用 `ubuntu-24.04-arm`，无需 EC2 编译或 QEMU 模拟。`PRODUCTION_DELIVERY_ENABLED` 未启用时跳过所有 AWS 权限及变更。GitHub concurrency 与主机 `flock` 串行；不以取消 workflow 推断远程 SSM 命令已停止。
5. **SSM 发布**：主机预装经 review 的发布入口；CI IAM 只允许指定实例和专用 SSM document，参数限定完整 SHA、受控 ECR digest 和发布配置版本，拒绝路径穿越/任意 shell。主机验证前置资源/配置，记录当前与上一成功版本，拉镜像、启服务、等待实际健康/HTTPS；失败时恢复兼容上一版，首次失败明确报告无回退点；重复成功版本返回已部署。保持卷、保留唯一正式 worker，禁止 `down -v`、自动覆盖 DB 或逆向 DDL。发布程序与 Compose/NGINX 配置作为同一版本交付；云端交付介质和访问权限在 runbook 列出，不能只回退镜像却遗留不兼容配置。
6. **备份/恢复**：每日 06:00 UTC 由 systemd timer 调用单实例备份入口；一致性导出使用 MySQL 8.4 `mysqldump --single-transaction`、失败即不上传成功标记；产生压缩备份、校验和与迁移版本元数据，上传私有 S3 `db-backups/`，生命周期保留 7 天。并发导出用锁，禁止备份期间另行执行不兼容 DDL；恢复只针对新建独立数据库，拒绝指定正式卷、损坏校验和及错误迁移版本。数据库备份与验证/SMTP/签名密钥的安全恢复清单分开，均不进入 Git 或日志；超期/失败须有可查状态，不把定时器存在写成备份成功。
7. **IAM/资源模板与文档**：预计增加 `deploy/aws/` 下 CI trust/permissions、EC2 permissions、专用 SSM document、S3/OAC/CORS 与生命周期模板，以及 runbook。当前实例仅有 AmazonSSMManagedInstanceCore，无 inline policy，不能宣称已可读 ECR/S3。模板采用明确可替换的仓库/桶/实例参数，S3 backup 与媒体权限分别限定；OIDC subject 格式需按实际 GitHub repo 配置核验，不能硬套旧 `repo:owner/name:ref` 格式。[GitHub OIDC 文档](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)说明新仓库可能使用含不可变 owner/repo ID 的 subject。真实 IAM 应用与 AWS 资源创建属于 P-08。

[GitHub 事件文档](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_run)明确 workflow_run 完成不等于成功，而且可能获得发布权限，因此所有来源/结果检查在权限申请前完成；[GitHub runner 文档](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)列出公开仓库的 ubuntu-24.04-arm，本仓库已通过 GitHub REST 确认 public/main。新 Action 选用实际核验的官方发行并固定 commit SHA；本计划不虚构尚未核验的 Action 版本。

## 4. 数据、API、架构与兼容影响

- 延续六边形业务模块、现有 REST、权限、CSRF、MySQL 8.4 和 V1–V10；不修改既有迁移，不把 v1 或本地数据库复制到生产。
- 第一版生产使用新库；启动 Flyway、教练初始化与验证属于需具体授权的首次操作。可通过已实现命令初始化，不增设公开教练注册入口。
- 前端与 API 同源，NGINX 直接终止 TLS，HTTP 转 HTTPS；证书申请/自动续期机制在版本/AMI 定稿后明确。后台仍仅容器网络可达。
- 正式 profile 不启用 local；现有 Cookie serializer 的默认 Secure 应实际验证。`APP_PUBLIC_URL`、可信代理及媒体 CORS 对应准确 HTTPS 域名。
- ECR、SSM 与 S3 是交付/外部适配器边界，业务内层不新增 SDK。实例角色、CI 角色分别最小授权；IMDSv2 与容器访问实例角色需实际验证，不能仅附上角色就宣称容器有权限。
- CD 不允许不可信 PR 获得 OIDC 发布权限；AWS trust policy 限制仓库和受控 ref/environment。SSM 只能操作指定目标与受控部署文档，不能把任意远程 shell 权限当成默认配置。
- SMTP/DB/验证 key 建议由实例权限受控的配置文件注入；CloudFront 私钥单独只读挂载，非 root Java 账号须有准确读权限。不向前端或镜像泄露秘密。
- 当前目标 EC2 已核实为 ARM64，拟发布 linux/arm64 镜像。六个基础镜像的 manifest 均包含 ARM64；完整构建、ffprobe、MySQL 及业务在 EC2 的实际行为尚未验证。
- 新工具版本、Action 引用、AMI、TLS 客户端和 IAM 权限须依据官方资料定稿并验证；不为上线顺便升级业务依赖。改变已确认架构时补 ADR 并重新 review。

## 5. 验收与验证计划

| 验收 ID | 具体验证 | 环境/命令 | 预期结果 |
|---|---|---|---|
| AC-01 | 干净 CI 环境、本地配置和全部既有必要检查 | 明确测试 key 的 `docker compose --env-file /dev/null -f deploy/compose.yaml config --quiet`；`python3 ai-docs/check_docs.py`；后端 `./mvnw -B test`；前端 `npm ci` 与 typecheck/test/build/lint | 所有检查实际通过，不读取本地秘密 |
| AC-02 | 生产配置、选定平台和隔离启动 | 预计 `python3 -m unittest discover -s deploy -p 'test_production_config.py'`；生产 Compose config、镜像启动/健康与网络检查 | 无源码构建/沙箱/公开 DB；TLS、Cookie、资源限制及健康状态正确 |
| AC-03 | 完整正式 HTTPS IP 业务及权限 | 受控邮箱/账号；注册、验证、登录、找回、重启 Session、申请/确认/取消、SMTP 实收、邮件链接及角色越权 | 现有行为在 HTTPS 正常；测试预约按应用流程撤销 |
| AC-04 | 真实媒体权限与完整播放 | 匿名 S3 和 CloudFront 无签名请求拒绝；允许的直传与签名 URL；草稿/发布隔离；同一约 65 秒视频连续播至 ended；Range 请求 | AWS 实际权限和播放通过；本地 TODO-0022 单独保留状态 |
| AC-05 | 自动发布编排、失败回退与持久化 | 预计 `python3 -m unittest discover -s deploy -p 'test_release.py'`；隔离 Compose 发布/故障演练；Actions OIDC/SSM 受控运行 | CI 失败、PR/其他仓库/其他分支、旧 main SHA、启用变量缺失均不发布；成功 main 自动发布；串行；保持卷和唯一 worker；兼容上一版恢复 |
| AC-06 | 每日 7 天备份可恢复 | 预计 `python3 -m unittest discover -s deploy -p 'test_backup_restore.py'`；独立 MySQL 8.4 恢复并核对表/迁移/关键数据，记录耗时；测试缺失/损坏备份拒绝，P-08 使用真实异机备份演练 | 每日频率/7 天保留正确，独立恢复通过；实际 RPO 目标 24 小时与待定 RTO 在 P-08 验证；不覆盖正式库 |
| AC-07 | 目标实例容量和成本 | 先定稿业务并发与媒体场景，再测登录散列、预约、媒体校验、备份时的内存/CPU credits/磁盘、重启恢复；核对 AWS 报价与首期实际账单 | 无 OOM，保留测得余量；超预算或容量不足先交用户取舍 |

批准后的预期 RED：干净 CI 配置缺 key；生产配置行为校验以明确 assertion 报告尚无生产文件/安全属性（不能以 FileNotFoundError 算 RED）；有效发布输入缺少健康失败恢复/串行保障；损坏备份缺少正确拒绝。工作流门禁用例包含 main/success/push、本仓库、PR/fork、failure、superseded SHA 与禁用开关。先把用例建立为能执行的行为检查，再运行确认因目标行为缺失失败；解析错误、依赖未安装和 Docker 不可用不是 RED。实现后运行相同用例 GREEN，并执行相关既有回归。

Shell 检查使用受控替身验证参数/状态，并在隔离实际 Compose 中验证启动/回退；不能仅以字符串匹配或 mocked 命令声称部署验证成功。生产流程不能运行创建 example.test 并依赖 Mailpit 的本地身份冒烟脚本。

未改变领域、并发协议或业务 SQL 时不新增镜像实现的业务测试；既有架构/MySQL/API/前端回归用于确认交付配置不影响应用。配置/交付目标测试遵守本项目 WORK-09。

## 6. 风险、成本、部署与恢复

### 已核实的费用与边界

2026-10-04 读取 [AWS 加拿大中部 Linux On-Demand 官方价格数据](https://b0.p.awsstatic.com/pricing/2.0/meteredUnitMaps/ec2/USD/current/ec2-ondemand-without-sec-sel/Canada%20(Central)/Linux/index.json)，价格数据发布日期 2026-09-25。按 730 小时/月、不计优惠或抵扣：

| 实例 | 内存/平台 | USD/小时 | 实例 USD/月 | 实例 + 1 个 IPv4，约 CAD/月 |
|---|---|---|---|---|
| t3.micro | 1 GiB / AMD64 | 0.0116 | 8.47 | 17.26 |
| t3.small | 2 GiB / AMD64 | 0.0232 | 16.94 | 29.33 |
| t4g.small | 2 GiB / ARM64 | 0.0184 | 13.43 | 24.33 |

- IPv4 按 [AWS VPC 价格](https://aws.amazon.com/vpc/pricing/)的 0.005 USD/小时，即 3.65 USD/月。闲置地址也收费。
- CAD 仅参考 [加拿大央行 2026-10-02 USD/CAD 观察值](https://www.bankofcanada.ca/valet/observations/FXUSDCAD/json?start_date=2026-10-01&end_date=2026-10-02) 1.4246；实际结算汇率和税费不同。这是运维成本估算，不是承诺。
- 上表是实例/地址对比；revision 3 的完整低用量报价另见下表。运行余量仍需目标 EC2 验收。
- revision 3 选择 [CloudFront pay-as-you-go](https://aws.amazon.com/cloudfront/pricing/pay-as-you-go/)，官方列明每月 1 TB 出站和 1000 万 HTTP/HTTPS 请求 Always Free，所有功能可用；OAC/可信 key group/签名 URL 仍需实际验收。额度是账户范围，已有分发共享，超量按区域计费；不选择未验收兼容性的 flat-rate plan。
- [T4g 官方页面](https://aws.amazon.com/ec2/instance-types/t4/)当前提供有期限的 t4g.small 试用；不计入长期基础估算，账户资格、结束日期和其他独立费用需核对。
- T 系列 Unlimited 的超额 CPU credits 可额外收费。预算方案建议评估 Standard 模式及其限速影响，不能以 swap 或关超额计费代替容量测试。
- AWS Budgets 仅提醒；实例停机后磁盘/地址/存储等仍可能计费。未选资源、完整使用量与报价前不宣称 30 CAD 已满足。
- 已有根盘的实际 `Encrypted` 为 false，见 [TODO-0024](../todo/0024-ec2-root-volume-unencrypted.md)。正式存储方案需明确是否采用指南建议的加密要求；现有卷不能直接原地加密，若采用新加密卷/快照方案，应先列出停机、保留数据、回退及临时存储费用，再按具体授权执行。[AWS EBS 加密说明](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-encryption.html)

### revision 3 完整低用量月费场景

2026-10-04 通过 AWS Price List `get-products` 核对 Canada (Central)：gp3 SKU `DQ3VBGBXYAYM4JS5` 为 0.088 USD/GB-month（20 GiB，包含基线 IOPS/throughput）；S3 Standard SKU `9MDYGCA9S4SXXTJF` 首档 0.025 USD/GB-month；PUT/COPY/POST/LIST 0.0055 USD/1000 次，GET 等 0.0044 USD/10000 次；普通 EBS snapshot SKU `5XUGBZ7CGY6QRFRD` 为 0.055 USD/GB-month。API 区域 us-east-1 是价格服务端点，产品筛选仍是 Canada (Central)。参考 [EBS 价格](https://aws.amazon.com/ebs/pricing/)、[S3 价格](https://aws.amazon.com/s3/pricing/)、[AWS Price List API](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/price-changes.html)。

| 项目与明确场景 | USD/月 | CAD/月（汇率 1.4246） |
|---|---|---|
| 已有 t4g.small，730 小时，Standard，不计试用抵扣 | 13.432 | 19.14 |
| 一个公网 IPv4，EIP 替代现有自动 IPv4 | 3.650 | 5.20 |
| 一个 20 GiB gp3 正式根盘 | 1.760 | 2.51 |
| 两个 ECR 仓库合计 2 GiB-month；[官方存储价](https://aws.amazon.com/ecr/pricing/) 0.10 USD/GB-month | 0.200 | 0.28 |
| S3 三桶合计 11 GiB-month：媒体及全部版本 10 GiB，7 日备份/发布包及全部版本 1 GiB | 0.275 | 0.39 |
| S3 写/列表 10000 次/月，读 100000 次/月 | 0.099 | 0.14 |
| CloudFront 媒体 50 GB/月、100 万 HTTPS 请求/月，账户共享免费额度仍充足 | 0.000 | 0.00 |
| **场景合计（按未四舍五入 USD 计算）** | **19.416** | **27.66** |

- 这是一组可核对的低用量估算，不是硬上限或已发生账单。合计包括现有实例支出，并非每月另增加 27.66 CAD；除现有 EC2/IPv4/根盘外，此场景持续增量约 0.82 CAD/月。
- 首发换盘临时保留旧 20 GiB gp3 和最多 20 GiB 普通 snapshot 48 小时，保守额外约 **0.27 CAD**；按实际快照数据量和持有时间计费。失败或未获清理授权长期保留时，旧盘约 2.51 CAD/月、快照最多约 1.57 CAD/月，应报告，不能隐去这项成本。
- 公网 EC2/S3/ECR 非 CloudFront 出站假设仍在 AWS 账户每月共享免费流量额度内；媒体在 CloudFront 分发。GitHub 为公开仓库、使用标准 ARM runner；IAM/OIDC/SSM 标准 Run Command 不引入独立付费托管服务。实际账户额度/已有用量在首发前核对，超出时重新给出费用，不默认有免费试用资格。
- 不包含税、域名、现有 SMTP 若有的外部账单和旧项目资源；用户已明确跳过买域名，不创建新 DNS 计费对象。沿用已可用 SMTP，不新增邮件付费服务。容器日志在受限根盘，不新增付费日志/监控系统。
- ECR tagged 版本与 S3 noncurrent 版本会累计；2 GiB/11 GiB 是本场景计费用量，不是已经设置的硬配额。实际增加后重新计算，清理前保护当前/上一版和已引用素材；不承诺约 30 CAD 永远足够，也不自行扩容。

### 提议资源与操作顺序

1. revision 3 按用户要求跳过域名，用一个 EIP/IP HTTPS；核对既有账户安全/账单设置和旧资源，预算提醒不等于费用上限。无需把密码、长期 Access Key 或私钥发到聊天。
2. review 后准备生产配置、IAM/SSM/ECR 模板、发布及备份程序，先做可逆隔离验证。
3. 汇总明确规格/AMI/磁盘、EIP、S3/CloudFront/ECR/日志等资源和总价，再取得覆盖创建及首次生产操作的授权。
4. 提议公网子网单 EC2，入口 80/443；通过 SSM 管理，不需对公网开放 22；MySQL/后端不发布 host 端口。CloudFront 此阶段用于媒体。
5. 两个媒体私有桶位于所选区域，staging 开启 versioning；S3 CORS 精确允许正式 origin，frozen 使用 OAC 和可信 key group；备份另用私有存储，不混入公开媒体分发。
6. 实例角色拉 ECR 并访问限定媒体/备份路径，GitHub OIDC 角色推镜像及调用受控 SSM；以 [GitHub OIDC 官方流程](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)及 [SSM Agent 官方说明](https://docs.aws.amazon.com/systems-manager/latest/userguide/ssm-agent.html)为依据。
7. 部署首个明确 SHA/digest，初始化新库及教练，重新上传真实内容；按 AC-03/04/06/07 完成上线验收。用户对公众访问的授权覆盖后开放入口。

### 恢复与清理

- 已确认每日一致逻辑备份 + 私有 S3 保留 7 日，RPO 目标 24 小时；先验证独立数据库恢复，RTO 仍待定稿。磁盘卷和未经验证的快照都不等于已完成备份验收。
- 保留当前/上一成功镜像、配置版本、发布记录及数据库迁移兼容检查。失败只回退兼容应用镜像；破坏性迁移、自动覆盖正式库或删除卷不在授权范围。
- 实例故障后的重建需要保存角色、网络、Compose、密钥恢复方式和异机备份；签名 key/验证 key 必须安全保管，不能仅留在故障主机上。
- 资源清理明确 ECR 版本保留、未完成上传及 S3 非当前版本、备份生命周期、日志大小；不能用一条总 bucket 过期规则误删已发布媒体。
- 取消上线时停止/终止实例、释放不再需要的 IPv4，按授权处理 EBS/镜像/存储/DNS/分发；保留数据优先，数据删除须另有明确依据。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-10-04 | P-01 / 本计划、配对功能、索引及 TODO-0023 | 调查、确认三项用户答复并编写草稿 | 尚未批准实现；没有云资源或生产操作 |
| 2026-10-04 | P-01 / `deploy/EC2_SETUP.md`、部署说明、本计划及功能 | 按用户新要求先提供空 EC2 规格、手动创建和 Session Manager 登录指导 | 未批准整体实施；未执行任何 AWS 操作，未把建议记为实测 |
| 2026-10-04 | P-01 / EC2 准备指南、功能与本计划 | 记录用户侧服务器终端检查，核对 AWS Docker 安装与官方 Compose v5.6.0 ARM64/checksum，提供手动安装步骤 | 用户已创建服务器；Codex 未远程执行安装，整体计划仍未批准 |
| 2026-10-04 | P-01 / EC2 指南、功能、本计划、TODO-0024 与待办索引 | 安装本机 AWS CLI、浏览器临时认证；SSM 检查目标实例并安装/验证 Docker 与 Compose | 按独立服务器准备授权执行；根盘未加密已记录，未更换资源；整体 revision 1 仍未批准 |
| 2026-10-04 | P-01 / 本计划 revision 2、配对功能/索引、EC2 指南与 TODO-0023 | 找回原会话并复核服务器/CI；记录用户选择自动发布与每日 7 天备份，补齐分阶段范围、具体交付约束与测试计划 | 实现范围/触发条件已具体化，revision 2 回到 AWAITING_REVIEW；只修改文档，未实施、commit/push 或新增资源 |
| 2026-10-04 | P-02 / `deploy/test_ci_config.py`、`test_production_config.py`、`test_release.py`、`test_backup_restore.py`、`test_aws_templates.py`、`test_tls_renewal.py`、`test_delivery_support.py` | 批准后先写测试，记录实际 RED；原用例 GREEN，最终 24 项交付测试通过 | 后续增加有效 RED 覆盖部分失败迁移、镜像预检、导出写入上限、ECR 重试及敏感日志；测试自身错误已修正后重新 RED，不算实现证据 |
| 2026-10-04 | P-03 / `compose.production.yaml`、`.env.production.example`、`frontend/nginx.production.conf`、证书 hook/timer | 生产三容器配置、configtree 密钥、HTTPS、安全 Cookie、只读挂载、资源/日志限制、续期校验与重载 | 实际启动发现 Actuator 默认连接 SMTP；目标测试 RED 后关闭 mail health indicator，邮件送达保留独立正式验收。访问日志只记录 URI，不记录 query/referrer/secret header |
| 2026-10-04 | P-04/05 / `release.yml`、`release_gate.py`、`ecr_existing.py`、`send_release.py`、`bundle_delivery.py`、`dispatch.py`、`release.py`/`.sh` | 可信事件门禁、原生 ARM64、固定官方 Action SHA、同受测 SHA/digest、可重试 ECR、可重复配置包、SSM 完成检查、配置回退 | 使用 Python 标准库 `fcntl.flock` 实现同一主机排他锁（与 CLI flock 同一锁语义，macOS/Linux 可测试）；脚本 `.sh` 为入口，Python 编排。配置包使用私有 operations 桶的 releases 前缀，和 backup 分权限；未增加真实资源 |
| 2026-10-04 | P-06 / `backup.py`/`.sh`、`restore_check.py`/`.sh`、`run_backup.py`、systemd backup service/timer | 一致导出与快照行数、2 GiB 写入硬限制、加密上传/最后成功标记、7 天模板、24 小时状态判定、独立容器恢复 | 不开放向已有数据库/正式卷恢复的输入；source version/行数/表集合实际核对。运维桶版本化对象过期异步、非当前版本额外 1 天计入费用；独立恢复需额外 640 MiB，正式主机容量未确认 |
| 2026-10-04 | P-07 / `render_aws_templates.py`、`deploy/aws/`、`PRODUCTION_RUNBOOK.md`、`EC2_SETUP.md`、部署 README | 提供分角色 IAM/SSM、S3/CORS/OAC/key group/distribution、生命周期和配置模板；记录 TLS/备份、资源、预算、清理及 P-08 清单 | tagged ECR/current/previous 配置不按最近 N 个自动删除，避免误删回退点；发布/备份磁盘余量门禁。完整账单/域名/存储/真实权限当时仍待 P-08 |
| 2026-10-04 | P-08 准备 / revision 3、配对功能/索引、runbook、TODO-0025 | 按用户跳过买域名要求，核对官方 IP 证书支持及区域存储单价，写明现有实例/EIP/加密换盘/具体云资源/费用/首发/回退/清理；复核 Git SSH 认证失败 | 只读调查及 review 文档，无代码/云资源/数据变更。revision 2 实现证据保留；revision 3 AWAITING_REVIEW，未把要求上线直接当作该具体方案批准 |

## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-10-04 | 本地仓库 | Git 状态、读取 CI/Compose/应用配置 | 初始工作区干净；CI 已存在但 CD/生产配置未实现 | 无 gh，当前远端 CI 结果未核实 |
| 2026-10-04 | 仓库根 | `env -u VERIFICATION_KEY DB_PASSWORD=ci-config-only MYSQL_ROOT_PASSWORD=ci-config-only-root docker-compose --env-file /dev/null -f deploy/compose.yaml config --quiet` | exit 1，缺必填 VERIFICATION_KEY | 与 workflow env 匹配；记录 TODO-0023，未修复 |
| 2026-10-04 | 本地 Docker CLI | 对 `eclipse-temurin:25-jre-alpine`、`maven:3.9.16-eclipse-temurin-25`、`mysql:8.4`、`nginx:1.29-alpine`、`node:24-alpine`、`alpine:3.23` 执行 `docker manifest inspect` | 六个镜像 manifest 均声明 linux/arm64 | buildx 插件不可用，manifest 查询改用内置命令；未构建或拉取运行镜像 |
| 2026-10-04 | 官方资料只读查询 | AWS EC2/VPC 价格、T4g、CloudFront、GitHub OIDC、S3/媒体配置源码、加拿大央行观察值 | 得到第 6 节的已验证基础单价与来源 | 尚无完整报价、AWS 权限/容量/恢复结果 |
| 2026-10-04 | 仓库根 | `python3 ai-docs/check_docs.py`；`git diff --check` | 均 exit 0；文档检查报告 11 paired records、23 tickets，链接/索引/状态/review gates 通过 | 仅文档验证，不证明实现或生产验收 |
| 2026-10-04 | 用户 EC2 终端 | 用户执行 `uname -m`、`cat /etc/os-release`、`free -h`、`df -h /` 并回传输出 | aarch64；Amazon Linux 2023.12.20260930；内存 total 1.8 GiB / available 1.5 GiB；根文件系统 20G / available 19G | 服务器基础检查通过；未证明业务容量、Docker/Compose 安装或控制台/账单配置 |
| 2026-10-04 | 官方资料只读查询 | Docker Compose 官方 release API 及 aarch64.sha256 | v5.6.0 为正式发布，ARM64 资产 SHA-256 与指南相符 | 未在 EC2 下载、安装或运行，不能提前写验收通过 |
| 2026-10-04 | 本机 AWS CLI / `ca-central-1` | 官方安装脚本、`aws login --profile snowboard-v2 --region ca-central-1`、STS 身份核对及目标 EC2/SSM 查询 | AWS CLI 2.37.9；浏览器临时登录成功；目标实例 running / t4g.small / ARM64，SSM Online | 未读取或记录凭证内容；仅操作用户提供的目标实例 |
| 2026-10-04 | AWS CLI / 目标实例 | 安全组、CPU credits、元数据、卷配置只读核对 | 入站为空、Standard、IMDSv2 Required / hop limit 2；20 GiB gp3，Encrypted false | TODO-0024 记录与指南建议的偏差；未停止实例、创建快照或替换卷 |
| 2026-10-04 | EC2 / SSM Run Command | 检查命令 `33125046-dc19-4f7c-a633-7b49a7bb9876`；安装命令 `ac22b0f0-3432-4f1e-90d0-8e91305ce051` | 均 Success / ResponseCode 0；Engine 25.0.16 / Client 25.0.14，Compose v5.6.0，checksum OK；Docker enabled/active，hello-world 成功 | 安装后 available 内存 1.3 GiB、根盘 18G；未测业务容量 |
| 2026-10-04 | EC2 / 隔离 Compose 项目 | 命令 `ed0bb276-c6c4-4d19-9d33-d59eaf9692d7`：临时配置校验、运行 probe、清理项目 | Success / ResponseCode 0；Compose 实际运行 hello-world 成功，临时容器和网络清理 | 只证明运行环境可用；不证明项目生产配置或交付验收通过 |
| 2026-10-04 | 仓库根 / 远程准备记录更新后 | `python3 ai-docs/check_docs.py`；`git diff --check` | 均 exit 0；11 paired records、24 tickets，链接/索引/状态/review gates 通过 | 文档验证；未修改业务实现、生产配置或 workflow |
| 2026-10-04 | 恢复会话 / AWS 只读 API | STS、EC2/SSM、卷/安全组及 IAM role policy 查询 | 既有实例 running/ARM64，SSM Online，IMDSv2 Required/hop 2，20 GiB gp3 未加密，入站为空；实例角色仅附 AmazonSSMManagedInstanceCore，无 inline policy | 未修改角色、磁盘或安全组；ECR/S3 权限尚未配置 |
| 2026-10-04 | EC2 / SSM 只读检查 | CommandId `887d94a7-0e17-4dab-8fac-2c34fd2e1a39`：Docker/Compose/service、docker ps、free/df | Success/ResponseCode 0；Engine 25.0.16、Compose v5.6.0、Docker active/enabled，运行容器为空；可用内存 1403 MiB，磁盘 18G 可用 | 无业务启动或负载验证，未安装/重启服务 |
| 2026-10-04 | GitHub REST / latest main | `/repos/Honggeer/snowboardLessonBookingV2`、`/actions/runs?per_page=5`、`/actions/runs/37176068847/jobs` 及失败 check annotations | 仓库 public、默认 main；当前提交 backend/frontend success；Compose configuration failure，exit 1 | 详细日志 connector 要求重新认证；API 元数据不含缺 key 的具体错误文本 |
| 2026-10-04 | 仓库根 / 干净配置环境 | `env -u VERIFICATION_KEY DB_PASSWORD=ci-config-only MYSQL_ROOT_PASSWORD=ci-config-only-root docker-compose --env-file /dev/null -f deploy/compose.yaml config --quiet` | exit 1：必填 VERIFICATION_KEY 缺失，复核 TODO-0023 | 仅诊断；不是批准后实现的测试 RED，未改 workflow |
| 2026-10-04 | 仓库根 / revision 2 文档同步 | `python3 ai-docs/check_docs.py`；`git diff --check` | 均 exit 0；11 paired records、24 tickets，链接/状态/索引/review gates 通过 | 仅文档通过；实现、云端权限、业务部署和生产验收未执行 |

### revision 3 准备的实际证据

- `describe-instances`：`i-0c7978984740cbd58` running / t4g.small / ARM64，当前自动 IP `15.223.235.98`；`describe-instance-information`：SSM Online、agent 3.3.5226.0。
- `describe-volumes`：根盘 20 GiB gp3、Encrypted false、AZ ca-central-1d；`describe-security-groups` 入站为空；`describe-addresses` 该实例无 EIP。两次分别查询 backend/frontend ECR 均 RepositoryNotFoundException；`list-open-id-connect-providers` 空。未创建、停止、换盘或修改权限。
- GitHub 公开 API：main `448b5c51e4c708edc4bd2fe28191aa7b651abd5b`，最新 CI run 37176068847 completed/failure；`git ls-remote` SSH BatchMode/StrictHostKeyChecking=yes 失败，exit 128 / Permission denied (publickey)，记录 TODO-0025，未改 remote/密钥或写远端。
- Price List API 的区域 SKU/单价及官方 CloudFront/ECR/IP TLS 资料见第 1/6 节。仅核对本地 SMTP 配置存在且必填字段非空，没有打印内容，不宣称正式发信验收通过。
- `python3 ai-docs/check_docs.py`：exit 0，11 paired records / 25 tickets，配对、状态、索引、链接与 review gates 通过；`git diff --check` exit 0。本次仅文档准备，不写实现/目标测试，不重复无变更的业务回归，也不把文档检查当生产验收。

### revision 3 批准后的云端执行证据

- 用户原话及日期见第 2 节，已批准真实资源、生产首发和 commit/push。GitHub CLI 2.102.0 已安装；第一次 device login 未完成并过期，用户要求重新验证；第二次浏览器登录成功为 Honggeer，仓库 permissions.admin/push 均 true，HTTPS git ls-remote 成功。origin 改为同一仓库的 HTTPS 通道，不保存或输出 token。
- 固定 IPv4 `52.60.174.156` / allocation `eipalloc-083305819fd58e611` 已关联既有实例；安全组只开放公网 80、443 暂限本机验收地址 `/32`。没有开放 22/8080/3306；网站尚未启动。
- 停机一致快照 `snap-09c0367d328529bec`，新根盘 `vol-00a6aa379a31df78d` 为同 AZ、20 GiB gp3、Encrypted true / AWS 托管 EBS key；实例恢复 running，根盘 DeleteOnTermination true。旧根盘 `vol-01afc752b9b52dd07` 与快照保留用于回退，按批准范围在复核成功 48 小时后清理，暂时额外计费；尚未删除。
- 换盘后实际 SSM Command `8ce008a8-4055-4542-8244-62f29a77cb37`：Success / ResponseCode 0，Docker active / Compose v5.6.0 / ARM64，业务容器为空，available 内存 1500 MiB / 根盘约 18 GiB 可用。AWS CLI 2.36.47、Python 3.9.25、OpenSSL 3.5.8 原有工具可用；不代表应用容量验收。
- 两个 private immutable ECR 已创建；三个 `snowboard-v2-481604401994-ca-central-1-{staging,frozen,operations}` 私有桶已配置 SSE-S3、公网阻断、HTTPS-only，staging/operations versioning、生命周期及精确 IP CORS。CloudFront distribution `ERC4HF3K0S0JC` / `d2f6jctwfa8hpe.cloudfront.net`，OAC `EY6E4PGYYPFH7`、viewer public key `KZSNRI2U2IYH6` / key group `6fad640e-f4c2-451b-b7da-e30783e1306d` 已创建；真实媒体上传/播放验收未执行。
- 现有 EC2 role 已加限定权限；GitHub OIDC provider、`snowboard-v2-github-release` role 和 `snowboard-v2-release` 专用 SSM document 已创建。GitHub OIDC 设置 API 实际返回 use_default/use_immutable_subject 均 true 和精确 sub_claim_prefix，已与 trust 核对；实际 GitHub OIDC/STS 成功证据仍待工作流，不放宽 trust。
- SSM bootstrap Command `27326bb7-4e7c-4067-9740-a959decbf1f4` Success / 0：受保护配置/新数据库密码/独立 verification key/CloudFront 私钥和现有 SMTP 密码已注入；Python 3.11 venv / Certbot 5.8.0、发布入口、systemd 单元安装成功，Python 编译检查通过。传输使用私有 S3 的临时加密对象与校验和；下载后主机临时目录清理，对象实际 VersionId 已删除，秘密不在 SSM 参数或 Git。恢复副本保存在仓库外受控 `.local` 目录，权限 0700/0600。
- 用户确认生产教练沿用本地测试邮箱，已只读取邮箱/姓名；不复制本地账号记录、密码、用户/预约数据。生产新账号尚未初始化。
- IP 证书 Command `742af121-5aea-4ea0-b76b-904808b0792e` Success / 0：staging HTTP-01 和正式申请均成功，可信证书已装入保护 TLS 目录，IP SAN `52.60.174.156`，notAfter `2026-10-11 13:01:16 UTC`。证书实际剩余有效期和续期转换在首发前再次核对；NGINX/webroot/dry-run/hook/timer 验收尚未执行。
- 提交前交付回归 `python3 -m unittest discover -s deploy -p 'test_*.py'`：24 tests / OK；未改业务实现，不重复既有 130 后端/51 前端回归，远端 CI 将再次跑适用门禁。GitHub 认证、commit/push、真实 CI、ECR 构建/拉取、应用/空库迁移、教练验证、SMTP 送达、媒体、真实 S3 恢复及容量/公网验收均待继续，不能记为 RELEASED。

### revision 2 批准后的 RED/GREEN 与回归

| 日期 | 实际命令 / 用例 | RED / GREEN 与限制 |
|---|---|---|
| 2026-10-04 | `python3 -m unittest discover -s deploy -p 'test_*config.py'` | 初次 5 项 / 4 failures：workflow 干净环境缺 VERIFICATION_KEY，生产隔离/TLS 配置尚未实现（明确 assertion，不是 FileNotFoundError）；实现后同 5 项通过 |
| 2026-10-04 | `python3 -m unittest discover -s deploy -p 'test_release.py'` | 初次 5 failures：发布/门禁行为缺失；同用例 GREEN。补充 RED 实际证明部分迁移未改变版本号仍错误回退、未执行预检、SSM archive 缺白名单、ECR digest 重试缺失；修复后全部通过 |
| 2026-10-04 | `python3 -m unittest discover -s deploy -p 'test_backup_restore.py'` | 初次 4 failures：备份/恢复行为缺失；同用例 GREEN。追加写入上限测试初次失败（dump 超限仍成功），设置子进程 RLIMIT_FSIZE 后 GREEN |
| 2026-10-04 | AWS/TLS 目标测试与生产 SMTP health 用例 | 模板/续期行为尚未实现、distribution 可信 key group 未输出、生产 health 依赖 SMTP，均实际 RED；实现后 GREEN，证书公私钥匹配/不匹配通过真实 OpenSSL 验证 |
| 2026-10-04 | `python3 -m unittest discover -s deploy -p 'test_*.py'` | 最终 24 tests，0 failures/errors/skips；包含现有 SMTP Compose 2 项回归。CI 按实际已安装 Docker CLI 自动选择 compose plugin，避免依赖 docker-compose 独立入口 |
| 2026-10-04 | `docker build --platform linux/arm64 -t snowboard-v2-delivery-backend:local backend`；对应 frontend build；镜像内 ffprobe/id | 两镜像实际构建 exit 0 / ARM64；ffprobe 9.0.2 可运行；backend UID 100/GID 101；前端镜像执行 npm build 成功 |
| 2026-10-04 | `python3 deploy/verify_delivery.py` | 真实隔离 Compose/HTTPS/MySQL 8.4 启动、Cookie Secure/HttpOnly/SameSite、CSRF、匿名 401；实际失败容器恢复上一版配置并保留记录，重复调用正确；真实独立恢复 24 张表、15 行、schema 10，约 8 秒；证书 hook 真实 nginx -t/reload。query token 日志用例首次明确 RED 泄露，安全日志格式后 GREEN |
| 2026-10-04 | Maven 后端完整回归（命令见下） | 130 tests，0 failures/errors/skips，BUILD SUCCESS；包含架构、真实 MySQL、API、并发与媒体检查。旧 Maven 临时目录 boot JAR 缺失是环境故障，不算 RED；从 Maven Central 下载原固定版本并核验 SHA-512 后完成 |
| 2026-10-04 | 前端 `npm run typecheck`、`npm test`、`npm run lint` 及镜像内 `npm run build` | 均 exit 0；5 test files / 51 tests passed；构建成功，未改变前端业务行为 |
| 2026-10-04 | actionlint 1.7.12 校验 CI/release；Python AST；模板 render | 均 exit 0；Action tags 经官方 GitHub refs 核对并固定 SHA。CloudFront OAC/key group/S3 lifecycle 的 AWS CLI 离线参数检查通过；distribution 离线 output skeleton 因工具生成的 OriginGroups 示例违反 min=2 而失败，改用官方 Botocore input model，distribution/OAC/key group 均通过；无实际 API 变更 |
| 2026-10-04 | 最终 `python3 ai-docs/check_docs.py`、`git diff --check`；shell 语法；同 SHA 配置包重复生成 | 均 exit 0；11 paired records / 24 tickets，索引/批准/状态一致；同 SHA 配置包 SHA-256 相同。隔离演练容器/卷已清理，已有本地业务容器与 AWS 保持原状 |

后端实际命令（仓库 `backend/`）：

```sh
env JAVA_HOME=/private/tmp/snowboard-v2-toolchain/jdk-25.0.4.1+1/Contents/Home DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock /private/tmp/geer-delivery-toolchain/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/private/tmp/snowboard-v2-toolchain/m2 test
```

隔离演练的 Colima 不共享 `/private/tmp`，移至仓库 `.local/`；内部网络加单独 frontend 入口网络才能进行回环 HTTPS。一天测试证书不满足续期 hook 的至少 24 小时剩余有效期，改为两天。以上均是测试环境/夹具修正，不当作业务 RED。真实 AWS/ECR/OIDC/SSM 自动运行、正式 SMTP/CloudFront、实际 RPO/RTO 和目标 EC2 容量未验证；本地上传替身不宣称异机备份已成立。

## 9. 完成状态与后续

- IMPLEMENTED revision 2：P-02 至 P-07 已按明确批准完成本地实现与隔离验证，交付 [生产运维手册](../../deploy/PRODUCTION_RUNBOOK.md)；整体 VERIFIED/RELEASED 等待 P-08 的实际验收。
- revision 2 本地实现证据保留；当前 revision 3 已获批准，P-08 正在执行，状态 IMPLEMENTING。用户已跳过买域名，不再用买域名作为前置要求；费用场景已形成，真实容量和恢复仍待执行。
- [TODO-0023](../todo/0023-ci-compose-verification-key.md) 已按干净配置检查验证修复，DONE；最新 GitHub 运行仍是旧提交失败，用户 commit/push 后再核对远端 CI，不宣称已更新。 [TODO-0022](../todo/0022-local-highlight-video-stalls.md) 仍是独立本地缺陷。
- [TODO-0024](../todo/0024-ec2-root-volume-unencrypted.md) 记录现有根盘未加密，未执行存储处理。
- 未 commit/push；本次只读核对/报价/文档工作，未创建付费资源、修改已有 EC2、迁移生产数据或发布。已有 EC2 running / SSM Online，入站为空、根盘未加密，无 EIP，预定 backend ECR 仓库不存在；远端 main/CI 仍为旧失败版本。当前 Git SSH 身份失败，见 [TODO-0025](../todo/0025-github-ssh-auth-unavailable.md)。本次提交具体 P-08 review；批准后执行，Git commit/push 仍需明确例外授权。
