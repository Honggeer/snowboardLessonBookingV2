---
id: "0012"
title: "已确认预约课前邮件提醒"
status: VERIFIED
revision: 1
approved_revision: 1
created: 2026-10-06
updated: 2026-10-06
feature: "../features/0012-lesson-reminder-emails.md"
---

# 0012 — 已确认预约课前邮件提醒实施计划（revision 1）

## 1. Review 摘要

用户要求参考 v1，在开课前邮件提醒。已核对 v1 实际逻辑：每天 `America/Toronto` 18:00 处理已确认预约，开课时间在 `(now+24h,now+48h]`，学员和教练各一封。发送失败也 markSent 且测试接受此行为，v2 不应沿用失败即完成的处理。

用户已选择 **“沿用 v1：每天 18:00，提前 24～48 小时”**，并明确 **“都发，然后记得提醒24小时之内不能取消”**。因此本修订使用多伦多当地 18:00，窗口严格为 `(runAt+24h,runAt+48h]`，学员与教练分别一封，正文写明“距开课不足 24 小时无法取消预约”及确切截止时间。保持现有取消边界：正好提前 24 小时仍可以取消，不变更 0006 的业务规则。

已批准范围：

1. 首次确认时同事务写延时邮件任务，复用现有 MySQL 队列/SMTP；原有申请通知、学员确认通知不变。
2. 两位收件人分别记录状态与重试，取消或已开课不再提醒，进程重启后可恢复。
3. 确认时计算其第一个有效 18:00 窗口作为 dueAt；已错过最后有效窗口才确认则不附加提醒。故障重试/停机恢复仅在距开课仍超过 24 小时时补发，达到 24 小时截止即失效。
4. 现有未来已确认预约依据真实确认时间计算有效窗口，且尚未超过提醒截止时，有限批次补建；永久规划标记阻止队列清理后再次生成。
5. 中文正文给出确切日期、时间、时区、雪场、课程、取消截止时刻和需登录的本人预约链接；不增加页面操作或电话外发。

不包含短信/评价提醒、改期、教练取消、自动完成、前端设置或新邮件供应商。关联[功能契约 0012](../features/0012-lesson-reminder-emails.md)、[0007](0007-booking-email-notifications.md)、[0006](0006-post-login-booking-home.md)、[ADR 0001](../decisions/0001-v2-baseline.md)与 [TODO-0037](../todo/0037-lesson-reminder-emails.md)。本地实现与验证已获用户批准；用户在验收完成后另行明确要求“提交推送”，授权记录见第 2 节。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-10-06 | 用户在收到 0012 revision 1 后回复：“批准” | 1 | 授权本修订本地实现、测试与文档维护；不含 Git commit/push、生产发布/迁移或新增付费资源 |
| 2026-10-06 | 用户在本地验收完成后回复：“提交推送” | 1 | 单独授权本功能 Git commit/push 至当前默认分支 main，并沿用既有 CI/CD 自动交付；不新增付费资源、不手工更改生产秘密、不额外发送测试邮件 |

当前 `approved_revision: 1`；用户 2026-10-06 “批准”对应本具体 revision 1，批准后进入 IMPLEMENTING，现已完成本地验收达到 VERIFIED。业务选择原话保留在第 1 节及配对功能第 3 节；实质范围变化重新 review。

## 3. 实现步骤与预计文件

