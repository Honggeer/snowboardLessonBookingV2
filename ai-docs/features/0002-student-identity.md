---
id: "0002"
title: "学员注册、邮箱验证与账号登录"
status: VERIFIED
plan: "../implement-plan/0002-student-identity.md"
created: 2026-09-28
updated: 2026-10-05
contract_version: "1.3"
modules: [identity, frontend]
---

# 0002 — 学员注册、邮箱验证与账号登录

2026-10-06 联系电话扩展单独见 [0011](0011-student-contact-phone.md) 与[配对计划](../implement-plan/0011-student-contact-phone.md)，已按批准的 revision 1 完成本地验证（VERIFIED）：用户确认首次预约前必填和联系确认用途，注册/登录规则不变；保存和教练显示已完成本地验证，独立验收证据由 0011 维护。

2026-10-04–05 生产注册邮件调查见 [TODO-0030](../todo/0030-production-registration-mail-not-received.md)：目标账号已注册未验证，首次与重发任务均为 SENT / attempts 1，SMTP 接受、收件地址及配置核对正常。用户随后确认邮件在垃圾邮件中，实收已确认；Gmail 的具体分类依据和验证链接使用尚未核对，不据此宣称全部身份流程验收或未来收件分类已修复。

关联[实施计划 0002](../implement-plan/0002-student-identity.md)。revision 1 曾按 RED/GREEN 实现并通过本地验证；用户随后要求页面按已选视觉稿呈现，并把密码下限改为 8 位。revision 2 已获用户批准且完成本地实现与技术检查；用户已确认关闭视觉 ticket，当前本地验收状态为 VERIFIED，未生产发布。

## 1. 目标、触发与范围

- 用户请求：“就做账号登录注册吧，注意安全session问题，学员注册必填项有姓名，当前水平（三个选项：零基础，入门，进阶），邮箱（需验证），密码。UI设计我想要跟v1不同一些，可以聊聊吗？”
- 2026-09-29 用户反馈：“页面跟你给的样板完全不一样……我想要和 geer blue desktop png 一模一样的，logo 我觉得那个样子就很完美，我喜欢线条锋利的，其次，密码长度八位就可以了。”用户随后明确密码为至少 8、最多 128 个字符，并选择按桌面稿视觉使用概念图照片。
- 学员能用四项必填信息注册，收到验证邮件，完成验证后用邮箱和密码登录，并能退出登录；刷新页面后可恢复有效会话。
- 注册表单不要求 v1 的电话、生日、性别、身高或体重。水平只有上述三个选项。密码确认如采用，仅在前端检查，不作为新的账号资料。
- 本次包含注册、验证、重发、学员与唯一教练登录、当前身份、退出及对应页面和安全控制。用户已选择教练账号私下初始化，公开入口不得创建教练账号；本地初始化命令见[后端说明](../../backend/README.md)。
- 本次暂不包含预约、课程、个人资料编辑、密码重置、社交登录、长期“记住我”、v1 数据迁移或生产发布；密码重置作为后续需求另行规划。
- v1 仅供对照：其雪景背景、深色毛玻璃表单及浏览器 localStorage 认证令牌不作为 v2 设计/认证方案。

## 2. 验收条件

