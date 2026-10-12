---
id: "0013"
title: "课后总结、练习作业与邮件通知"
status: VERIFIED
plan: "../implement-plan/0013-lesson-summaries.md"
created: 2026-10-12
updated: 2026-10-12
contract_version: "1.6"
modules: [bookings, identity]
---

# 0013 — 课后总结、练习作业与邮件通知

## 1. 目标、触发与范围

- 最初用户需求（2026-10-12）：“每次课程结束后给教练发邮件提醒写课程总结”，总结分为“总结本节课练习的东西，需要加强什么”和“布置练习作业”；学生从课程历史中专门的“已完成课程”标签查看，写完后邮件通知查看。
- 当前内容结构（2026-10-12 review 修改）：用户要求“我觉得两块改成三块吧，本节练习内容，待加强和课后强化练习部分。”；改为“本节练习内容”“待加强”“课后强化练习”三个独立输入和展示区。
- 已确认触发：“按排课结束时间自动发一封，已取消或拒绝的课程不发（推荐）”。已确认历史范围：“可以补写总结，但不补发旧课程的教练提醒（推荐）”。这两项回答为业务澄清；完整 revision 2 的随后批准另有记录。
- 配对[实施计划 0013 revision 2](../implement-plan/0013-lesson-summaries.md)于 2026-10-12 获用户明确“批准”；以下交互、数据与可靠性规则均在批准范围内。
- 包含：教练课后提醒、三块文字总结、草稿及发布、教练补写/修改入口、学生“已完成课程”分栏、发布通知、授权深链接和可靠投递。
- 课程结束按现有预约的结束时间快照判断；保留预约的 CONFIRMED 状态，新增课程历史查询，不引入预约 COMPLETED 状态或改变占位、雪场锁定、取消资格。这里的“已完成”是排课时间已结束，不是出勤核验。
- 不包含：考勤/缺席认定、反复催写、图片/视频附件、学生回复或提交作业、评分、总结版本历史、新邮件供应商、v1 数据导入。Git 操作和生产交付不在本次本地实现授权中。

## 2. 验收条件

