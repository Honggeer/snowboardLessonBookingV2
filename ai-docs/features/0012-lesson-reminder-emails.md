---
id: "0012"
title: "已确认预约课前邮件提醒"
status: VERIFIED
plan: "../implement-plan/0012-lesson-reminder-emails.md"
created: 2026-10-06
updated: 2026-10-06
contract_version: "1.6"
modules: [bookings, identity]
---

# 0012 — 已确认预约课前邮件提醒

## 1. 目标、触发与范围

- 用户请求：“可以参考V1的逻辑，就是预约提醒功能，距离上课一段时间以前发邮件提醒”。需求于 2026-10-06 明确，具体 revision 1 随后获批并完成本地实现/验证。
- 配对[计划 0012 revision 1](../implement-plan/0012-lesson-reminder-emails.md)已达到 VERIFIED。用户已确认时间与收件人，并于 2026-10-06 回复“批准”授权本具体 revision 1 的本地实现和验证；该原始批准不含 Git 操作，后续“提交推送”单独授权及状态见第 11 节。
- 用户选择“沿用 v1：每天 18:00，提前 24～48 小时”，并要求“都发，然后记得提醒24小时之内不能取消”。按多伦多时间每天 18:00 向符合窗口的已确认预约所属学员和教练分别发送中文提醒，提供课程、雪场、明确日期/时间/时区、取消截止时间及需登录的预约链接。
- 包含定时持久任务、独立投递/重试、取消失效、重启恢复、现有未来已确认预约的有限批次补建和 Mailpit 验证。预约页面与注册登录流程无需新增操作。
- 本次不包含短信、评价提醒、前端设置页、教练主动取消、改期、自动完成、邮件服务更换、生产发布或 v1 数据迁移。
- v1 只作为规则参考，保持旧仓库、数据、凭据和依赖不变。现有申请/确认通知继续遵循 [0007](0007-booking-email-notifications.md)，取消资格遵循 [0006](0006-post-login-booking-home.md)。

## 2. 验收条件

时间窗口沿用 v1 的严格边界：开课时间 `> runAt + 24h` 且 `<= runAt + 48h`；正好 24 小时不进入提醒窗口，正好 48 小时进入。

| ID | 场景/输入 | 预期行为 | 验证方式 | 证据/结果 |
|---|---|---|---|---|
| AC-01 | 存在确认时间及之后的有效 18:00 提醒窗口 | 确认同事务生成两条独立延时提醒；保留既有学员确认通知，重复确认不重复建任务 | MySQL API、确认回滚与重复确认 | 通过：LessonReminderApiTest 的确认/重复确认/任务失败回滚；193 项全量回归 |
| AC-02 | 多伦多 18:00 前/到达、窗口边界及 DST 前后 | 到期前不发送，18:00 到达后轮询投递；按 Instant 校验严格大于 24h、至多 48h，正文按预约时区显示日期和 UTC 偏移 | 固定 Clock 领域/worker、真实 MySQL | 通过：LessonReminderScheduleTest 5 项，worker 到期前/到期与 UTC 偏移断言 |
| AC-03 | 发送内容与收件人 | 学员收到本人提醒，教练收到所属预约提醒；中文主体/课程雪场时间准确，链接分别进入有权页面；正文不带电话、邮箱、认证秘密 | 邮件替身、Mailpit、既有深链接授权回归 | 通过：worker 双收件人内容/敏感信息排除，真实 Mailpit 双实收及原授权深链接回归 |
| AC-04 | 取消、未确认、已拒绝、距开课已不足或正好 24h、预约时间与任务不一致 | 不发过期提醒，未发送任务转 SKIPPED；不改变预约状态和取消门槛 | worker、取消/API、时间边界 | 通过：worker 取消/时间变化/正好 24h 拒发；既有预约取消边界回归 |
| AC-05 | 学员邮件失败，教练邮件成功 | 成功项 SENT，失败项独立重试；失败不得标 SENT，后续不因另一收件人失败重发已完成项 | MySQL 与 SMTP 故障注入 | 通过：学员失败/教练成功，独立退避后仅重发失败项 |
| AC-06 | 多个领取者、lease 到期、旧 token 写回及进程中断 | 单个有效领取令牌；到期可恢复，旧令牌不能覆盖新领取；SMTP 已接收但写回失败可能重复，明确 at-least-once | 独立事务并发、worker 故障注入 | 通过：并发领取、过期恢复/旧 token、SMTP 成功写回失败的重复投递证据 |
| AC-07 | SMTP 持续失败、重试到达提醒截止时刻 | 最多 8 次尝试，未耗尽但距开课已不超过 24h 的项 SKIPPED，最终失败 DEAD；不影响 HTTP 预约状态 | 固定 Clock、队列/worker | 通过：8 次持续失败 DEAD，正好 24h 停止；预约状态不变 |
| AC-08 | 上线前已有未来已确认预约，重复补建及任务清理 | 符合确认时间条件的旧预约分批补建；补建与确认/取消竞争不重复、不误发；SENT/SKIPPED 清理后也不重新生成 | V11→V12 迁移、MySQL 并发/清理 | 通过：V11→V12 数据兼容、53 条有限补建/清理后不重建、双补建及确认/取消竞争 |
| AC-09 | 错过最后有效 18:00 窗口才确认、提醒开关关闭或重新启用 | 不额外补提醒；暂停时不领取提醒、不误作 SKIPPED，既有两种通知仍工作；恢复后仍距开课超过 24h 的有效任务可继续 | 领域、worker、配置契约 | 通过：晚确认/DST 无候选不补造，提醒开关暂停/恢复、原通知及 Compose 映射 |
| AC-10 | 日常资源、重启和既有功能 | 有界批次/独立单线程调度与有限关闭；身份、预约、通知、架构及相关配置回归通过，无新增收费服务 | 生命周期、全量后端、配置及文档检查 | 通过：有界批次/生命周期；后端 193、前端 76、配置 29 项；既有 TODO-0034 另列 |

