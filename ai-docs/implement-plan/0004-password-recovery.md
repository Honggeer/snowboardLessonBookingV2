---
id: "0004"
title: "邮箱验证码找回密码"
status: VERIFIED
revision: 1
approved_revision: 1
created: 2026-09-30
updated: 2026-09-30
feature: "../features/0004-password-recovery.md"
---

# 0004 — 邮箱验证码找回密码实施计划

## 1. Review 摘要

用户要求先提交并推送上一轮文档整理（已完成，commit `585f7e1`），下一步实现找回密码。用户已选定学员/教练共用、邮件验证码；流程是申请邮箱验证码、核验、两次输入新密码并确认，成功后重新登录。关联[功能契约 0004](../features/0004-password-recovery.md)、[TODO-0008](../todo/0008-password-recovery.md)、[项目契约](../PROJECT_CONTRACT.md)及 [ADR 0001](../decisions/0001-v2-baseline.md)。

本修订采用：8 位数字码、10 分钟有效、最多 5 次错码，核验入口同 IP 每 15 分钟最多 30 次；同邮箱 60 秒冷却且每小时最多 3 次申请，同 IP 每小时最多 10 次申请；核验后仅当前服务端 Session 获 5 分钟重设授权。密码沿用 8–128 Unicode 字符并须与确认值一致、不同于当前密码。重设原子地消耗挑战并提升账号凭据版本；所有旧 Session 的后续请求必须失效。验证码用途独立于注册验证；邮件复用已配置的 Gmail/Mailpit SMTP，不增付费服务。

本次仅调整 identity 模块、认证 bootstrap、前端身份页面、追加 Flyway V3 和相关测试/文档。不修改预约模块、公开教练注册、已登录改密、生产部署或 v1 数据。以上数值由用户批准 revision 1 后成为本次实施范围。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-09-30 | 用户回复“批准，开杆” | 1 | 批准本计划 revision 1 的两角色邮件验证码找回、安全参数、会话失效与本地实现验证；未授权生产部署。 |

当前 `approved_revision: 1`；本地验收通过，状态为 VERIFIED。未生产发布。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | backend identity 的 domain/API/集成测试、frontend App 测试 | **批准后首先写目标测试并运行 RED**：申请中性响应、学员/教练码流程、两次密码不一致、错误/过期/重复/并发、跨 Session、旧会话拒绝、邮件重试、手机交互。将环境故障与目标失败区分。 | 实际记录目标行为缺失导致的 RED 命令和失败原因 | 完成 |
| P-02 | `backend/src/main/resources/db/migration/V3__password_recovery.sql`、identity application.port/domain/service、JDBC/crypto/mail adapters | 追加账号凭据版本、找回挑战和任务；用小端口封装验证码、存储、邮件；锁账号行、条件消耗、持久计数；同事务完成密码和版本更新。 | P-01 对应领域、真实 MySQL、邮件测试 GREEN | 完成 |
| P-03 | IdentityController/安全配置、登录结果与 SessionLifetimeFilter、identity API 测试 | 增加三个 CSRF 保护的公开 POST；核验成功只在当前服务端 Session 授权；登录写凭据版本，过滤器检查版本并清理旧 Session；新密码重设后清理当前会话。 | API、CSRF、多会话与旧 Session 测试 GREEN；不泄漏账号信息 | 完成 |
| P-04 | frontend `src/App.tsx`、`src/style.css` 与交互测试 | 在现有 GEER 视觉中加入忘记密码、申请、输码、确认新密码及成功状态；移动端数字键盘和响应式检查。 | 前端目标测试 GREEN，typecheck/lint/build 与视口检查通过 | 完成 |
| P-05 | 0004 功能/计划、索引、TODO-0008、必要运行文档 | 记录 RED/GREEN 与回归证据、实际偏差、成本/迁移信息；按完成判定更新状态。 | 文档检查与 `git diff --check` 通过，状态真实一致 | 完成 |

- [x] 用户明确批准 revision 1，记录日期、原话、范围与条件。
- [x] 先建测试、运行并确认因缺失目标行为而 RED；再实现至同一测试 GREEN，记录命令与结果。
- [x] 执行适用回归；Git commit/push 仅在用户明确要求时执行；新增超范围事项单独建 ticket 或修订计划。

## 4. 数据、API、架构与兼容影响

