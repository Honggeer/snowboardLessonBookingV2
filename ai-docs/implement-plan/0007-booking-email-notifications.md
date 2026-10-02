---
id: "0007"
title: "预约邮件通知与取消规则提示"
status: VERIFIED
revision: 2
approved_revision: 2
created: 2026-10-01
updated: 2026-10-01
feature: "../features/0007-booking-email-notifications.md"
---

# 0007 — 预约邮件通知与取消规则提示实施计划（revision 2 已批准）

## 1. Review 摘要

- 学员提交真实申请后，教练收到新申请邮件；教练确认后，学员收到确认邮件。两封邮件分别链接到该笔预约，收件人登录后查看最新状态。
- 学员在点击“申请预约”前看到取消规则：待确认可取消；确认后至少提前 24 小时取消。现有服务端取消门槛不变。
- “预约成功”按既有 0006 状态机定义为 CONFIRMED；PENDING 只称“申请已提交，待教练确认”。邮件只覆盖这两个指定事件，不发送拒绝、取消或课前提醒。
- 推荐 bookings 模块自有持久邮件任务，复用 SMTP/Mailpit。预约事务中只落任务，独立 worker 发信、重试和核对当前状态。邮件链接增加按角色授权的单笔只读 API，避免分页后找不到目标。
- 需要用户 review 的决定：上述“成功”时点、两种邮件的覆盖范围、链接直达该笔预约。关联[功能契约 0007](../features/0007-booking-email-notifications.md)、[约课功能 0006](../features/0006-post-login-booking-home.md)及[ADR 0001](../decisions/0001-v2-baseline.md)。

**revision 2 review 范围：**用户指出真实邮箱没有收到通知，要求复用注册验证邮件的邮箱发送。只读核查发现 `deploy/.env` 已有 Gmail SMTP 主机、587 端口、账号、应用密码、发件人及认证/TLS 开关，发件人与账号一致；本地 Compose 却把 `MAIL_HOST=mailpit`、`MAIL_PORT=1025` 和本地发件人写死，未传递凭据。身份验证与预约通知代码已经复用同一个 `JavaMailSender`，无需新增供应商或业务队列。SMTP TCP/STARTTLS 握手已通过，尚未验证凭据或真实投递。

- 将 Compose 后端的 `MAIL_*` 从被 Git 忽略的 `deploy/.env` 注入；这些值不存在时继续以 Mailpit 作为本地默认。切换后**注册验证、密码找回和预约两类邮件都会走 Gmail**，而不只是预约通知。
- 本次只切换本机 Compose 发信，不修改预约状态、收件人选择、业务 API、数据库、真实公网部署或付费资源。已有 Gmail 应用密码留在 `.env`，不得写入 Git、构建镜像、测试输出或日志。
- 用户补充：“这个按照参数传入，灵活一些，不要hardcoded，未来deploy的时候之间换参数就行”。链接继续由现有 `APP_PUBLIC_URL` 生成，Compose 通过 `V2_PUBLIC_URL` 传入；本机缺省值为 `http://localhost:8088`，未来部署改参数即可。当前 `.env` 尚未设置该参数，本机链接仅能在运行应用的电脑上打开；外部可用的 HTTPS 地址和部署不在本次范围。
- Gmail 配额及账号限制可能影响投递；本地验证仅对用户掌控的测试地址发送少量邮件，SMTP 接受不等于收件箱最终送达。若真实 SMTP 不可用，恢复 Compose 的 Mailpit 配置即可隔离外发，保留任务按现有重试规则处理。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-10-01 | 用户在收到 revision 1 计划链接、明确“预约成功”指教练确认及邮件范围的 review 请求后回复“开始实现” | 1 | 按 revision 1：PENDING 通知教练、CONFIRMED 通知学员、两类邮件直达该笔预约、提交前显示 24 小时规则 |
| 2026-10-01 | 用户收到 revision 2 计划链接及“Compose 读取 Gmail 参数、链接用 V2_PUBLIC_URL、身份与预约邮件真实外发”的 review 请求后回复“开始实现” | 2 | 按 revision 2 在本机接入已有 SMTP，少量真实测试；此前明确链接地址通过参数传入，未来部署换参数 |