| ID | 场景/输入 | 预期行为 | 验证方式 | 证据/结果 |
|---|---|---|---|---|
| AC-01 | CONFIRMED 课程结束前、正好结束、结束后 | 不提前提醒；到期后教练收到一次业务提醒，包含学员/课程/雪场/准确时间及写总结链接；仅已结束课程可写总结 | 固定 Clock、MySQL、worker、Mailpit | 通过：SummaryWorker 固定 Clock/实收 Mailpit、API 资格 |
| AC-02 | PENDING、REJECTED、CANCELLED_BY_STUDENT 或结束时间不匹配的任务 | 不进入已完成课程，不允许写总结、不发送课后提醒；取消不改变原课前规则 | 领域/API/worker 回归 | 通过：SummaryApi/Domain/Worker 状态与结束时间校验 |
| AC-03 | 首次启用前已结束、已有未来预约、停机或暂停期间结束 | 旧课可补写、无教练提醒；既有未来预约可规划；首次启用后到期的未发布课程可恢复提醒，重复规划不重复建任务 | V13 升级、规划/配置/MySQL | 通过：SummaryMigration + Worker 历史/补建/暂停恢复 |
| AC-04 | 教练保存草稿及再次进入 | 三块内容可分别为空，保存后可继续编辑；学生无法读取草稿、不发学生通知；显示“草稿” | API、前端交互、权限 | 通过：API 草稿私有；DOM + 实际浏览器刷新恢复 |
| AC-05 | 教练首次发布 | 三块分别填写且均非空，任一块缺失均不发布；总结与学生通知任务同事务提交；学生立即可读，收到查看通知；旧课补写首次发布也通知学生 | API、事务故障、Mailpit | 通过：API 三块必填/回滚，Worker/Mailpit 实收，浏览器发布 |
| AC-06 | 重复点击、请求超时重试、双窗口或规划/发布竞争 | 相同结果不重复发布/建任务；不同内容的过期版本返回 409，不覆盖他人更新；通知入队失败回滚发布 | 独立事务并发/MySQL/前端 | 通过：API 并发保存/发布/通知失败回滚；Worker 发布竞争；DOM 409 |
| AC-07 | 发布后修改三块内容 | 保存后学生看到最新内容；保留首次发布时间，不重新发发布通知、不重新产生教练催写提醒 | API/worker/前端 | 通过：Domain 首次时间；API 最新内容/单通知；DOM 保存修改 |
| AC-08 | 学生查看“已完成课程”及加载更多 | 服务端过滤本人 CONFIRMED 且 endAt <= now 的课程，按结束时间倒序；含尚未发布总结的课程，不只过滤已加载的预约页；草稿仅显示“总结待教练发布” | 多页 MySQL/前端 | 通过：API 服务端本人多页筛选；DOM 分页失败恢复/待发布 |
| AC-09 | 未登录、错误角色、其他学员/教练、缺 CSRF | 用例及 HTTP 均执行角色和归属验证；401/403/404 语义正确，学生不能写总结或读取草稿；GET 返回 no-store | API、纯用例授权、登录深链接 | 通过：API + 直接用例、CSRF/no-store；DOM/浏览器登录链接与换账号 |
| AC-10 | SMTP 失败、过期 lease、旧领取令牌、课前提醒开关关闭 | 两类新邮件独立持久重试，最多 8 次后 DEAD，失败不标 SENT；课前提醒开关不暂停总结邮件；已发布后未发送的教练提醒 SKIPPED | worker、真实 MySQL/配置 | 通过：Worker 独立开关/8 次 DEAD/lease 及模板；配置回归 |
| AC-11 | 重启、并发补建、SENT/SKIPPED 清理后再次扫描/发布 | 永久规划标记和首次发布时间阻止重新建任务；允许 SMTP 已接受但写回失败时的 at-least-once 重复，不宣称零重复 | worker/规划/清理/MySQL | 通过：Worker 有界补建/清理标记；API 清理后重放；共享投递写回故障回归 |
| AC-12 | V13 数据升级及新库初始化 | V1–V13 不改；旧预约/五类邮件/课前窗口/唯一键保留；新列与约束正确，旧课不会补发提醒 | MySQL 8.4 迁移和既有回归/架构 | 通过：V13→V14/新库，旧五类任务/约束/索引；架构基线维持 0 |
| AC-13 | 手机/桌面、空列表、读取/保存失败、会话过期及邮件链接 | 三个内容区清楚；空/加载/失败/重试独立；写失败保留输入；登录后进入目标课程；换账号或退出清理私有数据 | DOM/构建/lint、390px/1440px 浏览器 | 通过：最终 DOM 99 tests/lint/build；390px/1440px 真实 API 浏览器 |

## 3. 业务规则与未决事项

- 资格：服务端 Clock 判断 `status == CONFIRMED && endAt <= now`；时间使用 UTC Instant，展示使用预约自己的 zoneId 和课程快照，不依赖浏览器时钟、当天日期或当前课程模板。
- 每笔预约最多一份总结，分为 `practiceContent`（“本节练习内容”）、`improvementAreas`（“待加强”）和 `reinforcementExercises`（“课后强化练习”）。三个独立字段分别填写本节练过什么、需要改进什么、课后如何强化；均为纯文本，各最多 3000 个 Unicode 字符，保留内部换行，保存时去首尾空白，前后端一致计数。
- 已批准交互：首次发布前可保存草稿，允许暂缺任一块；点击“发布总结并通知学员”时三块必须非空。发布前只有所属教练可见；发布后所属学生只读。写入成功的提示为“已发布，已安排邮件通知”，不声称已送达。
- 已批准修改规则：发布后可“保存修改”，三块仍必填，学生读到最新版本，不再发通知；不撤回、不删除、不重新变草稿、不维护版本历史。首次发布时间保持不变。
- 课后只规划一条教练提醒，不按天反复催写。发送前总结已经发布则跳过；仅有草稿仍提醒。未发表的到期任务在故障恢复后可继续投递；无新增提醒截止窗口。发送前检查与 SMTP 间仍可能发生发布，已发邮件不能撤回。
- 历史分界为本功能迁移首次执行时保存的检查标记：当时已结束的预约标记已检查，不生成课后提醒；总结发布通知不受此历史分界限制。首次启用后结束的已有未来预约纳入规划。旧课不导入示例总结。
- 既有接口允许的异常迟确认不被本功能新增规则改变；若确认时课程已结束，标记课后规划已检查，不补造提醒，可补写总结。这一边界随 revision 2 获批，提醒关闭时也保存迟确认检查标记。

