# 后端

Java 25 / Spring Boot 4.1.1 模块化单体。身份功能位于 `identity` 业务模块：domain 与 application ports 为纯 Java，Web、MySQL、密码散列和 SMTP 位于 adapters。Flyway V2 建立身份与 Spring Session JDBC 表；V3 增加密码找回记录及账号凭据版本；V4 增加课程、时段和预约申请表；V5 增加独立可用时间、雪场和日级地点策略；V6 增加排班撤回状态与按日查询索引；V7 增加课程下架和学员取消状态及约束；V8 增加预约邮件任务表；V9 增加个人主页、媒体与任务/引用/配额表；V10 增加可选课程封面、构图位置和课程媒体引用隔离。`catalog`、`scheduling`、`bookings` 提供真实约课；`scheduling` 的时间范围预览仍只是本地架构示例。

## IDEA 本地开发

先按 [本地环境说明](../deploy/README.md)启动 MySQL 与 Mailpit。IDEA 至少使用支持 Java 25 的 2025.2 版本；旧版即使添加 JDK 25，也可能仍以 Java 21 编译并报告 class file 69.0/65.0 不匹配。本机 JDK 25 位于 `/Users/geerhong/.local/jdks/microsoft-jdk-25.0.4.1/Contents/Home`。升级 IDEA 后，在 Project Structure 中将 Project SDK、后端 Module SDK 与 Language level 设为 25；在 Maven 设置中将 Importer JDK 与 Runner JRE 设为 25，Run Configuration 的 JRE 也设为 25。重新加载 Maven 项目并执行 Maven `clean` 后再构建。运行 `com.geer.snowboard.v2.SnowboardV2Application`，active profile 设为 `local`，环境变量示例：

```text
DB_URL=jdbc:mysql://localhost:3307/snowboard_v2
DB_USER=snowboard_v2
DB_PASSWORD=使用 deploy/.env 中的 DB_PASSWORD
VERIFICATION_KEY=使用 deploy/.env 中的 VERIFICATION_KEY
APP_PUBLIC_URL=http://localhost:5173
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_FROM=geer@local.test
```

这里的等号右侧说明文字需要换成自己的本地值，不能把“使用 deploy/.env 中的 DB_PASSWORD”作为实际密码。IDEA Run Configuration 的 `DB_PASSWORD` 值应与 `deploy/.env` 中等号后的当前值完全一致；`DB_URL` 指向 `jdbc:mysql://localhost:3307/snowboard_v2`。若启动日志出现 `Access denied for user 'snowboard_v2'`，先核对 IDEA 中的这两个值及 `DB_USER`，再在本机运行 `mysql -h 127.0.0.1 -P 3307 -u snowboard_v2 -p -e 'SELECT 1'`，按提示输入 `deploy/.env` 中的密码；成功返回 `1` 表示 Docker MySQL 接受该凭据。不要为了匹配 IDEA 中的旧值直接改 `deploy/.env`：已有 MySQL 数据卷不会因环境变量变化而自动改密码。

后端默认监听 8080；前端 `npm run dev` 在 5173，通过 Vite 代理同源 `/api`。如果 Docker 后端也在运行，会占用 8080；在 IDEA 启动前执行 `docker-compose -f deploy/compose.yaml stop backend frontend`，保留 `db` 与 `mailpit`。Mailpit 收件箱在 `http://localhost:8025`。

`VERIFICATION_KEY` 至少 32 个 UTF-8 字节，可用 `openssl rand -hex 32` 生成并安全保存；它同时保护注册验证链接和找回密码验证码，两种用途隔离。更换它会使尚未使用的验证链接及找回验证码失效。不要把数据库密码、验证密钥或邮件应用密码提交到 Git。

正式 Gmail SMTP 使用独立的 v2 应用密码：`MAIL_HOST=smtp.gmail.com`、`MAIL_PORT=587`、`MAIL_USER`、`MAIL_PASSWORD`、`MAIL_FROM`。若仍用 `local` profile 联调 Gmail，还需设置 `MAIL_SMTP_AUTH=true` 与 `MAIL_STARTTLS=true`。正式环境不启用 `local` profile，认证 Cookie 默认 Secure；真实邮箱送达与 HTTPS 需部署后另验。