Java 路径前缀：`backend/src/main/java/com/geer/snowboard/v2/`；测试前缀：`backend/src/test/java/com/geer/snowboard/v2/`。

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | `bookings/LessonReminderScheduleTest.java`、`LessonReminderApiTest.java`、`LessonReminderWorkerTest.java`、`LessonReminderMigrationTest.java` | 获批后先建立可编译目标测试；18:00 前不发、24h/48h 窗口边界、确认生成独立任务、错过窗口不补、单收件人失败独立重试、取消失效；不提前修改实现来让测试通过 | API/worker 得到缺少提醒行为的有效 RED；领域规则用可编译边界验证行为 RED，记录命令与断言 | 完成 |
| P-02 | `bookings/domain/LessonReminderSchedule.java`、`application/port/in/BookingReminderOperations.java`、`application/service/BookingReminderService.java`、`application/port/out/BookingReminderSettings.java` 与配置 adapter | 建立纯 Java 时间规则/设置；用例编排确认规划和有界补建，设置读取不进入领域/用例 | 时间/角色/业务边界 GREEN，框架依赖仅在允许层 | 完成 |
| P-03 | `backend/src/main/resources/db/migration/V12__lesson_reminder_emails.sql`；`BookingStore/JdbcBookingStore`、`BookingMailQueue/JdbcBookingMailQueue` | 追加任务事件 CHECK、提醒开课时间、预约规划标记及补建索引；新增延时入队、token 保护、提醒领取过滤与持久去重 | V11→V12 兼容，旧数据/任务不变；真实 MySQL 原子建任务和独立领取 GREEN | 完成 |
| P-04 | `bookings/application/service/BookingService.java`、`BookingReminderService.java` | 在既有确认事务末尾规划提醒；相同确认不重复。补建每分钟最多 50 条、短事务锁预约行重新核对状态；不反向锁日/时段/学员 | 确认/取消/补建竞争、回滚和清理后不重复 GREEN | 完成 |
| P-05 | `BookingMailWorker.java`、`BookingMailPoller.java`、独立调度配置 adapter；`BookingMailQueue` 的 Task | 增加双方提醒内容、收件人/开课时间/当前状态检查，独立退避与距开课超过 24h 的有效期；复用 SMTP。预约邮件独立单线程，轮询异常不终止后续运行，关闭有界 | 对应 worker RED→GREEN，领取恢复/旧 token/单收件人失败/生命周期检查通过 | 完成 |
| P-06 | `backend/src/main/resources/application.properties`；`deploy/compose.yaml`、`compose.production.yaml`、两份 `.env*.example`；相关配置测试 | 非秘密 `BOOKING_REMINDERS_ENABLED` 与完整邮件 worker 暂停映射；关闭提醒不影响既有通知、不消耗提醒任务，恢复可继续；不读写真实 env/秘密 | 配置目标先 RED 后 GREEN，默认及暂停/恢复语义通过 | 完成 |
| P-07 | 既有 `BookingCoreApiTest`、`BookingMailWorkerTest`、迁移最新版本断言；`deploy/tests/smoke_lesson_reminder.py`（若需复用沙箱冒烟） | 旧即时通知仍唯一和授权；迁移 fixture 保留特定历史目标。Mailpit 用隔离实例/受控时钟验证双方提醒、链接与失败结果，禁止向真实账号测试发送 | 后端全量、架构、配置回归及 Mailpit 实收记录通过；不改变用户 IDEA/生产进程 | 完成 |
| P-08 | 本配对文档、两份索引、TODO-0037、0007 扩展关联、backend/deploy 操作文档 | 填实际 RED/GREEN/命令/验收及恢复证据，IMPLEMENTED/VERIFIED/RELEASED 分开；更新当前/归档 ticket | 必要验收完成后标 VERIFIED；清楚记录未推送/部署 | 完成 |

- [x] 业务选择已确认、文档同步到 revision 1。
- [x] 用户明确批准具体 revision 1，批准依据与范围已记录。
- [x] P-01：先运行因目标行为缺失失败的测试；编译、环境或 fixture 问题先解决，不算 RED。
- [x] P-02～P-06：实现同一行为到 GREEN，记录实际文件与局部调整。
- [x] P-07～P-08：回归、沙箱邮件和文档验收，关闭 ticket 并同步索引。

## 4. 数据、API、架构与兼容影响

