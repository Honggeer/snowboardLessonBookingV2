---
id: "0007"
title: "预约邮件通知与取消规则提示"
status: VERIFIED
plan: "../implement-plan/0007-booking-email-notifications.md"
created: 2026-10-01
updated: 2026-10-06
contract_version: "1.6"
modules: [bookings, identity, frontend]
---

# 0007 — 预约邮件通知与取消规则提示

关联[实施计划 0007 revision 2](../implement-plan/0007-booking-email-notifications.md)、[项目契约](../PROJECT_CONTRACT.md)、[ADR 0001](../decisions/0001-v2-baseline.md)及[约课功能 0006](0006-post-login-booking-home.md)。revision 1 经用户批准并完成 Mailpit 本地验收。用户于 2026-10-01 批准 revision 2 “开始实现”；本机 SMTP 已切到现有 Gmail 参数，两类预约邮件均被 SMTP 接受，收件人随后确认“两封都收到”。

## 1. 目标、触发与范围

- 用户请求：“有学生预约教练要收到邮件通知，然后预约成功学生要收到邮件通知，并且发送链接……提交预约的时候要告诉学生，课程开始前24小时可以取消预约。”
- 将“有学生预约”解释为新申请成功进入 PENDING，将“预约成功”解释为教练确认后进入 CONFIRMED；学员提交申请时仍显示“待教练确认”，不误称已预约成功。此解释须随 revision 1 一起由用户 review。
- PENDING 新申请给该申请所属的唯一教练发邮件；CONFIRMED 给该申请所属学员发邮件。邮件包括课程、雪场、多伦多当地日期/时间及可打开该笔预约的站内链接。链接只是导航，访问仍需登录与服务端授权。
- 学员提交按钮旁预先说明：待确认申请可取消；教练确认后，须在开课至少 24 小时前取消。确认邮件也重复已确认预约的 24 小时规则。
- 本次不发送拒绝、自动拒绝、取消或课前提醒邮件；不新增短信、支付、生产部署、付费邮件服务或 v1 数据迁移。
- revision 2 只改变本机 Compose 的 SMTP 传递方式：读取现有 `deploy/.env` 中的 Gmail 参数，使身份验证、密码找回与预约通知均发往所填的真实邮箱。`deploy/.env` 被 Git 忽略。用户明确要求链接地址“按照参数传入……未来deploy的时候之间换参数”；现有 `V2_PUBLIC_URL` → `APP_PUBLIC_URL` 配置链保留。本机缺省链接为 `localhost:8088`，外部可用的 HTTPS 地址在未来部署时传入。

## 2. 验收条件