## 3. 业务规则与未决事项

时间、收件人和取消提示已由用户确认；以下可靠性/兼容范围随 revision 1 获用户批准：

- 正常提醒时点是 `America/Toronto` 当地每天 18:00。选取满足 `runAt >= confirmedAt` 且 `runAt + 24h < startAt <= runAt + 48h` 的最早 runAt 作为 dueAt。候选仅需枚举 `[startAt-48h,startAt-24h)` 覆盖的当地日期；不存在有效候选就不生成提醒，既有确认邮件仍通知学员。确认正好在有效 runAt 时可以立即进入到期队列。
- 每个预约/收件人最多规划一条提醒。夏令时切换可能产生相邻两个满足窗口的 18:00；取确认后的第一个有效时点，发送后不在第二个时点重复。窗口的 24/48 小时按 Instant 时长判断，18:00 按当地时区计算，不使用固定 UTC 时刻代替。
- 已计划任务在 dueAt 到达后尽快轮询，不能承诺精确到秒或收件箱实收时间。故障/停机恢复允许在 `dueAt <= now < startAt-24h` 补发；达到这个截止时刻即停止，不发课程临近或已开始的过期提醒。正常每天 18:00，故障重试/恢复可能晚于 18:00，这是本计划明确的可靠性改进。
- 只处理 CONFIRMED；不改变预约、占位、价格、雪场锁定或取消规则。学员已确认预约正好提前 24 小时仍可取消，少于 24 小时不能取消；提醒明确给出取消截止时间和页面为准说明，教练文案不暗示新增教练取消能力。
- 两位收件人独立任务、独立状态。发送前校验当前状态、收件人归属和任务保存的开课时间。取消已提交后、有效性校验前到达的任务不发送；校验后至 SMTP 发送之间仍有状态竞争窗口，不能在持有业务锁时发信，已发邮件无法撤回。
- 首次启用/恢复时，以已有 `decidedAt` 计算其第一个有效 dueAt，且当前仍距开课超过 24h 时可分批补建；dueAt 已过但未达到提醒截止时刻也可补发。确认时已错过最后有效窗口的旧预约标记规划已检查，不生成提醒；历史课程不补发，缺失有效确认时间的记录不猜造时间，留待核对。
- 本版不支持改期；防御性开课时间匹配使过期任务失效。以后改期须单独 review 新任务版本和重建语义。

| 起始状态 | 用例/操作者 | 前置条件 | 目标状态 | 失败与副作用 |
|---|---|---|---|---|
| PENDING | 确认/所属教练 | 0006 条件满足 | CONFIRMED | 同事务建既有确认通知和符合时间条件的提醒；任务写入失败回滚 |
| CONFIRMED、规划未检查 | 补建/内部任务 | 未来预约、开关开启 | 预约状态不变 | 锁定本人模块预约行检查，原子写规划标记与任务；失败可下一轮重试 |
| CONFIRMED | 取消/所属学员 | 0006 的取消条件 | CANCELLED_BY_STUDENT | 原规则释放占位，尚未发送的提醒在发送前失效 |
| PENDING 邮件任务 | 领取/内部 worker | 已到期、功能启用 | CLAIMED | 有限 lease/token，失败保留持久状态 |
| CLAIMED 提醒任务 | 投递/内部 worker | 当前预约有效且距开课严格超过 24h | SENT/PENDING/DEAD/SKIPPED | 独立成功、退避重试、最终失败或失效；预约状态不变 |

