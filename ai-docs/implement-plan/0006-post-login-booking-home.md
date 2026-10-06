---
id: "0006"
title: "登录后约课主界面"
status: VERIFIED
revision: 5
approved_revision: 5
created: 2026-09-30
updated: 2026-10-05
feature: "../features/0006-post-login-booking-home.md"
---

# 0006 — 登录后约课主界面实施计划（revision 5 已验证）

> 2026-10-05 用户“批准”revision 5，当前实施范围仅为文末 revision 5：取消排班日期的未来 31 天上限。revision 1–4 的批准、实现和验证均作为历史保留；其中“未来 31 天”是旧修订的排班规则。按批准范围先写目标测试并取得 RED，再实现至 GREEN。前文关于“时段绑定课程”的描述只适用于 revision 1。

## 1. Review 摘要

用户希望登录后直接看到约课系统。已确认：学员浏览真实课程/时段并提交申请；固定两小时、一对一；提交后待教练确认且不占位，同一时段可多人申请；教练在工作区创建并发布课程/时段、确认或拒绝申请；教练设置 CAD 价格，线下付款。当前 v2 只有身份卡片和本地演示时段，无可预约真实数据。关联[功能契约 0006](../features/0006-post-login-booking-home.md)、[项目契约](../PROJECT_CONTRACT.md)与 [ADR 0001](../decisions/0001-v2-baseline.md)。

上述“只有身份卡片”是 revision 1 开始前的历史背景。revision 2 的课程与可用时间分离、revision 3 的月历和导航、revision 4 的课程及预约操作均已验证；本次获批的 revision 5 在文末单独说明。

**revision 1 已获批准的边界**：①教练填写地点和 IANA 时区，不存在/重复的当地时间拒绝；②确认一人后自动拒绝同槽其他待确认申请；③同一学员同一时段只可申请一次；④课程/时段创建即发布，本版不编辑或撤销已发布项，也不处理取消/改期；⑤仅在应用内看状态，无邮件/在线支付。批准依据见下表。

布局参考：学员[桌面样图](../design/0006-student-booking-home-concept.png) / [手机样图](../design/0006-student-booking-home-mobile-concept.png)，教练[桌面样图](../design/0006-coach-workspace-concept.png) / [手机样图](../design/0006-coach-workspace-mobile-concept.png)。图中样例人物、日期、价格、地点和额外控件不构成业务/API 承诺；实现依据为本计划和功能契约。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-09-30 | 用户在收到 revision 1 与桌面、手机样图后回复：“开始实现” | 1 | 按 revision 1 全部范围实施；无附加条件，包含 Review 摘要列出的提案；不含生产部署或提交推送 |
| 2026-09-30 | 用户在收到 revision 2 计划后回复：“开始实现” | 2 | 按 revision 2 课程与可用时间分离、雪场/日级锁定及批量排班实施；不含生产部署或 Git commit/push |
| 2026-09-30 | 用户在收到 revision 3 计划后回复：“开始实现” | 3 | 按 revision 3 教练三入口、月历多选及选中日整天替换方案实施；不含生产部署或 Git commit/push |
| 2026-10-01 | 用户在收到 revision 4 计划后回复：“开始实现” | 4 | 按 revision 4 可见反馈、紧凑雪场管理、课程编辑/下架、必填拒绝理由、学员取消及再申请规则实施；不含生产部署或 Git commit/push |
| 2026-10-05 | 用户在收到具体 revision 5 方案后回复：“批准” | 5 | 按 revision 5 前后端取消未来 31 天的排班日期上限并本地验证；保留批次/查询容量、过去时间和预约保护；不含 Git commit/push、生产部署或生产数据操作 |

revision 1、revision 2 的批准记录保留。revision 3 已获批准，按目标测试 RED → 实现 GREEN → 回归执行。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-00 | 本功能/计划与索引 | 记录用户已确认规则、review 提案、验收和成本 | 用户明确批准 revision 1 并记录原话、日期、范围/条件 | 已完成 |
| P-01 | `frontend/src/App.test.tsx`；`backend/src/test/java/com/geer/snowboard/v2/bookings/BookingCoreApiTest.java` | **批准后先写目标测试并运行 RED**：角色落地、真实列表、发布、多人申请、确认/拒绝、权限、幂等、并发与 DST | 前端缺少新页面的断言失败；后端新端点返回 404，均为有效 RED | 已完成 |
| P-02 | `backend/src/main/resources/db/migration/V4__booking_core.sql`；三个模块的 domain、port、application、Web/JDBC adapter | 追加课程、时段、申请和保护行表；实现公开用例、原子锁协议与 API；保留演示接口隔离 | 后端定向与全量测试 GREEN，真实 MySQL 并发/权限/回滚通过 | 已完成 |
| P-03 | `frontend/src/App.tsx`、`BookingHome.tsx`、`booking.css` | 登录/恢复进入学员约课或教练工作区；真实 API、CAD 价格和状态；桌面/手机布局，加载/空/错误/重试/防重复点击 | 前端目标测试 GREEN，390/1440px 学员和教练页面经 Chrome 检查 | 已完成 |
| P-04 | `backend/README.md`、`frontend/README.md`、功能/计划/索引 | 写明本地教练建课到学员申请的操作，记录实际 RED/GREEN、回归、偏差、成本和限制 | 文档检查、类型/lint/构建、后端回归与架构检查通过；按证据更新状态 | 已完成 |

- [x] 完成可审阅 revision 1，列出未单独确认的提案。
- [x] 用户明确批准 revision 1；记录日期、原话、范围与条件。
- [x] 测试先行 RED → 实现 GREEN → 适用回归，记录真实结果。
- [x] 同步文档状态和索引；未自行 commit/push。

## 4. 数据、API、架构与兼容影响

### 数据和时间

追加 `V4__booking_core.sql`；已应用 V1–V3 保持不变。MySQL 8.4 表归属和关键字段如下；实际字段名可按局部实现优化，但约束与语义不变。

| 表/归属 | 关键字段与约束 | 用途 |
|---|---|---|
| `catalog_course` / catalog | UUID `id`、`coach_id`、`title`、`description`、`price_amount DECIMAL(10,2)`、`currency='CAD'`、`created_at`、创建幂等键/指纹；价格非负，`(coach_id,idempotency_key)` 唯一 | 课程创建即发布；不物理删除 |
| `scheduling_coach_guard` / scheduling | `coach_id` 主键 | 创建时段前锁稳定行，避免同教练跨课程重叠排课 |
| `scheduling_slot` / scheduling | UUID `id`、`coach_id`、`course_id`、`location`、`zone_id`、`local_date`、`start_at_utc`、`end_at_utc`、`status=OPEN/BOOKED`、创建幂等键/指纹；`(coach_id,idempotency_key)` 唯一；索引按课程/日期/状态 | 创建即发布；UTC 区间 `[start,end)`；BOOKED 后不再接受申请 |
| `bookings_student_guard` / bookings | `student_id` 主键 | 确认前锁稳定行，防同一学员跨时段被同时确认重叠课程 |
| `bookings_request` / bookings | UUID `id`、`slot_id`、`course_id`、`student_id`、`student_name_snapshot`、`status=PENDING/CONFIRMED/REJECTED`、`decision_reason`、课程名/价格/币种/时段/地点/时区快照、创建/处理时间、申请幂等键/指纹 | 唯一 `(student_id,slot_id)` 防同人重复；唯一 `(student_id,idempotency_key)` 防重放；生成列仅在 CONFIRMED 时映射 slot ID 并加唯一约束；不能无条件唯一 slot ID |

- 价格在课程和申请中均存十进制与币种，前端用字符串传递/展示；申请快照创建后不可因课程模板变更重算。线下付款不产生支付记录。
- 教练输入 `localDate`、`localStartTime`、`zoneId`、`location`。使用 IANA ZoneRules 验证当地时刻只有一个有效偏移；夏令时缺失或重复时刻返回 400 并提示另选时间。开始 UTC 点取该唯一偏移，结束为 `start + 120 minutes`；当地日期独立保存，不用 UTC 截断。仅允许未来时段。
- 教练发布前经 `scheduling_coach_guard` 锁串行检查所有 OPEN/BOOKED 时段的区间交叠；相邻 `[a,b)` 和 `[b,c)` 可并存。对外列表只给未来 OPEN 时段，PENDING 数量不影响展示。创建课程和时段均用 `Idempotency-Key` 及请求指纹；同键不同内容返回 409。

### API 与权限

| Method / path | Actor 与输入 | 成功输出 | 主要错误/重试 |
|---|---|---|---|
| GET `/api/courses` | STUDENT；`limit<=50`、稳定 `cursor` | 已发布课程列表及 `nextCursor` | 400/401/403 |
| GET `/api/slots` | STUDENT；`courseId`、当地 `from/to`，跨度最多 31 天，`limit<=50`/`cursor` | 未来 OPEN 时段、地点、当地/UTC 时间及 `nextCursor` | 400/401/403/404 |
| POST `/api/bookings` | STUDENT；`slotId`，`Idempotency-Key` | 首次 201/PENDING；同键同内容 200，返回同一申请的最新状态和快照 | 400/401/403/404/409；网络重试复用键 |
| GET `/api/bookings/mine` | STUDENT；`limit<=50`/`cursor` | 仅本人按创建时间倒序的申请与状态 | 400/401/403 |
| GET/POST `/api/coach/courses` | COACH；创建含 title/description/CAD price、幂等键；GET 有界分页 | 教练课程；创建 201/重放 200 | 400/401/403/409 |
| GET/POST `/api/coach/slots` | COACH；创建含 courseId、location、zoneId、localDate/localStartTime、幂等键；GET 有界分页 | 教练时段；创建 201/重放 200 | 400/401/403/404/409 |
| GET `/api/coach/bookings` | COACH；状态过滤、`limit<=50`/`cursor` | 申请列表、学员展示名及决定结果 | 400/401/403 |
| POST `/api/coach/bookings/{id}/confirm` | COACH；申请 ID | CONFIRMED 与最新快照 | 401/403/404/409；同动作重试返回已确认结果 |
| POST `/api/coach/bookings/{id}/reject` | COACH；申请 ID、可选简短理由 | REJECTED | 400/401/403/404/409；同动作重试返回已拒绝结果 |

- 公开 DTO 与数据库记录分离。时间点返回带 UTC 偏移的 ISO 8601 字符串，价格为十进制字符串、币种 CAD；错误采用现有 Problem JSON 约定，400 输入、401 失效、403 错误角色、404 非本人/不存在、409 状态或幂等冲突；不泄露内部 SQL/身份资料。
- 前端复用 `AccountView` 与 `/api/auth/me`，登录或恢复后依服务端角色进入页面。控制器从安全上下文及 identity 入站用例构造 actor；catalog、scheduling、bookings 入站用例再次校验角色/资源归属。所有 POST 保持 Session CSRF；403 不登出，401 清理身份作用域状态。

### 模块与事务