- 模块/端口：identity 按 domain → application.port/service → adapter 的方向组织。REST、Session、JDBC、SMTP、HMAC 分别留在 adapter/bootstrap；service 不读取 Servlet/SecurityContext，不直接写 SQL；无需跨业务模块调用或新增架构例外。
- API：精确新增 `POST /api/auth/password-recovery/request`、`/verify`、`/complete`。全都需 CSRF；详细 DTO、HTTP 错误、中性响应与幂等语义见功能契约第 5 节。现有登录/注册/验证路径与公开响应形状保持，只有登录内部结果增加服务端凭据版本。
- 验证：申请只对已验证账号建挑战；不存在/未验证走相同对外结果。验证码用现有 `VERIFICATION_KEY` 做**用途分离的** HMAC 派生和摘要，不与注册验证 token 互认，也不写明文入 DB/日志。随机挑战 ID 防止同账号固定码；8 位数字按字符串处理前导零。IP/邮箱申请限流、单挑战错码次数和 verify 来源限流共同约束猜码；拒绝后计数必须真正提交，不能因异常事务回滚。成功核验给当前 Session 保存挑战 ID、账号 ID 和到期时间，不返回 bearer token；成功重设后失效。
- 数据：Flyway V3 append-only。`identity_account.credential_version` 默认 0，当前已有登录 Session 缺版本时首次受保护请求 fail closed，要求重新登录。新找回挑战表及 mail task 表归 identity；外键、按账号/任务状态索引。现有 V2 邮件任务绑定注册验证，不直接改其结构；找回用独立任务，复用调度入口和 SMTP 配置。
- 事务/并发：先锁账号固定行，再查/更新挑战；旧码在新申请时失效，完成时验证 grant、挑战未过期未消耗、当前版本/密码，并以一次事务写新摘要、版本 +1、消耗挑战。并行完成最多一个成功；失败不修改凭据。跨会话/错码失败不获取授权；错误次数用条件更新/锁保证并发最多 5 次。重设前已在处理的请求按实际提交时序处理，重设提交后的新受保护请求应拒绝旧版本。
- 会话：登录时将账号当前 `credential_version` 写入服务器 Session；SessionLifetimeFilter 经 identity 入站端口查询当前版本，缺失、不匹配或账号无效时 invalidate 并返回 401。注册/核验验证码不建立已登录身份，成功重设清理当前 Session，下一步必须重新登录。每个已认证请求增加一次已有 MySQL 账号版本查询，需纳入容量验证。
- 依赖：沿用当前 Spring Boot 4.1.1、Java 25、Spring Session JDBC、MySQL 8.4、邮件、前端依赖，不添加库。v1 不导入。安全规则参考 [OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html) 和 [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)。

## 5. 验收与验证计划

| 验收 ID | 具体验证及 RED 预期 | 环境/命令（批准后执行） | GREEN 预期 |
|---|---|---|---|
| AC-01 | 新请求端点不存在；中性响应与学员/教练投递目标测试失败 | backend：定向 `PasswordRecoveryApiTest`、`PasswordRecoveryFlowTest`，MySQL 8.4 Testcontainers | 已验证两角色收到码；未知/未验证响应相同，无有效任务 |
| AC-02 | 新验证码核验端点/Session grant 缺失 | 同上；`RecoveryRulesTest`、`PasswordRecoveryFlowTest` | 正确码仅授予当前 Session，5 分钟内可继续；无自动登录 |
| AC-03 | 确认密码端点缺失；7/8/128/129 字符和不一致/同旧密码场景失败 | `PasswordRecoveryFlowTest`、domain 测试、frontend 定向测试 | 仅合法且一致的新密码更新；旧密码失败、新密码成功 |
| AC-04 | 旧码/过期/错码/并发/重复安全场景因未实现而失败 | MySQL Testcontainers 集成及并发测试 | 5 次限制有效；并发只允许一次重设，无密码意外变更 |
| AC-05 | 已登录旧 Session 重设后仍能读 `/api/auth/me` | `PasswordRecoveryApiTest` 多 Cookie 场景 | 所有旧 Session 后续请求 401，新登录有效；旧版无版本 Session 401 |
| AC-06 | 邮件队列和前端找回入口不存在；CSRF/重试/响应式场景失败 | backend 队列/API 定向测试；frontend `npm test -- --run`；Mailpit 冒烟和 360/390/768/1440px 浏览器检查 | CSRF 拦截、限流与重试有效，页面可用且无横向溢出 |

所有新目标测试必须先在实现前运行；如果因环境/夹具错误失败，先修环境或测试，再取得有效 RED。GREEN 后运行 backend `./mvnw -q test`（Java 25，真实 MySQL Testcontainers 所需 Colima socket）、前端 `npm test -- --run`、`npm run typecheck`、`npm run lint`、`npm run build`、架构零基线与文档检查；在本地用 Mailpit 完成端到端冒烟。正式 Gmail、HTTPS、EC2 因未授权部署，留作发布前验证。

## 6. 风险、成本、部署与恢复