- bookings 自有预约和邮件任务；保持 bookings→identity 单向调用邮箱公开入站端口。没有直接读身份/排课表的新查询，无新模块或架构例外，六类基线仍 0、ADR 0001 不变。
- 领域枚举 `[startAt-48h,startAt-24h)` 覆盖的多伦多当地日期，构造当地 18:00，按 Instant 校验 `runAt >= confirmedAt`、`startAt > runAt+24h`、`startAt <= runAt+48h`，取第一个有效时点。这样保留 v1 每日扫描窗口；DST 周围没有有效时点时不补造，有两个有效时点时只取第一个，避免重复。显示按预约 IANA zone 和 UTC offset。正好提前 24 小时仍允许取消，但不进入提醒窗口；这两个边界分别验收。
- 已追加 V12：预约 `lesson_reminder_planned_at DATETIME(6) NULL`；任务 `reminder_start_at_utc DATETIME(6) NULL`；任务 CHECK 加 STUDENT_LESSON_REMINDER/COACH_LESSON_REMINDER；预约索引 `(status,lesson_reminder_planned_at,start_at_utc_snapshot,id)`。既有唯一键 `(booking_id,event_type)` 即可区分两人；不改 V1～V11。
- 规划标记记录已检查而非邮件已发送。旧预约 NULL 由内部补建处理，以 decidedAt 计算其确认后的第一个有效 runAt；若已过 dueAt 但仍距开课超过 24h，可恢复投递。确认已错过所有有效窗口的记录标已检查但不建任务；缺少有效确认时间不猜造。SENT/SKIPPED 清理后不重新补建，DEAD 不自动复活。
- 确认保留既有 READ_COMMITTED 和日→时段→学员→预约锁；计划标记与提醒任务同事务，SMTP 不在业务事务内。补建只锁预约行（稳定 start/id 顺序、SKIP LOCKED），不取得业务上游锁；批次最多 50，写失败整批回滚后下轮重试。
- worker 同时最多领取 10 项，逐项使用独立短事务；提醒初次可领取时间为 dueAt。提醒 lease 为 120 秒、写回用 token 条件，发送前检查有效 lease；队列发送成功仍不是 exactly-once 保证。旧即时通知的有效性与独立重试不变。
- 提醒开关关闭时：确认仍产生原有即时通知；不做新提醒规划、不领取已有提醒、不标 SENT/SKIPPED。开启时有限补建/恢复仍距开课超过 24h 且有效的任务。
- 不新增 API、页面或浏览器缓存；沿用授权深链接。邮件正文没有电话、收件邮箱、验证码/会话令牌。现有 0011 教练页面电话显示不受影响。
- v1 采用用户确认的每日 Toronto 18:00 / 24～48h 窗口、双收件人和中文说明；使用持久 dueAt 与独立投递状态改善临时故障漏发和失败也完成，保留窗口本身的错过/边界语义。标题使用“即将上课”，不把可能后天的课程称为“明天”；不照搬 SMTP 位于确认事务或一个 sent 控制两人。
- 复用 pom 已有 Java 25 / Spring Boot 4.1.1、MySQL 8.4、JavaMailSender，无版本升级、第三方库或新云服务。

## 5. 验收与验证计划