- catalog 独占课程表，scheduling 独占时段/教练保护行，bookings 独占申请/学员保护行。消费方通过自己的 `adapter.out.module` 调用提供方公开 `application.port.in`；bookings → scheduling/catalog，scheduling → catalog，禁止反向引用与跨模块私表读写。`/api/demo/time-window-previews` 保留本地演示隔离，不参与真实约课。
- 提交申请：先按学员+幂等键查询；同键同指纹重放返回同一申请的最新状态，同键异内容返回 409。新请求在 `READ COMMITTED` 事务中锁稳定时段行，检查仍 OPEN、未来、课程有效，再由 bookings 插入 PENDING；已有同人同槽申请返回 409 并让前端打开“我的预约”。唯一索引处理同键并发，冲突后在新事务读取已提交结果。提交与确认竞争同一时段锁，因此确认后不会漏入新申请。
- 确认申请：按时段行 → 学员保护行 → 申请行的顺序加锁；核验 PENDING、时段 OPEN、该学员无交叠 CONFIRMED；调用 scheduling 公开用例占用时段，同事务更新选中申请为 CONFIRMED，并将同槽其他 PENDING 自动 REJECTED。`bookings_request` 条件唯一确认槽约束兜底；失败整体回滚。拒绝也先锁时段再更新目标申请，只改变这一条。
- 重叠创建由教练保护行串行化。锁等待/死锁仅针对可识别 MySQL 错误做最多两次新事务重试；超过上限返回可安全重试的服务错误，不声称永久成功。测试覆盖提交/确认、双确认、同学员两槽确认和创建重叠时段竞争。六类架构违规基线保持 0；不申请 ARCH-09 例外。

## 5. 验收与验证计划

| 验收 ID | 批准后先写测试的有效 RED | 环境/定向命令 | GREEN 判定 |
|---|---|---|---|
| AC-01/02/03 | 现有登录后仅身份卡片；新角色页面/清理断言失败 | `frontend`: `npm test -- src/App.test.tsx` | 学员/教练登录与刷新正确落地；401/403、切号、网络错误状态正确 |
| AC-04/08 | 新 GET/POST 课程和时段端点当前返回 404；DST/重叠发布断言失败 | `backend`: `./mvnw -q -Dtest=BookingCoreApiTest test`；MySQL 8.4 Testcontainers | 真实发布/浏览、分页、地点/时区、重叠与 DST 测试通过 |
| AC-05 | 新申请端点当前返回 404；多人 PENDING、同人重复、同键重放断言失败 | 同一 API 测试；`BookingConcurrencyTest` | 多人可申请，同人不重复，同键异内容 409；提交/确认竞争无漏单 |
| AC-06 | 确认/拒绝端点当前返回 404；双确认和同学员交叠确认断言失败 | `./mvnw -q -Dtest=BookingConcurrencyTest test` | 单槽仅一 CONFIRMED；其余 REJECTED；拒绝后时段开放；失败回滚 |
| AC-07 | 新接口当前无角色授权边界；未登录/错误角色/他人记录测试失败 | `BookingCoreApiTest` + CSRF/Session | 401/403/404 正确，学员不能读取或处理他人申请 |

测试文件的具体类名可随代码位置微调；以可编译的 HTTP/DOM 断言取得因缺少目标行为而失败的 RED，再实现至 GREEN。环境、测试代码错误不算 RED；实际命令、结果和限制记录在第 8 节。后端使用真实 MySQL 8.4 Testcontainers 完成并发、Session 和 API 流程；前端在模拟 API 下完成 DOM 交互及 390px/1440px 视觉检查。

## 6. 风险、成本、部署与恢复

- 主要风险：初始无课程/时段时学员看到空状态，须引导教练先发布；多人申请的 PENDING 不代表锁定名额，页面须明确。并发确认、同学员交叠课、DST 和价格快照由真实 MySQL/领域测试覆盖。
- 新增五张小表及索引和有界分页查询；沿用已有 EC2/MySQL 规划，不新增云资源、邮件服务、支付服务或前端依赖。持续成本主要是 MySQL 存储、备份体积和请求负载；当前无真实流量数据，不能保证 30 CAD/月预算上限。上线前按实际 EC2 规格与负载核算。
- V4 仅追加，旧应用可忽略新表；回退应用时保留 V4 和预约数据，不编辑已应用迁移或自动删表。生产迁移前须有数据库备份、异机保留及恢复验证；部署后若新功能故障，可恢复上一镜像并暂时隐藏新入口，数据保留供修复。生产执行另需授权。
- 如需修改自动拒绝、地点/时区、课程编辑、邮件或支付范围，先增 revision、重新 review；不借实现过程自行扩张。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-09-30 | 需求与源码调查、0006 revision 1 文档 | 确认现有登录后仅身份卡片，真实约课模块未落地；记录用户七项业务选择与本计划提案 | 等待 review；尚未进入实现，无需 ADR 变更 |
| 2026-09-30 | P-01：前端 DOM 与后端 HTTP 测试 | 先写登录落地、真实浏览/提交及教练发布目标测试；前端因旧身份卡片缺少标题/时段按钮失败，后端新端点依次返回 404；Docker 配置故障修好后才计后端 RED | 定向测试留在 `App.test.tsx` 和 `BookingCoreApiTest.java`，并发情景并入同一 MySQL 集成测试类，无需另建 `BookingConcurrencyTest` |
| 2026-09-30 | P-02：Flyway V4、catalog/scheduling/bookings、共享 actor/分页/幂等、锁冲突重试 | 实现公开 API、固定两小时、CAD 快照、时段与学员保护行、单槽条件唯一确认、最多两次新事务重试；旧演示接口隔离 | 按计划实施，无 ADR/成本范围变更；既有迁移测试的当前版本断言从 V3 更新为 V4 |
| 2026-09-30 | P-03：`BookingHome.tsx`、`booking.css`、`App.tsx` | 学员直接约课，教练直接进工作区；分页“加载更多”、发布/申请幂等网络重试、错误与空状态；Chrome 390/1440px 学员/教练布局无横向溢出 | 视觉截图为临时本地验证文件，未纳入产品素材；后端真实 Session 流程由集成测试验证 |
| 2026-09-30 | P-04：README、功能/计划/索引与项目契约 | 同步本地操作、已决定首版规则、验证证据与限制 | 未部署、未付费、未 commit/push；架构基线仍为 0 |

## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-09-30 | 仓库只读调查 | 阅读 `frontend/src/App.tsx`、身份 API、项目契约与 v1 约课文档 | 确认 UI、数据和 API 的现状；未运行目标测试 | 不是 RED/GREEN；批准前不写测试或实现代码 |
| 2026-09-30 | 文档检查 | `python3 ai-docs/check_docs.py`、`python3 ai-docs/test_check_docs.py`、`git diff --check` | 文档链接/配对/状态检查 OK；检查器测试 3/3 通过；差异空白检查通过 | 仅文档验证，不代表功能实现通过 |
| 2026-09-30 | `frontend/` 目标 RED | `npm test -- src/App.test.tsx` | 新角色主界面测试因缺少“预约单板课”/时段按钮失败；后增分页和发布重试测试分别因按钮缺失、键变化失败 | 均为目标行为缺失；非编译/环境错误 |
| 2026-09-30 | `backend/` 目标 RED | `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q -Dtest=BookingCoreApiTest test` | 发布课程、发布时段、提交申请端点在实现前依次返回 404；后续同用例通过 | 首次未配好 Docker 环境时容器启动失败，不计 RED；修复环境后才取得有效 RED |
| 2026-09-30 | `backend/` 定向 GREEN | 同上；`./mvnw -q -Dtest=ArchitectureTest test`，使用相同 Docker 变量 | MySQL 8.4 下流程、幂等、权限、DST 缺失/重复时刻、分页、拒绝保留时段、并发申请/确认、并发双确认及并发排课通过；架构测试通过 | 单教练无法构造同学员跨两个重叠教练时段，保护行和校验已实现；未来多教练扩展需加该测试 |
| 2026-09-30 | `backend/` 全量回归 | 相同 Docker 变量下 `./mvnw -q test` | 76 个测试通过，含真实 Session 的教练→学员→教练→学员 API 流程与 `BookingLockRetryTest` | 第一次回归发现旧 `FoundationMigrationTest` 仍预期 V3，更新为 V4 与 13 张表后重跑通过 |
| 2026-09-30 | `frontend/` 全量回归 | `npm test`、`npm run typecheck`、`npm run lint`、`npm run build` | 17 个交互测试通过，类型/lint/生产构建通过 | DOM 测试采用模拟 API；真实 API 与 Session 由后端集成测试覆盖 |
| 2026-09-30 | 本地 Chrome | CDP 设置 390px/1440px，模拟 API 渲染学员/教练，检查截图和 `documentElement.scrollWidth` | 四种视口页面完整、无横向溢出；390px 学员页依次展示课程、日期、时段、申请信息 | 视觉检查使用模拟课程数据；不代表生产浏览器兼容性或真实部署验证 |
| 2026-09-30 | revision 2 文档 review 准备 | `python3 ai-docs/check_docs.py`、`python3 ai-docs/test_check_docs.py`、`git diff --check` | 文档配对、索引和审批门槛检查 OK；检查器测试 3/3 通过；空白检查通过 | 仅文档检查；revision 2 的目标 RED/GREEN、迁移和 UI 均未开始 |
| 2026-09-30 | `frontend/` revision 2 目标 RED | `npm test -- src/App.test.tsx` | 19 个测试中原有 17 个通过；新增教练雪场/批量发布测试因缺少“雪场名称”控件失败，学员组合申请测试因缺少雪场选项失败 | 均为目标行为缺失，非环境或编译错误 |
| 2026-09-30 | `backend/` revision 2 目标 RED，MySQL 8.4 Testcontainers | `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q -Dtest=AvailabilityRevision2ApiTest test` | 容器、迁移及应用启动成功；3 个新增目标 API 测试因 `POST /api/coach/mountains` 尚不存在返回 404 失败 | 目标接口缺失，属有效 RED；尚未执行实现或 GREEN |

## 9. 完成状态与后续

- revision 1 为 VERIFIED：本地实现与验收通过；生产部署与 RELEASED 单独授权。revision 2 也已 VERIFIED，见下方修订。
- 未部署、未提交或推送。新增 MySQL 表/索引的生产体量和 30 CAD/月预算需上线前按真实负载核算；本地测试不能证明该预算。

### Revision 2：课程与可用时间分离、批量排班

#### 需求、边界与待决定事项