本地 Compose 会读取 `deploy/.env` 中相同的 `MAIL_*` 参数；未设置时使用 Mailpit。切换为真实 SMTP 后，注册验证、密码找回与预约通知均共用该账号；应仅用本人控制的测试邮箱验证，勿运行向 `example.test` 发信的 Mailpit 脚本。链接地址由 `V2_PUBLIC_URL` 传为 `APP_PUBLIC_URL`，本机缺省为 `http://localhost:8088`，未来部署只需换成收件人可访问的 HTTPS 地址。操作与恢复见[本地环境说明](../deploy/README.md)。

## 教练私下初始化

唯一教练没有公开注册接口。准备好上述数据库和验证密钥环境变量、构建可执行 jar 后，在本地终端运行以下命令，按提示依次输入姓名、邮箱和密码；交互终端会隐藏密码输入：

```sh
./mvnw -q -DskipTests package
java -jar target/snowboard-v2-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --spring.main.web-application-type=none --identity.coach-init=true --identity.mail.worker.enabled=false
```

账号先处于待验证状态；正常启动后，邮件任务发送验证链接。第二位教练会被数据库约束拒绝。2026-09-30 用户明确授权 Codex 在本地测试库初始化并验证了一个教练账号；生产账号仍需独立按部署流程处理，凭据不记录在仓库。

## 测试

`./mvnw test` 包含 MySQL 8.4 Testcontainers、身份 API、Session、邮件队列及架构规则测试。macOS Colima 示例：

```sh
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw test
```

架构测试位于 `src/test/java/com/geer/snowboard/v2/architecture/`。`ArchitectureTest` 扫描实际生产类，并通过 `ArchitectureBaseline` 将违规分为 `DOMAIN_PURITY`、`PORT_PURITY`、`APPLICATION_BOUNDARY`、`INBOUND_BOUNDARY`、`CROSS_MODULE_INTERNAL`、`MODULE_CYCLE`。`BASELINE` 显式列出每类数字，初始全为 0；`EXCEPTIONS` 保存逐条精确身份。新增违规先修复；需要暂留时按 [项目契约 ARCH-09](../ai-docs/PROJECT_CONTRACT.md) 记录理由、关联计划/ADR 或 ticket、消除条件及代码旁注释，再为每条对应类别数字加 1。修复时同步移除条目并减 1。定向运行：`./mvnw -q -Dtest=ArchitectureBaselineTest,ArchitectureTest test`。

对外身份 API 见 [0002 功能文档](../ai-docs/features/0002-student-identity.md)和[0004 找回密码功能文档](../ai-docs/features/0004-password-recovery.md)。所有写请求需先通过 `GET /api/auth/csrf` 获取 token；登录、验证码核验、退出和找回成功后重新获取。Session 为服务端 MySQL 持久化，闲置 30 分钟、登录后绝对上限 12 小时。找回成功后必须重新登录，之前的登录会话会在后续受保护请求时失效。浏览器不保存认证密钥到 localStorage。

## 本地约课流程

先按上文初始化并验证唯一教练账号，再注册、验证至少一位学员。教练登录后默认进入「预约申请」，另有「创建课程」「管理可用时间」入口。先在可用时间页新增至少一座雪场，再在课程页填写名称、介绍和 CAD 价格并发布。雪场管理位于紧凑区域，改名和停用可按需展开。排班时在月历点选多个当地日期，统一设置起止时间和可选的当天限定雪场；特殊日期另分批处理。不选限定雪场时，教练的活动雪场均可选择。系统按 `America/Toronto` 将范围拆成完整的两小时时段，未满两小时的尾段只预览、不发布。选中日期整天替换原有未确认时段，已确认预约保留；只要其中一天有待确认申请，整批不覆盖。发布后日历标出受影响日期并显示时段。可先排班，之后再创建课程。学员登录后依次选择课程、日期、时间和雪场并提交申请；“我的预约”显示待教练确认、已确认、已拒绝或已取消。待确认不占位，同一时段可有多人申请。教练确认一人时，系统自动拒绝同一时段其他待确认申请，并锁定该多伦多当地日期的雪场、拒绝当天其他雪场的待确认申请；同山的其他时段仍可预约。教练单独拒绝一人须填理由，时段仍开放。费用线下支付。