| 未决问题 | 建议/选项 | 影响范围 | 是否阻塞 | 用户决定/已授权依据 |
|---|---|---|---|---|
| 提前多久提醒 | v1 每日多伦多 18:00，窗口 `(runAt+24h,runAt+48h]` | 时点、晚确认边界、DST、正文和验收 | 否 | 2026-10-06 用户选“沿用 v1：每天 18:00，提前 24～48 小时” |
| 收件人及取消提示 | 学员和教练分别一封；正文说明距开课不足 24h 不能取消，保持既有精确边界 | 事件类型、正文和验收 | 否 | 2026-10-06 用户：“都发，然后记得提醒24小时之内不能取消” |
| 具体实施计划 | revision 1 已批准 | 实现/测试授权 | 否 | 2026-10-06 用户在具体计划后回复“批准”，授权本地实现和验证 |

## 4. 模块、端口与依赖

- bookings 拥有预约、规划标记和 `bookings_mail_task`；沿用其内部 worker，不另建 notifications 模块或跨业务数据库查询。
- 新增纯 Java `BookingReminderOperations` 入站端口，供确认用例和内部补建入口调用；`BookingReminderService` 编排，`LessonReminderSchedule` 保存纯时间规则。
- 出站沿用 `BookingStore`、`BookingMailQueue`、`BookingMailContacts`、`BookingMailSender`、`BookingLinkBase` 和 Clock；扩展预约规划/任务延时方法，不把 JDBC、SMTP、调度实现传入用例。
- 启停设置经纯 Java 配置端口/值对象注入，Environment 读取位于 adapter。身份邮箱仍只经 `AccountContactOperations` 公开内部端口读取；单向 bookings→identity，不读身份表或增加电话邮件。
- 无架构例外，六类数字基线预期仍 0；[ADR 0001](../decisions/0001-v2-baseline.md)不改。
- 适用 WORK-02/03/08/09/11、ARCH-01～09、DEP-01～07、DATA-01/03、BOOK-01～04/07、SEC-01～03、ASYNC-01～04、OPS-01/03/05。

## 5. API 与前端契约

| Method / Path | 身份与资源权限 | 输入 | 输出 | HTTP/业务错误 | 幂等行为 |
|---|---|---|---|---|---|
| POST /api/coach/bookings/{id}/confirm（既有） | 所属教练、Session 与 CSRF | 预约 ID | 既有确认结果，不承诺邮件送达 | 既有 401/403/404/409；写任务失败回滚 | 重复确认不额外建提醒 |
| GET /api/bookings/{id}（既有） | 所属学员 | 预约 ID | 当前预约 | 既有权限语义 | 只读 |
| GET /api/coach/bookings/{id}（既有） | 所属教练 | 预约 ID | 当前预约及授权电话 | 既有权限语义 | 只读 |

- 新增 HTTP API、页面、账号偏好、缓存：N/A，本版只新增后台通知，沿用已有登录恢复/高亮预约页面。
- 学员链接 `APP_PUBLIC_URL + /#/my-bookings/{id}`；教练链接 `APP_PUBLIC_URL + /#/coach-applications/{id}`。根地址来自服务端可信配置，不能使用请求 Host；仅带预约 ID，不带认证令牌。
- 手机无需额外交互；沿用 0007 已有深链接。前端行为、Cookie/CSRF/CORS 不变，测试回归核对没有越权或跨账号数据。

## 6. 数据、事务与并发