| 验收 ID | 具体验证 | 环境/命令 | 预期结果 |
|---|---|---|---|
| AC-01、08 | 已有确认 API 返回成功但没有提醒事件应先 RED；实现后新确认/重复确认、任务失败回滚、旧预约补建/取消竞争、标记持久去重 | `backend/`：`./mvnw -q -Dtest=LessonReminderApiTest,LessonReminderMigrationTest test`；真实 MySQL 8.4 | 有效行为 RED 后同一场景 GREEN；V11 数据不丢不改 |
| AC-02、04、09 | 固定 Clock：Toronto 18:00 前/到达，24h 下界不含、48h 上界含、DST 相邻 23/25h 与错过窗口确认；worker 取消/距开课不超过 24h/时间不匹配拒发、暂停保留后恢复 | `./mvnw -q -Dtest=LessonReminderScheduleTest,LessonReminderWorkerTest test` | 预期 RED 为缺少规则或提醒行为，不是测试编译失败；实现后 GREEN |
| AC-03、05、07 | 双方独立收件、内容/时区/链接/取消截止文案、单人失败不影响另一项、8 次 DEAD、24h 截止停止；SMTP 成功落库失败的恢复 | 同上、Mailpit 沙箱 SMTP 和读取消息；`BookingMailWorkerTest` 回归 | 失败不假标已发，已完成另一项不因重试重发；SMTP/实收分别记录 |
| AC-06、08 | 独立事务两个 claimers、过期重领、旧 token、不持业务锁发信、补建和首次确认/取消并发、任务清理后不重复 | `LessonReminderWorkerTest`/`LessonReminderApiTest`；真实 MySQL 8.4 Testcontainers | 单有效 lease；失效不发，不死锁或生成重复事件；at-least-once 限制有证据 |
| AC-09、10 | 非秘密开关和 Compose 映射、专用单线程资源、异常后继续轮询与有限关闭 | 定向配置/lifecycle 测试；`python3 -m unittest discover -s deploy/tests -p 'test_*.py'` 的相关子集 | RED 为缺少映射/暂停语义；GREEN 后默认/关闭/恢复均正确，不打印真实秘密 |
| AC-10 | 身份、电话、原通知、预约规则、迁移、架构及取消临界值回归 | `./mvnw test`；前端现有 `npm test`、`npm run lint`、`npm run build` | 所有适用测试/检查实际通过；前端无新增行为不写镜像测试 |

- Mailpit 用隔离数据库/进程和受控 Clock 或测试构造的到期数据，不改生产时间、真实预约或用户正在运行的 IDEA 实例；核对两位收件人的实际消息、详情字段及已有深链接权限，不发送给互联网邮箱。不必等待真实 18:00 或真实 24～48 小时。
- 文档检查：`python3 ai-docs/check_docs.py`、`git diff --check`。审批前只检查文档，不创建/运行新功能测试。
- 上述 Maven 命令需要现有 Java 25/Colima 与 MySQL 8.4 Testcontainers 环境。回归时如复现 [TODO-0034](../todo/0034-full-backend-test-shutdown-timeout.md)，分别记录业务结果/进程结果，不将其写为本功能已修复。

## 6. 风险、成本、部署与恢复

