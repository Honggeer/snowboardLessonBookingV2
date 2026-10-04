---
id: "0010"
title: "首次生产上线与 CI/CD"
status: RELEASED
plan: "../implement-plan/0010-production-delivery.md"
created: 2026-10-04
updated: 2026-10-04
contract_version: "1.6"
modules: [bootstrap, identity, media, notifications, frontend, deploy]
---

# 0010 — 首次生产上线与 CI/CD

## 1. 目标、触发与范围

- 用户请求：“我想上线，然后做CICD，需要我做什么呢？我准备开AWS ec2了”。首次需求调查后，用户已明确批准 revision 2 本地实现和 revision 3 生产上线及 commit/push；批准原话见配对计划。
- 配对[实施计划](../implement-plan/0010-production-delivery.md) revision 3，当前 RELEASED；revision 2 的 P-02 至 P-07 已按批准完成本地交付实现和隔离验证，证据保留；revision 3 的 P-08 实际资源/生产发布已获用户批准，并明确允许 commit/push；首个远端受测版本及备份修复已部署，公网开放并验证，最终交付证据见下文。
- 用户已确认 AWS 总预算目标约 30 CAD/月、区域 `ca-central-1`、无现有域名、已有对外发信配置；v2 是全新网站，不导入 v1 用户或预约数据。区域为 Canada (Central)，不能把业务时区 `America/Toronto` 当成服务器区域名称。
- 用户随后要求先准备 EC2、域名后置，并说明单教练自用接学员、访问量较低；先交付[创建与登录指南](../../deploy/EC2_SETUP.md)。建议 t4g.small/ARM64、Amazon Linux 2023 与 20 GiB gp3；不构成整体计划批准。
- 用户回传服务器基础检查后，要求 Codex 远程操作、提供目标实例并完成 AWS 浏览器认证。已通过 SSM 独立核对 t4g.small/ARM64、AL2023.12、Standard、入站为空、IMDSv2，安装并验证 Docker Engine 25.0.16 和 Compose v5.6.0。服务器准备有实际证据，整体生产配置/CI/CD 实现仍未获批准；应用容量尚未验证。
- 恢复会话后用户要求“接着做”，并明确选择 main 测试通过后自动部署、数据库每日备份保留 7 天（RPO 目标 24 小时）。已复核 EC2、运行环境和最新远端 CI。域名仍后置；本地交付文件可用隔离测试参数实现，真实资源/存储/域名/首次发布在 P-08 定稿。
- 2026-10-04 用户再次要求上线，并明确“还没买域名，先跳过”。沿用已有 EC2 与约 30 CAD/月目标；revision 3 提议一个 EIP + 可信 IP HTTPS、正式数据前加密根盘、具体三桶/ECR/CloudFront/IAM/SSM 和首发验收；本次不买域名。完整低用量估算约 27.66 CAD/月未计税，见计划费用场景。
- 用户最后明确“初始教练账号用honggeer1208@gmail.com,你上线了就可以了，账号还有什么的我可以自己搞，我自己测试”：账号、完整业务/邮件/媒体与并发验收交给用户上线后自测。本次技术交付保留 CI/CD、HTTPS/续期、运行状态及异机备份恢复门禁；未测试场景不记为通过。
- 目标：用户以 HTTPS 根地址（本次固定 IP，后续可改域名）使用现有注册、约课、邮件及媒体功能；通过 GitHub Actions 构建版本化镜像并部署到单台 EC2，可检查发布结果并恢复兼容的上一版本。
- 包含生产配置、HTTPS、正式 S3/CloudFront 接入、CI 修复、镜像发布、部署流程、数据库备份与恢复演练、首次上线验收。
- 不包含 v1 数据迁移、在线支付、多机高可用、视频转码或业务功能重做。域名费用及资源创建均需用户决定。

## 2. 验收条件