- 已追加 `V12__lesson_reminder_emails.sql`，不修改 V1～V11：扩展现有任务事件 CHECK，增加可空 `reminder_start_at_utc DATETIME(6)`；两种提醒分别为 `STUDENT_LESSON_REMINDER`、`COACH_LESSON_REMINDER`，保留 `(booking_id,event_type)` 唯一键。
- `bookings_request` 追加可空 `lesson_reminder_planned_at DATETIME(6)`，表示已检查规划（包括晚确认而无需任务）；增加有界补建索引 `(status,lesson_reminder_planned_at,start_at_utc_snapshot,id)`。不修改认证、电话、价格或时间快照。
- 规划标记独立于短期队列表，避免 SENT/SKIPPED 清理后补建重复发信。next_attempt_at 初值为 dueAt，重试时可变；任务保存原开课时间用于失效校验。所有时间显式按 UTC DATETIME/Instant 转换。
- 确认继续遵循既有 READ_COMMITTED 日→时段→学员→预约锁顺序；在同一事务写规划标记及任务，不执行 SMTP。重复确认走原返回路径。
- 补建每次最多 50 条，短事务只锁 bookings 自有未来 CONFIRMED 行，按开课时间/id 稳定顺序、SKIP LOCKED；不反向取得日/时段/学员锁。任务与规划标记同时提交，取消并发后重新检查实际状态；无需跨模块 SQL。
- 邮件领取仍独立短事务、token 条件更新，不持有预约锁进行 SMTP。真实 MySQL 测试覆盖补建与确认/取消竞争和事务失败回滚。
- 旧任务新列 NULL，历史即时通知行为不变；规划旧值 NULL，由受控后台有限补建，迁移自身不大量插入邮件任务。
- 回退需先暂停预约邮件 worker，保留 V12 和任务；旧 worker 不识别新事件会跳过它们，不能直接带着待发提醒恢复旧 worker。保留生产备份，DDL 不视为可自动逆转事务。

## 7. 异步任务与外部依赖

- 提醒任务同确认事务持久化；旧预约补建为内部用例，一轮不超过 50 条，约每分钟执行。复用现有预约邮件轮询间隔，默认每 10 秒一次，每轮最多 10 项。
- 延用原子领取/过期回收与 claim_token 写回保护；为单条 SMTP 调用保留足够 lease，并增加发送前剩余 lease 检查（提醒 120 秒 lease，历史即时任务保持原语义）。数据库故障不吞为已发送。
- 失败沿用最多 8 次、1/2/4/8/16/32/60 分钟退避；每次仍需校验距开课超过 24h 和预约状态。DEAD 保留，人工重试前再次核对有效性；SENT/SKIPPED 按既有 7 天清理，规划标记不删。
- 提供非秘密 `BOOKING_REMINDERS_ENABLED` 运行开关；关闭时不规划/领取提醒，保留已有待发提醒，既有申请/确认通知继续运行。总邮件 worker 开关用于完整暂停和版本回退；开关关闭不是成功或跳过。
- 预约邮件轮询/补建使用一个独立、最多一线程的调度资源，避免阻塞 HTTP 或身份邮件轮询；异常记录任务/类别，线程资源有有限关闭行为。既有 [TODO-0034](../todo/0034-full-backend-test-shutdown-timeout.md)仍单独跟踪，不能承诺本功能修复其根因。
- 复用当前 JavaMailSender、现有 SMTP、连接/读/写超时各 5 秒及本地 Mailpit。无新库、SDK、邮箱认证参数或付费服务；不读取 v1/生产密钥，目标测试使用假发信器或 Mailpit。
- SMTP 接受但落库失败可重复发送；SENT 只代表 SMTP 已接受，不保证收件箱分类或实收。与 [TODO-0030](../todo/0030-production-registration-mail-not-received.md)的垃圾邮件问题分开处理。

## 8. 实施计划关联与实际变更

- [x] 阅读 v1 源码/目标测试和 v2 预约、通知、迁移与架构约束，创建同 ID 方案草稿。
- [x] 用户确认每日 Toronto 18:00 / 24～48h 窗口与双方收件及取消提示，已同步到待审 revision。
- [x] 用户明确批准 revision 1，2026-10-06 原话“批准”，范围为本地实现/测试/文档。
- [x] 批准后创建目标测试并取得有效 RED，再实现到相同测试 GREEN。
- [x] 完成迁移、并发、worker/配置、既有回归和 Mailpit 验收，同步状态/索引。

| 日期 | 实际变更/文件 | 理由及与计划的差异 |
|---|---|---|
| 2026-10-06 | 本文、配对计划、TODO-0037 与索引 | 仅需求调查/文档；发送时点和收件人未确认，不写功能测试/代码 |
| 2026-10-06 | 本文与配对计划 revision 1、索引 | 用户确认沿用 v1 时间规则、双方收件与取消提示；从 DRAFT 进入 AWAITING_REVIEW，未获实现批准 |
| 2026-10-06 | V12、时间规则/规划用例/端口、持久队列、BookingService/Worker/Poller、非秘密开关和配置映射 | 按批准 revision 1 实现；领取事务入口缺陷在定向并发验收中修复，清理改为每小时固定延迟 |
| 2026-10-06 | 目标/API/worker/迁移/lifecycle 测试、原通知和历史迁移 fixture、backend/deploy 文档及索引 | 有效 RED→GREEN 后补充真实 MySQL/SMTP 验收；实际证据见配对计划第 7～8 节 |

## 9. 验证证据