| ID | 场景/输入 | 预期行为 | 验证方式 | 证据/结果 |
|---|---|---|---|---|
| AC-01 | 学员填写姓名、三选一水平、邮箱、密码注册 | 建立待验证账号，产生验证邮件；缺项、非法水平或重复提交不会建立重复账号 | API、真实 MySQL、前端交互 | revision 1 的并发/API/Mailpit 回归通过；revision 2 的 8 字符注册与真实 MySQL API 验证通过 |
| AC-02 | 有效验证链接；过期、伪造或复用链接 | 仅有效且未使用的 token 使账号变为已验证；其余不激活账号 | API、真实 MySQL | 通过：IdentityFlowTest 与本地邮件链接 |
| AC-03 | 未验证、错误密码、未知邮箱及已验证账号登录 | 仅已验证且凭据正确的账号获取会话；失败响应不泄漏账号存在性 | API、安全测试 | 通过：IdentityFlowTest、IdentityApiTest、IdentityLoginTimingTest；对外使用统一错误文案，未知邮箱也执行密码比对 |
| AC-04 | 有效会话下刷新、退出、过期、后台重启 | 会话按批准的存储/超时规则恢复或失效；退出后旧会话不可再用 | API、浏览器、真实 MySQL | 通过：JDBC Session、30 分钟闲置/12 小时上限测试、Compose 后端重启冒烟 |
| AC-05 | 跨站写请求、缺失/失效 CSRF、固定旧 Session ID | 写请求被拒绝；登录后 Session ID 更换；认证 Cookie 安全属性正确 | 安全测试、实际 HTTP 响应 | 通过：IdentityApiTest 浏览器 token、Session ID 轮换、旧 CSRF 拒绝；本地 Cookie HttpOnly/SameSite=Lax，正式 HTTPS 待部署验证 |
| AC-06 | 手机、平板和电脑端完成注册、验证、登录、退出 | 桌面登录页在 1672×941 对照蓝色桌面稿还原锐角 GEER 图形字标、明亮概念雪景、蓝色刻滑线、斜切白色分界、右侧标题/表单/页脚的比例与位置；手机登录/注册分别对照对应移动稿，注册照片下缘渐变；真实表单与状态可交互、可访问、无横向溢出 | 前端交互与构建、目标视口截图对照、键盘与触控检查 | revision 2 前端 7 测试及构建/typecheck/lint 通过；Chrome 360/390/768/1440/1672px 无横向溢出，桌面与移动截图已对照；用户确认可关闭 TODO-0010 |
| AC-07 | 高频登录/重发验证请求、邮件发送失败 | 按批准阈值限制滥用；发送失败可恢复，不能把失败显示成已送达 | API、故障注入 | 通过：MySQL 限流、MailWorkerTest 失败/退避/旧任务抑制、Mailpit SMTP 冒烟；真实 Gmail 待上线验证 |
| AC-08 | 教练账号登录、学员尝试提交教练角色 | 仅私下初始化的教练可按教练身份登录；公开注册始终建立学员身份 | API、真实 MySQL | 通过：唯一教练/验证后登录/公开提权 API 测试、初始化命令单元测试及非 Web 整应用启动集成测试；2026-09-30 经用户明确授权在本地测试库创建并验证实际教练账号 |
| AC-09 | 注册与教练私下初始化使用 7、8、128、129 个 Unicode 字符的密码 | 7 与 129 拒绝；8 与 128 接受；前后端边界一致，既有较长密码仍可登录；无字符类别限制 | domain/API、前端交互、真实 MySQL 回归 | domain 与前端边界测试通过；真实 MySQL API 验证 7 拒绝、8 注册/验证/登录；全量回归含既有长密码登录 |

## 3. 业务规则与上线待验事项

- 公开注册仅创建学员身份；不得通过请求体选择教练角色。教练账号不公开注册，通过一次性受控初始化命令在本地交互输入凭据，不把密码写进仓库、日志或迁移脚本；2026-09-30 用户明确授权 Codex 代为初始化本地测试账号。
- 已实现：邮箱验证前不可登录；验证成功也不自动登录。验证 token 由随机 ID 与服务端密钥派生、一次性且有时效，数据库只存摘要。重发使旧 token 失效。
- 已实现：Session 闲置 30 分钟、最长 12 小时过期，不提供长期“记住我”；登录更换 Session ID，退出令服务端会话失效。会话保存在 MySQL 中，后端进程重启后可恢复。
- 已实现：验证链接 24 小时有效；登录失败按邮箱和可信来源地址分别限流，注册/重发也限流；阈值见计划。revision 2 的密码范围为 8–128 个 Unicode 字符，允许空格、不强制字符组合；散列与限流规则未变。

| 起始状态 | 用例/操作者 | 前置条件 | 目标状态 | 失败与副作用 |
|---|---|---|---|---|
| 不存在 | 注册/访客 | 四项合法、邮箱未占用 | 待验证 | 失败不建立已验证身份 |
| 待验证 | 验证/邮件持有人 | token 有效且未使用 | 已验证 | 无效 token 不改变账号 |
| 待验证 | 重发/访客 | 通过限流 | 待验证，旧 token 失效 | 邮件发送失败可再次请求 |
| 已验证 | 登录/账号本人 | 密码正确 | 已登录 Session | 失败不建立认证 Session |
| 已登录 | 退出/账号本人 | 有效 Session 与 CSRF | 已退出 | 旧 Session 不再授权 |