| 起始状态 | 用例/操作者 | 前置条件 | 目标状态 | 失败与副作用 |
|---|---|---|---|---|
| 无总结 | 保存草稿/所属教练 | 课程符合结束资格 | DRAFT | 版本递增，不产生学生通知 |
| 无总结或 DRAFT | 发布/所属教练 | 资格满足、三块完整、版本有效 | PUBLISHED | 同事务建一次学生通知；失败全部回滚 |
| PUBLISHED | 保存修改/所属教练 | 三块完整、版本有效 | PUBLISHED | 更新内容/更新时间，不变首次发布时间、不再通知 |
| 任意总结状态 | 学生读取 | 仅本人课程且已发布 | 原状态 | 草稿/无总结返回 404，不泄露草稿内容或存在性 |
| CONFIRMED 预约 | 时间到达/系统查询 | endAt <= now | 预约仍 CONFIRMED | 进入已完成查询，不释放时段、不改变取消规则 |

| 未决问题 | 建议/选项 | 影响范围 | 是否阻塞 | 用户决定/已授权依据 |
|---|---|---|---|---|
| 何时提醒 | 按排课结束时间自动提醒 | 时间规则、页面资格及后台 | 否 | 2026-10-12 用户选择“按排课结束时间自动发一封，已取消或拒绝的课程不发（推荐）” |
| 旧课是否补提醒 | 旧课可补写，不补教练提醒 | 迁移及历史范围 | 否 | 2026-10-12 用户选择“可以补写总结，但不补发旧课程的教练提醒（推荐）” |
| 总结内容结构 | 本节练习内容、待加强、课后强化练习三个独立字段 | 表单/学生展示/API/schema/验收 | 否 | 2026-10-12 用户明确要求由两块改为三块；不等于批准完整计划 |
| 具体 revision 2 | 草稿/发布、发布后修改不再次通知、三块字数限制及以下技术方案 | 实现及验收 | 否 | 2026-10-12 用户“批准” revision 2；本地实现与验收已授权 |

## 4. 模块、端口与依赖

- bookings 拥有 `bookings_request`、`bookings_lesson_summary` 和既有 `bookings_mail_task`。总结直接关联预约快照，不建立新业务模块或查询 catalog/scheduling 私表。
- 新纯 Java 入站端口 `LessonSummaryOperations`（本人已完成课查询、教练查询、草稿/发布/修改、学生读取）；`LessonSummaryReminderOperations`（确认规划及有限补建）。domain 保存结束资格、文本/状态/版本规则。
- 出站为 `LessonSummaryStore`、扩展 `BookingStore`/`BookingMailQueue`；沿用 Clock、`BookingMailContacts`、`BookingMailSender`、`BookingLinkBase`。配置读取在 adapter，纯设置端口传给用例。
- 身份邮箱仍通过现有 `BookingMailContactAdapter` 调 identity 已发布内部端口；单向 bookings→identity，不直接读取身份表。HTTP DTO、用例结果和数据库记录分别映射。
- 适用 WORK-02/03/06/08/09、ARCH-01～09、DEP-01～07、DATA-01/03、BOOK-01/03/04/07、SEC-01～03、API-01～04、FE-01、ASYNC-01～04、OPS-03/05/06；无架构例外，不改变 [ADR 0001](../decisions/0001-v2-baseline.md)。

## 5. API 与前端契约

| Method / Path（提案） | 身份与资源权限 | 输入 | 输出 | HTTP/业务错误 | 幂等行为 |
|---|---|---|---|---|---|
| GET /api/bookings/completed | 学生，仅本人 | limit/cursor | `Page<CompletedLesson>`；仅结束的已确认课程，summaryAvailable，不返回草稿 | 400/401/403 | 只读 |
| GET /api/coach/bookings/completed | 教练，仅所属课程 | limit/cursor | `Page<CoachCompletedLesson>`；含学生名和 NOT_STARTED/DRAFT/PUBLISHED 状态，不含全文 | 400/401/403 | 只读 |
| GET /api/coach/bookings/{id}/summary | 所属教练 | 预约 ID | 三块内容、state、version、updatedAt、publishedAt；无总结返回 NOT_STARTED/version 0/空内容 | 401/403/404/409 未结束 | 只读 |
| PATCH /api/coach/bookings/{id}/summary | 所属教练、CSRF | practiceContent、improvementAreas、reinforcementExercises、expectedVersion | 首次发布前保存 DRAFT；发布后更新 PUBLISHED；200 返回最新总结 | 400 输入/401/403/404/409 资格或版本 | 相同规范化内容返回当前结果，不重复写入 |
| POST /api/coach/bookings/{id}/summary/publish | 所属教练、CSRF | practiceContent、improvementAreas、reinforcementExercises、expectedVersion | 原子保存三块、首次发布和通知任务，200 | 400 必填/401/403/404/409 资格或版本 | 已发布且内容相同返回当前结果，不重建任务；不同内容须走 PATCH |
| GET /api/bookings/{id}/summary | 学生，仅本人已发布总结 | 预约 ID | 三块、updatedAt、publishedAt，不含草稿/内部任务 | 401/403/404 | 只读 |