| 日期 | 环境/工作目录 | 实际命令/人工步骤 | 实际结果 | 限制/待处理 |
|---|---|---|---|---|
| 2026-10-06 | 本地 v1、v2，只读 | 阅读 v1 LessonReminderScheduler、LessonReminderMapper.xml、SchedulerTest 与设计；v2 BookingService、BookingMailWorker/Queue/Poller、V8～V11 | v1 每日 Toronto 18:00、窗口 `(now+24h,now+48h]`、CONFIRMED 双收件人；发送失败仍 markSent，测试明确保留此行为；v2 已有持久队列和重试，但没有课前事件 | 只读事实，不是运行 v1 测试或新功能验收 |
| 2026-10-06 | 仓库根目录 | `python3 ai-docs/check_docs.py`、`git diff --check` | 通过：13 对记录、37 tickets、链接/索引/状态/批准门槛一致，空白检查 exit 0 | 仅文档验证，不是功能 RED/GREEN 或生产验收 |

- v1 参考路径均在独立 `snowboardLessonBookingApp`：`backend/src/main/java/com/geer/snowboard_lesson_booking/scheduler/LessonReminderScheduler.java`、`backend/src/main/resources/mapper/LessonReminderMapper.xml`、`backend/src/test/java/com/geer/snowboard_lesson_booking/scheduler/LessonReminderSchedulerTest.java`、`docs/superpowers/specs/2026-04-03-lesson-reminder-email-design.md`。源码而非描述是当前参考行为的依据；“明天”的旧邮件标题与可能后天开课不一致，不照搬。
- 有效 RED：确认提醒数 0（预期 2）的 API 1 failure、领域规则 5 failures、配置映射 1 failure；同一目标实现后 GREEN。初始测试编译故障不计 RED。
- 最终 `mvn test`：46 类、193 tests，0 failures/errors/skips，Maven exit 0 / BUILD SUCCESS。真实 MySQL 8.4 验证事务/锁/lease/补建；Mailpit v1.31.3 实际收取双方邮件，并核对正文、取消截止时间与权限链接。完整命令、环境、故障修复及日志名见[配对计划第 8 节](../implement-plan/0012-lesson-reminder-emails.md#8-实际验证证据)。
- `npm test` 76/76；`npm run lint`、`npm run build`（含 tsc）exit 0；`python3 -m unittest discover -s deploy/tests -p 'test_*.py'` 29/29、exit 0。文档和空白检查通过。
- 最终全量仍出现既有 Surefire 30 秒 JVM 收尾超时及临时库关闭后的 Hikari 警告；业务断言全部通过、进程 exit 0。继续跟踪 [TODO-0034](../todo/0034-full-backend-test-shutdown-timeout.md)，未将其根因记为已修复。
- AC-01～10 本地验收已完成。未进行真实互联网发信、生产迁移或发布；SMTP 接收和沙箱实收不能保证 Gmail 收件箱分类。

## 10. 部署、成本与恢复

- 沿用现有 EC2/MySQL/SMTP，不新建云资源或密钥；新增少量列/索引、每个符合条件预约最多两条提醒和有限线程。双收件人会增加两次初始 SMTP 投递及失败重试，不宣称现有配额或总月预算已得到重新验证。
- 新非秘密提醒开关及总 worker 开关已补充配置示例和 Compose 参数映射，默认开启提醒；不改现有 SMTP 凭据或真实 secret 文件。
- 发布需另获授权、先备份，再应用 V12并验证健康和目标提醒。回退先暂停预约邮件 worker、恢复旧镜像并保留 V12；新版恢复后可继续有效任务。
- 本修订先获本地实现/验证授权，随后用户另行要求“提交推送”，授权既有 CI/CD 自动交付；没有额外付费资源或手工生产操作，发布验证前不记 RELEASED。

## 11. 交付状态与后续

- VERIFIED：2026-10-06 按用户批准 revision 1 完成实现及 AC-01～10 的本地验收，记录有效 RED/GREEN、MySQL 并发、Mailpit 与适用回归。
- [TODO-0037](../todo/0037-lesson-reminder-emails.md)结案并移入归档，两份索引已同步。既有 TODO-0034 保持 OPEN。
- 本地重启后端时由 Flyway 应用 V12；启用后有限补建符合规则的未来已确认预约。实现验收阶段未提交推送；随后用户于 2026-10-06 明确要求“提交推送”，单独授权本功能提交至 main 及既有 CI/CD 自动交付，远端结果另行核对，未提前记 RELEASED。已有 `frontend/README.md` 的独立修改保留在工作区，不纳入本功能提交。