revision 1 的本地 Mailpit 实现已完成并验证。当前 `approved_revision: 2`；revision 2 已获上述批准，开始按测试先行流程实施。用户 2026-10-01 的“帮我接入……用一样的邮箱发”是本次修订的需求来源。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | backend/src/test/java/com/geer/snowboard/v2/bookings/ 目标集成测试、worker 测试；frontend/src/App.test.tsx | 先写 AC-01~06 的可执行测试，分别运行至缺少行为所致 RED | 记录每类有效 RED 和命令 | 完成：API 404、前端缺文案/目标为有效 RED |
| P-02 | backend/src/main/resources/db/migration/V8__booking_mail_tasks.sql；bookings.application.port.out 与 adapter.out.persistence | 建任务表、唯一键、任务入库/领取/状态更新；限定批量、lease、重试上限、清理 | MySQL 并发、故障与迁移测试 GREEN | 完成：V8 和真实 MySQL 测试通过 |
| P-03 | BookingService、bookings 邮件 worker/定时入口、SMTP adapter、identity 的内部联系方式用例及跨模块 adapter | 新申请/确认同事务入任务；worker 发信前校验状态，按预约快照构造邮件，失败退避；SMTP 不在业务事务中 | 事务回滚、幂等、失效、投递测试 GREEN；无依赖违规 | 完成：MySQL 与本地 Mailpit 通过 |
| P-04 | BookingOperations、BookingController、BookingHome.tsx、App.tsx 与相关样式 | 增加两条授权单笔 GET，解析/保留邮件深链接并聚焦目标；提交区展示 24 小时规则 | 权限、登录恢复、手机/桌面与文案测试 GREEN | 完成：API、前端测试、Chrome 两种宽度通过 |
| P-05 | backend/README.md、frontend/README.md、deploy/README.md、功能/计划/索引 | 配置 APP_PUBLIC_URL、SMTP、worker 开关和 DEAD 排查说明；记录真实验证与状态 | 文档检查、回归、Mailpit 冒烟和索引同步 | 完成：文档与本地冒烟通过 |
| P-06（revision 2） | deploy/compose.yaml、deploy/.env.example、deploy/README.md、backend/README.md；配置测试 | 先写注入配置测试并确认 RED，再让 Compose 读取现有 `.env` 中的 Gmail SMTP 参数，保留 Mailpit 默认值；沿用 `V2_PUBLIC_URL` 参数生成链接 | 配置测试 GREEN，Compose 配置校验，后端健康；无秘密进入 Git/输出 | 完成 |
| P-07（revision 2） | 本地 Compose、现有注册/预约邮件任务与功能/计划/索引 | 在用户掌控的邮箱上验证 SMTP 认证和真实投递路径，并核对任务状态、失败/回退行为；同步实测结果 | 明确 SMTP 接受与邮箱实收证据及限制，更新状态 | 完成：SMTP 接受，用户确认两封均收到 |

- [x] 批准后先做 P-01，运行目标测试确认有效 RED，再实现 P-02~04 到同一测试 GREEN。
- [x] 运行后端适用全量回归、前端测试/typecheck/lint/build、文档检查和真实浏览器/Mailpit 冒烟。
- [x] 按实际结果同步功能契约、计划、两个索引；未发现需单独建票的后续问题。
- [x] revision 2 经用户 review 后，先写 Compose SMTP 注入测试并得到因写死 Mailpit 而失败的 RED，再实现并得到 GREEN。
- [x] 在已批准范围内验证真实 SMTP 外发与两类预约任务，记录身份邮件共用配置、任务状态、链接可达性及恢复方式。
- [x] 收件人确认测试邮件到达；2026-10-01 用户回复“两封都收到”。SMTP `SENT` 本身仅证明服务器接受。

## 4. 数据、API、架构与兼容影响