| ID | 场景/输入 | 预期行为 | 验证方式 | 证据/结果 |
|---|---|---|---|---|
| AC-01 | 干净检出执行 CI | 后端、前端、文档和 Compose 校验通过，不依赖未提交的本地密钥 | GitHub Actions 与同等本地命令 | 本地交付回归当前 25 项、后端 130、前端 51 通过；SHA `62fddc9` 的真实 GitHub CI 全部 success。备份修复 SHA `0abc87b` 的 CI 三个 job 与自动发布也全部 success |
| AC-02 | 生产配置与启动 | 拉取明确 SHA/digest 镜像；不启用 local、Mailpit、S3Mock；只有入口发布 HTTP/HTTPS 端口 | 配置检查、启动、端口检查 | 真实 ARM64 ECR 镜像拉取/启动 success；目标 EC2 三容器健康，V1–V10 / schema 10；仅 NGINX 发布 80/443，无 local/Mailpit/S3Mock |
| AC-03 | HTTPS 身份与约课 | Cookie 为 Secure/HttpOnly/SameSite；注册、验证、登录、找回、申请、确认和通知链接正常；越权仍被拒绝 | 使用受控账号的实际浏览器验收及相关回归 | 正式可信 IP HTTPS、Secure/HttpOnly/SameSite=Lax Cookie、CSRF 200 / 匿名 me 401 已验证；新教练验证邮件 SMTP 接受。完整账号、约课、通知实收及重启会话按用户明确要求自行测试，未宣称通过 |
| AC-04 | 正式媒体 | 私有 S3 禁止匿名读；CloudFront OAC 与 viewer 签名生效；教练直传、预览、发布、封面和完整视频播放正常 | 真实 AWS 正反例与 Range/播放验证 | 正式私有桶、OAC、可信 key group/签名配置及 CloudFront Deployed 已核对；用户自行上传/发布及验证完整播放/Range，未导入本地媒体，未宣称业务媒体验收通过 |
| AC-05 | 自动发布成功/失败/并发 | 启用后可信 main 的 CI 成功 push 自动发布同一受测 SHA/digest；PR/fork/失败/过时版本或未启用不发布；串行；故障可恢复上一兼容版本与配置；保留数据库卷 | 工作流门禁用例、隔离部署演练及实际发布后检查 | 真实可信 main CI → OIDC/ECR/SSM 自动发布 success；禁用时实际 skip，启用变量现为 true。门禁/锁/部分迁移拒绝回退和保留数据的失败恢复隔离验证通过 |
| AC-06 | 每日备份与恢复 | 每日产生一致性异机备份，保留 7 天，RPO 目标 24 小时；独立数据库实际恢复及核对；失败/超期不算成功 | 实际恢复演练、频率/保留和损坏备份拒绝检查 | 真实 EC2 → 私有 S3 备份上传成功；首次异机恢复发现含生成列的账号行数漏计，修复 RED/GREEN 已完成，新实际备份异机独立 MySQL 恢复 24 表/22 行/schema 10 一致，7.97 秒；每日 timer enabled/active；不把初次失败记为通过 |
| AC-07 | 成本与容量 | 汇总实例、IPv4、磁盘、媒体、备份、镜像和其他费用；在目标容量下验证内存/CPU/磁盘余量；预算不可行时先由用户决定 | 官方报价、容量测量、账单检查 | 官方低用量场景约 27.66 CAD/月未计税；EC2 公网后运行 available 607 MiB、根盘 17 GiB 空闲，三容器 healthy / OOM false；未执行原 10 并发/15 分钟压测，实际用量和账单待观察 |

## 3. 业务规则与未决事项

- 现有业务规则、角色权限、CAD 金额和多伦多排课时区保持原定义。
- 网站使用独立新数据库；本地测试账号、邮件任务和媒体记录不自动复制到生产。教练初始邮箱使用用户最后指定的地址；用户自行验证邮箱、找回密码并按既有编辑流程发布真实资料和媒体。
- 生产数据写入、Flyway 迁移及首次发布以明确操作授权为前提。

