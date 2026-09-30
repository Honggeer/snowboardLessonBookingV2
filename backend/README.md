# 后端

Java 25 / Spring Boot 4.1.1 模块化单体。身份功能位于 `identity` 业务模块：domain 与 application ports 为纯 Java，Web、MySQL、密码散列和 SMTP 位于 adapters。Flyway V2 建立身份与 Spring Session JDBC 表；V3 增加密码找回记录及账号凭据版本。预约尚未实现；`scheduling` 的时间范围预览只是本地架构示例。

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

## 教练私下初始化

唯一教练没有公开注册接口。准备好上述数据库和验证密钥环境变量、构建可执行 jar 后，在本地终端运行以下命令，按提示依次输入姓名、邮箱和密码；交互终端会隐藏密码输入：

```sh
./mvnw -q -DskipTests package
java -jar target/snowboard-v2-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --spring.main.web-application-type=none --identity.coach-init=true --identity.mail.worker.enabled=false
```

账号先处于待验证状态；正常启动后，邮件任务发送验证链接。第二位教练会被数据库约束拒绝。初始化命令由用户自行执行，Codex 未创建实际教练账号。

## 测试

`./mvnw test` 包含 MySQL 8.4 Testcontainers、身份 API、Session、邮件队列及架构规则测试。macOS Colima 示例：

```sh
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw test
```

架构测试位于 `src/test/java/com/geer/snowboard/v2/architecture/`。`ArchitectureTest` 扫描实际生产类，并通过 `ArchitectureBaseline` 将违规分为 `DOMAIN_PURITY`、`PORT_PURITY`、`APPLICATION_BOUNDARY`、`INBOUND_BOUNDARY`、`CROSS_MODULE_INTERNAL`、`MODULE_CYCLE`。`BASELINE` 显式列出每类数字，初始全为 0；`EXCEPTIONS` 保存逐条精确身份。新增违规先修复；需要暂留时按 [项目契约 ARCH-09](../ai-docs/PROJECT_CONTRACT.md) 记录理由、关联计划/ADR 或 ticket、消除条件及代码旁注释，再为每条对应类别数字加 1。修复时同步移除条目并减 1。定向运行：`./mvnw -q -Dtest=ArchitectureBaselineTest,ArchitectureTest test`。

对外身份 API 见 [0002 功能文档](../ai-docs/features/0002-student-identity.md)和[0004 找回密码功能文档](../ai-docs/features/0004-password-recovery.md)。所有写请求需先通过 `GET /api/auth/csrf` 获取 token；登录、验证码核验、退出和找回成功后重新获取。Session 为服务端 MySQL 持久化，闲置 30 分钟、登录后绝对上限 12 小时。找回成功后必须重新登录，之前的登录会话会在后续受保护请求时失效。浏览器不保存认证密钥到 localStorage。