| ID | 场景/输入 | 预期行为 | 验证方式 | 证据/结果 |
|---|---|---|---|---|
| AC-01 | 学员成功创建一条 PENDING 申请 | 与申请同事务保存一条教练通知任务；教练邮件包含正确快照与指向该申请的链接；重复点击/幂等重放不重复建任务 | 真实 MySQL API、Mailpit 冒烟 | 通过：预约 API 测试、写任务失败回滚测试、本地 Mailpit 实际收件 |
| AC-02 | 教练将 PENDING 确认为 CONFIRMED | 与确认同事务保存一条学员通知任务；学员邮件明确“已确认”、课程详情、24 小时取消门槛及该笔预约链接；重复确认不重复建任务 | 真实 MySQL API、Mailpit 冒烟 | 通过：预约 API、worker 模板测试及本地 Mailpit 实际收件 |
| AC-03 | SMTP 暂时失败、进程重启或并发轮询 | 业务提交后任务可恢复、限量领取和退避重试；成功后记 SENT，超过上限记 DEAD 并可排查；重复发送窗口如实记录 | 真实 MySQL 并发/故障测试、邮件 worker 测试 | 通过：MySQL 竞争领取、lease 到期重领、SMTP 失败与第 8 次 DEAD 测试；至少一次投递窗口保留 |
| AC-04 | 发信前申请已取消/拒绝/确认，或已确认预约已取消 | 不发送过时的“待确认申请”或“已确认”邮件；标记 SKIPPED；业务状态不因 SMTP 失败改变 | worker 状态测试、真实 MySQL | 通过：worker 状态失效及失败测试 |
| AC-05 | 邮件链接在未登录、正确角色、错误角色或其他账号下打开 | 未登录先登录并保留目标；正确账号显示目标预约的最新状态；错误角色/他人不能读取目标信息；无效 ID 有明确提示 | API 授权测试、前端交互、真实浏览器冒烟 | 通过：单笔 API 401/403/404、前端登录恢复测试、Chrome 真实深链接与最新取消状态、Mailpit 冒烟 |
| AC-06 | 学员准备提交申请 | 按钮附近清晰展示“待确认可取消；确认后须在开课至少 24 小时前取消”；手机和桌面均可见，不声称提交即确认 | 前端交互与视觉检查 | 通过：前端测试，Chrome 390px 和 1440px 页面检查，均无横向溢出 |
| AC-07（revision 2） | Compose 使用已配置的真实 SMTP | 身份与预约邮件共用相同发件账号，通过认证/TLS 对用户控制的邮箱投递；未配置时仍可用 Mailpit | 配置测试先 RED 后 GREEN、本机小量真实发信、任务状态 | 2/2 配置测试通过；SMTP 登录成功，两类预约任务均 SENT；用户确认两封均收到 |
| AC-08（revision 2） | 真实邮件中的链接 | 使用 `V2_PUBLIC_URL` 参数生成；本机缺省链接只支持本机打开，部署时可换 HTTPS 地址，无需修改业务代码 | 配置校验、邮件内容检查 | 参数映射测试通过；本机缺省 localhost，未来部署需传可达地址 |

## 3. 业务规则与未决事项

- 既有预约状态及 24 小时门槛保持 0006 revision 4 规则；本功能不改变取消资格。正好提前 24 小时可以取消已确认预约，少于 24 小时不能取消；服务端时间是最终依据。
- 一条申请的 PENDING 通知只给所属教练；一条被确认的申请只给所属学员。其他学员的自动拒绝、单独拒绝及取消暂不触发邮件。
- 邮件内容使用已保存的课程、雪场和时间快照，时间按预约的 IANA 时区呈现，并标明时区；不包含会话令牌、密码、敏感操作链接。邮件中的链接由受控的公开站点地址配置产生，不能采用请求 Host。
- 邮件是否送达不作为申请/确认 API 的同步结果；页面继续立即显示持久化的预约状态。邮件任务写入失败则整个业务事务失败并回滚，避免成功操作丢失应有通知。

| 起始状态 | 用例/操作者 | 前置条件 | 目标状态 | 失败与副作用 |
|---|---|---|---|---|
| 无申请 | 申请/学员 | 0006 的所有申请条件满足 | PENDING | 新申请同事务记录教练邮件任务；幂等重放不新增 |
| PENDING | 确认/教练 | 0006 的所有确认条件满足 | CONFIRMED | 同事务记录学员邮件任务；其他申请按原规则自动拒绝但不发邮件 |
| PENDING/CONFIRMED | 拒绝或取消/既有操作者 | 0006 的前置条件满足 | 既有终态 | 使尚未发送的相关通知失效；已发邮件无法撤回，打开链接显示最新状态 |

| 未决问题 | 建议/选项 | 影响范围 | 是否阻塞 | 用户决定/已授权依据 |
|---|---|---|---|---|
| “预约成功”的确切时点 | 仅在教练确认后发学员成功邮件；提交时页面仅显示待确认 | 邮件触发与文案 | 否 | 用户 2026-10-01 “开始实现”批准 revision 1 |
| 邮件链接目标 | 直达该笔预约；未登录先登录，登录后继续 | 页面导航与两个只读 API | 否 | 用户 2026-10-01 “开始实现”批准 revision 1 |
| 其他状态通知 | 本版仅两封指定邮件 | 范围与成本 | 否 | 用户 2026-10-01 “开始实现”批准 revision 1 |

## 4. 模块、端口与依赖