| 未决问题 | 建议/选项 | 影响范围 | 是否阻塞 | 用户决定/已授权依据 |
|---|---|---|---|---|
| 实例、存储及完整成本 | 既有 t4g.small/ARM64，20 GiB gp3 新根盘已加密；低用量场景约 27.66 CAD/月未计税 | 数据保护、容量、月账单 | 实际账单/长期容量待观察，不阻塞本次用户指定的技术发布 | revision 3 已批准并执行；旧卷/快照 48 小时后清理见 [TODO-0024](../todo/0024-ec2-root-volume-unencrypted.md) |
| 首发 HTTPS 地址 | 固定 EIP `52.60.174.156` + 可信 IP 证书及自动续期 | TLS、邮件、S3 CORS 同一 origin | 无域名前置要求 | 用户明确跳过域名；正式证书、webroot/dry-run/hook 已通过 |
| CD 触发方式 | main 的 CI 成功后自动更新既有 EC2 | 可信受测 SHA、发布门禁 | 已启用并实际首发成功 | 用户选择自动发布；revision 2/3 已批准，无逐次人工审批 |
| 数据恢复目标 | 每日备份、保留 7 日，RPO 目标 24 小时；RTO 4 小时为评估目标 | 数据损失容忍、恢复与费用 | 异机恢复/每日 timer 以最终发布证据为准；完整主机灾难恢复未演练 | 用户选择每日/7 日/最多 24 小时数据；数据库恢复耗时不等于整机 RTO 承诺 |
| 预算口径及使用量 | 按明确低用量场景计算；实际税、媒体播放/版本量及 SMTP 账单持续观察 | 月费用及清理 | 不声称预算绝不超出 | 用户批准 revision 3 具体费用/资源；本次不购买域名 |

## 4. 模块、端口与依赖

- 沿用 [ADR 0001](../decisions/0001-v2-baseline.md)：单台 EC2 上运行 NGINX/前端、Java、MySQL，媒体独立使用 S3/CloudFront；CI 构建，EC2 拉镜像。
- 沿用现有 SMTP、S3/CloudFront 出站适配器与 IAM 默认凭证链，不让 domain/application 引入 AWS 运维机制。
- 新增的脚本和 workflow 属交付边界，不新增业务模块或跨模块数据访问。
- 涉及 WORK-02/04/07/09/11、SEC-02、API-01、DATA-01、MEDIA-01 至 04、OPS-01 至 06。
- 架构例外：无。若正式方案改变单机架构或引入例外，修订计划并按契约处理。

## 5. API 与前端契约

- N/A：本项不新增业务 API。复用 `/api/actuator/health`、`/api/auth/csrf` 及既有业务路径；入口保持同源 `/api`，静态页面刷新可用。
- HTTPS 配置决定 Cookie、可信代理客户端 IP 与重定向；不能沿用 local 的非 Secure Cookie。
- `APP_PUBLIC_URL` 使用最终 HTTPS 根地址；媒体浏览器直传依赖准确的生产 origin/CORS。
- 健康端点仅返回状态；发布日志不得记录凭据、验证码、会话 Cookie 或私钥。

## 6. 数据、事务与并发

- 沿用 MySQL 8.4 和既有 V1–V10 append-only 迁移；不新增业务 schema，不改旧迁移。
- 首次在空生产库启动会执行迁移并创建表，该操作包含在具体首次上线授权中。
- 生产 MySQL 只走容器网络，应用使用专用账号；命名卷用于持久化，异机备份另行保存。
- 部署保持单发布者，防止两个版本同时迁移或发送任务；不额外启动第二个正式邮件/媒体 worker。
- 镜像回退必须核对 schema 兼容；不自动逆向 DDL，不自动把旧备份覆盖正式库。恢复演练使用独立目标库。

## 7. 异步任务与外部依赖