| 决策或待验证事项 | 已批准值与当前状态 | 影响范围 | 是否阻塞本地验证 | 用户决定/已授权依据 |
|---|---|---|---|---|
| revision 2 视觉目标 | [电脑稿](../design/0002-geer-blue-desktop.png)为桌面登录页明确目标，[手机登录稿](../design/0002-geer-blue-mobile-login.png)和[手机注册修订稿](../design/0002-geer-blue-mobile-register-v2.png)为移动目标；按稿中概念照片和锐角 GEER 字标呈现。用户原始照片保留在设计目录作为来源记录 | 桌面精度、注册桌面延展、移动响应式 | 本地实现与截图检查完成，用户确认关闭 TODO-0010 | 用户本次明确要求与样板一致，并选择概念图照片，批准 revision 2；2026-09-29 确认可结案 |
| 正式验证邮件发送 | 按 v1 的 Gmail SMTP 587/STARTTLS 协议提供独立 v2 配置；本地通过 Mailpit，正式应用密码与送达未验证 | 真实邮件交付与上线 | 否；上线前需验证 | 用户：“可以按照v1的那种吗？”、批准 0002 revision 1 |
| 本次教练登录 | 学员与唯一教练都能登录；教练通过私下初始化命令创建、禁止公开注册；命令与唯一性已测试 | 角色/API/初始化 | 否；本地测试账号已按用户本次授权创建并验证 | 用户原选择“教练账号由我私下初始化”；2026-09-30 改为“你来帮我吧，反正是测试数据，你帮我创建了拉倒” |
| Session 与验证时效 | 30 分钟闲置、12 小时绝对上限、24 小时验证链接；不设长期记住 | 安全与体验 | 否 | 用户批准 0002 revision 1 |
| revision 2 密码规则与邮箱比较策略 | 密码下限已改为 8 个 Unicode 字符，上限 128 不变、不要求组合；保留展示邮箱、比较键小写、不做 Gmail 特殊归并；既有密码摘要仍有效 | 注册、教练初始化与前端校验 | 已实现并在 domain、前端和真实 MySQL API 测试验证 | 用户回复确认“至少 8 位，上限 128 位”，批准 revision 2 |

## 4. 模块、端口与依赖

- identity 模块拥有账号、注册时姓名/水平、邮箱验证记录；其他模块未来通过公开入站端口读取当前学员资料，不直接读 identity 表。不会新建空 students 包。
- 入站用例：注册、验证、重发、认证身份查询；认证/登出通过 Spring Security 入口编排。domain 与 application.port 保持纯 Java。
- 出站端口：账号/验证记录存储、验证 token、密码散列、邮件发送；时间通过应用层 Clock 注入。身份模块的原子 SQL 由持久化适配器内的 JdbcTemplate 执行，Spring Security 与 SMTP 位于 adapter/bootstrap，未把数据库或 Web 类型带入 domain/端口。
- 涉及 PROD-01、WORK-02、WORK-09、ARCH-01 至 ARCH-07、DEP-01 至 DEP-06、DATA-01、SEC-01 至 SEC-03、API-01 至 API-04、OPS-01。
- 架构例外：无。相关 [ADR 0001](../decisions/0001-v2-baseline.md)。若教练初始化或模块归属改变基线，先修订计划/ADR。

## 5. API 与前端契约

下列为已批准并实现的 v2 API 契约。成功响应返回明确 DTO，错误使用带 HTTP status 的统一问题响应，不继承 v1 的成功/失败混合 envelope。所有 JSON 响应不得返回密码、密码摘要、验证 token 或 Session ID。

| Method / Path | 身份与资源权限 | 输入 | 输出 | HTTP/业务错误 | 幂等行为 |
|---|---|---|---|---|---|
| GET /api/auth/csrf | 访客/登录者 | 无 | CSRF token 与请求头名称 | 5xx | 可重复获取 |
| POST /api/auth/register | 访客 + CSRF | 姓名、水平、邮箱、密码 | 待验证提示 | 400/429/503 | 同邮箱不创建第二个账号；响应避免枚举 |
| POST /api/auth/email-verification | 访客 + CSRF | token | 验证成功提示 | 400/410/429 | token 一次性；重复不再次激活 |
| POST /api/auth/email-verification/resend | 访客 + CSRF | 邮箱 | 通用提示 | 400/429/503 | 限流，不泄漏邮箱存在性 |
| POST /api/auth/login | 访客 + CSRF | 邮箱、密码 | 当前身份 DTO、Set-Cookie | 400/401/429 | 成功建立新 Session |
| GET /api/auth/me | 登录者 | 无 | id、角色、姓名、水平 | 401 | 只读 |
| POST /api/auth/logout | 登录者 + CSRF | 无 | 退出成功 | 401/403 | 旧 Session 失效 |