- bookings 拥有预约和本版预约邮件任务表；邮件任务及状态检查保留在同模块，使 worker 能在发送前核对预约状态，避免 bookings 与独立 notifications 模块形成双向依赖。此选择仍符合 ADR 0001 的“同一 Java 应用 + MySQL 持久任务”；以后新增其他通知类型时再评估抽取通用模块。
- bookings 的 application service 在成功状态转换时经出站端口写入任务。bookings 的邮件 worker 经任务队列端口、账号联系方式端口和发信端口工作；MySQL 与 SMTP 位于各自 adapter。
- identity 发布仅供内部模块调用的账号邮件联系方式入站用例；bookings.adapter.out.module 使用它，不直接读 identity_account。客户端/API 不暴露邮箱。
- GET 单笔预约由 bookings 公开入站用例按服务端 actor 做本人/所属教练校验；Web adapter 不替代业务授权。
- domain 与 port 保持纯 Java，跨模块单向 bookings → identity。适用 WORK-02/08/09、ARCH-01~09、DEP-01~07、DATA-01/03、SEC-01~03、API-01~04、FE-01、ASYNC-01~04、OPS-01/05；预期不增加架构违规基线。相关 ADR 0001 无需修订。

## 5. API 与前端契约

| Method / Path | 身份与资源权限 | 输入 | 输出 | HTTP/业务错误 | 幂等行为 |
|---|---|---|---|---|---|
| POST /api/bookings（既有） | 学员 + CSRF | 既有申请体及 Idempotency-Key | 既有 PENDING/最新状态，不暴露邮件内部状态 | 沿用 0006；任务落库故障为服务端失败且整个事务回滚 | 相同键不增加邮件任务 |
| POST /api/coach/bookings/{id}/confirm（既有） | 所属教练 + CSRF | 预约 ID | 既有 CONFIRMED/最新状态 | 沿用 0006；任务落库故障回滚确认 | 重复确认不增加邮件任务 |
| GET /api/bookings/{id}（新增） | 预约所属学员 | 预约 ID | 该笔预约的现有公开 DTO 和最新状态 | 401 未登录、403 错角色、404 不存在或不属于本人 | 只读 |
| GET /api/coach/bookings/{id}（新增） | 预约所属教练 | 预约 ID | 该笔预约的现有公开 DTO 和最新状态 | 401 未登录、403 错角色、404 不存在或不属于该教练 | 只读 |

- 邮件深链接拟为公开站点地址 + /#/my-bookings/{id} 或 /#/coach-applications/{id}。前端识别路径、登录后继续打开对应标签并突出目标预约；若目标状态已变化，显示当前状态。无效/无权目标显示安全的提示和返回列表入口。
- 同源 Cookie/CSRF 维持现状；只读 GET 不产生状态变化。链接中仅有预约 ID，不含可复用的认证秘密。登出或换账号后不得显示上一账号的预约数据。
- 申请信息区域新增可见规则：“待确认申请可取消；教练确认后，须在课程开始至少 24 小时前取消。”成功反馈继续明确“待教练确认”。

## 6. 数据、事务与并发

- 追加 Flyway V8 创建 bookings_mail_task：预约 ID、事件类型（APPLICATION_RECEIVED / BOOKING_CONFIRMED）、收件账号 ID、状态、尝试次数、下次尝试时间、领取有效期、最后错误类别、创建/发送时间。唯一约束为 (booking_id,event_type)；领取索引支持状态 + 下次尝试时间。表由 bookings 拥有，不把密码或邮件正文存库。
- 新申请/确认沿用 0006 的 READ COMMITTED、日/时段/学员锁顺序；在同一事务末尾插入任务，唯一键和现有业务幂等防止重复。任务入库错误使业务事务回滚，SMTP 发送在事务外执行。
- worker 短事务使用 FOR UPDATE SKIP LOCKED 领取最多 10 条、30 秒 lease；发信前重读预约状态，不持业务锁进行 SMTP。进程重启后到期 CLAIMED 可重新领取。SMTP 成功但记 SENT 前崩溃可能重复发送，采用 at-least-once 语义；邮件内容不承诺只收到一次。
- V8 只追加新表，不追补旧预约的邮件；旧应用可忽略表，应用回退需停用新 worker，保留已迁移表及历史任务。生产迁移须另获部署授权与备份/恢复验证。

