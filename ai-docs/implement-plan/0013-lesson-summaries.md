---
id: "0013"
title: "课后总结、练习作业与邮件通知"
status: VERIFIED
revision: 2
approved_revision: 2
created: 2026-10-12
updated: 2026-10-12
feature: "../features/0013-lesson-summaries.md"
---

# 0013 — 课后总结、练习作业与邮件通知实施计划（revision 2）

## 1. Review 摘要

用户希望课程结束后提醒教练写总结，学生在“已完成课程”查看，写完后邮件通知。用户本次 review 要求由两块拆为三个独立内容区：“本节练习内容”“待加强”“课后强化练习”。2026-10-12 已确认：**按排课结束时间自动提醒一次，取消/拒绝不发；旧课可以补写，但不补发教练提醒**。用户随后于 2026-10-12 回复“批准”，授权本 revision 2 的完整本地实现。

本次已批准的完整流程：

1. 已确认课程到达预约结束时间后，教练收到“填写课程总结”的邮件，链接直达该课程；每课只规划一次，不反复催写。已有未来预约也覆盖。
2. 教练新增“课程总结”入口，按结束时间倒序列出课程及“待填写 / 草稿 / 已发布”；表单三块为“本节练习内容”“待加强”“课后强化练习”，各最多 3000 个 Unicode 字符。
3. **允许保存草稿；点击“发布总结并通知学员”时三块必填。** 草稿只有教练可见；首次发布与学生通知任务原子提交。发布后的修改立即更新学生所见内容，**不重复发邮件**，不提供撤回/删除/版本历史。
4. 学生“我的预约”增加“全部预约 / 已完成课程”标签。后者由服务端分页查询本人已确认且时间已结束的课程，即使还没写总结也列出并提示待发布；发布后可查看三块内容。
5. 旧课允许保存/发布总结，首次发布照常通知学生；首次启用前已结束课程不补教练提醒。启用后结束的课程发生停机/暂停，可在恢复后继续一次提醒；发送前已发布则跳过。

“已完成”采用服务端结束时间筛选，预约状态仍为 CONFIRMED，不引入 COMPLETED、不改原预约确认/拒绝/取消、时段占用、雪场锁定或课前提醒。它不证明实际出勤；考勤/缺席、附件、学生交作业、评价、新邮件服务不在本版。

关联[功能契约及 AC-01～13](../features/0013-lesson-summaries.md)、[0006 预约](0006-post-login-booking-home.md)、[0007 现有通知](0007-booking-email-notifications.md)、[0012 课前提醒](0012-lesson-reminder-emails.md)及 [ADR 0001](../decisions/0001-v2-baseline.md)。无新增付费资源、依赖升级或架构例外。具体 revision 2 已批准；遵守 WORK-02/09，先完成可运行 HTTP/DOM 的行为 RED，再实施。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-10-12 | 用户在收到三块总结的 0013 revision 2 后回复：“批准” | 2 | 授权本修订本地实现、隔离测试和文档维护；不含 Git commit/push、生产部署/迁移、新付费资源或真实外部测试邮件 |
| 2026-10-12 | 用户在本地实现/验证完成后明确要求：“提交推送部署” | 2（交付授权） | 授权本功能 Git commit/push、既有生产流水线部署及必需的 V14 迁移/备份/技术检查；保留用户独立 frontend/README.md 修改，不新增付费资源，不用真实用户数据发送测试邮件 |

业务澄清记录（不是实现批准）：

- 2026-10-12 用户最初提出两块课后总结、教练提醒、学生“已完成课程”标签及发布后邮件通知；三块结构的后续修改另列如下。
- 同日用户选择：“按排课结束时间自动发一封，已取消或拒绝的课程不发（推荐）”。
- 同日用户选择：“可以补写总结，但不补发旧课程的教练提醒（推荐）”。
- 同日用户 review 修改：“我觉得两块改成三块吧，本节练习内容，待加强和课后强化练习部分。”；对应本 revision 2，结构选择不等于批准完整实现计划。