- revision 2 页面目标：桌面登录页按照[蓝色桌面稿](../design/0002-geer-blue-desktop.png)还原构图和视觉，而非只借用配色。左侧显示明亮概念雪景、稿中锐角 GEER 字标与蓝色刻滑线；白色斜切分界通向右侧可交互表单。右侧使用稿中的“欢迎回来”、辅助文案、字段顺序与比例，移除当前页面多余的顶部导航、左侧大段口号和重色蒙层。概念图照片已由用户明确选择；[原始照片](../design/0002-geer-photo-original.png)仍保留作为素材来源。
- 手机端分别按照[登录稿](../design/0002-geer-blue-mobile-login.png)和[注册修订稿](../design/0002-geer-blue-mobile-register-v2.png)还原品牌区与表单；注册照片下缘柔和渐变。桌面注册页沿用桌面登录稿的斜切构图、品牌区与表单视觉，并容纳移动注册稿中的姓名、当前水平、邮箱和密码字段。页面使用批准的视觉素材，表单与状态保留真实 HTML 交互、键盘可达与响应式布局。
- 字段有明确标签、密码可见切换、错误反馈、提交中禁重复提交。验证页处理有效/过期链接并给出重发入口。避免 v1 的整屏背景、毛玻璃表单和蓝色渐变按钮；推荐蓝色为单一实色强调。
- 前端只在内存中保存当前身份；刷新时请求 /api/auth/me。退出清空内存中的身份与受保护查询结果。认证凭据不进 localStorage/sessionStorage。
- 验证 token 置于链接片段，由页面读取后立即从地址栏移除并提交 POST；页面不加载第三方资产，后端配置 `Referrer-Policy: no-referrer`。
- 同源 /api；认证 Cookie 为 HttpOnly、SameSite=Lax，正式 HTTPS 环境必须 Secure；CSRF token 通过独立入口取得并在写请求头提交，登录/退出后刷新 token。本地 HTTP 仅在 local 配置下处理 Secure 差异。

## 6. 数据、事务与并发

- Flyway V2 在独立 v2 库增加账号、验证 token、邮件投递任务、认证限流记录及同库 Spring Session JDBC 表；Schema 由 Flyway 创建，不允许运行时自动重建。DDL、约束及索引见迁移文件。
- 邮箱唯一性由数据库约束承担；注册并发下只有一个账号成功创建，返回统一提示。验证通过条件更新保证一次性；重发与验证竞争时按事务与行锁/版本条件确定唯一有效 token。
- 存储密码自适应单向摘要，不存明文；验证 token 仅存摘要和到期时间。会话记录按到期清理。
- v1 无数据导入。上线前须备份；Flyway DDL 不假定可回滚，恢复依赖备份和应用版本协调。

## 7. 异步任务与外部依赖

- 邮件适配器支持 v1 风格的 Gmail SMTP 587/STARTTLS，但 v2 使用独立配置与新应用密码，不复制 v1 凭据；本地实际联调使用 Mailpit。注册事务同时保存待验证账号、验证凭据与投递任务；提交后由同一 Java 应用发送，失败可重试，进程重启可恢复。原始验证密钥不存数据库/日志；失败不能标为已送达。界面显示中性“请检查邮箱/可重发”提示，不承诺邮件已经送达。
- SMTP 连接/读/写设置有界超时；任务限量领取、失败退避、重发和注册限流。邮件可能在发送后、写回成功前重试，因此同一有效链接可能重复投递；已失效且尚未发送的任务会跳过。与重发同时进行的在途邮件可能随后到达，但旧链接不能验证。
- S3/媒体、预约提醒均不适用本功能。

## 8. 实施计划关联与实际变更

- [x] 建立 0002 功能/计划与索引，提交 revision 1 review。
- [x] 用户明确批准 0002 revision 1；条件为清理无用设计图并严格遵守六边形架构。
- [x] 获批后先建测试，运行有效 RED；再实现至同一用例 GREEN。
- [x] 完成实现、回归、文档与证据更新。
- [x] 用户明确批准 revision 2，按 RED/GREEN 完成视觉还原和密码 8–128 字符变更。
- [x] 用户看实际页面或截图后确认最终视觉；完成 [TODO-0010](../todo/0010-identity-visual-mismatch.md) 的结案。

| 日期 | 实际变更/文件 | 理由及与计划的差异 |
|---|---|---|
| 2026-09-28 | 本功能文档、配对计划、批准视觉稿及索引 | 用户提出 0002，选定蓝色视觉方向并要求手机注册照片下缘渐变；实现前仅文档和设计稿 |
| 2026-09-28 | identity 领域、端口、应用服务、MySQL/密码/邮件/Web 适配器、Flyway V2、后端测试 | 完成注册/验证/限流/教练初始化/Session 与邮件投递；模块内六边形边界由架构测试验证 |
| 2026-09-28 | 前端身份页面、原始照片、Docker Mailpit、本地冒烟与运行文档 | 登录与注册移动端照片下缘渐变到表单；本地完整流程可复现 |
| 2026-09-29 | revision 2 功能契约与实施计划 | 根据用户实际页面截图记录视觉偏差和 8 位密码新决策；用户随后批准该 revision |
| 2026-09-29 | 前端蓝色稿资产、响应式 HTML/CSS、交互测试；后端 domain 与 API 测试 | 桌面按稿中锐角字标、照片、刻滑线与斜切布局实现；手机登录/注册分别按稿裁切并渐变；密码范围改为 8–128 Unicode 码点，无 schema 或 API 路径变化 |