- 模块/端口：bookings 业务用例通过自己的任务出站端口写邮件任务；bookings worker 经队列、SMTP、内部账号联系方式端口工作。identity 仅公开内部账号联系方式入站用例，bookings 的 out.module adapter 调用它，不读 identity 表。预约状态读取留在 bookings 模块，避免模块依赖环；domain/port 纯 Java。
- API：既有申请和确认响应不加“邮件已发送”承诺；新增 GET /api/bookings/{id} 与 GET /api/coach/bookings/{id}，按真实 Session actor 做本人/所属教练鉴权，401/403/404 保持既有 Problem Details。邮件链接只带预约 ID，不带认证令牌。
- 前端：App 识别 /#/my-bookings/{id}、/#/coach-applications/{id}，未登录保留目标；登录后 BookingHome 显示对应标签、读取单笔并聚焦/突出；错误或状态变化有可见反馈，切换账号清理私人数据。申请按钮附近在提交前显示取消规则。Cookie、CSRF 和同源 API 不变。
- 数据：追加 V8 bookings_mail_task，bookings 自有，事件唯一键 (booking_id,event_type)，含状态/尝试/lease/下次尝试/错误类别/时间戳；不存正文和认证秘密。旧预约不补发历史邮件；V1~V7 不改。新表需要真实 MySQL 8.4 迁移测试。
- 事务与并发：复用现有 READ COMMITTED 和日/时段/学员锁顺序；只在真实新申请/首次确认之后入任务，任务失败整个业务事务回滚。worker 领取在短事务中采用 SKIP LOCKED；SMTP 调用不持业务锁。多 worker 同时领取不能领取同一有效 lease；lease 到期可重领。发送成功但状态落库失败可能重复，按 at-least-once 告知。
- 失效与重试：APPLICATION_RECEIVED 发送前须为 PENDING，BOOKING_CONFIRMED 须为 CONFIRMED；不符 SKIPPED。每轮至多 10 项、30 秒 lease；失败指数退避上限 60 分钟，最多 8 次转 DEAD。保留 DEAD 供人工检查和有条件重试，清理旧 SENT/SKIPPED。持续数据库故障须留下可定位日志。
- v1 仅参考业务表述，不迁移数据或通知实现。复用当前 Spring Boot Mail 配置/JavaMailSender、身份邮件的任务经验与本地 Mailpit，不新增库或付费资源；精确现有版本以 backend/pom.xml 为准，无版本升级。无架构例外，ARCH-09 数字基线不增加；ADR 0001 预期不变。
- revision 2 不改变上述业务数据与模块边界。Compose 只把已有 SMTP 环境变量传给已存在的 Spring Mail 配置；若当前 `.env` 存在 Gmail 值，身份与预约邮件都改为真实投递。无数据库迁移、API/前端变更、依赖升级或架构例外。

## 5. 验收与验证计划

| 验收 ID | 目标测试先行 RED 与实现后检查 | 环境/命令 | 预期结果 |
|---|---|---|---|
| AC-01 | 新申请应有教练任务且唯一；申请事务回滚不留任务；同键重放不重复 | backend/ 下新增预约通知 API/MySQL 测试，Maven 定向运行 | 初跑因无任务表/行为失败；实现后通过 |
| AC-02 | 首次确认产生学员任务；重复确认无新任务；邮件详情、日期时区、链接和 24 小时说明正确 | backend/ 下新增确认通知测试、Mailpit API/SMTP 冒烟 | 初跑因无任务/模板失败；实现后通过 |
| AC-03 | 两个 worker 竞争同一任务、SMTP 失败/重启/lease 过期、8 次 DEAD、成功后 SENT | backend/ 下 worker 单元与 MySQL 并发测试 | 初跑因队列/worker 缺失失败；实现后无丢任务或重复同时领取 |
| AC-04 | 申请变为拒绝/取消/确认及已确认课取消后未发任务 SKIPPED；SMTP 失败不改预约状态 | backend/ 下 worker 状态与 API 测试 | 初跑因状态校验缺失失败；实现后通过 |
| AC-05 | 两类单笔 GET 的未登录/错角色/其他账号/目标不存在，前端登录前后深链接恢复与目标状态 | backend/ 下 Session/API 测试；frontend/ 下 App.test.tsx | 初跑 404 或未导航；实现后鉴权及交互通过 |
| AC-06 | 提交前在按钮邻近展示准确取消规则，手机和桌面可见 | frontend/ 下 App.test.tsx 与真实浏览器 390/1440px 检查 | 初跑缺少文案；实现后通过 |

- 后端使用当前 MySQL 8.4 Testcontainers 环境和 ./mvnw test；迁移、事务、并发、worker、授权与架构测试必须通过。验证 24 小时恰好相等和不足边界保持 0006 原行为。前端运行 npm test、npm run typecheck、npm run lint、npm run build。
- Mailpit 冒烟：启动本地 Compose、以真实学员申请→教练收件→教练确认→学员收件走完整流程；点击链接，经未登录与已登录路径检查目标及最新状态。核查邮件不会发往外部，测试环境只用 Mailpit。
- 文档执行 python3 ai-docs/check_docs.py、python3 ai-docs/test_check_docs.py、git diff --check；记录实际命令、RED/GREEN 输出、测试数量与限制。计划批准前只做文档验证，不运行或创建功能测试。
- revision 2 的配置目标测试使用临时假 SMTP 凭据渲染 Compose，并只断言后端环境变量映射；先因当前写死 Mailpit 而 RED，再改配置到 GREEN。随后检查缺省参数仍指向 Mailpit，运行 Compose 配置校验、后端健康、相关邮件回归，并仅向用户控制的真实邮箱做小量端到端发信。测试不打印凭据；收件箱实收若无法由本机核实，明确记为未验证。