课程、雪场和可用时间批次创建、学员申请的写请求都要求 `Idempotency-Key` UUID；网络重试复用原键。同键同内容返回原记录或批次，同键不同内容返回 409。所有写请求还需当前 Session 的 CSRF token。教练可改名或停用雪场；有该山待确认申请时停用返回 409，历史预约名称不随改名变化。教练可编辑课程名称、介绍和 CAD 价格，也可将课程从学员选课列表移除；已有预约及价格快照不变。学员可直接取消待确认申请；已确认预约须距开课至少 24 小时，取消理由可选。取消已确认预约会释放时段；若当天没有其他已确认预约，解除当天雪场锁定。取消后可用新请求键重新申请仍开放的时段。业务 API、规则与限制见 [0006 功能文档](../ai-docs/features/0006-post-login-booking-home.md)。本版不提供时段逐条编辑、改期或在线支付。V5 对未来旧时段日期保留原数据并标记地点待映射；这些日期在人工审查映射前不能接受新申请或确认旧申请。

`GET /api/coach/availability/month?year=YYYY&month=M` 只读单月排班与当天雪场限制/锁定状态；`POST /api/coach/availability/replacements` 接受 `days[]` 与 `Idempotency-Key`，首次成功 201、相同请求重放 200，待确认申请、已确认时间或雪场锁定冲突返回 409。原有 `/api/coach/availability/batches` 保留新增排班语义。撤回的时段保留为 `CLOSED`，不会出现在学员可约列表。课程编辑使用 `PATCH /api/coach/courses/{id}`，下架使用 `POST /api/coach/courses/{id}/archive`，学员取消使用 `POST /api/bookings/{id}/cancel`。若 IDEA 中已有旧版后端进程，重启它后新接口才会生效；本地启动时 Flyway 自动应用迁移，当前最新为 V10。生产迁移与发布另需授权。

## 预约邮件

学员申请成功进入 PENDING 时，同事务保存教练邮件任务；教练确认后，同事务保存学员邮件任务。后台另行通过现有 SMTP 发送，两封邮件分别链接到需要登录且按账号授权的预约详情。任务写入失败会回滚申请或确认；SMTP 失败只影响邮件任务，不能改变已保存的预约状态。相关状态、API 和限制见[0007 功能文档](../ai-docs/features/0007-booking-email-notifications.md)。

`APP_PUBLIC_URL` 必须是收件人可访问的站点根地址；本机 `localhost:5173` 或 `localhost:8088` 只适合在运行应用的电脑上打开。不能用请求 Host 构造邮件链接。`BOOKING_MAIL_WORKER_ENABLED=false` 可暂停预约邮件轮询，`BOOKING_MAIL_WORKER_DELAY_MS` 可调整毫秒间隔。每轮最多处理 10 项；失败退避并最多尝试 8 次，超过上限记 `DEAD`。用 `SELECT id,booking_id,event_type,attempts,last_error FROM bookings_mail_task WHERE status='DEAD'` 排查，确认 SMTP 恢复后可针对指定任务重置为 `PENDING`、`attempts=0` 和当前 `next_attempt_at`；发送前 worker 仍会核对预约当前状态。邮件采用至少一次投递，发信成功后进程中断可能重复。

## 关于 GEER 与媒体（0008）

公开页面在 `/about-geer`；教练登录后点击“个人主页”，填写资料并上传人物照、证书、微信二维码、视频封面及一个 MP4。保存草稿不会改变公开内容，预览最新已保存草稿后再发布。发布至少需要称呼、一句话介绍和已验证人物照片；证书说明/图片、视频/封面分别成对填写。微信仅提供二维码；小红书/抖音账号与链接各自可选，保留 80/2048 字长度上限，取消格式、平台域名与成对校验。HTTP/HTTPS 地址可跳转，其他链接文本仅展示。历史微信号在新保存时忽略。实际资料由教练填写，首次未发布时显示准备中。

IDEA 开发前，在仓库根运行：

```sh
docker-compose -f deploy/compose.yaml up -d db mailpit s3mock
scripts/install-media-probe.sh
```

第二条从官方固定版本源码编译 ffprobe 9.0.2 到被 Git 忽略的 `backend/.local/bin/ffprobe`，不升级系统 Homebrew；需 curl、tar/xz、make 和 C 编译器。已安装者无需重复执行。应用自动查找 `.local/bin/ffprobe`、`backend/.local/bin/ffprobe`，也可在 IDEA 设置 `FFPROBE_PATH` 为绝对路径。启动 `local` profile 后，新媒体任务专用 worker 自动处理，S3Mock 在 `http://localhost:9090`；8080 由 IDEA 后端使用。现有 IDEA 进程需重新加载 Maven 依赖并重启，才能加载新模块和 V9 迁移。