当前 `approved_revision: 2`；2026-10-12 用户“批准”授权本 revision 2，批准后进入 IMPLEMENTING，本轮完成验收后标记 VERIFIED。此前其他功能的“提交推送”不作为本次授权；本次以用户最新“提交推送部署”为交付依据。

## 3. 实现步骤与预计文件

生产 Java 路径前缀为 `backend/src/main/java/com/geer/snowboard/v2/`，测试前缀为 `backend/src/test/java/com/geer/snowboard/v2/`；以下记录批准范围；实际文件及验证列于第 7～8 节。

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-00 | 本配对文档、功能/计划索引 | 读取规则/相关设计/源码/Git；记录两项业务回答，整理可 review 的具体方案 | 文档链接/状态/索引检查通过，用户可审阅 | 完成，revision 2 已批准 |
| P-01 | 新 `bookings/LessonSummaryApiTest.java`、`frontend/src/LessonSummaries.test.tsx` | 批准后先用现有可编译测试基础建立完成课查询、教练写/发布、学生读取、草稿隔离及 UI 标签/发布通知语义；补固定 Clock 边界场景 | 旧实现因没有完成课/总结行为产生有效断言 RED；记录原命令/失败原因 | 完成 |
| P-02 | `bookings/domain/LessonSummary.java`；新 `application/port/in/LessonSummaryOperations.java`、`LessonSummaryReminderOperations.java`、`port/out/LessonSummaryStore.java`/设置端口；对应 service | 纯 Java 资格/三个独立文本字段/状态/版本；应用用例验证 actor/归属，编排保存草稿、首次发布、修改、已完成查询和课后规划 | 核心规则/用例授权 GREEN；framework/SQL/SMTP 不进入 domain/port | 完成 |
| P-03 | 新 `backend/src/main/resources/db/migration/V14__lesson_summaries.sql`；`BookingStore/JdbcBookingStore`；新 `JdbcLessonSummaryStore`；`BookingMailQueue/JdbcBookingMailQueue` | 新总结表、永久规划标记/历史检查、完成课/规划索引、两类事件和 endAt 快照列；保留旧事件约束及唯一键。数据库记录与用例结果映射，所有 SQL 留在 adapter | V13→V14/新库、旧数据保留、边界约束及分页 MySQL 验收 | 完成 |
| P-04 | `BookingService`；新 `LessonSummaryService`、`LessonSummaryReminderService` | 首次确认同事务规划 endAt 任务；每分钟最多 50 条补建；保存/发布先锁预约，再总结、任务；乐观版本、同内容重放、首次 publish 原子入队并标记规划已检查 | 双窗口/重复/回滚、确认/取消/补建/发布竞争和任务清理后不重建通过 | 完成 |
| P-05 | 新 `bookings/adapter/in/web/LessonSummaryController.java`、请求/响应 DTO；必要 `bootstrap/SecurityConfig.java` 接口配置 | 增加两类完成课列表、教练 GET/PATCH/publish、学生 GET；显式 DTO 映射、actor 和 CSRF，no-store/ProblemDetail，写用例沿用 BookingLockRetry | 400/401/403/404/409/503 正确，直接用例调用亦无越权，既有路由不受影响 | 完成 |
| P-06 | `BookingMailWorker`、`BookingMailPoller`、队列/任务记录、总结配置 adapter；`application.properties`、两份 Compose/两份示例 env | 新模板/角色链接/发送前资格与 lease 校验；复用单线程调度，独立课后开关，不混用课前 24h 截止逻辑；两类新事件参与正确领取/过期回收白名单 | 到期不提前、发布后跳过、8 次 DEAD、独立开关/重试/恢复通过 | 完成 |
| P-07 | `frontend/src/BookingHome.tsx`、`App.tsx`；新 `LessonSummaries.tsx`、`lessonSummaryApi.ts`、相应 CSS | 完成课标签、教练入口、三块编辑/草稿/发布/修改、授权深链接。新组件承载总结，不把全部新表单继续堆进现有 BookingHome；只复用现有 React/CSS | 先前 DOM RED→GREEN，加载/空/错误/409/重复提交/跨账号及旧链接回归 | 完成 |
| P-08 | 新 `LessonSummaryDomainTest`、`LessonSummaryReminderTest`、`LessonSummaryWorkerTest`、`LessonSummaryMigrationTest`；既有 API/worker/poller、Foundation/Legacy 迁移最新版本断言；配置测试 | 真实 MySQL 独立事务并发、受控 Clock/DST、故障注入、Mailpit 实收；历史迁移目标保留，只有确实断言最新版本的测试改 14 | AC-01～13、适用前后端/架构/配置回归均实际通过 | 完成 |
| P-09 | 本配对文档、两份索引、backend/README.md、deploy/README.md；必要相关功能关联 | 补实际文件、RED/GREEN、命令、限制/恢复；发现独立问题按 ticket 规则记录，现有 TODO-0034 不重复建票 | IMPLEMENTED/VERIFIED/RELEASED 分开，报告证据及未解决问题 | 完成 |