## 6. 风险、成本、部署与恢复

- 邮件链接需要收件人可访问的 APP_PUBLIC_URL；当前本地 Compose 默认 localhost:8088，只适合本机。实施时提供配置示例并确认 URL 来自服务端可信配置；将来生产须由授权部署配置 HTTPS 域名。链接不授予访问权。
- 邮件存在投递延迟、退信、SMTP 限流、重复投递及状态检查后瞬间变化的窗口。页面状态为准；任务 DEAD 留存供人工排查和按当前状态重新投递。应用日志只记任务 ID/错误类别，不记密码、会话 Cookie 或完整敏感内容。
- 预计新增一张小型表、一个有限速率的轮询器及两类邮件。复用已存在 SMTP/Mailpit；不创建外部付费资源。真实 Gmail 配额、EC2 CPU/内存及月成本须在部署前按实际服务和流量评估，不能假定永远满足 30 CAD。
- V8 追加迁移不能假设 DDL 可回滚。发布前需备份和恢复验证；应用回退为停用新 worker、使用上个镜像、保留 V8 表和任务供恢复后处理。生产迁移、真实外部 SMTP 配置与部署均需另行授权。
- revision 2 本机 Gmail 测试复用用户已有邮箱和应用密码，不新建付费服务。Compose 传递的环境变量可被本机 Docker 管理员读取；继续使用被 Git 忽略的 `.env`，不在命令/日志回显秘密。若不再需要真实投递，将 Compose 环境值切回默认 Mailpit 并重建后端；已对外发送的邮件无法撤回。邮箱服务商的限额与最终送达无法由本地配置保证。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-10-01 | 目标 API/前端测试 | API 先因单笔 GET 404 失败，前端先因无取消文案及深链接加载失败 | 有效 RED；worker 的后续故障/并发测试在实现后加入并通过，不将测试夹具错误算作 RED |
| 2026-10-01 | V8、BookingMailQueue/Worker/Poller、SMTP 与身份邮箱适配、BookingService | 申请/确认同事务入任务，worker 后发信；幂等、回滚、并发、lease、重试、失效状态检查均通过 | 与 revision 1 一致；未增加第三方服务或架构基线 |
| 2026-10-01 | 单笔 GET、前端 hash 深链接与规则提示 | 按账号鉴权；邮件直达预约并显示最新状态；提交前提示取消门槛 | 与 revision 1 一致 |
| 2026-10-01 | Compose、Mailpit 脚本、README 与索引 | 重建本地容器并走完整申请/确认投递；记录本地结果 | 仅本地环境更新，未部署生产 |
| 2026-10-01 | revision 2 只读配置调查 | 发现 `.env` 有 Gmail 参数但 Compose 固定 Mailpit；创建待审方案，未写测试或改运行配置 | 真实外发与本机链接范围需重新 review |
| 2026-10-01 | revision 2 配置测试与 Compose/README | 新增 `deploy/test_compose_mail.py` 后先因 `MAIL_HOST=mailpit` 失败；参数映射后同一测试通过，默认 Mailpit 保留 | 无业务代码、迁移、架构或 API 变更；未新增付费资源 |
| 2026-10-01 | 本地真实 Gmail SMTP | 应用读取现有 `.env`，完成 SMTP 登录、两种预约任务真实外发，随后取消测试预约；后端恢复并保持 Gmail 配置 | 接收方邮箱实收待用户确认；任务 SENT 不能解释为最终送达 |

## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-10-01 | 仓库只读调查 | 阅读项目契约、0006、邮件 worker/SMTP、V2~V7、前端约课入口 | 确认现有 PENDING/CONFIRMED 状态、24 小时取消规则及 Mailpit/SMTP 基础设施 | 仅方案依据，尚无新功能测试 |
| 2026-10-01 | backend/、MySQL 8.4 Testcontainers | 先运行 `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q -Dtest=BookingCoreApiTest#bookingMailTasksAreCreatedOnceAndDeepLinksRequireTheCorrectAccount test`；实现后运行 `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q test` | 定向测试先 404 RED 后 GREEN；全量 101 个测试、0 失败/错误/跳过；架构测试通过 | 首次全量测试漏设 Colima 变量，因 Docker 环境不可用报错；补上后重跑通过。旧迁移测试版本断言随 V8 改为 8 |
| 2026-10-01 | frontend/ | 新增测试后 `npm test -- --run -t 'cancellation cutoff|mailed booking link'`；实现后 `npm test`、`npm run typecheck`、`npm run lint`、`npm run build` | 两项目标先因行为缺失 RED 后 GREEN；全量 32/32 通过，类型、lint、构建通过 | 无 |
| 2026-10-01 | 本地 Compose/Mailpit | `docker-compose --env-file deploy/.env -f deploy/compose.yaml up -d --build backend frontend`；`SMOKE_COACH_EMAIL=… python3 deploy/smoke_booking_mail.py`；`python3 deploy/smoke.py` | V8 应用到本地测试库，教练申请邮件和学员确认邮件均到 Mailpit；链接、权限与测试数据清理通过；基础冒烟通过 | 本地沙箱，不代表 Gmail 真实送达或生产部署 |
| 2026-10-01 | 本地 Chrome headless，390px/1440px | 邮件深链接进入登录，登录后重载并读取已取消预约最新状态；切换约课页，检查取消提示矩形与页面宽度，人工检查截图 | 目标预约高亮、规则可见，两种宽度均无横向溢出 | 仅 Chrome 本地视觉检查 |
| 2026-10-01 | 仓库根目录 | `python3 ai-docs/check_docs.py`、`python3 ai-docs/test_check_docs.py`、`git diff --check` | 全部通过 | 无 |
| 2026-10-01 | 本机只读 SMTP 探测 | 检查 `.env` 中 SMTP 必要键是否存在、Compose 后端实际键是否缺失；对配置主机:端口建立连接并协商 STARTTLS | Gmail 主机/587、账号/密码/发件人及认证/TLS 均在 `.env`；Compose 容器未获得账号/密码；TCP 与 STARTTLS 成功 | 未执行 SMTP 登录或真实发信；未披露凭据 |
| 2026-10-01 | deploy/ | `python3 -m unittest deploy.test_compose_mail -v` | 修改前定向 RED：传入测试 SMTP 主机仍渲染 `mailpit`；修改后 2/2 GREEN，含无 SMTP 时的 Mailpit 默认值及 `V2_PUBLIC_URL` 参数映射 | 测试仅使用临时假凭据，不输出真实密码 |
| 2026-10-01 | 本地 Compose + Gmail SMTP | 渲染配置并在进程内对比 `deploy/.env` 中 7 个 `MAIL_*` 值；真实 SMTP 登录；重建并等待后端健康；受控学员申请、教练确认，分别轮询任务状态；最后取消测试预约 | 7/7 参数映射一致、SMTP 登录成功；教练 APPLICATION_RECEIVED 与 Gmail 别名学员 BOOKING_CONFIRMED 均为 SENT；无 PENDING/CLAIMED/DEAD 任务，后端保留真实 SMTP 配置；`python3 deploy/smoke.py` 通过 | 测试学员先在临时 Mailpit 模式验证；Gmail SMTP 接受两封预约邮件，收件箱实收未从应用侧确认；链接仍为本机 localhost |
| 2026-10-01 | 收件人反馈 | 核对教练新申请通知和学员预约确认通知是否到达 | 用户回复“两封都收到”；两类邮件的收件验收通过 | 链接仍为本机 localhost；生产公网投递尚未验证 |
| 2026-10-01 | 提交前工作区复核 | `env DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q test`；`npm test -- --run`、`npm run typecheck`、`npm run lint`、`npm run build`；`python3 -m unittest deploy.test_compose_mail -v`、`python3 ai-docs/check_docs.py`、`python3 ai-docs/test_check_docs.py`、`git diff --check` | 后端 101/101、前端 32/32、Compose 配置 2/2、文档测试 3/3 均通过；类型、lint、构建、文档检查和 diff 检查通过 | 后端首次运行被沙箱阻止访问 Colima socket；获准访问后同一全量命令通过 |

## 9. 完成状态与后续

- revision 1 于 2026-10-01 完成 P-01~05 实现，并经 101 个后端测试、32 个前端测试、本地 Mailpit 投递、Chrome 双宽度检查及文档检查达到 VERIFIED。
- revision 2 已按批准范围完成配置实现，达到 VERIFIED，`approved_revision: 2`；Compose 测试先 RED 后 GREEN，真实 Gmail SMTP 已接受教练及学员两类预约邮件，用户确认两封均收到。
- 生产部署、真实公网 HTTPS 域名与发布后验证未执行，尚未 RELEASED。