- 用户 2026-09-30 指出：“课程创建其实没问题……教练创建时间段的时候，只需要录入可用时间就可以了，因为教练什么都可以教啊，选时间最好能批量选。”随后选择“输入每天的可用时间范围，系统拆成两小时”。课程创建、CAD 定价与线下付款保持 revision 1 规则；一个可用时间不预先属于任何课程。学员申请时才把课程与该时间组合并保存预约快照。
- 保留已确认的固定两小时、一对一、多个 PENDING 不占位、教练确认一人并自动拒绝同一时间的其他申请；课程不同也共同竞争这一个时间。教练可在尚未创建课程时先发布可用时间，但学员必须选择已发布课程才能申请。
- 批量输入按多伦多当地日期填写起止时间：可在未来 31 天内多选日期后填同一时间范围，再逐日改写；一次最多 31 个不同日期、100 个生成时段。每一天从起点连续切分完整的 120 分钟块，不能组成两小时的尾段在提交前预览并不发布；不生成跨午夜区间或每周自动重复。含 DST 不存在/重复时刻或 UTC 实际时长不足/超过 120 分钟的块使整批失败，提示具体日期与时间。
- 用户继续明确当天地点规则：教练发布某天可用时间时可不选地点，表示他设置的几座山都允许；若选一座山，当天只在该山授课。确认当天第一笔申请时，该天地点锁定为这笔申请的山；当天其他山的所有 PENDING 申请自动拒绝，后续也不能再申请其他山。这个限制跨当天不同的两小时时段，不只作用于被确认的时段。
- 用户已选择由教练在工作区新增、改名、停用雪场，历史预约保留原名称；有该山 PENDING 申请时不能停用。学员先选课程，再选日期、两小时可用时间及当天允许的雪场；当天边界固定为 `America/Toronto`。逐日输入、拆分、DST 验证及日期锁定均采用此时区，不再要求教练每批填写时区。
- 本计划提案的具体边界：教练发布前至少有一个活动雪场；同一天的多批次排班必须使用相同的“仅限雪场/不限雪场”设置，已锁定日期只能继续在锁定山开放；“不限雪场”按申请当时教练的活动雪场列表计算，新增山会进入尚未锁定的不限雪场日期。停用无 PENDING 的山后，它不再出现在新的选择中；已确认与历史申请保持名称快照。这些取舍随 revision 2 一起提交 review。
- 本次不增加修改/删除已发布课程和时段、取消/改期、支付、通知、每周循环排班或生产部署。无新外部服务或预计付费资源。

#### 预计实现步骤与文件

| 步骤 | 预计文件/模块 | 改动与完成条件 | 状态 |
|---|---|---|---|
| R2-01 | 本功能/计划与两个索引 | 记录用户选择、修订验收/API/迁移；用户 review 并明确批准 revision 2 | 已完成：2026-09-30 用户“开始实现” |
| R2-02 | `AvailabilityRevision2ApiTest.java`、`AvailabilityRangeTest.java`、`LegacyAvailabilityMigrationTest.java`、`frontend/src/App.test.tsx` | 先写可编译的目标 API/DOM 测试并执行有效 RED；覆盖无课程排班、雪场/课程组合、批量回滚、并发确认及旧数据迁移 | 已完成：新端点 404 与缺失 UI 控件为有效 RED |
| R2-03 | Flyway V5；scheduling 的 domain/port/application/JDBC/Web；bookings 的 port/application/Web | 分离 `scheduling_slot` 与课程；增加雪场、有界幂等原子批量发布与日级地点限制/锁定；申请显式选择课程+时段+雪场并保留快照 | 已完成：MySQL 8.4 目标测试 GREEN |
| R2-04 | `frontend/src/BookingHome.tsx`、`frontend/src/booking.css` | 教练多选日期、逐日输入范围、当天限制雪场可空、预览和批量发布；学员按课程→日期→时段→雪场选择并申请 | 已完成：DOM 测试与 390/1440px 浏览器布局检查通过 |
| R2-05 | README、本功能/计划/索引 | 记录目标测试 GREEN、MySQL/并发/权限/迁移与前端回归、实际命令、结果、偏差与状态 | 已完成：证据与限制见下方 |

#### 数据、接口和并发方案

- V4 已应用，不能改写。追加 V5：让 `scheduling_slot.course_id` 可空并移除其课程外键，旧行的 `course_id` 值保留作迁移前历史字段；新的查询/用例不以它限制可约课程。新时段不需输入 `location`，其当地时间与日界线固定为 `America/Toronto`。调整开放时段索引，使筛选按状态、当地日期和时间进行。`bookings_request.course_id` 与课程/价格快照继续保留，旧申请不重新计算。
- scheduling 新增教练拥有的雪场列表（稳定 ID、名称、停用状态）和日级策略记录：每个教练/多伦多当地日期唯一，记录可空的“当天仅限雪场 ID”和确认后不可更改的“当天已锁定雪场 ID”，并作为申请/确认的稳定锁目标。bookings 为新申请存所选雪场 ID 与名称快照；后续改名不改快照，旧申请的自由文本地点和其他快照保留。有该雪场 PENDING 申请时停用返回 409；成功停用后不接该雪场的新申请。旧 `location` 可能是集合地点而非雪场，不能自动推断；V5 为含未来旧时段的日期建立“待地点映射”标记，保留原记录，阻止该日期的新申请与确认，直到有经 review 的人工映射方案。当前本地调查为 4 门课程、0 时段、0 申请；生产数据仍需部署前核验，若有未来旧数据则发布前需独立处理映射。V5 DDL 及迁移路径在批准后以真实 MySQL 8.4 测试验证；生产数据不在本计划中操作。
- 批量发布采用新的批次幂等记录（scheduling 模块拥有，教练 ID + 幂等键唯一、保存请求指纹和批次 ID）；每个新时段保存批次 ID 与独立时段 ID。一次请求超过 31 个日期或 100 个生成时段返回 400；所有日期/时间、DST、过去时间、已发布时段及批内重叠先验证。沿用教练保护行和单事务：有一处失败整批不写入；同键同内容重放返回原批次，同键不同内容返回 409。
- 教练雪场设置用 `GET/POST /api/coach/mountains`、`PATCH /api/coach/mountains/{id}`（改名）、`POST /api/coach/mountains/{id}/deactivate`；只管理本人雪场，保存稳定 ID/名称/停用状态，禁止空名、重复活动名称和越权修改，历史名称快照不变。`GET /api/slots` 不再需要 `courseId`，仍按多伦多当地日期范围和有限分页查询未来 OPEN 时间，每个时段返回当天可选雪场 ID/名称与锁定状态；前端选中的课程不改变可用时间集合。新批量创建接口为 `POST /api/coach/availability/batches`，输入逐日日期/当地起止时间、可空的当天限制雪场 ID 与幂等键，返回生成时段列表和未形成整块的尾段；服务端用固定 `America/Toronto` 解释时间。旧 `POST /api/coach/slots` 在新前端中移除，不继续要求课程 ID；保留教练时段列表 GET。当前 rev1 未生产发布，生产兼容评估仍在部署授权阶段执行。
- `POST /api/bookings` 改为提交 `courseId` + `slotId` + `mountainId`，三者都进入幂等指纹；bookings 经 catalog 和 scheduling 公开用例验证课程已发布、时间仍开放、两个资源属同一教练、雪场由该教练设置且在当天允许集合、没有违反当天锁定，然后保存原有快照与所选雪场名称快照。保留 `(student_id,slot_id)` 唯一申请约束：同一学员不能通过换课程或雪场重复申请同一时间。
- 为教练每个排班日使用稳定锁目标，申请、确认、拒绝均先锁当天，再锁时段，并按既有顺序处理学员/申请；避免两个不同时间同时确认不同山而产生跨时段地点冲突。首次确认原子锁定当天地点，并按教练/多伦多日期/雪场 ID 更新其他山全部 PENDING 为 REJECTED；同槽其他 PENDING 仍按 revision 1 规则拒绝。锁定后当天非该山的时段不再开放给新申请。同一天重复批量发布时，日级限制必须与已有设置一致；已锁定日只能允许锁定山。旧数据的未知地点需经映射审查，不能丢弃或静默改写历史快照。
- 雪场停用与新申请以该雪场行为共同串行：停用锁行后检查是否仍有 PENDING，存在则返回 409；新申请在保存前再次校验活动状态，避免“停用成功后仍插入申请”。改名只改变当前展示名，bookings 创建时已保存的名称快照不变。上述事务使用现有 MySQL `READ COMMITTED` 和有限死锁重试；新增索引支撑按教练/当地日期/雪场/状态查找跨时段 PENDING。
- 六边形边界和表归属沿用 revision 1；scheduling 不再需要对 catalog 的课程查找，bookings 继续经公开入站端口读取 catalog 与 scheduling。无 ARCH-09 例外或 ADR 基线变化。旧版本应用回退时要保留 V5 和旧数据，且不能让旧版写入新型空课程关联时段；发布/回退需另拟有备份与恢复验证的部署步骤。

#### Revision 2 验收与测试计划

| ID | 批准后先写测试并取得的预期 RED | GREEN 判定 |
|---|---|---|
| R2-AC-01 | 未创建课程时提交多天时间范围；现有接口要求课程且只创建一条 | 生成各天完整两小时块；预览与返回一致，尾段明确；手机/桌面可操作 |
| R2-AC-02 | 同一时段分别选择两门已发布课程申请；当前提交仅带 slotId，时段锁定其预设课程 | 两位学员的不同课程申请均为 PENDING；确认一条后另一条被拒绝，时间仅一人占用 |
| R2-AC-03 | 换课程重复申请同一时段、同键重放/异内容、不同教练资源组合 | 同人同槽仍唯一；重放安全，异内容 409；跨教练/无效课程被拒绝且无部分写入 |
| R2-AC-04 | 批内重叠、与旧时段重叠、DST 缺失/重复、过去时间、超上限及并发批次 | 整批原子失败；同键重试结果一致；无重叠发布或重复数据 |
| R2-AC-05 | 从 V4 含课程绑定时段及已有预约数据升级 | V5 成功且不丢旧时段/申请/快照；无法自动对应雪场的未来旧日期先阻止新申请和确认、等待明确映射，不静默当作任意雪场；已有确认继续占用 |
| R2-AC-06 | 学员/教练/未登录真实 Session 及手机流程 | 授权与 CSRF 正确；学员列表、选课/选时、教练批量发布及申请处理正常；401 清理状态 |
| R2-AC-07 | 同一天多个时段的不同山申请并发确认；第一笔确认后还有其他山 PENDING 或新申请 | 全日最多锁定一座山；其他山全部自动拒绝且不能再申请；同山其他时段仍可申请/确认，已确认数据与快照保持正确 |
| R2-AC-08 | 教练新增/改名/停用雪场，学员查看已改名雪场，读取已有预约 | 只可管理本人列表；新申请使用当前活动名称，历史预约保留原名称；有该山 PENDING 时停用返回 409，成功停用后不再接受新申请 |

- 后端用 MySQL 8.4 Testcontainers 在 `backend/` 执行目标测试 `./mvnw -q -Dtest=BookingCoreApiTest test`，再执行全量 `./mvnw -q test` 与架构检查；前端在 `frontend/` 执行 `npm test -- src/App.test.tsx`，再运行 `npm test`、`npm run typecheck`、`npm run lint`、`npm run build`，并检查 390px/1440px 布局。实际运行时沿用有效 Docker 环境变量，并记录 RED 因行为缺失、GREEN 与回归结果；环境错误不计 RED。
- 主要风险是旧版课程绑定时段、跨课程竞争和批量数据体量；以真实迁移/并发测试和 100 条上限控制。新增小型批次表及索引，占用现有 MySQL；未测真实负载前不声称满足 30 CAD/月预算。无生产部署、付费资源、不可逆数据操作授权。

#### 当前状态

revision 2 已 VERIFIED：用户 2026-09-30 回复“开始实现”批准本修订；先写目标测试取得有效 RED，再完成实现和适用回归。revision 1 的验证证据保留在第 7–9 节。未部署或发布，生产迁移、旧地点映射及费用核算需单独处理。

#### Revision 2 实际执行与偏差