## 7. 异步任务与外部依赖

- 复用项目已用的 JavaMailSender、SMTP 配置与本地 Mailpit；新增预约邮件 worker 的独立开关/轮询间隔，避免依赖身份邮件 worker 的开关。无第三方 SDK 或新付费服务。
- 每轮领取上限 10；SMTP 使用既有连接/读/写超时。失败按 1、2、4、8、16、32、60 分钟退避，最多 8 次后 DEAD；DEAD 保留错误类别和排查/人工重试步骤，不能误标 SENT。SENT/SKIPPED 任务定期清理，DEAD 保留待处理。
- 发送前 APPLICATION_RECEIVED 只允许 PENDING，BOOKING_CONFIRMED 只允许 CONFIRMED，否则 SKIPPED。发送前状态检查与 SMTP 之间可能发生取消，不能原子撤回已发邮件；链接始终重新鉴权并读取最新状态，邮件文案提示“以页面当前状态为准”。
- 公开站点地址需由 APP_PUBLIC_URL 配为收件人可访问的 HTTPS 地址；本地 Mailpit 的 localhost 地址仅适合本机验证，局域网/正式环境须配置对应域名或可达地址。不得从请求 Host 拼接邮件链接。
- revision 2 的只读调查确认 `deploy/.env` 已有 Gmail SMTP 主机、账号、应用密码、发件人和 TLS 开关，且发件人与账号一致；本地 Compose 后端固定 Mailpit 并未读取它们。身份与预约 SMTP 适配器已经共用 Spring `JavaMailSender`。只验证了 TCP/STARTTLS 握手，未登录 SMTP 或发送真实邮件。

## 8. 实施计划关联与实际变更

- [x] 完成 revision 1 契约与独立实施计划草案。
- [x] 用户明确 review 通过 revision 1，记录批准依据。
- [x] 批准后先创建目标测试并运行到因行为缺失而失败（RED）。
- [x] 实现预约邮件任务、授权直达链接与取消规则提示。
- [x] 同一测试 GREEN，完成真实 MySQL、Mailpit、前端和架构回归。
- [x] 同步功能、计划、索引和实际验证证据。
- [x] revision 2 经用户 review 后按测试先行流程切换本机 Compose 邮件配置，验证 SMTP 接受与本机链接限制。
- [x] 收件人核对两封测试邮件最终到达；2026-10-01 用户确认“两封都收到”。

| 日期 | 实际变更/文件 | 理由及与计划的差异 |
|---|---|---|
| 2026-10-01 | Flyway V8、bookings 邮件队列/worker/SMTP/受控链接、identity 内部邮箱用例、预约单笔 GET、前端深链接与提示、Compose/冒烟脚本 | 按 revision 1 实现；未改变已批准事件范围或收费服务 |
| 2026-10-01 | revision 2 功能与计划文档 | 根据用户真实邮箱要求准备待审变更；尚无代码或配置改动 |
| 2026-10-01 | Compose 参数映射、配置测试、README | Gmail `MAIL_*` 由被忽略的 `.env` 注入；缺省 Mailpit；`V2_PUBLIC_URL` 继续参数化；无业务/数据库改动 |

## 9. 验证证据