- 主要风险：短数字码可被猜测、邮箱枚举、旧 Session 继续有效、邮件延迟/重试。用 8 位码 + 有效期/次数/IP 限流、统一请求响应、服务端版本门禁、持久邮件任务和过期校验控制；核验端点与完成端点不泄漏内部账号数据。
- 复用现有 `VERIFICATION_KEY` 和 SMTP 配置；必须做用途分离，密钥更换会使未过期找回码失效。无新增实际付费资源；邮件量与每请求一次版本查询影响当前 EC2/MySQL 容量，生产前实测。测试用 Mailpit 不消耗 Gmail 额度。
- 迁移前备份数据库；MySQL DDL 不假定回滚。若需退回旧应用版本，先确认其可忽略 V3 新列/表，必要时以备份和匹配版本恢复；重设成功的密码数据不能用旧备份无损保留。
- 本计划批准只授权本地实现/验证，不授权生产部署、不可逆数据恢复或新增付费资源。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-09-30 | 审批记录与状态 | 用户批准 revision 1，按 P-01 至 P-05 实施并完成本地验收 | 无范围变化 |
| 2026-09-30 | identity domain、port、service、JDBC、crypto、SMTP、邮件任务和 Flyway V3 | 验证码、速率限制、单次重设、凭据版本与重试邮件均落地；真实 MySQL 测试及 Mailpit 冒烟通过 | 复用已有 `VERIFICATION_KEY`，无新依赖或架构基线例外 |
| 2026-09-30 | identity Web、SecurityConfig、SessionLifetimeFilter | 精确公开三个带 CSRF 的 POST；服务器 Session 持有短时授权；旧 Session 在后续访问时被拒绝 | 额外清理登录、新申请、再次核验前的旧授权，避免同会话误用；在批准安全范围内 |
| 2026-09-30 | frontend App、样式与测试 | 完成申请、输码、确认密码、成功提示；四种视口无横向溢出 | 页面沿用既有 GEER 视觉，无新素材/费用 |
| 2026-09-30 | frontend App 与测试 | 重设成功清除旧 CSRF 后，返回登录可按需获取新 token；网络故障显示可读提示且可重试 | 收尾发现按钮因缺 token 被禁用；在批准的重设后重新登录流程内修复 |
| 2026-09-30 | FoundationMigrationTest、TimeWindowPreviewControllerTest、IdentityProblemHandlerTest、backend/README.md | 适配 V3、MVC slice 新端口，并落实数据库故障 503 中性响应 | 属回归及已批准 API 约定，未改业务范围 |

## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-09-30 | backend / Java 25、MySQL 8.4 Testcontainers | `./mvnw -q -Dtest=PasswordRecoveryApiTest test`（先写用例） | RED：申请端点 401，预期 200；核验端点 401，预期 410；失败由目标行为缺失造成 | 后续同一 API 测试 GREEN |
| 2026-09-30 | frontend | `npm test -- --run`（先写找回入口用例） | RED：页面缺“忘记密码”按钮；实现后同一用例与流程用例 GREEN | 9 个前端测试通过 |
| 2026-09-30 | frontend | 扩展重设成功后的登录用例；`npm test -- --run` | RED：返回登录时按钮仍禁用；按需取新 CSRF 后同一用例 GREEN，并验证网络故障可重试 | 后续 `npm run typecheck`、`npm run lint`、`npm run build` 再次通过 |
| 2026-09-30 | backend / Java 25、Testcontainers | `./mvnw -q -Dtest=PasswordRecoveryApiTest,PasswordRecoveryFlowTest,RecoveryRulesTest test` | API、两角色、错误/过期/旧码、限流、并发、密码边界与旧会话 GREEN | MySQL 测试执行；另有授权残留用例先 RED（错误地允许 200），清理后 GREEN |
| 2026-09-30 | backend / Java 25 | `./mvnw -q -Dtest=IdentityProblemHandlerTest test` | 数据库故障 503 用例先 RED（缺处理方法），补中性映射后 GREEN | 仅映射 Spring `DataAccessException`；未模拟外部 SMTP 故障 HTTP |
| 2026-09-30 | backend / Java 25、Colima | `./mvnw -q test` | 61 个测试、19 个套件，0 失败、0 错误；架构六类基线均为 0 | 首次全量回归发现旧迁移断言和 MVC slice 缺 mock，修复后通过；TODO-0011 的测试停机日志仍存在 |
| 2026-09-30 | frontend | `npm test -- --run`、`npm run typecheck`、`npm run lint`、`npm run build` | 9 个测试及类型、lint、构建通过 | 本地构建，不代表生产发布 |
| 2026-09-30 | 本地 Docker MySQL/Mailpit/后端 | `SMOKE_BASE_URL=http://localhost:8080 python3 deploy/smoke_password_recovery.py` | 收码、重设、新密码登录、旧登录 Session 拒绝均成功 | 使用 Mailpit；Gmail 实际送达待发布环境验证 |
| 2026-09-30 | 本地 Chrome | 360、390、768、1440 CSS px 打开找回页，检查 `scrollWidth == innerWidth` 并查看 390/1440 截图 | 四种视口无横向溢出，手机/桌面布局可读 | 仅人工查看找回申请页，其余步骤有前端交互测试 |
| 2026-09-30 | 仓库根目录 | `python3 ai-docs/check_docs.py`、`python3 ai-docs/test_check_docs.py`、`git diff --check` | 通过 | 文档校验不替代功能测试 |

## 9. 完成状态与后续

- VERIFIED：revision 1 已在本地实现并通过上述验收；TODO-0008 已结案。用户已另行明确要求提交并推送本次工作区改动；未生产部署。
- 正式 Gmail、HTTPS、EC2 容量和迁移前备份仍需在获授权的发布流程中验证；本计划的本地验收不能替代发布后验证。