图片要求真实 JPG/PNG、8 MiB 内、宽高不超过 4096；视频要求真实 MP4/H.264/yuv420p、最多一个 AAC 音轨、100 MiB/120 秒/1080p/60 fps 内，不自动转码。选择文件后浏览器直传，后台固定版本并校验；校验失败保留旧素材，可重新选择。最多一个未终结上传、每小时 20 个新申请、逻辑容量 1 GiB。未完成上传 24 小时过期；后台删除过期暂存全部版本，未引用素材保留 7 天，保留 tombstone 回扫迟到对象。逻辑容量不限制实际 S3 写入量或账单。

视频通过 ffprobe 完整逐帧校验，默认探测上限 90 秒；`FFPROBE_TIMEOUT_SECONDS` 可配置 1–90 秒，超出范围时拒绝启动。超时属于临时故障，不判为格式无效；仍保留后台任务总预算 180 秒和租约 300 秒。验证最多执行 4 次，以 1/5/15 分钟退避；重试期间保留 VERIFYING，耗尽后 FAILED，页面说明校验超时及重试。高负载或更复杂的视频仍可能超过预算，现有规格和完整内容检查不放宽。

`MEDIA_WORKER_ENABLED=false` 暂停媒体任务。删除最多 9 次（首次加 8 次重试），最终失败留在 `media_job`。可只读查询 `SELECT id,asset_id,kind,attempts,error_code FROM media_job WHERE status='FAILED'`；排除存储/ffprobe 故障后，对明确的失败任务单独恢复，不批量重置或删除已发布引用。数据库与 S3 操作不能共用事务；发布只原子切换已验证引用。

生产须显式 `MEDIA_STORAGE_MODE=aws`，提供 `MEDIA_REGION`、两个独立私有桶 `MEDIA_STAGING_BUCKET`/`MEDIA_FROZEN_BUCKET`、`MEDIA_CDN_BASE_URL`、`MEDIA_KEY_PAIR_ID`、秘密文件路径 `MEDIA_SIGNING_KEY_PATH`，并开启 worker。staging 必须开启 versioning；生产权限用 IAM Role，签名私钥另外注入。CloudFront OAC 保护私有源站，frozen 分发还必须要求可信 key group 的 viewer 签名。公开 API 只签已发布引用，签名 15 分钟；预览仅限教练。现有 AWS 资源、生产部署、权限与备份证据见 [0010 生产交付记录](../ai-docs/implement-plan/0010-production-delivery.md)；S3Mock 本地结果仍不能替代实际云验证。

S3Mock 忽略真实签名/过期校验，且 CORS 宽松，只用于本地测试；不能作为生产授权证明。新增目标测试位于 `coachprofile/` 和 `media/`，实际 MP4 样本是蓝色测试片段，不是真实教练视频。CI 同样先运行固定版本探测工具的安装脚本。

## 课程封面（0009）

教练创建或编辑课程时可上传一张可选 JPG/PNG，限制仍为 8 MiB、宽高各不超过 4096，建议 16:9。媒体用途为 `COURSE_COVER`，与主页共享限额与后台校验/清理；仅本人 READY 图片可保存。保存课程时原子维护 `COURSE:<courseId>` 的 PUBLISHED 引用，与主页 GEER 引用隔离。下架或移除封面解除该课程引用，历史预约不变。

创建请求可带 `coverAssetId` 和数字 `coverPositionX/Y`（0–100，最多两位小数，默认 50）。PATCH 缺省字段保留，`coverAssetId: null` 清除；替换图片未提供的位置归中。支持只更新构图；无图位置统一归中。列表新增 `coverAssetId`、位置和可空 `cover`（id/url/expiresAt/width/height），含签名响应 no-store，媒体故障降级无图。预约内部取课/锁课不签名。

本地 IDEA 重启后自动应用追加 V10，无需修改旧迁移或重置数据。完整契约、并发协议与验证见 [0009](../ai-docs/implement-plan/0009-course-selection-visuals.md)。回退到不支持封面的旧应用时需暂停封面编辑及媒体清理，保留 V10。