- 沿用直接 JSON 和 `application/problem+json` 错误，不引入 envelope。正文示例：`{"practiceContent":"本节练习前刃换刃。","improvementAreas":"加强换刃时的重心移动。","reinforcementExercises":"下次上雪时复习换刃，重点体会重心移动。","expectedVersion":0}`。请求不接受学生/教练 ID 作为授权依据。
- 完成课分页默认 20、范围 1–50，服务端按 `(endAt DESC,id DESC)` 查询并核对本人游标；所有页均执行结束资格过滤。既有 mine/coach 列表及状态字段不变。
- 学生“我的预约”内增加“全部预约 / 已完成课程”标签；完成课显示日期、课程、雪场和“查看总结”或“总结待教练发布”。教练增加“课程总结”导航，列表按结束时间倒序，状态为“待填写 / 草稿 / 已发布”，点击进入同一三块表单。
- 学生链接 `/#/completed-lessons/{id}`，教练链接 `/#/coach-lesson-summaries/{id}`，来自可信 APP_PUBLIC_URL；保留现有两类预约链接。未登录先登录，之后直达有权目标；错误角色和无权资源明确提示。
- 字段错误贴近表单；保存/发布期间禁止重复提交，失败保留输入；409 提示先读取最新版本，不自动覆盖。未保存离开提示；空、加载、刷新、分页失败及重试有独立反馈。纯文本渲染并保留换行，不解释 HTML。
- GET 设置 `Cache-Control: no-store`，浏览器状态按账号/角色/预约 ID 隔离，登出、401 或换账号清理表单/列表；过期异步返回不得写入新账号。草稿存服务端，不存 localStorage。写请求保持 Session + CSRF，同源 CORS 不变。

## 6. 数据、事务与并发

- 追加 `V14__lesson_summaries.sql`，不改 V1–V13。bookings 新表 `bookings_lesson_summary`：booking_id 主键/预约外键、state、practice_content、improvement_areas、reinforcement_exercises（各 VARCHAR(3000)，utf8mb4）、version、updated_at、published_at。CHECK 限定状态/长度、PUBLISHED 时三块非空且 published_at 非空、DRAFT 时 published_at 为空；updated/published 为 UTC DATETIME(6)。NOT_STARTED 表示无行。
- `bookings_request` 增加可空 `lesson_summary_reminder_planned_at`，含义是已检查，不是已送达；迁移用同一 UTC 分界给当时已结束的记录设置标记，跳过历史提醒。此步骤只标记旧记录，不生成总结或邮件。上线前需评估实际行数/执行时间，不把 DDL/历史标记操作视为可事务逆转。
- 增加规划 `(status,lesson_summary_reminder_planned_at,end_at_utc_snapshot,id)` 和学生/教练结束课索引 `(student_id,status,end_at_utc_snapshot,id)` / `(coach_id,status,end_at_utc_snapshot,id)`；后两项支撑有限分页，不在前端拉取全量再筛选。
- 队列扩展事件 `COACH_SUMMARY_REMINDER` / `STUDENT_SUMMARY_PUBLISHED`，增加可空 `summary_end_at_utc` 供课后提醒校验；保留既有五类事件和 `(booking_id,event_type)` 唯一键。两种课前事件仍必须有 reminder_start_at_utc；普通通知及新事件该列必须为空；仅课后提醒的 summary_end_at_utc 非空。
- 所有写用例 READ_COMMITTED，先核角色，再锁稳定预约行复核归属/CONFIRMED/endAt，再读取或写总结行/规划标记，最后写邮件任务。双窗口用 expectedVersion 防止覆盖；相同已持久结果可安全重放。首次 publish 同时标记课后规划已检查，阻止发布后的补建。
- 首次确认沿用日→时段→学生→预约锁顺序，同事务规划 endAt 延时提醒和永久标记；重复确认不再生成。补建最多 50 行，以 endAt/id 顺序 FOR UPDATE SKIP LOCKED，仅锁 bookings 预约行，不反向锁日/时段/学生；标记与任务一起提交。总结与补建都先预约后总结/任务，避免锁顺序反转。
- 人工写入用现有 BookingLockRetry 在失败事务结束后对 MySQL 1205/1213 最多重试两次，耗尽 503；其他业务冲突不盲重试。补建失败由下一轮恢复。通知任务写入失败回滚相应发布/确认/规划。
- 首次发布时间持久保留，队列清理后相同 publish 不重建学生通知；课后规划标记也不随队列清理。旧应用回退保留 V14/总结/标记，先暂停全部预约邮件 worker，避免旧 worker 将新事件标 SKIPPED。

