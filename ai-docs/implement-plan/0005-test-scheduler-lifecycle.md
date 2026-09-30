---
id: "0005"
title: "集成测试后台任务与数据库生命周期"
status: VERIFIED
revision: 1
approved_revision: 1
created: 2026-09-30
updated: 2026-09-30
feature: "../features/0005-test-scheduler-lifecycle.md"
---

# 0005 — 集成测试后台任务与数据库生命周期实施计划

## 1. Review 摘要

本计划处理 [TODO-0011](../todo/0011-mail-poller-test-shutdown-noise.md)：测试类使用各自的 MySQL Testcontainer，Spring 测试上下文可能继续保留后台任务。2026-09-30 的全量测试 61/61 通过，但 Surefire 日志确认 `MailPoller.poll` 和 Spring Session JDBC `cleanUpExpiredSessions` 都曾在测试阶段因数据库不可用报连接错误。类级容器停止与仍活跃的上下文交错是当前推断，实施时用定向测试和连续全量运行核实。

建议只在使用临时数据库的测试上下文禁用自动邮件轮询，并将 Spring Session JDBC 的测试清理 cron 设为 `-`；邮件 worker 继续显式调用测试，生产默认值不改。Spring Session 4.1.1 [官方 JDBC 文档](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)说明了 `spring.session.jdbc.cleanup-cron` 和禁用符号。无需业务、成本或架构决策；用户只需 review 本 revision 1。关联[功能契约 0005](../features/0005-test-scheduler-lifecycle.md)、[项目契约](../PROJECT_CONTRACT.md)和 [ADR 0001](../decisions/0001-v2-baseline.md)。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-09-30 | 用户在收到 0005 revision 1 计划后回复“开始实现” | 1 | 批准本计划的测试专属后台任务生命周期修复与本地验证；未授权生产部署或 Git commit/push。 |

当前 `approved_revision: 1`；已按批准范围完成本地验收，状态为 VERIFIED。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | `backend/src/test/.../bootstrap/LocalApplicationStartupTest.java`、`identity/MailPollerTest.java` | 先断言临时数据库上下文无自动轮询且 Session 清理 cron 为禁用值，取得两项有效 RED；故障注入证实轮询异常会传播。 | 完成：3 个启动测试中目标 2 项因现有行为失败；故障注入测试通过 |
| P-02 | 六个完整应用集成测试类的测试专属属性 | 对使用类级 MySQL 容器的上下文显式设置 `identity.mail.worker.enabled=false` 和 `spring.session.jdbc.cleanup-cron=-`；`src/main` 未改。 | 完成：同一启动目标测试 GREEN；邮件 worker 手动测试通过 |
| P-03 | 后端全量测试、Surefire XML | 连续运行两次完整测试，分别保存日志；检查报告与目标错误，并核对生产源码无变化。 | 完成：两次各 20 套件、64 测试全部通过；两份日志及最终 Surefire XML 中目标错误计数均为 0 |
| P-04 | 0005 功能/计划、TODO-0011、索引 | 记录 RED/GREEN 与回归，关闭 ticket；保留未发布状态。 | 完成：文档检查和差异检查通过 |

- [x] 用户明确批准 revision 1，记录日期、原话、范围与条件。
- [x] 先写目标测试并确认有效 RED，再实现到 GREEN，运行适用回归。
- [x] 记录日志和测试证据，按完成判定同步功能、计划和 ticket。

## 4. 数据、API、架构与兼容影响

- 仅测试配置与测试代码；不改生产 `MailPoller`、Spring Session 配置、身份端口、HTTP API、前端或 Flyway。生产的自动任务、错误日志与持久队列重试语义保持。
- 类级容器测试的各个完整 Spring 上下文都须使用相同的测试专属属性，避免共享缓存上下文保留某个未禁用的后台任务。手动执行的邮件 worker 测试继续覆盖投递、失败退避和领取恢复。
- Spring Session JDBC 清理仅在这些测试中停用；正常 Session 读写、认证和过期判断继续由现有 API 测试覆盖。Spring Session 4.1.1 与 Boot 4.1.1 的属性绑定在 GREEN 阶段实际确认。
- 不新增依赖、表、迁移、权限或架构例外；0003 六类基线保持 0。若发现必须改生产调度、真实故障处理或新增配置成本，先修订计划并重新 review。

## 5. 验收与验证计划

| 验收 ID | 具体验证及 RED 预期 | 环境/命令 | GREEN 预期 |
|---|---|---|---|
| AC-01 | 完整测试上下文仍有自动轮询/Session 清理；新增测试因目标行为缺失失败 | Java 25、MySQL 8.4 Testcontainers；定向 `LocalApplicationStartupTest` 与 worker 测试 | 两类后台任务在测试上下文不自动运行；显式 worker 仍通过 |
| AC-02 | 原完整运行的 Surefire 日志含 `MailPoller.poll` 与 `JdbcIndexedSessionRepository.cleanUpExpiredSessions` 连接错误 | `DOCKER_HOST=... TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q test` 连续两次，分次保存输出并检查 Surefire XML | 两次测试均 0 失败/错误，且无目标后台连接错误 |
| AC-03 | 注入数据库访问异常到邮件轮询入站端口 | 定向故障注入测试、生产配置只读核对 | 异常仍可被调度错误处理器观察；没有静默忽略 |