| 日期 | 实际文件/步骤 | 结果与偏差 |
|---|---|---|
| 2026-09-30 | `AvailabilityRevision2ApiTest.java`、`AvailabilityRangeTest.java`、`LegacyAvailabilityMigrationTest.java`、`frontend/src/App.test.tsx` | 目标测试先写后跑；前端因缺少雪场与批量排班控件失败，后端因新雪场端点 404 失败；均为目标行为缺失的有效 RED。旧 `BookingCoreApiTest` 更新为新批量排班和三项申请输入。 |
| 2026-09-30 | `V5__independent_availability.sql`、scheduling 与 bookings 的端口/服务/适配器 | 追加雪场、排班批次和日级策略；保留 V4 旧数据，旧时段日期隔离。时段不再预设课程，申请捕获课程/价格、时间、雪场快照；日级锁串行确认并拒绝异山申请。幂等重放先查已有批次，再校验新发布日期范围，避免稍后重放被当前日期窗口拒绝。无架构例外或 ADR 变更。 |
| 2026-09-30 | `BookingHome.tsx`、`booking.css`、README、功能/计划/索引、项目契约 | 教练可设置雪场并逐日批量排班；学员按课程、日期、时段、雪场申请。页面的 390px/1440px 布局和手机批量排班表单经 Chrome 检查。README 与项目契约同步已定规则。 |

#### Revision 2 实际验证

| 环境/目录 | 命令或步骤 | 结果与限制 |
|---|---|---|
| `frontend/` 目标 RED → GREEN | `npm test -- src/App.test.tsx` | 新增两项先因缺少雪场/批量控件失败；实现后目标测试 19/19 通过。 |
| `backend/` 目标 RED → GREEN，MySQL 8.4 Testcontainers | `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q -Dtest=AvailabilityRevision2ApiTest test` | 新端点最初返回 404；实现后 5/5 通过，含无课程多日排班、跨课程同槽申请和价格快照、整批回滚、异山并发确认、同山第二笔确认及雪场停用。 |
| `backend/` 迁移与领域定向 | 同样 Docker 变量下 `./mvnw -q -Dtest=AvailabilityRangeTest,LegacyAvailabilityMigrationTest test` | 两小时拆分、尾段与 DST 边界通过；V4→V5 保留旧课程时段、预约和快照，未来旧日期标记待映射。 |
| `backend/` 全量回归 | 同样 Docker 变量下 `./mvnw -q test` | 84 个测试通过，0 失败/错误，含真实 Session、权限、并发和架构测试。首次未带 Colima 环境变量的运行无法启动容器，不计代码失败；正确配置后通过。 |
| `frontend/` 全量回归 | `npm test`、`npm run typecheck`、`npm run lint`、`npm run build` | 19 个 DOM 测试及类型、lint、生产构建通过。DOM 测试使用模拟 API。 |
| 本地 Chrome，模拟 API | 390px/1440px 分别渲染学员与教练；滚动检查 390px 批量排班表单，并读取 `documentElement.scrollWidth` | 四种视口均无横向溢出；手机排班的日期、起止时间、当天雪场、添加日期与发布按钮完整可见。浏览器使用模拟课程数据，不代表生产浏览器/真实部署验收。 |

本项目仅允许一个教练，无法在真实业务约束下构造“跨两个教练组合资源”的测试；申请服务仍校验课程和时段属于同一教练。V5 对未来旧时段日期保持隔离，直到经 review 的地点映射方案完成；本次没有生产数据操作。MySQL 表和索引增加的实际云成本尚未测量，30 CAD/月预算需上线前按真实负载核算。

### Revision 3：独立创建标签与月历多选排班

#### Review 摘要与已确认规则

用户 2026-09-30 指出教练“工作区”和“课程与时段”功能重复，希望创建课程和创建 availability 各有独立标签，并参考 v1 日历，减少逐日添加的操作。已只读核对 v1 `frontend/src/views/AvailabilityManagementPage.jsx`、`AvailabilitiesCreateDTO` 和 `InstructorServiceImpl`：其批量表单是起止日期、星期筛选、统一时间和可选雪场；月历按月读取并显示每天的空档、地点及确认地点。v1 的“生成/覆盖”会先清掉范围内原有未预订时段，且删除循环未按星期筛选；v1 的日期格使用 UTC 转换。本修订拟借鉴交互结构，由 v2 自身的多伦多日期、原子排班与预约约束决定数据行为。

用户已选择三个独立入口：“创建课程”“管理可用时间”“预约申请”；排班日期直接在月历上多选；选中日期共用一组时间与雪场设置，例外另分批处理；批量发布对选中日期整天替换原有未确认时段，保留已确认预约；任一选中日有 PENDING 申请时整批禁止覆盖。下列 API、状态与事务方案随 revision 3 提交 review；revision 1/2 的业务规则及验收证据保留。

#### 预计实现步骤

| 步骤 | 预计文件 | 具体工作与完成条件 | 当前状态 |
|---|---|---|---|
| R3-01 | 0006 功能/计划与两个索引 | 记录导航、批量选择、月历和整天覆盖；提交 revision 3 供 review，取得明确批准并记录原话/日期/范围 | 已完成 |
| R3-02 | `frontend/src/App.test.tsx`、新增真实 MySQL API 测试 | 批准后先写新导航、月历多选/跨月保留、统一设置、月份读取及覆盖事务的目标测试；真实运行到因目标行为缺失而 RED | 已完成；有效 RED 见下表 |
| R3-03 | Flyway V6；scheduling 与 bookings 的公开端口、服务、JDBC/Web 适配器 | 月份读取；旧 OPEN 时段撤回状态；bookings 用例检查选中日没有 PENDING，再协调 scheduling 的时段撤回/重建，同事务保持锁顺序、幂等和已确认保护 | 已完成 |
| R3-04 | `BookingHome.tsx`、`booking.css` | 拆分教练创建标签；月历点选多个日期并保留跨月选择，统一时间/雪场设置，显示日期/块数/尾段与覆盖提示；月历导航、日期详情和手机布局 | 已完成 |
| R3-05 | README、0006 功能/计划/索引 | 目标 GREEN、前后端回归、390/1440px 浏览器检查和文档证据；同步状态 | 已完成；验证证据见下表 |

#### 页面、数据与 API 提案

- 导航提案：删除重复的“工作区 / 课程与时段”组合。课程标签仅含课程创建和已发布课程；可用时间标签含雪场管理、批量排班及月历；预约申请保持独立入口并作为教练默认页。学员导航和申请流程保持 revision 2 行为。三个入口的数量与归属已获用户确认，默认页仍在本计划 review 范围内。
- 月历多选提案：教练在多伦多当地月历点选未来日期；选中状态跨月保留，再点一次取消，过去及超过未来 31 天的日期只能查看。允许最多 31 个选中日期、100 个新时段，试图超出时立即提示；零个日期不能提交。所有选中日期共用一组当地起止时间与可空限定雪场，特殊日期另选一批。页面列出选中日期、每日期完整 120 分钟块与尾段，并用明确的“整天覆盖并发布”操作提示旧 OPEN 将撤回、PENDING 会阻止提交。日期按钮支持键盘和可识别的选中状态；服务端仍权威校验日期、容量、DST 和已确认时段。
- 月度读取提案：新增 `GET /api/coach/availability/month?year=YYYY&month=M`，仅 COACH 可访问；响应含 `zoneId=America/Toronto` 与该月份有记录的日期，每日返回可空 `limitedMountain`/`lockedMountain` 的 ID 和当前名称、`legacyReviewRequired`，以及活动时段 ID、UTC 起止点和 OPEN/BOOKED 状态。月历自行补足空日期格；点日期切换选中状态并在下方显示当地时间与地点明细。月度读取本身只读，不提供单条删除或任意状态更新。查询限定单个自然月，最多 31 天；当前单教练不重叠时段的数量有自然上界。月度读取在 scheduling 模块内实现，不跨读 bookings 私表。
- V6 数据提案：`scheduling_slot.status` 增加 `CLOSED`，以保留旧时段、旧批次幂等回放及 `bookings_request.slot_id` 外键；已确认 `BOOKED` 时段不撤回，学员公开列表和新的重叠检测忽略 `CLOSED`。追加 `(coach_id,local_date,status,start_at_utc,id)` 用于月度读取及选中日锁定，另给 bookings 的 `(coach_id,local_date_snapshot,status,slot_id)` 增加查询索引。已应用 V4/V5 保持不变；月度读接口不修改历史快照。真实 MySQL 8.4 迁移测试检查旧数据和新约束，生产迁移另行授权。反复覆盖会累积 CLOSED 记录，上线前按真实数据量核算存储成本。
- 覆盖 API 提案：新增显式 `POST /api/coach/availability/replacements`，请求为展开后的 `days[]` 与 `Idempotency-Key`，首次成功 201、同键重放 200；400 输入/容量/DST、401/403 身份、409 PENDING/已确认重叠/地点限制/旧日期待映射/键冲突。旧 `POST /api/coach/availability/batches` 继续保留 revision 2 的新增与冲突语义。新用例放在 bookings 模块协调，先经 scheduling 公开用例锁教练保护行、按日期排序的选中日及旧时段，再检查 bookings 所拥有的 PENDING；任一选中日存在 PENDING 即整批 409，返回受阻日期，不撤回旧时段或修改申请。通过后由 scheduling 撤回选中日期全部旧 OPEN、更新当天雪场限制、验证新时段不与 BOOKED 重叠并插入新批次。全部在同一 MySQL `READ COMMITTED` 事务中原子提交；批次指纹区分覆盖与旧版新增，锁教练前后复查同键，重放在任何撤回前返回原批次，异内容仍 409。scheduling 不直接依赖 bookings，不跨模块写私表。
- 当天已有确认时，`locked_mountain_id` 保持不变；新限定雪场若与锁定山不同则整批 409，未指定雪场时仍只开放锁定山。新时间块与 BOOKED 重叠也整批 409，已确认预约不自动改期或拆分；旧数据待地点映射日期不可覆盖。月历上的单条删除、整月清空和拖拽编辑不在本修订内。

#### 目标测试与验收映射

| 验收 ID | 批准后先跑的有效 RED | GREEN 与回归判定 |
|---|---|---|
| R3-AC-01 | 前端仍显示重复的“工作区 / 课程与时段”且课程、排班表单同时出现 | 教练标签分离，申请入口明确，角色切换和 401 清理仍通过 |
| R3-AC-02 | 前端没有月历多选，必须逐日添加 | 多选、再次点击取消、跨月保留、过去日期不可选、无选择/超限均有正确反馈；统一设置和所选日期/尾段预览正确，一次提交走覆盖 API，网络重试复用键 |
| R3-AC-03 | 月度端点返回 404，前端没有月历 | MySQL 8.4 上月份日程、锁定/限制/旧日期标记与权限正确；前端空、加载、失败、前后月和手机点击详情通过 |
| R3-AC-04 | 新覆盖端点在旧 OPEN/BOOKED 混合的日期提交 | 选中日原有 OPEN 全部撤回、BOOKED 保留；若新块撞 BOOKED、雪场与锁定日冲突或任一选中日错误则整批回滚；同键重放不重复改写 |
| R3-AC-05 | 覆盖与新申请/确认并发 | 日→时段的稳定锁使覆盖与申请/确认串行；没有确认被撤回、没有新 PENDING 落在 CLOSED；任一旧时段有 PENDING 时覆盖整批冲突，申请历史不变 |