## 7. 异步任务与外部依赖

- 课后提醒在确认事务内规划，dueAt 为 endAt；既有未来预约和暂停后恢复通过每分钟最多 50 条的补建处理。只针对尚未检查且未发布的符合范围预约；标记与任务原子提交。既有历史标记不清除。
- 复用独立单线程 BookingMailPoller：每轮最多 10 项、默认 10 秒间隔；增加总结规划入口，不增加线程池。新增 `LESSON_SUMMARY_REMINDERS_ENABLED` 非秘密开关，关闭时暂停课后规划及领取，保留任务；不影响学生发布通知或原课前提醒。原 BOOKING_REMINDERS_ENABLED 仍只控制两类课前邮件；总 worker 开关仍可暂停全部预约邮件。
- 发送前检查预约状态、收件归属、课程结束快照及总结状态；教练提醒要求已到 endAt 且未发布，学生通知要求仍为本人已发布总结。新事件不套用课前提醒的“距开课超过 24h”失效逻辑。发送前再核有效性与 lease，SMTP 不在业务锁事务内。
- 课后提醒 lease 120 秒，发布通知沿用普通通知 30 秒；保持 claim_token 条件写回、过期回收和并发原子领取。最多 8 次，沿用 1/2/4/8/16/32/60 分钟退避，持续失败 DEAD；SENT/SKIPPED 沿用清理，DEAD 保留人工核对，不自动复活。
- 教练主题“GEER 课后提醒：请填写课程总结”，学生主题“GEER 课程总结已发布”；正文包含课程/雪场/预约时区日期与 UTC 偏移和授权链接。教练信包含所属学生名；不把总结全文、电话、邮箱、密码或登录令牌放进邮件。
- 沿用现有 SMTP/JavaMailSender 和各 5 秒连接/读/写超时。只有本地 Mailpit/替身验收，不向真实学生发测试邮件。SMTP 接收但 SENT 落库前中断可重复发送；SENT 不代表收件箱实收。

## 8. 实施计划关联与实际变更

- [x] 只读调查预约、时间快照、分页、邮件队列、前端入口与根/目录规则。
- [x] 确认结束时点与历史范围，创建配对 revision 1 和索引。
- [x] 按用户 review 意见将总结拆为三块，更新为 revision 2 供 review。
- [x] 用户明确批准 revision 2，2026-10-12 原话“批准”，范围见配对计划。
- [x] 批准后先建立可运行的目标测试并取得有效行为 RED。
- [x] 实现、同一用例 GREEN、真实 MySQL/权限/邮件/前端/架构回归。
- [x] 更新实际证据、状态与索引；既有 TODO-0034 收尾超时复现已单独记录，无新增独立 ticket。

| 日期 | 实际变更/文件 | 理由及与计划的差异 |
|---|---|---|
| 2026-10-12 | 本文、配对计划、两份索引（revision 1） | 新功能需求分析与具体设计；尚无本功能测试、实现、迁移或发送邮件 |
| 2026-10-12 | 本文、配对计划、计划索引（revision 2） | 用户要求由两块拆为三个独立内容区，已同步字段、发布必填、学生展示、schema、验收及成本；仍等待完整计划批准 |
| 2026-10-12 | 后端领域/端口/服务、JDBC/V14/Web、预约确认/worker/poller、新配置与两类邮件；前端 LessonSummaries/样式及 App/BookingHome 导航 | revision 2 按批准实现；独立开关关闭时迟确认仍持久标记，防止恢复后补提醒；请求 helper 与提醒集成测试按实际文件组织，业务/数据/架构范围不变 |

