---
id: "0002"
title: "学员注册、邮箱验证与账号登录"
status: IMPLEMENTED
revision: 2
approved_revision: 2
created: 2026-09-28
updated: 2026-09-29
feature: "../features/0002-student-identity.md"
---

# 0002 — 学员注册、邮箱验证与账号登录实施计划

## 1. Review 摘要

关联[功能契约 0002](../features/0002-student-identity.md)、[ADR 0001](../decisions/0001-v2-baseline.md)及[视觉偏差 Ticket](../todo/0010-identity-visual-mismatch.md)。revision 1 曾获批准并通过本地验证；用户看到实际页面后要求按蓝色设计稿精确还原，并把密码下限改为 8 位。用户现已批准 revision 2，进入测试先行的实施流程。历史 revision 1 的批准、执行和验证证据保留在下文。

### Revision 2 已批准范围

- **视觉**：以[蓝色桌面稿](../design/0002-geer-blue-desktop.png)为 1672×941 桌面登录基准，而非宽泛风格参考。保留稿中概念照片、锐角黑蓝 GEER 字标、蓝色刻滑线、明亮雪景、斜切白色边界、右侧“欢迎回来”文案、输入框与蓝色按钮的相对尺寸和位置。移除当前页面在稿中不存在的深色蒙层、左侧大段标题、右侧顶部导航和厚重页脚。字标从稿中图形呈现，不用系统字体加斜杠代替。用户已明确选择概念稿照片；原始照片保留为来源记录。
- **响应式**：手机登录和注册分别以[移动登录稿](../design/0002-geer-blue-mobile-login.png)与[移动注册稿](../design/0002-geer-blue-mobile-register-v2.png)为目标，保留注册照片下缘渐变。桌面注册页复用桌面斜切构图和品牌视觉，右侧表单采用移动注册稿的四项字段和水平三选一。所有输入、按钮和状态仍是真实 HTML，不把整页截图当作不可交互的页面。
- **密码**：用户确认最低 8、最高 128 个 Unicode 字符；仍允许空格与 Unicode，不加字符类别限制。学员注册与教练私下初始化使用相同边界，前后端一致。PBKDF2 600,000 次、限流、现有密码摘要和邮箱验证逻辑不变；既有账号无需改密或迁移。
- **实现策略**：优先复用已批准的三张概念稿，在 CSS 中只展示其品牌视觉区域，以保留照片、字标与蓝色线条的原样外观；表单区用真实 HTML/CSS 在白色斜切层中重建。目标尺寸做截图逐项对照；其他视口依移动稿重排。视觉素材分辨率在大尺寸高 DPI 屏幕上可能有限，验收时检查清晰度；若用户提供矢量字标或高分辨率视觉源，再评估替换。
- **验收**：在 1672×941 对照桌面稿核对构图/字标/照片/蓝色线条/表单，在 390×约 700、768 和 1440 px 宽度核对移动与过渡布局，无横向溢出，注册/验证/登录/退出可用；密码 7、8、128、129 字符边界及既有较长密码登录通过相应测试。技术回归、文档与发布边界见第 5–9 节。

需 review 的取舍：概念图照片和图形字标会作为视觉素材直接参与页面呈现，保证与稿件接近；桌面注册页面因无独立桌面稿，按同一视觉系统延展。批准本 revision 即确认这两个具体方式及 8–128 密码范围。除此之外无待定的阻塞决策。

### Revision 1 历史基线

完成后，学员填写姓名、零基础/入门/进阶之一、邮箱和密码，验证邮箱后登录；可查看当前身份、刷新后保持有效会话、退出。仅学员可公开注册。用户已决定唯一教练也在本次登录，教练账号由用户私下初始化。

建议使用 Spring Security 服务端 Session + MySQL JDBC 持久化、同源 Cookie/CSRF、一次性邮箱验证；对公开认证入口限流。这样符合既有 SEC-02，并可在单机 Java 进程重启后按有效期恢复会话。会话存于 MySQL 会增加少量读写，应在真实 MySQL 测试中核实。v1 的 JWT/localStorage、公开教练注册与额外个人资料不复用。