#### 验证命令、风险与恢复

- 批准后先在 `frontend/` 运行 `npm test -- src/App.test.tsx` 获得因缺失目标 UI 而失败的 RED；后端新 API 测试在 `backend/` 使用 Colima Docker 变量运行 `./mvnw -q -Dtest=AvailabilityRevision3ApiTest test`，以新月份/覆盖端点 404 获得有效 RED。实现后运行相同目标测试至 GREEN，再运行 `./mvnw -q test`、`npm test`、`npm run typecheck`、`npm run lint`、`npm run build`、文档检查与 `git diff --check`；Chrome 检查学员/教练 390px 和 1440px，并核对月历无横向溢出。
- 风险是跨月选中状态与多伦多时区、月度查询遗漏数据、覆盖与申请/确认的竞态及幂等重放。选中日期以纯 `YYYY-MM-DD` 保存，不从 UTC 时间点截断；服务端校验 DST 和容量。覆盖遵循教练保护行→日期→时段的固定锁顺序，确认/申请沿用日期→时段锁；真实 MySQL 并发测试验证。V6 追加检查约束和索引，占用现有 MySQL，持续费用未按真实负载测量；无新前端依赖、云服务或生产部署。
- 页面可恢复 revision 2 的教练导航和新增接口；覆盖产生的 CLOSED 时段须保留，不能用页面回退逆转历史。申请状态在覆盖事务中不修改。旧应用无法识别 CLOSED，应在单独的发布/回退计划中确认镜像兼容性、备份与恢复；生产发布及迁移不在本修订的默认授权范围。

#### Revision 3 当前状态

用户 2026-09-30 回复“开始实现”批准 revision 3 后，先取得有效 RED，再实现并运行目标 GREEN 和全量回归。教练默认进入预约申请，课程和可用时间分别管理；月历用纯当地日期保存选中状态，统一设置时间与雪场。替换用例由 bookings 协调 scheduling，在同一 `READ COMMITTED` 事务中依序锁教练保护行、日期和活动时段，检查选中日期的 PENDING，再将旧 OPEN 标记 CLOSED、保留 BOOKED、更新日期限制并插入新时段。旧新增接口不变。无 ARCH-09 例外或 ADR 变更。

| 验证项目 | 命令/方式 | 实际结果 |
|---|---|---|
| 前端目标 RED → GREEN | `frontend/`: `npm test -- src/App.test.tsx -t 'separates coach tabs'`；实现后 `npm test -- src/App.test.tsx` | RED：找不到「管理可用时间」按钮；GREEN：20/20 DOM 测试通过，含入口分离、月历多选、跨月切换后保留、替换请求及既有学员/教练流程。 |
| 后端目标 RED → GREEN | `backend/`: Colima Docker 环境下 `./mvnw -q -Dtest=AvailabilityRevision3ApiTest test` | RED：月份与替换端点均 404；GREEN：4/4 真实 MySQL 8.4 测试通过，含 CLOSED/BOOKED、整批 PENDING 阻止、幂等、地点锁定、并发申请与角色权限。 |
| 后端全量回归 | 同样 Docker 环境下 `./mvnw -q test` | 88 个测试，0 失败、0 错误、0 跳过；含 V4→V6 旧数据保留、Session、架构检查。首次回归的两项旧测试把最新迁移写死为 V5，修正预期为 V6 后重跑通过。 |
| 前端全量回归 | `frontend/`: `npm test`、`npm run typecheck`、`npm run lint`、`npm run build` | 20 个 DOM 测试、类型、lint、生产构建均通过。 |
| Chrome 视觉检查 | 本地 Vite + 模拟 API，在 390px/1440px 检查教练月历和学员页，读取 `documentElement.scrollWidth` | 教练与学员两种视口的 `scrollWidth` 均等于视口宽度，月历手机端 7 列完整可见；教练截图已人工检查。模拟 API 不代表生产浏览器/真实部署验收。 |
| 文档与格式 | `python3 ai-docs/check_docs.py`、`python3 ai-docs/test_check_docs.py`、`git diff --check` | 通过。 |

V6 仅追加迁移，旧 OPEN 行以 CLOSED 保留，历史预约引用与快照不删除。反复覆盖会增加 CLOSED 行，当前未按生产负载量化 MySQL 存储与备份费用；生产迁移、部署和旧版应用回退须独立授权与验证。当前状态按目标与回归证据标记 VERIFIED，尚未 RELEASED。

### Revision 4：可见操作反馈、课程编辑/下架、拒绝理由与学员取消

#### Review 摘要及决策依据

2026-10-01 用户反馈教练发布排班后日历没有可见变化、雪场设置占页面一半、课程无法修改/删除、拒绝预约缺少理由；学员提交申请后缺少反馈且不能取消。当前 `BookingHome.tsx` 的全局成功/失败提示位于内容顶部，离实际操作区远；`submitBooking` 把写入与随后列表刷新置于同一个 `try/catch`，会把“写入成功、列表刷新失败”误报为申请失败。`coach-publish` 使用两列等宽布局，使雪场管理占半页。

用户在本轮答复：“名称、介绍、加元价格（推荐）”；课程删除“不影响已有预约，直接删除课程，未来的学生就看不到这个课程了”。本计划以数据库保留课程行的下架实现用户可见的删除，因为已有预约与旧时段持有课程外键，硬删除会破坏历史；下架后已有 PENDING 可继续由教练确认/拒绝，已有 CONFIRMED 及快照保持原状。用户要求学员取消“模仿 V1”；只读核对 v1 `StudentServiceImpl.cancelBooking` 与 `MyBookingsPage`：PENDING 可直接取消，CONFIRMED 须距离开课至少 24 小时，取消理由可选。用户进一步确认：已确认取消后，若当天没有其他已确认课程，就解除当天雪场锁定并重新开放其他符合排班限制的雪场；取消理由按 v1 处理；取消后同一学员可重新申请同一时段。这些均属于 revision 4 待批准的规则，并非此前 revision 1–3 的批准范围。

#### 页面与交互

- 教练“管理可用时间”以月历和排班操作为主。雪场管理缩为月历上方紧凑摘要：展示活动雪场数量和名称、一个“管理雪场”展开按钮；展开后才显示新增/改名/停用操作。手机以单列展开，桌面不再为雪场预留半页固定列。保留输入标签、键盘操作和当前待确认申请阻止停用的说明。
- 月历发布按钮附近设置独立、可读屏的状态区域：提交中禁用重复点击；成功显示“已发布 N 个时段”、实际日期/时间，自动切到首个保存日期所在月份并展开日期详情；服务端月份数据刷新后日期格显示时段数量，受影响日期有短暂可见标记。跨月发布时保留受影响日期摘要，用户可切月份核对。API 写入失败保持已选日期、时间、雪场和幂等键，在按钮附近说明原因并允许重试。API 写入成功但月份刷新失败则仍显示“已发布”，另提示“月历暂未同步”及重试，不能写成发布失败；在刷新前利用写入响应显示已提交日期/数量，不假称未读取的数据已同步。
- 课程列表改为可操作卡片，教练可打开编辑表单修改名称、介绍、CAD 价格，保存后就地显示结果；可点击“删除课程”，由清楚的二次确认提示其含义为“从学员可选课程移除，已有预约不受影响”。下架课程仍在教练页标识“已下架”并可看历史，不出现在学员选课列表；首版下架后不可自行恢复或编辑，避免重新发布语义不明。
- 教练点击拒绝先打开当前申请的内联理由输入，填入去首尾空格后 1–200 字符才可提交；请求成功后对应卡片直接显示 REJECTED 和理由。失败保留文本及当前卡片，按钮附近显示错误。教练确认仍不要求理由；因确认其他申请/锁山导致的自动拒绝继续使用系统理由，不伪造教练输入。
- 学员申请区域在按钮附近显示提交中与成功的申请 ID/状态，接收 POST 成功响应时立即把申请插入/更新“我的预约”和最近预约卡片，再单独刷新列表；读取失败只提示状态同步失败。网络写入失败保留选择和原请求键，重试安全。取消按钮置于本人 PENDING 或符合时间限制的 CONFIRMED 卡片内，展示取消范围、可选理由（仅 CONFIRMED）及确认步骤；提交后用返回结果立即更新卡片与公开时段。未到 24 小时边界、已取消、已拒绝等状态不展示可操作按钮，并解释时间限制；服务端始终再次校验。取消后若时段重新开放，可用新的请求键重新申请；旧请求键的重放只返回旧取消记录。

#### API、状态与数据方案

| 接口 | 权限及输入 | 成功与错误 |
|---|---|---|
| `PATCH /api/coach/courses/{id}`（新） | 教练本人；名称 1–100、介绍至多 1000、非负两位小数 CAD 价格 | 200 返回最新课程；400 无效字段，401/403 角色，404 非本人/不存在，409 已下架；不改旧预约快照 |
| `POST /api/coach/courses/{id}/archive`（新） | 教练本人；无业务内容 | 200 返回 `active=false`，重复下架返回同一结果；非本人 404；学员公开列表与新申请立即排除，旧预约继续有效 |
| `POST /api/coach/bookings/{id}/reject`（收紧） | 教练本人；`{reason}` 去首尾空格后 1–200 字符 | 200 返回 REJECTED 和实际理由；缺失/空白/过长 400，无权限/非本人 403/404，非 PENDING 409；同一已拒绝申请重放返回旧结果 |
| `POST /api/bookings/{id}/cancel`（新） | 申请所属学员；PENDING 无需理由，CONFIRMED 理由可选且至多 200 字符 | 200 返回 CANCELLED_BY_STUDENT；重复取消 200 返回旧结果；非本人/不存在 404，太晚或终态冲突 409；写接口继续使用 Session/CSRF |

- Flyway V7 追加 `catalog_course.active`（原有课程默认活动）；学员课程列表过滤下架，教练列表带 `active`。已应用 V4–V6 不改。课程更新/下架与新申请使用同一课程行锁：新申请遵循现有日期→时段→雪场后，调用 catalog 公开用例锁定并检查活动课程；下架/编辑通过拥有者校验锁该行。由此确定先后顺序：先完成的新申请可保留快照，先完成的下架禁止新申请；编辑不会与快照混合。bookings 不直接查询 catalog 私表，catalog 不写 bookings 私表。下架不修改既有 PENDING/CONFIRMED/REJECTED/CANCELLED。
- V7 扩展 `bookings_request.status` 检查约束，加入 `CANCELLED_BY_STUDENT`，继续保留旧记录、课程外键、申请快照。为允许“取消后同人同槽再申请”，将旧 `(student_id,slot_id)` 唯一约束替为按状态生效的生成列唯一约束：仅已取消记录不占用该唯一位，其余 PENDING/CONFIRMED/REJECTED 继续保证同人同槽最多一条；`(student_id,idempotency_key)` 仍唯一。旧键重放返回旧取消申请；新键才能创建新申请，且需重新检验活动课程、OPEN 时段、活动雪场与当天地点。读取“是否已申请”只看未取消记录。迁移在真实 MySQL 8.4 验证旧数据和唯一约束。
- 学员取消具名 bookings 用例，先读候选以确定教练日期，再按当前日期→时段→学员→申请的稳定锁顺序加锁，重查本人归属、状态与服务器 UTC 时钟。PENDING 原子改为 CANCELLED_BY_STUDENT，不触及时段；CONFIRMED 距开始至少 24 小时才可原子改状态并将原 BOOKED 时段改回 OPEN。取消后查询当日剩余 CONFIRMED：有则保留 `locked_mountain_id`，无则清除锁定；当天原本的 `limited_mountain_id` 保留。已因旧确认自动拒绝的记录不复活。确认、申请、覆盖与取消在同一日期锁上串行；重复取消只返回结果，不再变更时段/锁山。bookings 经 scheduling 公开端口改时段和日锁，不写 scheduling 私表。
- `decision_reason` 保存教练人工拒绝原因或学员取消时的可选原因，不把自动拒绝改成人工理由。学员与教练列表展示新的取消状态及实际原因。课程价格继续用 DECIMAL/BigDecimal 和 CAD，编辑不重算任何旧预约快照；下架不物理删除课程。现有 `CLOSED` 时段不得因取消重新开放；正常 CONFIRMED 对应 BOOKED，此异常要返回冲突而非破坏历史。