## 9. 验证证据

| 日期 | 环境/工作目录 | 实际命令/人工步骤 | 实际结果 | 限制/待处理 |
|---|---|---|---|---|
| 2026-10-12 | 本地仓库，只读 | 阅读 BookingStatus、BookingService/Store、BookingMailQueue/Worker/Poller、V8/V12/V13、BookingHome/App、配对 0006/0007/0012 记录 | 现有四种预约状态、无总结/完成课接口；邮件持久队列与时间快照可复用；前端只有我的预约及申请页 | 设计依据，不是新功能验收 |
| 2026-10-12 | 仓库根目录 | `python3 ai-docs/check_docs.py`；`git diff --check` | 通过：14 对功能/计划、40 张 ticket、链接/索引/状态及 review 门槛一致；空白检查 exit 0 | 仅文档检查，不代替功能 RED/GREEN |
| 2026-10-12 | Java 25 / MySQL 8.4 Testcontainers / Mailpit v1.31.3 | HTTP 3 及 DOM 2 行为 RED→同目标 GREEN；目标/API/领域/worker/升级测试；`./mvnw -q clean test`（Colima 环境） | 全量 51 类 / 226 tests，0 failures/errors/skips，Maven exit 0；架构检查通过，邮件实际进入隔离邮箱 | 复现已知 TODO-0034 Surefire 30 秒收尾超时，未修复根因；全部报告为本轮 clean 后生成 |
| 2026-10-12 | frontend/deploy | `npm test`、`npm run lint`、`npm run build`；`python3 -m unittest discover -s deploy/tests -p 'test_*.py'` | 前端 99 tests、部署配置 29 tests、lint/typecheck/Vite build 通过 | 完整过程/原始失败与修正记录见配对计划第 8 节 |
| 2026-10-12 | 本地真实 Chrome / 实际 API / 独立 MySQL/Mailpit | 390px 与 1440px：草稿保存刷新恢复、发布、退出/学生邮件链接登录、三块只读及完成课列表 | 两尺寸通过，0 溢出/0 脚本错误；Mailpit 共实收 2 教练提醒 + 2 学生通知 | 运行产物在忽略目录 `.local/lesson-summaries`；沙箱服务/容器已清理，没有访问真实用户数据或发送真实邮件 |

- 测试先行、MySQL 并发、架构/权限、Mailpit、浏览器及最终全量均实际执行，完整命令、日志与限制见配对计划第 8 节。

## 10. 部署、成本与恢复

- 沿用 EC2/MySQL/现有邮箱，不引入库、云服务、秘密或额外线程；新存储为三块有限文本、标记/索引及每课最多两条新邮件任务，每条最多 8 次投递尝试。邮件增量计入现有配额，不承诺月预算不变或无限容量。
- 新非秘密开关补充 application.properties、两份 Compose 和示例 env；不修改真实 env。生产升级需要既有备份、评估历史标记更新量、迁移验证和独立授权。
- 旧版不认识新邮件事件；回退先暂停全部预约邮件 worker，保留 V14/新数据，恢复支持本功能的版本后再处理积压。不开 DOWN 迁移、不删除总结，数据库恢复按已授权备份方案执行。
- 2026-10-12 用户在本地验收完成后明确要求“提交推送部署”，现已授权本功能 Git 和生产交付及 V14 迁移，正在执行既有发布流程。技术自动发布和真实收件业务验收分别记录。

## 11. 交付状态与后续

- 当前 **VERIFIED**：2026-10-12 用户“批准”具体 revision 2，已本地实现并完成验收；尚未 RELEASED。批准与证据见配对计划。
- 草稿/发布、发布后修改不重复通知、三块字数上限及实现方案已随 revision 2 批准；业务及三块结构的原话见第 1/3 节。
- 已执行目标测试 RED→实现 GREEN 及全量回归；保留用户独立的 frontend/README.md 修改，不将旧“提交推送”请求视为本功能 Git 授权。