- 沿用现有 MySQL 持久邮件/预约/媒体任务，失败不能当成成功；不导入本地测试任务。
- 正式媒体需要独立 staging/frozen 私有桶，staging versioning、CloudFront OAC、可信 key group、签名私钥，以及精确 IAM/CORS。
- IAM Role 提供实例 S3/ECR/SSM 权限；CI 用 OIDC 临时权限。签名私钥与 SMTP/数据库密钥另行安全注入，不放 Git 或镜像。
- 生产验证邮件已由 SMTP 接受，账号操作及实收由用户自行核验；health UP 不作为邮件送达证据。
- 有界 worker、日志轮转、存储保留与镜像清理均需纳入容量/费用评估；媒体实际版本存储量不等于应用逻辑配额。
- CI/CD 通过 GitHub OIDC 与受控 SSM 发布入口运行；云端 job 的 `PRODUCTION_DELIVERY_ENABLED` 默认不启用。首次云端配置与发布授权覆盖后启用，之后按已确认规则自动部署，无逐次手动发布门槛。具体可信事件/SHA 校验、ARM64 构建、主机锁和配置回退约束见计划 revision 2。
- 现有 EC2 role 已增加限定 ECR/S3 权限；CI 独立 OIDC role 与专用 SSM 入口已配置，实际受测发布成功。

## 8. 实施计划关联与实际变更

- [x] 完成只读调查并创建配对草稿；阻塞决策见第 3 节。
- [x] 确认自动发布与每日 7 天备份目标，形成 revision 2 本地实施范围。
- [x] 用户批准 revision 2 的 P-02 至 P-07；本地交付已完成。
- [x] 按用户要求跳过域名，形成并获得 revision 3 的具体 P-08 资源/费用/存储/首发批准。
- [x] 批准后先执行目标测试 RED，再实现至 GREEN 及适用回归。
- [x] 完成生产配置、CI/CD、备份恢复的本地交付与隔离验证。
- [x] 真实权限/资源、运行余量与异机恢复技术验收；完整业务/媒体/并发按用户最后要求自行测试，不记为已通过。
- [x] 按 revision 3 授权及最新自测条件完成首次公网技术发布验收，同步 RELEASED。

| 日期 | 实际变更/文件 | 理由及与计划的差异 |
|---|---|---|
| 2026-10-04 | 本功能、配对计划、两个索引、TODO-0023 | 上线调查及文档草稿；未修改实现或 workflow |
| 2026-10-04 | `deploy/EC2_SETUP.md`、部署说明及配对文档 | 用户要求先准备服务器；只增加具体手动操作指导，域名/发布后置 |
| 2026-10-04 | EC2 准备指南及配对计划 | 记录用户回传的服务器基础检查，增加 Docker/Compose 手动准备说明；未执行远程安装 |
| 2026-10-04 | EC2 准备指南、配对计划及 TODO-0024 | 用户单独要求远程准备已有服务器；通过临时登录和 SSM 安装/验证 Docker、Compose，记录现有根盘未加密；未开始生产配置或 CI/CD 实现 |
| 2026-10-04 | 本功能、配对计划 revision 2、两个索引、EC2 指南及 TODO-0023 | 恢复会话并确认自动部署/每日备份需求；复核现有 EC2 与 CI；将本地交付实施范围提交 review，真实上线后置；只更新文档 |
| 2026-10-04 | 生产配置、release workflow、交付/备份/TLS 程序与测试、AWS 模板、运维手册、配对计划和索引 | 用户明确要求“开始实现”；P-02 至 P-07 已实施并通过隔离验证，具体文件/RED-GREEN/偏差见配对计划。TODO-0023 DONE；未改业务规则、AWS 资源或生产数据 |

## 9. 验证证据