测试先行 RED 必须来自目标行为缺失；Docker 无法连接、Spring 容器初始化失败或测试断言本身错误不算 RED。无前端变更，前端测试不适用；架构、身份 API、邮件队列和文档检查适用。

## 6. 风险、成本、部署与恢复

- 风险：测试禁用自动执行可能漏掉生产装配问题。保留 MailPoller 的定向错误传播/手动 worker 测试，并核对生产默认条件和 Spring Session 正式清理配置；若不足以覆盖装配，需在本计划范围内补生产装配测试，但其调度不得依赖类级容器停机时序。
- 无新云服务、持续费用、密钥或数据库数据变更。仅本地测试环境使用既有 Docker/MySQL 资源。
- 回退为撤销测试专属属性与测试改动；不需数据库恢复。生产部署未授权也不在本计划内。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-09-30 | 审批记录 | 用户回复“开始实现”，批准 0005 revision 1 | 按计划进入测试先行阶段，无范围变化 |
| 2026-09-30 | `LocalApplicationStartupTest`、`MailPollerTest` | 新增两项配置行为断言及数据库故障传播断言；前两项先 RED，后续同一测试 GREEN | 使用 Spring Session 4.1.1 仓库对象的实际 `cleanupCron` 值验证绑定；无生产代码变化 |
| 2026-09-30 | 六个 `@SpringBootTest` 类 | 仅设置测试环境的邮件轮询与 Spring Session 清理禁用属性；保留显式 worker 测试 | 覆盖所有使用类级 MySQL 的完整应用上下文；无需新增公共测试配置或生产开关 |
| 2026-09-30 | 两次全量回归和文档 | 64 个测试、20 套件连续两次通过，后台连接错误消失；TODO-0011 关闭 | 首轮历史日志中的旧 `HikariPool-1` 随后续测试类仍报错，支持缓存上下文在类级容器关闭后继续运行的原因判断 |

## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-09-30 | 只读调查 / backend | 查看上一轮 Surefire XML、`MailPoller` 与完整上下文测试配置；核对 Spring Session 4.1.1 官方文档和本地属性元数据 | 已定位两种后台任务与类级容器测试的生命周期交错风险；代码尚未修改 | 仅调查，不是 RED/GREEN；因果关系需在实施中验证 |
| 2026-09-30 | backend / Java 25、MySQL 8.4 Testcontainers | `env DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q -Dtest=LocalApplicationStartupTest test > /private/tmp/todo11-red.log 2>&1`，先写目标断言 | RED：3 个测试中 2 个失败、0 错误；实际轮询 bean 存在，实际 Session 清理 cron 是 `0 * * * * *`，预期 `-` | Docker 与应用上下文正常启动；失败由目标行为缺失造成 |
| 2026-09-30 | backend / Java 25 | `./mvnw -q -Dtest=MailPollerTest test` | 故障注入测试通过，`DataAccessResourceFailureException` 从轮询入口向外传播 | 这是保留既有故障信号的补充回归，不计入目标 RED |
| 2026-09-30 | backend / Java 25、MySQL 8.4 Testcontainers | `env DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q -Dtest=LocalApplicationStartupTest,MailPollerTest,MailWorkerTest test > /private/tmp/todo11-green.log 2>&1` | GREEN：同一目标测试通过；合计 7 个测试，0 失败/错误；定向日志无目标错误 | MailWorkerTest 显式调用 worker，邮件任务行为保留 |
| 2026-09-30 | backend / Java 25、MySQL 8.4 Testcontainers | 连续两次运行 `env DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q test`，分别重定向输出到 `/private/tmp/todo11-full-1.log` 与 `/private/tmp/todo11-full-2.log` | 每次均 20 套件、64 个测试、0 失败、0 错误、0 跳过；两份日志中 `TaskUtils$LoggingErrorHandler`、`MailPoller.poll`、`JdbcIndexedSessionRepository.cleanUpExpiredSessions`、`CannotCreateTransactionException` 均为 0 | 最终 20 份 Surefire XML 中同样无目标日志；临时日志不作为仓库交付物 |
| 2026-09-30 | 仓库根目录 | `python3 ai-docs/check_docs.py`、`python3 ai-docs/test_check_docs.py`、`git diff --check`；核对 `git diff --name-only backend/src/main` | 文档/差异检查通过；生产源码无变更 | 本地验证，不是生产发布 |

## 9. 完成状态与后续

- VERIFIED：revision 1 已按批准范围完成测试先行和连续两次全量回归；TODO-0011 已关闭。六类架构基线仍为 0。
- 仅测试配置与测试代码变化；未生产部署。用户在本地验证后另行明确要求提交并推送本次改动。