- 分别投递与重试增加 SMTP 数量和存储；每个符合条件预约最多两条新提醒，各最多 8 次。无新付费资源、域名、密钥或邮件账号，不承诺 Gmail 收件箱分类、无限配额或精确时刻送达。
- 迟延恢复只允许在取消截止之前（距开课严格超过 24h），超过后 SKIPPED；正文给出真实截止时间并指向页面。发送有效性检查与 SMTP 接受之间仍可能跨过截止或发生取消，已发送不可撤回；投递后崩溃也可能重复。
- 扩展现有队列事件，旧版本 worker 不识别新事件会将其跳过。生产回退先暂停完整预约邮件 worker，再恢复旧镜像且保留 V12/任务；原有申请/确认邮件会随暂停延后，恢复新版后再继续处理有效记录。
- 发布需额外生产授权及备份，应用 V12 后验健康、后台开关/队列、目标 Mailpit 验收之外的受控生产场景；DDL 不按自动回滚设计。此次不运行生产迁移、不修改真实环境参数或生产任务。
- 用户“批准”具体 revision 1 后授权本地实现、测试与文档；随后在验收完成后明确要求“提交推送”，授权本功能 commit/push 与既有 CI/CD 自动交付。额外付费资源、手工生产操作与真实测试邮件不在本次范围。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-10-06 | v1/v2 源码、设计/测试与 Git 只读调查 | 确认 v1 定时窗口、双方收件和失败 markSent；v2 V11、两种通知事件与现有 lease/重试；工作区已有独立 frontend README 修改 | 仅作为设计依据；不执行 v1 测试、不改旧仓库 |
| 2026-10-06 | 功能/计划草稿、TODO-0037 和索引 | 提出两个业务选择并形成可评估建议；无测试/实现代码 | 答案未到，DRAFT；不是自行决定发送规则 |
| 2026-10-06 | 功能/计划 revision 1、索引与 TODO-0037 | 用户选择沿用 v1 18:00 / 24～48h，双方收件并说明 24h 取消规则；同步实际窗口、恢复截止和验收，进入 AWAITING_REVIEW | 未获具体实施批准，无功能测试/代码；保留需求选择与批准的区别 |
| 2026-10-06 | P-01～P-06；用户“批准”后记录 revision 1 批准、实现与目标测试 | API 有效 RED 1、领域有效 RED 5、配置有效 RED 1 后实现至同一场景 GREEN | JDBC 测试最初误用 queryForList/RowMapper 的编译失败已修正，不算 RED |
| 2026-10-06 | P-03/P-05；JdbcBookingMailQueue 单参数 claim 入口 | 定向并发发现接口 default 入口自调用绕过事务，两个领取者均取同一任务；适配器为两个入口分别声明事务后通过 | 新实现缺陷当场修复，领取/去重契约与架构基线不变 |
| 2026-10-06 | P-05/P-07；BookingMailPoller、worker/迁移/API/并发测试 | 专用一个线程、固定延迟、异常后继续；补建 50 条与领取 10 条有界；Mailpit 实际双投递 | cleanup 从整点 cron 改为每小时固定延迟，不改变 7 天保留；沙箱验收直接进入自动化 MySQL/SMTP 测试，不另建重复 smoke 脚本 |
| 2026-10-06 | P-07；旧通知和迁移 fixture | 旧通知计数仅统计原 APPLICATION_RECEIVED/BOOKING_CONFIRMED；最新断言 V12，StudentContactMigrationTest 固定本功能历史目标 V11 | 保留原通知唯一/事务断言，不把新增两条提醒解释为旧通知重复 |
| 2026-10-06 | P-07；第一次全量和 BookingMailPollerTest | 193 项有 1 项测试瞬时线程 isAlive 断言竞争；改为有限 join 后核对，运行最终全量复验 | 属于测试关停判断修正，不计为新业务 RED；既有 TODO-0034 JVM 收尾超时本轮也复现，单独记录 |


## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-10-06 | 本地 v1/v2 | 读取 scheduler、mapper、target tests；读取 v2 BookingService/MailWorker/Queue/Poller、SMTP 超时和 V8～V11 | 确认既有行为和待补能力，参考依据见配对功能第 9 节 | 只读调查，无有效新功能 RED/GREEN 或生产验收 |
| 2026-10-06 | 仓库根目录 | `python3 ai-docs/check_docs.py`、`git diff --check` | 通过：13 对记录、37 tickets、链接/索引/状态/批准门槛一致；空白检查 exit 0 | 仅文档检查，不提前记功能验收通过 |
| 2026-10-06 | backend/，Java 25、MySQL 8.4 | `mvn -q -Dtest=LessonReminderApiTest test` | RED：1 failure、0 errors/skips，确认后的提醒数 0，预期 2 | 先修正 JDBC 测试编译误用后才运行有效 RED |
| 2026-10-06 | backend/ | `mvn -q -Dtest=LessonReminderScheduleTest test` | RED：5 failures、0 errors/skips，空结果不满足晨课/晚课/24h/48h/DST 时点 | 最小可编译时间规则边界未实现，再按同一测试实现 GREEN |
| 2026-10-06 | 根目录 | `python3 -m unittest discover -s deploy/tests -p test_compose_mail.py` | RED：新开关映射 1 failure，旧配置 2 项通过；实现后 3/3 GREEN | 假配置凭据，不读取真实 env |
| 2026-10-06 | backend/ | `mvn -q -Dtest=LessonReminderScheduleTest,LessonReminderApiTest test` | GREEN：同一时间/API 6 tests，exit 0 | 后续故障/并发/迁移/lifecycle 用例在基础行为 RED→GREEN 后增补，不伪称每个补充用例均先运行过 RED |
| 2026-10-06 | backend/ | `mvn -q -Dtest=LessonReminderScheduleTest,LessonReminderApiTest,LessonReminderWorkerTest test` | 19 tests，1 failure、0 errors/skips | 并发领取同一 lease 失败；显式事务入口修复后同一场景通过 |
| 2026-10-06 | backend/，MySQL 8.4 + Mailpit v1.31.3 | `mvn -q -Dtest=LessonReminderScheduleTest,LessonReminderApiTest,LessonReminderWorkerTest,LessonReminderMigrationTest,BookingMailPollerTest test` | GREEN：24 tests、0 failures/errors/skips，exit 0；包含双方独立提醒、V11→V12、确认/补建/取消并发、回滚、独立重试/lease、批量/清理与 Mailpit 双实收 | 隔离沙箱，SMTP/邮箱均为测试数据，不代表生产实收；随后再补两项发送后写回失败/双补建者测试进入全量 |
| 2026-10-06 | backend/，第一次全量 | `mvn test` | 46 类、193 tests，1 failure、0 errors/skips，Maven exit 1 | Poller 关闭后瞬时线程 isAlive 断言竞争，改有限 join 后复验；复现已有 Surefire 30 秒 JVM 收尾超时，非生产故障 |
| 2026-10-06 | backend/，最终全量复验 | `mvn test` | GREEN：46 类、193 tests，0 failures/errors/skips，BUILD SUCCESS，Maven exit 0 | 关闭线程断言修正后通过；仍复现既有 Surefire 30 秒 JVM 收尾超时及临时数据库关闭后的 Hikari 警告，TODO-0034 保持 OPEN |
| 2026-10-06 | frontend/ | `npm test`、`npm run lint`、`npm run build` | 76/76 通过，lint/build（含 tsc）exit 0 | 前端没有新增页面/行为；既有身份、约课与深链接回归 |
| 2026-10-06 | 根目录 | `python3 -m unittest discover -s deploy/tests -p 'test_*.py'` | 29/29 通过，exit 0；本地 3/3、生产开关/隔离配置包含于全量 | 不运行真实发布/恢复操作，无真实配置或秘密读取 |