| 日期 | 环境/工作目录 | 实际命令/人工步骤 | 实际结果 | 限制/待处理 |
|---|---|---|---|---|
| 2026-10-04 | 本地仓库 | 查看 Git 状态、现有 CI、Compose、应用及媒体配置 | 初始工作区干净；存在 CI，本地 Compose 不能直接用于生产 | 未核实当前远端 CI 结果 |
| 2026-10-04 | 本地仓库 | `env -u VERIFICATION_KEY DB_PASSWORD=ci-config-only MYSQL_ROOT_PASSWORD=ci-config-only-root docker-compose --env-file /dev/null -f deploy/compose.yaml config --quiet` | exit 1：缺少必填 VERIFICATION_KEY；见 [TODO-0023](../todo/0023-ci-compose-verification-key.md) | 现有配置诊断；不是本功能实现阶段的 RED |
| 2026-10-04 | 本地 Docker manifest | `docker manifest inspect` 检查六个现有构建/运行基础镜像 | 均声明 linux/arm64：Maven、Temurin、MySQL、NGINX、Node、Alpine | 不证明完整镜像构建、ffprobe 或 EC2 容量通过 |
| 2026-10-04 | 仓库根 | `python3 ai-docs/check_docs.py`；`git diff --check` | 均 exit 0；11 paired records、23 tickets，文档链接/索引/状态/review gates 通过 | 仅文档验证 |
| 2026-10-04 | 用户 EC2 终端 | 用户执行 `uname -m`、`cat /etc/os-release`、`free -h`、`df -h /` 并回传输出 | ARM64、AL2023.12、1.8 GiB 内存、20G 根文件系统；终端可访问 | 未检查应用负载、Docker/Compose、账单或独立验证控制台配置 |
| 2026-10-04 | AWS CLI / `ca-central-1` | `ec2 describe-instances`、`describe-security-groups`、`describe-instance-credit-specifications`、`describe-volumes`；SSM 节点和远程系统检查 | t4g.small/ARM64、Standard、IMDSv2 Required/hop 2、入站为空、SSM Online；20 GiB gp3 未加密 | 正式存储要求待 review；记录 TODO-0024；未核实完整账单 |
| 2026-10-04 | 目标 EC2 / SSM Run Command | `dnf install -y docker`、启动/启用服务、下载并校验 ARM64 Compose、`docker run --rm hello-world` | Success / ResponseCode 0；Docker Engine 25.0.16、Compose v5.6.0；服务 enabled/active，容器正常 | 只准备运行环境，未部署业务；具体 CommandId 见 EC2 指南第 10 节 |
| 2026-10-04 | 目标 EC2 / 隔离 Compose 项目 | `config --quiet`、`run --rm probe`、`down --remove-orphans` | Success / ResponseCode 0；实际容器输出 Hello from Docker，临时网络已清理 | 不证明生产 Compose、应用或备份可运行 |
| 2026-10-04 | 仓库根 / 远程准备记录更新后 | `python3 ai-docs/check_docs.py`；`git diff --check` | 均 exit 0；11 paired records、24 tickets，链接/索引/状态/review gates 通过 | 文档检查，未改业务代码或 workflow |
| 2026-10-04 | 恢复会话 / AWS API 与 SSM | EC2/SSM/卷/安全组/role 只读查询；CommandId `887d94a7-0e17-4dab-8fac-2c34fd2e1a39` | 实例 running、SSM Online；Docker active/enabled、版本与此前一致；无运行容器，available 1403 MiB、根盘 available 18G；根盘仍未加密，role 尚无业务权限 | Success/ResponseCode 0；未改资源/服务，未做业务容量验证 |
| 2026-10-04 | GitHub REST / latest main | 查询 [run 37176068847](https://github.com/Honggeer/snowboardLessonBookingV2/actions/runs/37176068847) 与 jobs/check annotations | backend/frontend success；docs-and-compose 的 Compose configuration failure / exit 1 | 详细日志需 GitHub connector 重新认证；本地独立复核缺 VERIFICATION_KEY，未修复 |
| 2026-10-04 | 仓库根 / revision 2 文档同步 | `python3 ai-docs/check_docs.py`；`git diff --check` | 均 exit 0；11 paired records、24 tickets，链接/状态/索引/review gates 通过 | 仅文档检查；未实现、发布或新增资源 |

- 本地实现 RED/GREEN、24 项交付测试、130 项后端（含架构/MySQL/API）、51 项前端、ARM64 镜像/ffprobe、真实隔离 HTTPS/证书重载/失败回退/备份恢复已通过；详细实际命令与 RED/GREEN 见[计划第 8 节](../implement-plan/0010-production-delivery.md)。访问日志 token 泄露先实际 RED，再使用不含 query 的格式 GREEN。业务所需真实 AWS 权限、正式 IP HTTPS/送达、实际异机备份/RPO/RTO 及目标 EC2 容量仍未验证。
- 2026-10-04 revision 3 准备：AWS 只读核对实例 running / SSM Online、入站为空、20 GiB gp3 未加密、无 EIP，两个预定 ECR 仓库均不存在、无 IAM OIDC provider。GitHub 公开 API 的 main/CI 仍是旧失败版本；Git SSH 只读认证失败。核对官方 IP 证书和完整报价，未改云资源/业务代码或 commit/push。文档检查通过：11 paired records / 25 tickets；`git diff --check` exit 0。

## 10. 部署、成本与恢复

- 已核对官方加拿大中部 Linux On-Demand 实例报价及公网 IPv4 计价，具体来源与计算见配对计划第 6 节。
- t4g.small 加一个 IPv4 约 24.33 CAD/月，尚未包含 EBS、媒体/请求、备份、镜像、DNS、税；30 CAD 总预算的长期可行性尚未验证。不能把优惠期、免费额度或 AWS Budgets 提醒当成永久费用上限。
- 计划提议实例只拉镜像，NGINX 终止 TLS，证书自动续期，数据库不公开；具体版本、配置和权限须在 review 时定稿。
- 备份异机存储、独立恢复演练与 schema 兼容的版本回退是验收内容。
- 用户创建现有 EC2；Codex 按 revision 3 明确授权完成 EIP、加密换盘、限定云资源、独立空库迁移、教练初始化与首次自动部署。旧卷/快照保留期及费用见 TODO-0024；最终公网、备份和验证状态见配对计划。

## 11. 交付状态与后续

- revision 2 IMPLEMENTED 证据保留，revision 3 技术交付已 VERIFIED / RELEASED，地址 https://52.60.174.156，2026-10-04 多伦多 19:41 公网开放；实际版本、每日备份/恢复、续期及限制见计划第 8/9 节。
- 用户指定初始教练邮箱并明确自行处理账号、业务/邮件及真实媒体测试；这些场景未验收通过，不以 health UP 或本地回归数量代替。
- [TODO-0022](../todo/0022-local-highlight-video-stalls.md) 保持 OPEN；本地 S3Mock 播放缺陷和正式 CloudFront 播放结果分别记录。
- 新根盘已加密；旧根盘/快照保留 48 小时，后续限定清理见 [TODO-0024](../todo/0024-ec2-root-volume-unencrypted.md)。GitHub HTTPS 认证、真实 push/权限/流水线成功，[TODO-0025](../todo/0025-github-ssh-auth-unavailable.md) DONE。首次恢复发现的行数问题已 RED/GREEN 修复并通过新真实备份异机恢复，[TODO-0026](../todo/0026-backup-generated-column-row-count.md)。

- 公网发布证据：HTTP 308 → HTTPS、首页/静态资源/health/CSRF 200、匿名 me 401、安全 Cookie；CI 37244118260 / production 37244412174 success。真实备份 `20261004T234038Z-305863bf02c1` 独立恢复 24 表/22 行/schema 10 / 7.97 秒；backup/certbot timer enabled/active。完整账号业务、媒体与并发未测，用户自行验收。