#### 步骤、目标测试与验收映射

| 步骤 | 预计文件/模块 | 批准后执行的工作与有效 RED | GREEN 判定 |
|---|---|---|---|
| R4-01 | 本功能/计划、两个索引 | 完成本 revision 4 方案并交用户 review；记录明确批准的原话、日期、范围与条件 | 获批准前不写功能测试/实现 |
| R4-02 | `frontend/src/App.test.tsx`；新增 `BookingRevision4ApiTest.java`、迁移/并发目标测试 | 先写操作区反馈、课程编辑/下架、拒绝原因、取消与再申请的目标测试并运行；当前页面找不到控件、新端点 404 或旧接口错误接受空拒绝理由，属于目标行为缺失的有效 RED | 记录实际 RED 命令与失败断言；环境/测试故障不计 RED |
| R4-03 | Flyway V7；catalog 与 bookings 的 domain/port/service/Web/JDBC；scheduling 公开端口 | 课程行活动态、编辑/下架与申请串行；取消状态及有效唯一约束；取消释放时段/解锁日期；拒绝理由校验 | 真实 MySQL 目标 API/迁移/并发测试 GREEN，权限、历史快照、幂等和边界通过 |
| R4-04 | `frontend/src/BookingHome.tsx`、`booking.css` | 紧凑雪场管理、课程卡片管理、拒绝理由输入、取消操作；发布与申请附近就地状态，写入与刷新错误拆分 | 前端目标测试 GREEN；390/1440px 教练、学员流程无横向溢出且操作反馈可见 |
| R4-05 | README、功能/计划/索引 | 执行适用回归，记录 RED/GREEN、真实结果、偏差、成本与恢复限制 | 后端/前端全量回归、文档检查、格式检查通过；状态按证据推进 |

| 验收 | 关键测试输入 | 必须检查的结果 |
|---|---|---|
| R4-AC-01/02/05 | 跨月发布，POST 成功+GET 失败，申请 POST 成功+GET 失败，POST 真失败，390/1440px | 受影响日期/数量与申请状态立即可见；刷新失败不误报写入失败；输入和幂等键保留；雪场紧凑且完整可管理 |
| R4-AC-03 | 课程编辑/下架时已有 PENDING/CONFIRMED；新申请与下架/编辑并发 | 公共列表过滤、教练可见历史、旧快照/状态/处理可用；线性顺序下没有下架后新申请；非教练和非本人受拒 |
| R4-AC-04 | 空白、过长、有效拒绝理由，重复与对立处理 | 400/200/409 符合契约；学员看到原理由；自动拒绝理由保持系统文本 |
| R4-AC-06/07 | PENDING/CONFIRMED 取消、正好 24 小时边界、23:59、本人/他人、重复取消、取消后新键再申请；取消与确认/覆盖并发 | 状态与历史完整，时段释放，剩余确认决定是否解锁当天；旧键不生成新申请，新键可重申；无 BOOKED 漏状态/双确认 |

#### 风险、恢复与当前状态

- 锁顺序、课程活动态与确认取消竞态是主要风险；以 MySQL 8.4 Testcontainers 的并发、迁移、权限、快照与 24 小时边界测试验证。页面状态须分别表达写入结果和后续读取结果；不会仅依赖短暂顶部提示。已下架课程的历史申请确认不重新读取活动态，避免违背“不影响已有预约”。
- 预计在 `backend/` 用现有 Colima Docker 环境运行 `./mvnw -q -Dtest=BookingRevision4ApiTest test` 取得有效 RED，再运行同一目标测试至 GREEN 与 `./mvnw -q test`；在 `frontend/` 运行 `npm test -- src/App.test.tsx` 的目标 RED/GREEN、`npm test`、`npm run typecheck`、`npm run lint`、`npm run build`；最后执行 `python3 ai-docs/check_docs.py`、`python3 ai-docs/test_check_docs.py`、`git diff --check` 和 390/1440px 浏览器检查。实际命令、数量和结果只在执行后记录。
- V7 只追加迁移、保留全部历史课程/预约/时段；旧应用无法理解取消状态及新唯一约束，不能直接用旧版写入新库。生产迁移、回退、备份/恢复验证及发布另行计划/授权；本 revision 不执行生产数据操作。新增列/索引占用现有 MySQL，暂无实际生产容量与 30 CAD/月成本测量；不引入新服务或付费资源。
- revision 1–3 的 VERIFIED 证据保留。用户于 2026-10-01 回复“开始实现”批准 revision 4；本次批准不含生产发布、Git commit 或 push。revision 4 的实际结果如下。

#### Revision 4 实际执行与偏差

| 日期 | 实际工作 | 结果与偏差 |
|---|---|---|
| 2026-10-01 | `BookingCoreApiTest.java` 与 `frontend/src/App.test.tsx` | 先加课程编辑/下架、拒绝理由、待确认/已确认取消、重新申请及页面反馈目标测试，取得有效 RED；后续增加日级锁保留、24 小时限制、确认/取消及下架/申请并发测试。后端沿用已有真实 Session/MySQL 测试夹具，未另建计划中预计的 `BookingRevision4ApiTest.java`。 |
| 2026-10-01 | Flyway V7；catalog、bookings、scheduling 的公开端口、服务和 JDBC/Web 适配器 | 课程采用活动态下架，教练编辑与新申请同课程行串行；取消加入独立终态，更新按状态生效的同人同时段唯一约束；确认取消后原子释放 BOOKED 时段，并在当天无其他 CONFIRMED 时解除锁山。V7 同时把预约状态列从 16 扩至 32 字符，容纳 `CANCELLED_BY_STUDENT`。 |
| 2026-10-01 | `BookingHome.tsx`、`booking.css` | 排班成功后跳至受影响月份并标出日期/时段；写入成功与后续列表/月历读取失败分开呈现。雪场区改为顶部紧凑区域，课程可编辑/下架，拒绝填写原因，学员卡片可取消。Chrome 检查发现全局 `form` 样式使雪场输入异常变高，已显式改为横向布局。 |
| 2026-10-01 | 旧测试、README、项目契约 1.6、0006 功能/计划/索引 | 按新规则更新旧拒绝请求及 Flyway 最新版本断言；同步 v1 取消门槛与 v2 当天解锁、下架语义。未改 ADR 0001、无 ARCH-09 例外。 |

#### Revision 4 实际验证

| 范围 | 命令/方式 | 实际结果与限制 |
|---|---|---|
| 目标 RED → GREEN | `backend/`：Colima Docker 环境下 `./mvnw -q -Dtest=BookingCoreApiTest#revision4CourseEditAndArchivePreserveExistingApplication+revision4RequiresRejectReasonAndAllowsCancelThenReapply+revision4ConfirmedCancellationReopensSlotAndUnlocksLastMountain test`；`frontend/`：`npm test -- src/App.test.tsx -t 'shows a saved calendar result|keeps a successful student application visible'` | 初跑后端 3/3 因新端点 404、旧空理由被接受而失败；前端 2/2 因缺少操作区反馈/成功记录而失败，均为目标行为缺失的有效 RED。实现后同组目标测试通过；V7 首次试跑暴露新状态超过旧 `VARCHAR(16)`，修正迁移后通过。 |
| 后端全量及补充并发 | Colima 环境下 `backend/`: `./mvnw -q test`；定向 `BookingCoreApiTest#revision4KeepsMountainLockUntilTheLastConfirmedBookingIsCancelled+revision4RefusesConfirmedCancellationWithinTwentyFourHours`、`BookingCoreApiTest#revision4ConcurrentConfirmAndCancelLeaveNoBookedSlotBehind+revision4RequiresRejectReasonAndAllowsCancelThenReapply`、`BookingCoreApiTest#revision4ConcurrentCourseArchiveAndApplicationHaveOneClearOrder` | 全量 93 个测试、0 失败/错误/跳过，含真实 MySQL 8.4、V4→V7 迁移、旧规则回归及架构检查；随后新增的两条并发目标及旧键重放断言各自定向通过。全量首次发现旧测试仍假设空拒绝理由可用、最新迁移为 V6，另有新旧测试共用同日期时段的夹具冲突，修正后全量通过。并发检查仅覆盖测试竞争交错，未作生产负载压测。 |
| 前端回归与构建 | `frontend/`: `npm test`、`npm run typecheck`、`npm run lint`、`npm run build` | 最终 25/25 DOM 测试通过；类型、lint 和生产构建通过。覆盖排班保存/月历读取失败、申请写入成功/列表读取失败、课程管理、拒绝理由与学员取消。DOM 测试用模拟 API。 |
| 实际 Chrome 页面 | Chrome headless + 本地模拟 API，教练和学员各在 390px、1440px 点击操作并截图、读取 `documentElement.scrollWidth` | 四种组合均等于视口宽度；教练发布后表单旁显示“已发布”、日期格显示时段、详情展示时间；学员手机端提交后可见预约编号及取消入口。截图已人工检查。模拟 API 不等于真实部署浏览器验收；未执行生产端到端测试。 |
| 文档与格式 | `python3 ai-docs/check_docs.py`、`python3 ai-docs/test_check_docs.py`、`git diff --check` | 均通过。 |

revision 4 状态为 **VERIFIED**，尚未 RELEASED。V7 保留全部课程/预约/时段历史，生产迁移及旧应用回退仍需独立验证和授权；30 CAD/月预算未按真实负载量化。本轮未执行生产部署、Git commit 或 push。

#### 2026-10-01 月历日期点击反馈维护

用户报告教练管理日程的“添加日期”点击无反应。当前 revision 3 月历通过日期格直接点选，不再有独立“添加日期”按钮；本地 5173 服务提供的也是该版本。调查发现点击不可选日期时仅打开详情并静默返回；例如多伦多当地时间已晚于默认开始时间 10:00 时，点击今天不会加入选中列表，也没有原因说明。这违反已批准的 R3-AC-02/05 选择与错误反馈要求，属于既有范围内缺陷修复，无需提高 revision 或改变后端规则。