- [x] 完成只读调查、两项业务澄清、配对设计和具体计划。
- [x] 完成文档校验，revision 2 已完成 review。
- [x] 用户明确批准 revision 2，2026-10-12 原话“批准”，范围已记录。
- [x] 批准后取得有效行为 RED，再实现同一用例 GREEN。
- [x] 全部必要验收/回归及实际证据完成，更新状态和索引。

## 4. 数据、API、架构与兼容影响

- bookings 自有总结，无新模块或跨模块私表 SQL。纯入站用例及出站端口将 Web、数据库和 SMTP 隔开；身份邮箱沿用 bookings→identity 的公开用例适配器。API DTO/用例对象/持久记录分开。
- 三个 API/存储字段为 practiceContent/practice_content（本节练习内容）、improvementAreas/improvement_areas（待加强）、reinforcementExercises/reinforcement_exercises（课后强化练习）；每块最多 3000 个 Unicode 字符。草稿允许分别留空，发布及发布后修改要求三块均非空。V14 按三个字段直接创建，无实际两块数据迁移。
- 新表字段、CHECK、索引及六个 API 详见[功能第 5～6 节](../features/0013-lesson-summaries.md#5-api-与前端契约)。服务端分页 1–50，按 endAt/id 倒序，不把现有 createdAt 预约页在前端筛选成完成页。保留原接口/状态值和老邮件链接。
- V14 为 append-only：旧数据/五类邮件保留，迁移只给首次执行时已结束的记录设置永久规划检查标记，不生成总结/任务。既有未来预约可补建；即使迁移到启动之间课程结束，未检查记录仍能规划。历史分界不随每次重启变化，不因队列清理重新提醒。
- 写入先锁稳定预约行，再读写总结/任务；confirm 保留原日→时段→学生→预约顺序。补建用同模块预约行 SKIP LOCKED、有界 50 条，不反向拿上游锁；发布与规划由相同预约行串行。SMTP 在业务事务之外。
- expectedVersion 初值 0 表示尚无总结；首次创建到 1，每次内容或发布状态实际改变递增。同内容同结果重放返回当前结果；不同内容过期版本 409。已发布的同内容 publish 即使任务已清理也不建新通知；已发布不同内容须用 PATCH 更新，不再通知。
- 课后提醒任务保存 endAt，当前 CONFIRMED/endAt 匹配且到期/未发布才发；学生发布任务只在本人已发布时发。新任务字段与旧课前 startAt 字段独立，不误套课前 24h 规则。单业务提醒不意味着 SMTP exactly-once。
- 总结相关读取 no-store，客户端按账号/角色/预约 ID 清理和隔离，服务端草稿不暴露给学生。保留 CSRF/Session/401 重新登录语义；403 不清有效会话。未保存离开提示、失败保留输入，409 不自动覆盖。
- `LESSON_SUMMARY_REMINDERS_ENABLED` 控制课后规划/领取，学生首次发布通知始终是普通通知；BOOKING_REMINDERS_ENABLED 只管课前，BOOKING_MAIL_WORKER_ENABLED 仍为总开关。暂停后开启会恢复启用后结束且未发布的课程，不重复检查历史已标记课程。
- 无 v1 复用或导入；沿用仓库已验证的 Java 25/Spring Boot 4.1.1、MySQL 8.4、React/TypeScript 工具，不升级依赖。无新增 SDK、收费服务或架构例外，无需新 ADR。审批前不改变项目契约当前已批准规则。

## 5. 验收与验证计划

| 验收 ID | 具体验证及预期 RED | 环境/命令（获批后） | 预期 GREEN |
|---|---|---|---|
| AC-01/02/08 | 可编译 HTTP 测试先断言新完成课查询/总结读取；旧实现缺少目标行为而断言失败。固定 Clock 覆盖 endAt 前/等于/后、跨日/DST、四种状态和多页课程 | backend：`./mvnw -q -Dtest=LessonSummaryApiTest,LessonSummaryDomainTest test`；MySQL 8.4 Testcontainers | 资格完全以服务器 Instant 判断；取消/拒绝/待确认不冒充完成课；列表各页正确 |
| AC-04/05/07/09 | 草稿私有、三块分别读写/任一块缺失拒绝发布/各自 Unicode 及长度边界、发布后可读及修改不再次通知、本人/他人/角色/未登录/CSRF；直接调用用例亦验证授权 | 同一 API/领域目标命令；补用例授权目标 | 草稿不外泄，权限/错误格式正确，旧课补写发布通知学生 |
| AC-03/06/11 | 两个独立事务规划/保存/发布/取消竞争、同内容重放和不同内容旧版本；通知入队故障回滚；清理任务再重放、停机恢复 | `./mvnw -q -Dtest=LessonSummaryApiTest,LessonSummaryWorkerTest test` | 一个持久结果/业务任务，无丢修改；失败全部回滚；标记长期去重 |
| AC-01/02/10/11 | 有效 API/DOM RED 取得后添加 worker 目标：不提前、仅草稿仍提醒、发布后跳过、收件归属/endAt 不匹配、SMTP 故障/8 次 DEAD、过期重领/旧 token | `./mvnw -q -Dtest=LessonSummaryWorkerTest,BookingMailPollerTest test` | 独立领取、有效 lease、失败非 SENT、重试/恢复正确；课前开关不影响新邮件 |
| AC-03/12 | V13 预置旧五类任务、历史/未来预约再升级；全新库、约束/长度/外键、规划与历史标记、索引和数据保持 | `./mvnw -q -Dtest=LessonSummaryMigrationTest,FoundationMigrationTest,LegacyAvailabilityMigrationTest test` | 不改 V1–V13，无历史教练补发，原课前和普通事件数据/约束保留 |
| AC-08/13 | 先建立“已完成课程”标签、待发布/查看、三块编辑、草稿保存/发布/修改、错误/重试、409、分页/双账号、退出/会话过期、四类深链接的 DOM 目标 | frontend：`npm test -- src/LessonSummaries.test.tsx`，之后 `npm test`、`npm run lint`、`npm run build` | 旧实现因缺少标签/行为 RED；同一目标 GREEN；输入与账号隔离，旧预约功能回归 |
| AC-01/05/10 | 隔离 Mailpit 实收教练课后提醒及学生发布通知，核对各自收件人/主题/快照/时区/链接；先草稿无学生信，重复发布/修改无新业务信 | 对应集成测试中的 Mailpit v1.31.3 和受控 Clock，不等真实课程时刻 | 两类邮件在沙箱实际收到，无总结全文/电话/认证秘密；数据库 SENT 与实收分别核对 |
| AC-10/12/13 | 配置开关默认/关闭/恢复、Compose 映射、单线程和有限关闭；前后端既有行为/架构及必要配置回归 | `./mvnw test`；frontend 全量命令；`python3 -m unittest discover -s deploy/tests -p 'test_*.py'` | 适用检查真实通过；架构六类基线保持 0，无对 IDEA/生产数据的测试写入 |
| AC-13 | 真实本地浏览器：390px 与 1440px；完成课进入、写三块/存草稿/发布、换账号、邮件链接登录恢复 | 隔离本地数据库、后端/前端及 Mailpit | 无横向溢出、内容换行/焦点/标签可用、学生能找到已发布总结 |

- 审批前仅运行 `python3 ai-docs/check_docs.py` 和 `git diff --check`；不创建/运行新功能测试。没有新类、未配置迁移版本、测试编译或 Docker 故障不算有效 RED；先用可编译 HTTP/DOM 行为断言证明缺失，再实施。
- Maven 使用现有 Java 25 和 Colima/MySQL 8.4；macOS 命令按需加 `env DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。固定 Clock/隔离沙箱不改系统时间、不改用户已有本地/生产预约。
- [TODO-0034](../todo/0034-full-backend-test-shutdown-timeout.md) 是已有全量测试收尾问题；若复现，分别记录断言结果和进程结果，不声称由本功能修复。不得把旧 Surefire 报告算作本轮证据，核对所有报告的时间。

## 6. 风险、成本、部署与恢复

- 三块各 3000 字的私有文本、三组有界查询索引和每课至多一条教练/一条学生业务通知增加现有数据库及 SMTP 负载；仍复用一个调度线程，不新增 EC2、队列服务、付费邮件服务或密钥。最多 8 次投递尝试/任务；不承诺当前配额无限或总账单不变。
- “已完成”按排课时间而非实到；第一版不自动判断缺席。教练在发布按钮明确知道学生随后可读；未保存离开提醒/失败保留输入，双窗口通过版本冲突避免静默覆盖。
- 教练 reminder 在发布前最后校验与 SMTP 间仍有竞争窗口；SMTP 已接受但写回失败也可能重复。邮件只带查看链接与快照，不外发总结全文；课堂文字以登录页面最新版本为准。
- V14 新表/索引及旧课永久标记操作需在生产执行前核对数据规模、备份和迁移耗时；不假设 MySQL DDL 可回滚。备份/恢复按既有生产交付流程，不执行新恢复操作。
- 旧 worker 不识别新事件可能将其跳过，回退先暂停**全部预约邮件 worker**，再回退应用，保留 V14/草稿/发布数据及任务；恢复兼容版本后重新启用。只暂停课后提醒开关不足以安全运行旧 worker。
- 本计划批准仅覆盖本地实现、隔离测试、文档维护；Git commit/push、生产部署/迁移、真实外部测试邮件和新增付费资源均不从本次需求自动推导。现有 main 自动交付行为在后续 Git 授权时一并说明。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-10-12 | 只读调查根/前后端 AGENTS、项目契约/索引/ADR、0006/0007/0012、预约与邮件及前端源码、Git diff | 确认四种状态/现有快照和持久队列；无完成课/总结；保留用户独立 frontend/README.md 修改 | 仅设计依据，未改功能代码/测试或运行环境 |
| 2026-10-12 | 两项业务澄清、本文、配对功能与两份索引 | 结束时点/历史范围已确认，具体 revision 1 为 AWAITING_REVIEW（历史记录） | 草稿/发布/修改及技术契约仍为待审提案 |
| 2026-10-12 | revision 2：本文、配对功能及计划索引 | 按用户原话将两块拆为本节练习内容、待加强、课后强化练习；同步三字段/API/schema、发布必填、学生展示、验收和存储成本 | revision 1 未获批准；新 revision 2 继续 AWAITING_REVIEW，无实现或测试代码 |
| 2026-10-12 | 用户明确批准 revision 2；目标 HTTP/DOM 测试、domain/port/service、JDBC/V14/Web、worker/poller/配置、总结组件及导航 | 原 HTTP 3/DOM 2 有效 RED 后实现；草稿/发布/学生只读、邮件实收、并发与版本/回滚及 UI 已通过目标验证 | API 小型请求 helper 保留在新总结组件内，未另拆 lessonSummaryApi.ts；提醒与投递集成合并 LessonSummaryWorkerTest，共用隔离 MySQL/Clock，替代单独 ReminderTest。仅文件组织调整，无范围/架构/成本变化 |
| 2026-10-12 | 暂停期间迟到确认边界 | 新测试实际 RED（预期 0 任务而实际 1），修复为迟到确认总是持久标记，无论开关；恢复不补提醒 | 已批准“迟到确认不补提醒”规则内修复，无新范围 |
| 2026-10-12 | 独立 BrowserHarness、Chrome、MySQL/Mailpit | 390px/1440px 真实 API：草稿保存刷新恢复、发布、退出/学生邮箱链接登录、三块只读、列表；无溢出/脚本错误，实收共 4 封沙箱邮件 | 临时工具/截图均在忽略的 .local/lesson-summaries；未改 IDEA 或真实数据库/SMTP |

## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-10-12 | 仓库根目录 | `python3 ai-docs/check_docs.py`；`git diff --check` | 通过：14 对记录、40 张 ticket、链接/索引/状态/批准门槛一致；空白检查 exit 0 | 仅文档校验，不是功能验收或 RED/GREEN |
| 2026-10-12 | backend；Java 25/真实 MySQL 8.4 | `env DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q -Dtest=LessonSummaryApiTest test` | 有效 RED：3 tests / 3 failures / 0 errors；新完成课/总结接口缺失，预期 200/409 而返回 404；exit 1 | 日志 `.local/lesson-summaries/api-red.log` |
| 2026-10-12 | frontend | `npm test -- src/LessonSummaries.test.tsx` | 有效 RED：2 tests / 2 failures，缺少已完成课程/课程总结入口；exit 1 | 日志 `.local/lesson-summaries/frontend-red.log` |
| 2026-10-12 | backend；MySQL 8.4 | `./mvnw -q -Dtest=LessonSummaryApiTest,ArchitectureTest test`（同前述 Colima 环境） | 原 3 个 HTTP 目标全部 GREEN；架构检查通过；exit 0 | `.local/lesson-summaries/backend-target-first.log`；随后扩展验证 |
| 2026-10-12 | frontend | `npm test -- src/LessonSummaries.test.tsx` | 原 2 个 DOM 目标全部 GREEN；exit 0 | `.local/lesson-summaries/frontend-target-first.log` |
| 2026-10-12 | backend；MySQL 8.4 | `./mvnw -q -Dtest=LessonSummaryApiTest,LessonSummaryDomainTest,LessonSummaryWorkerTest,LessonReminderApiTest,BookingMailPollerTest test` | 首轮 25 tests：1 failure / 1 error，其余通过；修正 HTTP 角色认证预期与故障约束作用范围 | `.local/lesson-summaries/backend-target-expanded.log`；均为目标测试修正，最终复验另列 |
| 2026-10-12 | backend；MySQL 8.4 | `./mvnw -q '-Dtest=LessonSummaryWorkerTest#confirmationAfterEndWhilePausedStaysExcludedWhenRemindersResume' test` | 有效边界 RED：预期 0 任务，实际 1；1 failure / 0 errors，exit 1 | `.local/lesson-summaries/paused-late-red.log`；修复迟到确认永久标记 |
| 2026-10-12 | backend；MySQL 8.4/Mailpit v1.31.3 | `./mvnw -q -Dtest=LessonSummaryWorkerTest,LessonSummaryMigrationTest,FoundationMigrationTest,LegacyAvailabilityMigrationTest test` | 14 tests 全通过，包含上述边界 GREEN、V13→V14 与新库、Mailpit 两类新邮件实收；exit 0 | `.local/lesson-summaries/backend-fixed-target.log`；两份最新版本断言改为 14，历史迁移目标不变 |
| 2026-10-12 | backend；Java 25/MySQL 8.4 | `./mvnw -q test` | 首轮全量 224 tests：2 failures（两处旧版本断言仍为 13）/1 error（迁移 fixture 复用时段触发旧唯一键）；exit 1 | `.local/lesson-summaries/backend-full.log`；已同步最新版本断言、分开历史/未来 fixture 时段；复现 TODO-0034 30 秒退出超时，未修复其根因 |
| 2026-10-12 | frontend | `npm test`；`npm run lint`；`npm run build` | 最终 7 files / 99 tests 全通过；lint/typecheck/Vite build exit 0 | `.local/lesson-summaries/frontend-regression-final.log`、`frontend-lint-final.log`、`frontend-build-final.log`；补充的历史离开测试曾因 mock 缺 coach 详情及测试类型选项失败，修正后全量复验 |
| 2026-10-12 | deploy | `python3 -m unittest discover -s deploy/tests -p 'test_*.py'` | 29 tests 全通过，含本地/生产课后开关传递 true/false 与默认值；exit 0 | `.local/lesson-summaries/deploy-tests-final.log`；仅读配置，无发布/外部邮件 |
| 2026-10-12 | 真实 Chrome / 本地实际 API / 独立 MySQL 8.4 + Mailpit | `node .local/lesson-summaries/browser-check.mjs`；核对 Mailpit HTTP 实收 | 390px、1440px 两种尺寸完整通过：草稿持久化/刷新、三块发布、退出/学生邮件链接登录恢复、只读及完成课列表；0 横向溢出/0 pageerror；Mailpit 实收 2 教练提醒 + 2 学生通知 | `browser-results.json`、`browser-mail.json`、coach/student 各尺寸截图均在 `.local/lesson-summaries`；临时依赖在忽略目录，无 API mock，容器及后台服务已清理 |
| 2026-10-12 | backend；Java 25/真实 MySQL 8.4/Mailpit | `env DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q clean test` | **51 类 / 226 tests / 0 failures / 0 errors / 0 skipped；Maven exit 0**；ArchUnit 通过 | `.local/lesson-summaries/backend-regression-final.log`、`backend-test-summary.json`；clean 后生成的全部报告均为本轮，复现 TODO-0034 Surefire 30 秒收尾超时/Hikari 临时库警告，未改该根因 |
| 2026-10-12 | backend；真实 MySQL 8.4 | `./mvnw -q -Dtest=LessonSummaryApiTest,ArchitectureTest test`（同 Colima 环境） | 最后补充发布后学生读取最新三块内容断言，8 API + 4 架构 tests 通过；exit 0 | `.local/lesson-summaries/backend-final-target.log`；完整回归后仅补测试断言，无生产代码变化 |
| 2026-10-12 | 仓库根目录 | `python3 ai-docs/check_docs.py`；`git diff --check` | 最终文档门槛、14 对记录/40 tickets、索引/状态/链接及空白检查通过 | 用户独立 frontend/README.md SHA256 与任务前一致；没有 commit/push/部署/真实邮件 |

## 9. 完成状态与后续

- 当前 **VERIFIED / revision 2 / approved_revision 2**；本地实现及验收完成，尚未 RELEASED。
- 已确认提醒时点、历史范围和三个独立内容区；草稿、首次发布、发布后修改不重复通知均属已批准 revision 2。
- 已取得 HTTP/DOM 行为 RED 并实现 GREEN，最终全量验收及实际证据已齐全；Git/生产交付按后续实际授权处理。
- 功能/计划/两个索引同步；本工作已完整记录在配对计划内，不重复建待办 ticket。用户原有 frontend/README.md 修改保持原样。
- 2026-10-12 用户最新“提交推送部署”已授权生产交付；当前启动发布流程，RELEASED 仅在实际 CI/SSM/生产验证成功后记录。