## 9. 验证证据

| 日期 | 环境/工作目录 | 实际命令/人工步骤 | 实际结果 | 限制/待处理 |
|---|---|---|---|---|
| 2026-09-28 | Java 25 / Colima MySQL 8.4 | `./mvnw -q test`（设置 DOCKER_HOST 与 TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE） | 34 tests，0 failures/errors；含 ArchUnit、MySQL、API/Session、邮件队列 | 仅本地环境 |
| 2026-09-28 | Node 24 / Chrome / 390–1440px | `npm test`、`npm run typecheck`、`npm run build`、`npm run lint`；Chrome 360/390/768/1440px 截图、键盘选水平 | 4 前端测试通过，构建/lint/typecheck 通过；无横向溢出 | 浏览器人工核对不替代正式设计验收 |
| 2026-09-28 | 本地 Compose + Mailpit | `python3 deploy/smoke.py`、`python3 deploy/smoke_identity.py`、`SMOKE_RESTART_BACKEND=1 python3 deploy/smoke_identity.py` | 图片可访问；SMTP 收件、验证、登录、重启后 Session 恢复、退出通过 | 正式 Gmail/HTTPS/EC2 未验证 |
| 2026-09-29 | Java 25 / Colima MySQL 8.4 | `./mvnw -q -Dtest=RegistrationTest test` RED→GREEN；`./mvnw -q test`；新增 8 字符 `IdentityApiTest` 单独运行 | 14 套件、36 测试 0 failure/error；新增真实 MySQL API 测试验证 7 拒绝、8 注册/验证/登录 | 全量回归后添加 API 用例并单独通过；正式 Gmail/HTTPS/EC2 未验证 |
| 2026-09-29 | Node 24 / Chrome 360–1672px | `npm test -- --run` RED→`npm test` GREEN；`npm run typecheck`、`npm run lint`、`npm run build`；桌面/手机截图与宽度检查 | 7 前端测试及构建/typecheck/lint 通过；桌面/手机无横向溢出，[桌面实际图](../design/0002-implemented-desktop-login.png)、[手机登录实际图](../design/0002-implemented-mobile-login.png)、[手机注册实际图](../design/0002-implemented-mobile-register.png) | 长页面可纵向滚动；用户随后确认关闭视觉 ticket |
| 2026-09-29 | 用户对话 | 用户明确回复“todo 10可以关了” | TODO-0010 完成判定中的用户视觉确认已满足 | 不表示生产发布 |
| 2026-09-30 | 本地 Compose / MySQL 8.4 / Mailpit | 用户明确授权代为创建测试教练；交互式命令初始化，Mailpit 链接验证，实际 HTTP 登录后读取 `/api/auth/me` 与 `/api/coach/courses` | 初始化成功；邮箱验证 200；登录 200 且角色 COACH；当前身份与教练工作区 API 均 200 | 仅本地测试库与邮件沙箱；密码、验证令牌和 Session 未写入仓库或文档，正式 Gmail/生产未验证 |

- 各切片 RED/GREEN 的实际命令、失败原因与绿灯见配对实施计划第 8 节。

## 10. 部署、成本与恢复

- 本地邮件沙箱与现有 MySQL 不新增云付费资源；v1 的 Gmail SMTP 方式可延续，但 v2 的新应用密码由用户私下提供、不得写入 Git。其投递限制与正式适用性需实际验证。Session JDBC 增加既有 MySQL 的读写/存储，生产容量需验证。
- 本计划不执行生产部署、不迁移 v1 用户或密钥。生产 HTTPS、Cookie 与邮件域名需另行验收；备份/恢复按部署计划执行。

## 11. 交付状态与后续

- 当前 VERIFIED：revision 2 已完成本地实现和技术检查，用户确认[桌面与手机实际截图](../implement-plan/0002-student-identity.md#8-实际验证证据)的视觉结果并关闭 [TODO-0010](../todo/0010-identity-visual-mismatch.md)。真实 Gmail 投递、HTTPS 与 EC2 资源表现仍需实际环境验证；密码找回见 [TODO-0008](../todo/0008-password-recovery.md)。