先在 `frontend/src/App.test.tsx` 增加“今天开始时间已过时解释原因、未来日期点击后显示选中结果”的交互测试，运行 `npm test -- --run App.test.tsx -t 'explains why a calendar date cannot be added'`：1 个目标测试因找不到就地 `alert` 而 RED。随后在 `BookingHome.tsx` 和 `booking.css` 中标示不可添加日期、点击后就地给出具体原因、选中后显示成功状态；已选日期即使时间随后过期仍可移出。修正测试自身的多元素查询后，同一命令 1/1 GREEN；补充 `npm test -- --run App.test.tsx -t 'lets the coach remove a selected date'` 验证时间流逝后仍能取消，1/1 通过。前端 `npm test -- --run` 为 27/27，通过 `npm run typecheck`、`npm run lint` 和 `npm run build`。此次只改前端交互，没有数据库、API、架构、成本或部署变更；两个索引保持 revision 4 / VERIFIED，未执行 Git commit 或 push。

#### 2026-10-01 本地教练提交按钮维护

用户再次报告“新增雪场”和“整天覆盖并发布”按钮点击无效，关联 [TODO-0012](../todo/0012-local-coach-submit-buttons-no-feedback.md)。调查确认运行环境版本脱节：5173 Vite 页面使用当前源码，但其 8080 代理后端是 2026-09-30 构建的旧镜像，运行包只有 V4；8088 容器前端也是旧版，仍显示“课程与时段”。因此新页面的雪场和整天覆盖写接口不能由旧后端处理。页面本身还存在反馈缺口：雪场失败提示远离按钮，空名称被浏览器校验静默拦下；排班在无活动雪场、无选中日期或无完整两小时块时禁用按钮，不解释原因。

在已批准的 R2 雪场管理、R3 月历排班、R4 可见反馈和 FE-01 范围内修复，无业务/API/数据库规则修订。先增加两个前端目标测试，执行 `frontend/`: `npm test -- --run App.test.tsx -t 'shows mountain validation|explains missing availability'`，2/2 因找不到就地 `alert` 而有效 RED。随后新增雪场输入及 API 错误改在本表单显示，并在写入成功后立即显示雪场；排班按钮在非忙状态允许点击，按雪场、日期、时间、容量和当天过期情况就地解释。相同目标测试 2/2 GREEN；现有成功创建并发布交互测试补充雪场成功提示断言。全量 `npm test -- --run` 为 29/29，`npm run typecheck`、`npm run lint`、`npm run build` 通过。

本地运行环境使用 `docker-compose --env-file deploy/.env -f deploy/compose.yaml up -d --build backend frontend` 重建前后端容器；数据库卷保留且启动日志显示现有 schema 已为 V7，无新迁移执行。8088 前端已包含本次两个就地提示，`/api/actuator/health` 返回 UP。测试教练经 8088 真实登录后，`GET /api/coach/mountains` 返回 200，两处写接口对空输入分别返回 400（说明路由已存在并执行业务校验）。Chrome headless 打开实际 8088 页面并登录教练：两个按钮均可点击，空雪场名称时表单旁显示“请输入雪场名称”，未选日期时发布表单旁显示“请先在月历中选择可用日期”。`backend/`: `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q -Dtest=AvailabilityRevision2ApiTest,AvailabilityRevision3ApiTest test` 为真实 MySQL 8.4 下 9/9 通过，覆盖有效雪场创建和覆盖发布。未对用户当前本地排班写入测试数据；本地容器更新不代表生产 RELEASED，未执行 Git commit/push。

#### 2026-10-01 局域网有效提交补充修复

用户在本地镜像更新后仍反馈两个按钮无响应。此前浏览器检查只点了空表单，未覆盖填好内容后生成幂等键的路径。进一步使用实际 Chrome 分别打开 `http://localhost:5173` 与本机局域网地址的 5173：前者为安全上下文且 `crypto.randomUUID` 可用；后者 `isSecureContext=false`、`crypto.randomUUID` 为 `undefined`，但 `crypto.getRandomValues` 可用。四处提交动作在 `try` 前直接调用 `crypto.randomUUID()`，导致局域网页面的有效输入点击后抛 `TypeError`、没有 HTTP 请求，也没有页面错误提示。

在 `frontend/src/App.test.tsx` 增加模拟缺少 `randomUUID` 的真实提交交互，运行 `npm test -- --run App.test.tsx -t 'submits mountain and availability when randomUUID is unavailable'`：目标因 `TypeError: crypto.randomUUID is not a function` 且雪场请求未发出而 RED。随后给请求键加入 `getRandomValues` 生成 UUID v4 的回退路径，并将四处请求键生成放入已有错误处理范围；相同测试 1/1 GREEN，确认雪场和覆盖请求都带有效 UUID。前端全量 `npm test -- --run` 为 30/30，`npm run typecheck`、`npm run lint`、`npm run build` 通过。

真实 Chrome 在局域网地址的 5173 页面登录教练后，填写一座已有雪场名称以安全触发重复名错误：页面发出 `POST /api/coach/mountains` 并在表单旁显示服务端错误。选中未来日期后，浏览器将覆盖请求截获并返回测试错误：确认 `POST /api/coach/availability/replacements` 发出且表单旁显示错误，没有改动排班。随后使用 `docker-compose --env-file deploy/.env -f deploy/compose.yaml up -d --build --no-deps frontend` 更新 8088 镜像，在其局域网地址重复上述两步，两个请求与就地反馈也均出现。此修复不改变服务端业务、数据库或成本；本地镜像更新不等于生产 RELEASED，未执行 Git commit/push。


### 2026-10-03 教练课程列表显示排序维护

此段保留排序维护的历史证据；当前显示行为见后面的隐藏已下架课程维护。

- 用户原话：“教练创建课程页面 已发布课程里，麻烦先列出来发布了的，下架的放在后面”；关联 [TODO-0020](../todo/0020-coach-course-display-order.md)。这属于 revision 4 已批准的教练课程管理展示细化，不改变发布/下架/预约业务、API/分页协议、架构、数据或成本；按 WORK-06 在原范围内维护，保留 approved_revision 4。
- 显示规则：仅「创建课程」的「已发布课程」列表，对当前已加载课程稳定分组；`active !== false`（兼容旧字段缺省）在前，`active === false` 在后。组内保留 API 返回/加载的原顺序，不原地修改 courses 状态。加载更多后重新分组，下架成功后进入后组。
- 边界：服务端仍按现有 ID 游标分页；只重排已加载内容，不自动读取所有页或改变接口顺序；学员课程选择和历史预约继续按既有契约。
- 文件：`frontend/src/BookingHome.tsx`；`frontend/src/CoachCourseOrdering.test.tsx`；必要 README/文档索引与票据。
- 实施：先建立混排/缺省 active/加载更多及下架后的 DOM 顺序目标测试并执行有效 RED，再实现同一测试 GREEN；完成前端回归与构建/lint。

| 验证 | 命令（前端命令在 `frontend/` 执行） | 实际结果 |
|---|---|---|
| 目标 RED → GREEN | `npm test -- --run src/CoachCourseOrdering.test.tsx` | 实现前 2/2 失败：初始列表仍混排，下架成功后该课程仍排在发布中课程之前；均为行为缺失。显示稳定分组后相同命令 2/2 通过，覆盖加载更多、组内顺序、缺省 active、原数组不变和下架后的控件状态。 |
| 前端回归 | `npm test -- --run` | 最终 5 个文件、50/50 通过；使用模拟 API 的实际页面交互。 |
| 类型与构建 | `npm run build` | 通过，含 `tsc --noEmit` 与 Vite 生产构建。首次类型检查指出新增测试使用了 `findByRole` 不支持的 `exact` 选项；删除该多余选项后全量测试与构建通过，此测试代码类型错误不计行为 RED。 |
| 静态检查 | `npm run lint` | 通过。 |
| 文档与空白 | 仓库根目录：`python3 ai-docs/check_docs.py`、`git diff --check` | 均通过；10 对功能/计划、20 张 ticket、索引/状态/review 门槛与 Markdown 链接一致。 |

本次维护为本地 **VERIFIED**；未改变后端/数据库，不额外运行后端回归。未执行生产部署、Git commit 或 push。

### 2026-10-03 教练课程列表隐藏已下架课程维护

- 范围与确认：用户先要求“已下架的直接删掉就好了，留着没意义”，在收到“只从列表移除、保留历史预约关联”的提案后明确回复“从列表移除，数据库保留”。关联 [TODO-0021](../todo/0021-remove-archived-courses.md)。这是已批准 revision 4 课程管理的局部展示调整，按 WORK-06 维护；替代同日 TODO-0020 的下架组展示，数据库保留、历史预约及原下架用例继续执行。
- 具体方案：`BookingHome.tsx` 的教练「已发布课程」由已加载课程中过滤 `active === false`，保留 `active` 缺省兼容及其余课程原顺序；下架成功后根据返回状态立即隐藏，失败时保留原卡片；初次读取及加载更多始终应用同一过滤。原课程数组、后端分页游标/API/数据存储不变。
- 空状态：以过滤后的可见数量判定。无可见课程且有后续游标时提示可加载更多，并保留按钮；没有后续页时显示“尚无已发布课程”。不自动读取全部页。教练预约申请及学员列表按既有规则，不增加永久删除接口或执行数据清除。
- 文件：`frontend/src/BookingHome.tsx`、`CoachCourseOrdering.test.tsx`、`App.test.tsx`；配对功能/计划、索引、前端 README 及 TODO-0021。
- 测试与步骤：先修改混排/加载更多和下架交互目标测试，补充全下架页及最后一门课程下架的空状态，实际执行 `npm test -- --run src/CoachCourseOrdering.test.tsx` 取得行为 RED；再实现至同一命令 GREEN，运行 `npm test -- --run`、`npm run build`、`npm run lint`、根目录 `python3 ai-docs/check_docs.py` 与 `git diff --check`，记录实际结果。
- 数据/架构/成本与恢复：仅前端渲染，保留现有 POST archive 的活动态与媒体引用处理、预约外键和历史快照；无 SQL/迁移/后端变更或新增费用。恢复旧展示不涉及数据恢复；8080 的 IDEA 后端继续使用，不重启服务。不执行 commit/push 或生产部署。
- 实际执行与验证：已按测试先行完成，本地 **VERIFIED**。同一批测试从行为 RED 到 GREEN；不触碰当前本地数据库、预约或 IDEA 运行服务。

| 验证 | 命令（前端命令在 `frontend/` 执行） | 实际结果与限制 |
|---|---|---|
| 目标 RED → GREEN | `npm test -- --run src/CoachCourseOrdering.test.tsx` | 实现前 3/3 因初始混排仍显示下架课程、下架成功后课程未隐藏、全下架页没有空状态而失败；实现后同一命令 3/3 通过。覆盖初次加载、加载更多、缺省 active、发布中顺序、原数组不变、下架最后一门课程和继续分页；断言前端不发 DELETE。 |
| 适用回归 | `npm test -- --run` | 5 个文件、51/51 通过。原课程编辑/下架/拒绝申请交互已改为验证下架后隐藏，并继续验证原预约处理；模拟 API，不代表真实数据库端到端验收。 |
| 类型/构建与静态检查 | `npm run build`、`npm run lint` | 均通过，构建包含 `tsc --noEmit`。 |
| 文档与空白 | 仓库根目录 `python3 ai-docs/check_docs.py`、`git diff --check` | 均通过；10 对功能/计划、21 张 ticket、索引/状态/review 门槛及 Markdown 链接一致。 |