用户希望炫酷且凸显本人 GEER 的高速刻平品牌，喜欢最初概念的约 55/45 电脑端左右分区和大字标，并要求手机电脑兼容。用户提供[本人原始照片](../design/0002-geer-photo-original.png)，认可近黑 #111820、雪白 #F5F7F8、板底蓝 #0877C9 的[电脑稿](../design/0002-geer-blue-desktop.png)。[手机登录稿](../design/0002-geer-blue-mobile-login.png)与[手机注册修订稿](../design/0002-geer-blue-mobile-register-v2.png)呈现上下布局；注册页照片下缘也柔和渐变到白色表单区。无用的旧概念图按用户要求清理。

revision 1 曾要求页面直接使用用户原图、概念图仅作参考、正式 GEER 图形标志待提供。revision 2 按用户新指令改为以概念照片和锐角字标为呈现目标；这不改变用户对原始照片的所有权，也不表示已有独立的矢量商标文件。

邮件按用户希望沿用 v1 的发送方式：源码证实 v1 是 Gmail SMTP 587/STARTTLS。v2 使用相同传输协议，但要独立配置、新应用密码，不复制 v1 凭据；测试用本地邮件沙箱。[Google 官方说明](https://support.google.com/accounts/answer/2461835?hl=en-GB)要求为不同应用创建各自的应用密码；账号本身需满足相应登录条件。v1 邮件服务会吞掉发送异常，本次必须让失败可见且能重发。教练账号建议由一次性受控初始化命令创建，密码在用户本地输入，不进入 Git/日志；由用户实际执行初始化。

用户已批准以下 revision 1 参数；如需实质修改，先更新 revision 并重新 review：

| 决策 | revision 1 推荐值与理由 |
|---|---|
| 会话时效 | 闲置 30 分钟、登录后最长 12 小时；浏览器会话 Cookie，不提供长期“记住我”。限制被盗会话使用时间，同时允许手机与电脑分别登录。 |
| 邮箱验证 | 24 小时一次性链接；重发间隔至少 60 秒，旧链接失效。公开页面只给中性提示，不暴露邮箱是否已注册。 |
| 密码和邮箱 | 密码 15–128 字符，允许 Unicode/空格、不强制字符类别；用 PBKDF2-HMAC-SHA256 自适应散列，计划工作因子 600,000 次，实施时测单次耗时。邮箱保留原输入供显示，用统一的小写比较键，不做 Gmail 点号/加号归并。 |
| 防滥用 | 登录按邮箱 5 次失败/15 分钟和来源 IP 30 次/15 分钟分别限制；注册按 IP 10 次/小时；重发按邮箱 3 次/小时及 IP 10 次/小时，失败返回中性 429。计数持久化到既有 MySQL，按短保留期清理。 |
| 教练初始化 | 提供无公开 Web 入口的一次性命令，用户本地从标准输入提供邮箱/密码；最多一位教练，仍需邮箱验证。 |
| 品牌素材 | 本次以已认可的文字 GEER 字标与用户原图实现；正式图形标志若后续提供，再按实际影响更新设计。 |

Gmail 新应用密码由用户私下配置；本计划不要求在对话里提供密钥。照片在不同视口的焦点由人工验收，不把概念稿直接作为网页照片。

本次不含密码重置、账号资料编辑、预约、v1 账户迁移、生产部署或新增付费资源。密码重置见 [TODO-0008](../todo/0008-password-recovery.md)，需要单独规划。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-09-28 | 用户：“开始实现，没问题，design里没必要的图片可以删掉了，开发的时候注意六边形架构” | 1 | 批准本计划学员/教练登录注册、邮箱验证、安全 Session、响应式 GEER 设计与所列安全参数；清理弃用设计稿，严格保持模块内六边形架构。未授权生产部署、付费资源或 Git commit/push。 |
| 2026-09-29 | 用户在收到具体 revision 2 计划链接、视觉/密码范围和验收摘要后回复：“批准” | 2 | 批准概念稿视觉用于桌面/手机身份页面、同视觉系统延展桌面注册及密码范围 8–128；按先 RED 后 GREEN 实施。未授权生产发布、付费资源或 Git commit/push。 |

当前 `approved_revision: 2`，revision 2 已完成本地实现及技术检查，等待用户对页面视觉结果确认。2026-09-28 的批准适用于 revision 1；本次批准适用于上方明列的 revision 2 范围。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | backend 测试、identity/domain、application/port | 先写注册字段/水平、状态转换、验证 token 规则测试并确认 RED，再实现纯 Java 模型/用例 | 同一测试 GREEN | 完成 |
| P-02 | Flyway V2、identity/adapter/out/persistence、真实 MySQL 测试 | 账号唯一邮箱、验证 token 摘要/有效期、投递任务、认证限流计数、并发注册与验证/重发；迁移 append-only | MySQL 8.4 测试 GREEN | 完成 |
| P-03 | SecurityConfig、认证 Web adapter、Session JDBC、API 安全测试 | 公开入口、登录/退出/当前身份、会话持久化、Session ID 轮换、Cookie、CSRF、授权、限流 | 401/403/429、过期/退出/重启场景通过 | 完成 |
| P-03a | identity 初始化入口/测试 | 受控创建唯一教练账号；不开放公开 API，用户私下提供凭据 | 用户能在本地安全初始化，第二个教练创建被拒绝 | 实现与测试完成；实际教练账号由用户创建 |
| P-04 | 邮件 out adapter/任务执行器、本地邮件沙箱配置、API/故障测试 | 注册事务保存投递任务，提交后有界领取/发送/退避重试；进程重启恢复，重发使旧任务失效；不保存原始验证密钥，不复制 v1 凭据 | 邮件被本地沙箱接收；故障和中断可恢复 | 完成，本地 Mailpit 验证 |
| P-05 | frontend/src、交互测试、样式、用户原始照片 | 按最终批准方向做登录/注册/待验证/验证结果、会话恢复及退出；桌面左右分区，手机上下重排，注册手机照片区更短，登录与注册照片下缘均渐变至白色表单区；真实照片按视口裁切不改写 | 手机/平板/电脑、键盘、照片焦点与状态交互通过 | 完成 |
| P-06 | 文档、索引、配置示例 | 记录实际文件、RED/GREEN 命令与结果、环境配置、风险及差异 | 文档检查通过、状态一致 | 完成 |
| P-07（revision 2） | `frontend/src/App.test.tsx`、`frontend/src/App.tsx`、`frontend/src/style.css`、已有设计图 | 先以可交互页面结构和稿件关键要素写新测试并确认 RED；再以概念稿品牌视觉与真实表单还原桌面登录/注册、手机登录/注册及其余身份状态 | 同一测试 GREEN，目标视口截图与样稿逐项核对，键盘/触控可用 | 实现完成；桌面/手机截图已核对，待用户视觉确认 |
| P-08（revision 2） | `backend/src/test/.../RegistrationTest.java`、`frontend/src/App.test.tsx`、`Registration.java`、`App.tsx` | 先加 7/8/128/129 Unicode 字符边界和既有长密码回归测试，确认 8 字符因旧规则失败；再同步 domain 与前端规则，并验证教练初始化共用边界 | 同一测试 GREEN；API、真实 MySQL、旧密码登录回归 | 完成，含真实 MySQL API 验证 |
| P-09（revision 2） | 0002 功能/计划、索引、必要运行说明 | 记录修订的 RED/GREEN、桌面/手机对照截图结论、偏差与风险；同步状态 | 文档检查及实际验收证据齐全 | 实施记录完成，待视觉确认后结案 |

- [x] 在 P-01 至 P-05 各实现切片先建目标测试、实际运行有效 RED，再改代码至同一测试 GREEN；环境问题不算 RED。
- [x] 运行后端真实 MySQL/架构/API、安全和前端交互/构建/类型/lint 回归。
- [x] 同步功能契约、计划、索引；本次未改变 ADR 0001 的模块边界。
- [x] revision 2 获用户明确批准并记录批准日期、原话、范围与条件后，才开始 P-07 至 P-09 的测试和代码。
- [x] revision 2 的视觉结构和密码边界先写目标测试并运行有效 RED，再实现同一测试 GREEN。
- [x] revision 2 跑前端 test/typecheck/build/lint、后端适用测试和完整回归，并在目标视口人工对照设计稿。

## 4. 数据、API、架构与兼容影响

- identity 负责账号、姓名、当前水平与邮箱验证记录；对未来 students/bookings 只发布入站端口，不让其他模块读其持久化实现/表。domain/port 纯 Java，Spring Security、JdbcTemplate 与 SMTP 位于外层适配器或 bootstrap。
- API 草案见功能文档第 5 节。成功为类型化 JSON，失败为统一问题响应，HTTP status 区分 400/401/403/410/429/503；不沿用 v1 envelope。不得把客户端 role/studentId 当作授权依据。
- 新 Flyway 迁移创建 identity、投递任务、限流计数及 Spring Session JDBC 表；由 Flyway 管理数据库 schema，Session JDBC 自身不自动初始化。并发唯一性靠数据库约束，验证/重发靠条件更新或行锁。Spring Session JDBC 与 Mail starter 由现有 Spring Boot 4.1.1 依赖管理；实施前核对实际受管版本与兼容性。
- revision 1 密码用 Spring Security PasswordEncoder 包装 PBKDF2-HMAC-SHA256，独立随机盐、600,000 次、版本化编码格式；当时允许 15–128 个 Unicode 字符。revision 2 已将下限改为 8，仍允许 Unicode/空格、不静默截断，沿用同一散列格式和已有摘要；8 位相较 15 位降低最短密码强度，既有限流与散列继续保留。邮箱展示值保留，比较键统一小写；不做供应商特殊归并。
- revision 2 不变更 API 路径/DTO、Session/CSRF、数据库 schema 或迁移；前端输入提示与实际 domain 边界同步。视觉资产限于前端 adapter/UI，不进入 domain/application；无新依赖、付费资源或 ADR 级架构变化。历史账号密码无需批量重置，回退到 revision 1 代码会再次拒绝新注册的 8–14 字符密码，因此回退前必须评估已创建账号的注册/教练初始化影响；登录本身不重新校验长度。
- 验证凭据由安全随机 ID 与独立 v2 服务端签名密钥派生，数据库只保存 ID、摘要、时效和状态；投递任务只保存 ID/收件账号，执行器可重建同一链接重试，无需存储原始可用 token。24 小时一次使用，重发替换旧凭据且旧任务不再发送。签名密钥由用户私下配置，不进 Git 或日志。
- 生产同源 HTTPS；认证 Cookie HttpOnly、Secure、SameSite=Lax。local HTTP 仅 local 配置允许非 Secure Cookie；禁止该配置用于正式环境。CSRF token 通过 GET /api/auth/csrf 提供，所有写请求包括登录/注册/退出均检查，认证状态变化后获取新 token。登录后更换 Session ID，退出作废服务端记录。
- 会话建议闲置 30 分钟、绝对最长 12 小时，不启用长期“记住我”；服务端同时执行两种时效。登录按邮箱与可信来源 IP 分别计数，注册/重发也按表中阈值限流；计数在 MySQL 持久化，过期清理。反向代理只信任受控 NGINX 转发的客户端地址，不能直接信任任意请求头。账号/邮箱存在性使用统一对外文案。
- 本地自动化测试使用邮件沙箱；正式配置走 Gmail SMTP 587/STARTTLS 与独立 v2 环境变量。注册事务同时保存待验证账号、验证凭据和投递任务，提交后由同一 Java 应用异步发送；失败记录且退避重试，进程重启恢复，发送成功但状态写回失败可重复投递同一有效链接。SMTP 超时有界，界面不宣称已送达。若无法在测试环境真实投递，只报告本地沙箱验证，不能宣称真实邮箱链路或上线准备完成。
- [Spring Security Session 与固定会话防护](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)、[Spring Security SPA CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)、[Spring Session JDBC](https://docs.spring.io/spring-session/reference/guides/boot-jdbc.html)、[Spring Boot Cookie 配置](https://docs.spring.io/spring-boot/appendix/application-properties/)、[Spring Boot 邮件](https://docs.spring.io/spring-boot/reference/io/email.html)、[OWASP 邮箱验证](https://cheatsheetseries.owasp.org/cheatsheets/Email_Validation_and_Verification_Cheat_Sheet.html)、[OWASP 认证密码规则](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)、[OWASP 密码存储](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)和[OWASP 会话时效](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)为方案依据；实际 API/版本组合需实现时验证。

## 5. 验收与验证计划

| 验收 ID | 具体验证 | 环境/命令 | 预期结果 |
|---|---|---|---|
| AC-01 | 四项必填、三个水平、邮箱唯一、并发注册 | 目标 domain/API/MySQL 测试；backend ./mvnw test | 合法注册待验证；无重复账号 |
| AC-02 | token 一次性、过期、重发使旧 token 失效 | MySQL 8.4/Testcontainers + API 测试 | 无效 token 不激活 |
| AC-03 | 未验证/未知邮箱/错密码、已验证登录 | Spring Security API 测试 | 仅有效身份建立会话，错误不枚举 |
| AC-04 | 刷新、退出、30 分钟闲置、12 小时绝对时效、应用重启 | 实际 HTTP + MySQL Session 测试 | 会话按规则恢复/失效 |
| AC-05 | CSRF、Cookie 属性、Session fixation | API/浏览器 HTTP 头检查 | 缺失 CSRF 拒绝，登录轮换 ID |
| AC-06 | revision 2 桌面稿 1672×941、移动登录/注册稿、过渡视口；交互状态、键盘与水平控件 | `frontend npm test`（先 RED 后 GREEN）、typecheck/build/lint；截图逐项对照设计稿；约 360/390/768/1440/1672 px 宽度人工检查 | 桌面品牌字标/雪景/蓝线/斜切线/表单构图对齐目标稿；手机品牌图与表单渐变符合对应稿；无横向溢出，所有状态可交互 |
| AC-07 | 双维度限流、邮件失败/重发/中断恢复、失效任务抑制 | API/真实 MySQL/邮件沙箱故障注入 | 429、任务可恢复且不假报已送达 |
| AC-08 | 教练初始化/登录与公开提权尝试 | 初始化命令、API、真实 MySQL | 唯一教练能登录；公开注册只能建学员 |
| AC-09 | 7/8/128/129 Unicode 字符密码，含空格/多码位字符；既有长密码 | `RegistrationTest`、相关 API 与前端交互测试（先 RED 后 GREEN）；后端全量回归 | 7/129 拒绝，8/128 接受；学员和教练共用下限，已存在账号仍可登录 |

每个改动先建立以上对应测试并运行到目标行为缺失造成的 RED，再实现后运行同一测试 GREEN；记录实际命令、断言和失败原因。数据库用真实 MySQL 8.4；架构规则、后端完整测试及前端完整检查回归。生产邮箱送达和 HTTPS Cookie 需在有环境和授权后单独验收，不能拿本地沙箱代替。

## 6. 风险、成本、部署与恢复

- 用户希望延续 v1 Gmail SMTP；不能把本地沙箱成功写成真实邮件可达。Gmail 账号/新应用密码由用户私下设置，不复制旧凭据；投递限制与正式适用性要实际验证。
- Session JDBC 在既有 MySQL 增加读写与到期清理；通过真实 MySQL 验证，监控容量。若单机数据库中断，已登录请求也会受影响；不承诺高可用。
- 注册成功后邮件发送失败会留下待验证账号；持久任务自动重试，用户仍可安全重发。验证 token 不记录到日志或前端持久化存储。新签名密钥若丢失或更换，尚未使用的验证链接将失效；更换前保留旧密钥至任务排空或明确通知用户重发。
- 不修改 v1、不复制密钥/个人数据；不生产部署。上线前备份独立 v2 库、验证迁移与恢复。Flyway 已应用 DDL 不用编辑旧版本或假设反向 DDL 安全。
- revision 2 的视觉稿作为位图展示时，大屏或高 DPI 可能比矢量资源模糊；人工对照时检查像素清晰度、文字对比度、裁切与浏览器缩放。表单仍是可访问 HTML；视觉稿内的表单像素不得替代可交互控件。现有前端可在未提交期间回退；密码新边界投入使用后回退会影响新注册/教练初始化，生产发布另需授权。

## 7. 实际执行与偏差

下表保留 revision 1 的实际执行记录，并追加 revision 2 的实施结果。历史结果不代替本次验证。

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-09-28 | `identity/domain`、`application/port`、`IdentityService` 与测试 | 注册四项校验、三种水平、待验证/已验证与唯一教练用例；domain/ports 纯 Java | 按批准范围；无重新 review |
| 2026-09-28 | Flyway V2、JDBC 持久化/限流/邮件任务、真实 MySQL 测试 | 数据库唯一邮箱/单教练、一次性 token、原子限流与并发注册 | 身份模块用 JdbcTemplate 编排条件更新与行锁，仍完全在 out adapter；未改变六边形边界，MyBatis 依赖保留在底座 |
| 2026-09-28 | Spring Security 配置、身份 Web 适配器、Spring Session JDBC、ClientAddress | CSRF、Session ID 轮换、30 分钟闲置/12 小时绝对时效、Cookie 和可信 NGINX 地址；禁用自动生成的 Basic 用户 | 配置从本地 HTTP 切到正式 HTTPS 时 Secure Cookie 默认启用；正式环境仍需部署验证 |
| 2026-09-28 | 私下教练初始化命令及测试 | 从本地终端读取姓名/邮箱/密码；数据库拒绝第二位教练 | 未替用户创建实际教练账号 |
| 2026-09-29 | 教练初始化整应用启动集成测试、SecurityConfig、IdentityController | 修复非 Web 命令行启动被 Servlet 安全依赖阻断；真实 MySQL 中创建待验证教练 | 仅限定 Web 配置与控制器的加载条件，不改变公开 API、用例或模块边界；无需重新 review |
| 2026-09-28 | SMTP 适配器、持久队列/worker、Mailpit、本地 Compose | 故障重试、指数退避、过期领取恢复、失效待发任务抑制；本地 SMTP 邮件实际到达 | Mailpit 为批准的本地沙箱；真实 Gmail 未验证；在途邮件可能晚于重发到达但旧链接无效 |
| 2026-09-28 | `frontend/src`、原始照片、响应式页面、交互测试 | 电脑端约 55/45，手机上下布局；登录与注册照片均渐变到白色表单区；照片原图直接用作静态资产 | 删除三张弃用概念图；Docker 中照片最初因权限返回 403，增设冒烟断言并修复为可读 |
| 2026-09-28 | README、功能/计划索引、配置示例、本地冒烟脚本 | 记录 IDEA、Docker MySQL 3307、Mailpit 8025/1025 与验证步骤 | 本地 Compose 为四服务；无新增付费资源或生产发布 |
| 2026-09-29 | revision 2 的 `App.tsx`、`style.css`、三张前端视觉资产、`App.test.tsx`、`deploy/smoke.py` | 页面使用已批准概念稿的锐角字标、雪道和刻滑线；白色斜切层内保留真实 HTML 表单，手机登录/注册分别使用批准稿的品牌区，注册下缘渐变；过渡/验证/账号状态沿用同一布局；冒烟检查改验三张现用图片，移除前端未使用的原始照片副本，设计目录保留原图 | 桌面注册没有独立设计稿，按已批准的延展方式制作；位图在超高 DPI 屏幕可能不及矢量清晰；用户视觉确认仍待完成 |
| 2026-09-29 | revision 2 的 `Registration.java`、`RegistrationTest.java`、`IdentityApiTest.java`、前端校验 | 密码最少 8、最多 128 个 Unicode 码点；学员与教练共用 domain 边界，前端在提交时按码点计数；既有长密码登录保持可用 | 无数据库迁移、API 路径、散列或 Session 改动；新 API 测试直接覆盖 7 拒绝、8 接受及验证后登录 |

## 8. 实际验证证据

下表保留 revision 1 的历史验证证据，并追加 revision 2 的 RED/GREEN 与截图对照。

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-09-28 | backend / Java 25 | `./mvnw -q -Dtest=RegistrationTest test`，先运行后实现并重跑 | RED：找不到 Registration；GREEN：测试通过 | Java 25 从微软官方 tar 包安装到用户目录；系统 Java 21 的不兼容未计入 RED |
| 2026-09-28 | backend / Testcontainers MySQL 8.4 | `./mvnw -q -Dtest=FoundationMigrationTest test` | RED：只存在 V1，期望 V2；GREEN：六张新表、版本 V2 通过 | 首次 Docker socket/Ryuk 挂载错误属环境故障，不算红灯；设置 Colima 的 DOCKER_HOST 与 TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE 后重跑 |
| 2026-09-28 | backend / MySQL 8.4 | `./mvnw -q -Dtest=IdentityFlowTest test` | RED：入站/出站端口缺失；GREEN：注册、唯一邮箱、并发、验证/重发、双维度限流、教练登录通过 | 初始测试查询未限定账号导致多结果，修正测试后通过，不把测试错误当功能红灯 |
| 2026-09-28 | backend / MySQL 8.4 | `./mvnw -q -Dtest=IdentityApiTest test` | RED：公开入口 401；加绝对时效断言后 RED：13 小时仍返回 200；禁用自动用户断言 RED：存在 inMemoryUserDetailsManager；各自实现后 GREEN | MockMvc 与 Spring Session 的请求包装导致初始断言取不到 Session，改用实际 Cookie/JDBC 验证；非功能红灯不计 |
| 2026-09-28 | backend / Java 25 | `./mvnw -q -Dtest=CoachInitCommandTest test` | RED：命令类缺失；GREEN：标准输入字段被传至入站端口 | 只测试命令和唯一约束；实际凭据由用户私下输入 |
| 2026-09-29 | backend / Testcontainers MySQL 8.4 | `./mvnw -q -Dtest=CoachInitApplicationTest test`（Java 25；Colima Docker socket 环境变量） | RED：真实数据库迁移后，非 Web 启动缺少 `HttpSecurity`，安全配置导致整应用启动失败；GREEN：限定 Web 配置/控制器后，命令成功创建一个待验证教练账号 | 初次系统 Java 21 编译失败、测试参数未覆盖默认数据库值，均为环境或测试设置错误，不计 RED；未创建用户的实际教练账号 |
| 2026-09-28 | backend / MySQL 8.4 | `./mvnw -q -Dtest=MailWorkerTest test` | RED：邮件端口/worker 缺失；退避断言 RED：第二次仍 60 秒；GREEN：失败重试、120 秒退避、失效抑制、领取中断恢复 | 测试最初混用数据库 UTC_TIMESTAMP 与驱动时间，修正为参数化 Instant；真实 Gmail 未测 |
| 2026-09-28 | backend / Java 25 | `./mvnw -q -Dtest=ClientAddressTest test` | RED：可信代理地址提取器缺失；GREEN：直连伪造头被忽略，仅可信代理地址被采用 | 生产代理拓扑仍需部署验证 |
| 2026-09-28 | backend / Java 25 | `./mvnw -q -Dtest=IdentityLoginTimingTest test` | RED：未知邮箱未执行密码校验；GREEN：未知/未验证账号使用一次性启动时生成的假摘要走相同 PBKDF2 路径 | 消除明显的账号存在性响应时间差；EC2 性能仍需实际测量 |
| 2026-09-29 | backend / 全量回归 | `./mvnw -q test`（Java 25；Colima Docker socket 环境变量） | 14 个测试套件、35 个测试、0 failure、0 error；包含真实 MySQL 与 ArchUnit | 仅本地环境 |
| 2026-09-29 | backend / 可执行包 | `./mvnw -q -DskipTests package`（Java 25） | 打包通过，生成教练初始化命令所用 jar | 初始化行为由上述非 Web 整应用集成测试验证，未在用户本地库创建教练 |
| 2026-09-28 | frontend / Node 24 | `npm test`，然后 `npm run typecheck && npm run build && npm run lint` | RED：三项登录/注册/图片交互均不存在；过期链接重发断言 RED；GREEN：4 测试及类型、构建、lint 全通过 | 测试按钮可访问名称含装饰箭头，调整匹配式；这不是功能红灯 |
| 2026-09-28 | Chrome 360/390/768/1440px | 实际截图、DOM scrollWidth 与 viewport 比较；390px 水平单选键盘 Space | 登录和注册无横向溢出，照片/板底标志及渐变可见，键盘选择通过 | 截图位于临时目录，仅供本地检查；正式标志仍待提供 |
| 2026-09-28 | 本地 Compose + Mailpit | `python3 deploy/smoke.py`、`python3 deploy/smoke_identity.py`、`SMOKE_RESTART_BACKEND=1 python3 deploy/smoke_identity.py` | 图片访问断言 RED：NGINX 403，修正资产权限后 GREEN；SMTP 投递、验证、登录、重启后 Session 恢复、退出均通过 | 冒烟会在本地 v2 库建立随机测试学员；不代表真实 Gmail/HTTPS |
| 2026-09-28 | 本地 macOS / JDK 25 | PBKDF2-HMAC-SHA256 600,000 次单次本地测量 | encode 约 205ms，verify 约 158ms，匹配为 true | 不是 t3.micro/EC2 性能结论；生产容量与邮件送达后验 |
| 2026-09-29 | backend / Java 25 | `./mvnw -q -Dtest=RegistrationTest test`（新测试先运行，再改 domain 并重跑） | RED：`acceptsEightUnicodeCodePointsAndRejectsSevenForStudentsAndCoachInput` 因现有 15 字符下限抛出 `Invalid password`；GREEN：3 测试通过，含 7/8/128/129 与 emoji 边界 | RED 是目标行为缺失，不是编译或环境错误 |
| 2026-09-29 | frontend / Node 24 | `npm test -- --run`（新测试先运行）→实现后 `npm test`；主标题语义另跑一轮 RED/GREEN | RED：蓝色品牌图/“欢迎回来”不存在，7 字符密码会提交注册；主标题新增断言确认原页面只有 `h2`；GREEN：7 测试通过，含页面视觉结构、一级标题、7/8 ASCII 与 128/129 emoji、键盘选择水平 | 原有“原始照片”断言按获批视觉变更同步修订；标题语义变更未改变视觉样式 |
| 2026-09-29 | backend / Testcontainers MySQL 8.4 | `./mvnw -q test`（Java 25、Colima Docker 环境变量）；`./mvnw -q -Dtest=IdentityApiTest#eightCharacterStudentPasswordRegistersAndLogsInWhileSevenIsRejected test` | 全量 14 个套件、36 测试、0 failure/error；追加 API 测试在真实 MySQL 上验证 7 字符拒绝、8 字符注册/邮箱验证/登录，通过 | 全量回归后补充该 API 测试，随后单独运行通过；测试结束时有 Spring Session 定时清理已关闭测试库的日志，未导致测试失败 |
| 2026-09-29 | frontend / Node 24 | `npm run typecheck`、`npm run lint`、`npm run build` | 均通过；生产构建生成 CSS/JS 静态资产 | 不代替正式浏览器端到端认证链路与 Gmail 送达验证 |
| 2026-09-29 | Chrome / 1672×941、1440×900、768×900、390×844、360×780 | 实际截图并检查 `document.documentElement.scrollWidth === innerWidth`；对照[桌面实现](../design/0002-implemented-desktop-login.png)、[手机登录实现](../design/0002-implemented-mobile-login.png)、[手机注册实现](../design/0002-implemented-mobile-register.png)与对应批准稿 | 锐角字标、明亮雪景、蓝色线条、斜切边界及桌面表单位置接近目标；手机登录/注册品牌区、注册照片渐变与真实表单可见；以上宽度无横向溢出 | 360px 与 768px 的长页面需纵向滚动；截图时仅运行前端开发服务，认证 API 未联机，截图只用于视觉核对，交互由测试验证；最终视觉满意度待用户确认，超高 DPI 清晰度需实际设备复核 |

## 9. 完成状态与后续

- IMPLEMENTED：revision 2 的视觉和 8–128 字符密码规则已按批准范围实现，本地技术检查通过；[TODO-0010](../todo/0010-identity-visual-mismatch.md)仍等待用户看实际截图确认视觉满意度，确认后再把 ticket 与本计划标记 VERIFIED/DONE。未生产发布；真实 Gmail 送达、HTTPS Cookie、EC2 容量与备份恢复仍需相应环境/授权。教练实际账号由用户按[后端说明](../../backend/README.md)私下初始化；密码找回另见 [TODO-0008](../todo/0008-password-recovery.md)。