目标与沙箱验收及完整后端、前端和部署配置回归实际结果见表，全部业务断言通过；既有 JVM 收尾问题单独保留。实际 Maven 为 `/private/tmp/geer-delivery-toolchain/apache-maven-3.9.16/bin/mvn`，使用 `-Dmaven.repo.local=/private/tmp/snowboard-v2-toolchain/m2`；`JAVA_HOME=/Users/geerhong/Library/Java/JavaVirtualMachines/ms-25.0.4.1-1/Contents/Home`，`DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock`，`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。日志保存在忽略目录 `.local/lesson-reminder-emails/`：`backend-red.log`、`domain-red.log`、`config-red.log`、`backend-green.log`、`backend-target.log`、`backend-target-final.log`、`backend-regression.log`、`backend-regression-final.log`、`frontend-regression.log`、`frontend-lint.log`、`frontend-build.log`、`deploy-regression.log`。最初测试编译错误单列 `backend-test-compile-fix.log`，不计有效 RED。

## 9. 完成状态与后续

- VERIFIED：2026-10-06 完成获批 revision 1 的 P-01～P-08，实现及 AC-01～10 本地验收通过；批准原话、有效 RED/GREEN、修复与限制见上述记录。
- 最终后端 193/193、前端 76/76、部署配置 29/29，lint/build 与文档检查通过；真实 MySQL 并发与隔离 Mailpit 双收件人实收通过。既有 Surefire 收尾超时仍复现、Maven exit 0，TODO-0034 保持 OPEN。
- 同步配对功能、两份索引、0007 扩展关联、backend/deploy 操作文档；TODO-0037 DONE 并归档。已有 frontend README 独立修改保留。
- 实现验收阶段未执行 Git commit/push 或生产操作。2026-10-06 用户后续明确要求“提交推送”，本次按该授权提交本功能至 main，由既有流水线执行 CI 及通过后的自动交付；远端运行结果另行核对，未提前记 RELEASED。SMTP 接收及本地 Mailpit 实收不代表生产收件箱分类。