本次没有后端变更，数据库保留依据是继续使用原 POST archive 活动态用例且不引入删除写入；既有后端验证证据保留，未重复运行后端或浏览器检查。未执行 Git commit/push 或生产部署。

### 2026-10-04 提交授权

用户明确要求“提交推送”，授权随 0009 当前工作区提交课程列表维护及关联文档；此前各阶段未提交记录作为历史保留。提交前统一检查见 [0009 配对计划](0009-course-selection-visuals.md)，不改变 revision 4、本地 VERIFIED 状态或生产部署边界。

### Revision 5：取消排班日期的未来 31 天上限（已验证）

#### Review 摘要与已核对事实

用户于 2026-10-05 要求：“还有，把可用日期的只能一个月以内限制去掉”。关联 [TODO-0033](../todo/0033-availability-future-date-limit.md)及配对功能 R5-AC-01–04。当前限制准确含义是“日期不能晚于多伦多今天加 31 天”，不是月份导航限制。它存在于前端选择、选中日期提示、提交校验和后端批量发布/整天替换两个用例中；仅调整页面不足以成功发布。

提议将这个日期上限取消，让教练可以提前安排更远的有效未来日期，包括跨月和跨年；每批最多 31 个日期、100 个生成时段，以及单次学员查询最多 31 天、教练按自然月读取的容量保护继续保留。过去日期/开始时间、DST、PENDING 和 BOOKED 保护、幂等与回滚继续沿用。现有月历查询年份有效范围不变，不引入新的提前天数或月份业务限制。

#### 具体步骤与预计文件

| 步骤 | 文件/模块 | 具体工作与完成条件 | 状态 |
|---|---|---|---|
| R5-P0 | 功能/计划、两个索引及 TODO-0033 | 整理范围、定位证据和验收；明确批准 revision 5 前不写测试或实现 | 已完成 |
| R5-P1 | `frontend/src/App.test.tsx`；新增 `backend/src/test/java/com/geer/snowboard/v2/bookings/AvailabilityRevision5ApiTest.java` | 批准后先创建较远未来日期的月历选择/提交及两个发布接口目标测试；执行同一命令取得行为 RED，记录具体失败；测试/环境故障不计 RED | 已完成 |
| R5-P2 | `frontend/src/BookingHome.tsx` | 去掉 `lastSelectable`、`invalidDate`、`hasPastSelection` 与日期点击中的未来 31 天上限；只对已过去日期/开始时间显示相应错误；保留日期个数/时段容量、选择撤销、跨月保存、发布反馈 | 已完成 |
| R5-P3 | `backend/src/main/java/com/geer/snowboard/v2/scheduling/application/service/SlotService.java` | `createBatch` 和 `lockReplacement` 去掉 `today.plusDays(31)` 检查，保留过去日期及未来时段判定并更新错误文案；不改变 `prepare` 容量、`open` 查询跨度、月份读取或事务/锁协议 | 已完成 |
| R5-P4 | 目标测试、既有前后端回归、文档 | 同一目标测试 GREEN；真实 MySQL 验证较远日期发布、整天替换、重放、月度读取及学员查询/申请；运行容量/时间/DST/权限/并发回归和构建，同步实际证据与状态 | 已完成 |

#### 目标测试与回归边界

- 前端使用固定时钟：选中今天后第 32 天、第 90 天及跨年日期，跨月往返后仍选中并可提交；提交体保持日期/时间/雪场字段一致；较远未来日期不会显示“开始时间已过”。保留今天开始时间已过时拒绝、已选日期过期后可以移除、过去日期只能查看，以及已有发布成功/刷新失败提示的回归。
- 后端采用真实 MySQL 8.4 Testcontainers：两个发布入口均接受第 32 天、第 90 天及跨年日期；同键重放返回原结果、异内容冲突；较远日期可通过月历和学员有界日期查询读取，学员可提交 PENDING 申请。
- 负向验收覆盖过去日期/已开始时段、32 个日期或超过 100 个生成时段的整批拒绝、重复日期、超过 31 天的学员查询跨度拒绝。复用并运行既有替换/预约测试，验证 PENDING 阻止覆盖、BOOKED 保留、雪场锁定、重叠冲突、并发、DST 与权限；既有覆盖缺失时补充必要用例。
- 前端目标命令：在 `frontend/` 执行 `npm test -- --run src/App.test.tsx -t 'beyond 31 days'`，新增目标测试名称使用该关键词；相同测试先 RED 后 GREEN。
- 后端目标命令：在 `backend/` 使用项目现有 Java 25/Maven 环境执行 `mvn -Dtest=AvailabilityRevision5ApiTest test`；实际执行时记录 Maven 路径/参数。Docker/时区/构建环境故障须先修复，不算目标 RED。
- GREEN 后执行前端 `npm test -- --run`、`npm run lint`、`npm run build`；后端 `mvn test` 包含业务、架构和真实 MySQL 回归；390/1440px Chrome 查看跨月选择及提交反馈。浏览器使用本地测试响应，不在生产创建排班或预约。
- 根目录执行 `python3 ai-docs/check_docs.py` 与 `git diff --check`。在功能、计划和票据记录真实命令/结果/限制，先区分 IMPLEMENTED 和 VERIFIED；生产发布另行授权与验收。

#### 数据、架构、成本与恢复

无 API 字段或数据库结构变化，无迁移或生产数据操作；后端改动仍在 scheduling 的应用服务内，不跨模块私表、不新增依赖或架构例外。按原有批次/查询容量约束处理请求，不新增付费资源。

回退时恢复本次前后端校验和文案即可；如日后已发布较远日期，这些时段/预约不得因恢复日期上限而删除，原有读取和预约保护应继续运行。审批前无需执行回退或数据操作。当前工作区已完成的 Logo 统一保留，属于独立的已验证维护范围。

#### 当前状态与审批边界

- [x] 定位实际前后端约束，完成配对文档、索引与 ticket。
- [x] 2026-10-05 用户“批准”revision 5，已记录日期、原话与范围。
- [x] 目标测试取得有效 RED，实施后同一命令 GREEN；补充远期 PENDING 保护断言后另执行目标复核。
- [x] 完成回归和浏览器验收，已记录实际证据并同步状态。

**VERIFIED（本地）**。已完成前后端实现、目标 RED/GREEN、适用回归、浏览器和文档检查；不含 Git commit/push、生产部署或生产数据写入。全量测试关停的独立问题记录于 TODO-0034，未混入本修订实施范围。

2026-10-05 规划阶段实际检查：根目录 `python3 ai-docs/check_docs.py` 通过（11 对功能/计划、33 张 ticket、链接/索引/状态/review 门槛）；`git diff --check` 通过。当时 `BookingHome.tsx` 的 Git 差异只有此前已验证的 Logo 复用，排班实现尚未修改。

#### Revision 5 实际实现、验证与限制（2026-10-05）

实际文件：`BookingHome.tsx` 移除日期选择、选中提示和提交的未来 31 天判断；`SlotService.createBatch` 与 `lockReplacement` 只拒绝过去日期，已有开始时间/容量/DST/锁协议保留。新增 `AvailabilityRevision5ApiTest`，在 `App.test.tsx` 新增两项交互测试；更新前后端 README、配对文档/索引及 TODO-0033。无 Flyway、API 字段、依赖、架构例外或付费资源变化。先进入 IMPLEMENTED，所有本功能验收与适用检查通过后进入 VERIFIED。

Maven 实际环境：`JAVA_HOME=/Users/geerhong/Library/Java/JavaVirtualMachines/ms-25.0.4.1-1/Contents/Home`；`DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock`；`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。在 `backend/` 使用 `/private/tmp/geer-delivery-toolchain/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/private/tmp/snowboard-v2-toolchain/m2` 加下表参数。初次使用旧临时 JDK 时缺少 `lib/jvm.cfg`，未执行到测试；改用已安装 ARM Java 25 后测试可运行，该环境故障不算 RED。

| 验证 | 实际命令/操作 | 实际结果与边界 |
|---|---|---|
| 前端目标 RED → GREEN | `frontend/`: `npm test -- --run src/App.test.tsx -t 'beyond 31 days'` | 实现前 2/2 因第 32 天 `2026-11-06` 只有“查看日期”而失败；实现后同一命令 2/2 通过。固定多伦多时钟，验证第 32/90 天、跨年、跨月保留选择、实际提交体、过去日期拒绝，以及 31 日期/100 时段容量。 |
| 后端目标 RED → GREEN | 上述 Maven 前缀 + `-Dtest=AvailabilityRevision5ApiTest test` | 有效 RED：8 项中 6 失败/0 错误；4 项因较远日期发布返回 400 而非 201，2 项因旧错误仍要求“未来 31 天”；DST 负向 2 项通过。实现后同一命令 8/8 GREEN。两个入口均接受第 32/90/370 天，重放、异内容冲突、远期月历/学员查询和 PENDING 申请均通过。 |
| 目标补充复核 | 同一 Maven 目标命令 | 补充远期 PENDING 阻止整批覆盖且所有原 OPEN 保留的断言，简化撤回时段的月历断言；最终再次 8/8 通过，进程正常退出。 |
| 后端全量回归 | 上述 Maven 前缀 + `test` | 37 类、152/152 通过，失败/错误/跳过均为 0，退出码 0 / BUILD SUCCESS。包含架构检查、预约并发、PENDING/BOOKED 保护、锁定、权限、DST、媒体和身份回归。完整测试结束后 Surefire 等待关停 hook 30 秒并强制结束测试 JVM；日志和转储单独记录 [TODO-0034](../todo/0034-full-backend-test-shutdown-timeout.md)。这条实际收尾异常不记为业务测试失败，也不声称测试资源关停已验证。 |
| 前端回归 | `frontend/`: `npm test -- --run` | 5 文件、63/63 通过，包含原有开始时间过期、移除过期选择、发布反馈和其他业务交互。 |
| 前端静态与构建 | `frontend/`: `npm run lint`、`npm run build` | 退出码均为 0；构建含 TypeScript `tsc --noEmit`。 |
| Chrome 页面检查 | 根目录 `node .local/availability-revision5/browser-check.mjs` | 390/1440px × COACH/STUDENT 共 4 项通过；跨月选择和远期提交、学员读取跨年 31 天窗口并申请均正确，无页面异常或横向溢出。截图已查看；浏览器用本地测试响应，不代表真实生产端到端。真实持久化/预约由上述 MySQL API 测试验证。 |
| 文档与空白 | 根目录 `python3 ai-docs/check_docs.py`、`git diff --check` | 通过；11 对功能/计划、34 张 ticket 的链接、索引、状态和批准门槛一致。 |

本地运行产物在忽略的 `.local/availability-revision5/`：`frontend-red.log`、`frontend-green.log`、`backend-red.log`、`backend-green.log`、`backend-final-target.log`、各项回归日志、`browser-result.json` 及 390/1440px 月历截图。没有运行生产写请求、SMTP 发送、迁移、提交推送或部署；前一任务已验证的 Logo 改动保留。本地 IDEA 后端需重启以加载新的校验，测试 Maven 编译不等于正在运行的 IDEA 进程已更新。