| 日期 | 环境/工作目录 | 实际命令/人工步骤 | 实际结果 | 限制/待处理 |
|---|---|---|---|---|
| 2026-10-01 | 仓库只读调查 | 阅读 0006、身份邮件队列/worker、预约状态与前端入口 | 确认申请为 PENDING、确认后为 CONFIRMED；SMTP/Mailpit 已存在，尚无预约邮件任务 | 设计调查，不是实现验证 |
| 2026-10-01 | backend/、MySQL 8.4 Testcontainers | 新增目标 API 测试后定向运行，GET 单笔预约返回 404；实现后 `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q test` | 有效 RED 后 GREEN；全量 101 个测试，0 失败/错误，含迁移、事务、并发、权限和架构 | 初次全量命令漏设 Colima 变量是测试环境故障；补变量后通过 |
| 2026-10-01 | frontend/ | 新增目标测试后 `npm test -- --run -t 'cancellation cutoff|mailed booking link'`，再运行 `npm test`、`npm run typecheck`、`npm run lint`、`npm run build` | 文案/深链接目标测试先 RED 后 GREEN；全量 32/32、类型、lint、构建通过 | 无 |
| 2026-10-01 | 本地 Compose + Mailpit + Chrome | 重建 backend/frontend；`python3 deploy/smoke_booking_mail.py`、`python3 deploy/smoke.py`；Chrome 390px/1440px 打开邮件深链接并检查提示 | 两封邮件实际进入 Mailpit，单笔链接和权限通过；浏览器展示取消后的最新状态，提示均在视口内 | 仅本机 Mailpit；真实 Gmail/HTTPS/生产部署未验证 |
| 2026-10-01 | 本机只读配置/网络检查 | 检查 `.env` 与 Compose 的 SMTP 键是否传递；对配置 SMTP 端口执行 TCP/STARTTLS 握手 | 发现 Compose 写死 Mailpit；Gmail TCP 与 STARTTLS 成功 | 未测试 SMTP 登录或真实收件 |
| 2026-10-01 | Compose 配置测试 | `python3 -m unittest deploy.test_compose_mail -v` | 新测试先因 Mailpit 写死而 RED；参数映射后 2/2 GREEN，现有 `.env` 中 7/7 `MAIL_*` 参数映射一致 | 测试只用临时假凭据，不记录真实密码 |
| 2026-10-01 | 本地 Compose + Gmail SMTP | SMTP 登录、重建后端、受控学员申请与教练确认；轮询邮件任务并取消测试预约 | 教练申请与学员确认两种任务均 SENT；无待发送/失败任务，后端保持真实 SMTP，基础健康冒烟通过 | Gmail SMTP 已接受；收件箱最终到达尚待用户确认；链接为本机 localhost |
| 2026-10-01 | 收件人反馈 | 向用户核对教练新申请通知和学员预约确认通知是否到达 | 用户回复“两封都收到”，AC-07 的最终到达条件满足 | 本机链接仍为 localhost；公网 HTTPS/生产投递尚未验证 |

- 任务写入失败回滚、幂等任务、8 次重试 DEAD、过时邮件 SKIPPED、并发领取及 lease fencing 均纳入真实 MySQL 测试。SMTP 发出后进程中断可能导致重复投递，属于已记录的至少一次语义。

## 10. 部署、成本与恢复

计划批准不授权生产部署或新增付费服务。预计复用现有 SMTP 与单机 Java/MySQL；新增任务表、轮询和少量邮件流量。实施时记录任务数量、重试上限、日志与预算影响；真实投递和域名/HTTPS 需在被授权的部署环境另验。回滚保留 V8 数据及旧应用兼容。

revision 2 拟在本机复用用户已存的 Gmail 应用密码，不新增服务或预计固定费用；实际投递受邮箱服务商配额、限流及垃圾邮件判断影响。切换后注册验证、找回密码也会真实外发。若测试失败或不再需要外发，恢复 Mailpit 参数并重建后端；已发出的邮件不可撤回。部署到公网仍需独立授权。

## 11. 交付状态与后续

revision 1 于 2026-10-01 完成代码并经全量回归、本地 Mailpit 与真实浏览器检查达到 VERIFIED。revision 2 配置已完成，真实 Gmail SMTP 接受两类预约邮件，用户确认两封均收到，当前为 VERIFIED。公网 HTTPS 链接和生产部署均未完成。

- 2026-10-06 后续扩展：[0012 课前邮件提醒](0012-lesson-reminder-emails.md) revision 1 完成本地验证。在两种原通知之上增加双方独立的延时提醒、V12 及专用有界调度；0007 既有通知/权限契约和历史批准不变，详见 0012 实际证据，未进行本次生产发布。
