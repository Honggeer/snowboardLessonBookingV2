---
id: "0005"
title: "集成测试后台任务与数据库生命周期"
status: VERIFIED
plan: "../implement-plan/0005-test-scheduler-lifecycle.md"
created: 2026-09-30
updated: 2026-09-30
contract_version: "1.4"
modules: [backend, identity]
---

# 0005 — 集成测试后台任务与数据库生命周期

关联[已批准的实施计划 0005 revision 1](../implement-plan/0005-test-scheduler-lifecycle.md)、[TODO-0011](../todo/0011-mail-poller-test-shutdown-noise.md)及[项目契约](../PROJECT_CONTRACT.md)。

## 1. 目标、触发与范围

- 用户询问 TODO-0011 的内容以及现在能否处理。目标是让使用类级 MySQL Testcontainers 的集成测试在数据库停止前后，不再因测试上下文中的后台定时任务访问已停止的数据库而输出预期内的连接错误。
- 2026-09-30 的全量测试通过 61/61，但 Surefire 日志中有两条不同来源：`MailPoller.poll` 和 Spring Session JDBC 的 `cleanUpExpiredSessions`。二者都在已启动的测试上下文后台执行；容器按测试类结束时停止，具体交错时序仍需在实施验证中确认。
- 本次仅调整后端集成测试的定时任务配置和相关测试，不改生产轮询、会话清理、数据库结构、邮件业务流程或部署配置。邮件 worker 在测试中继续由测试显式调用以验证投递与重试。

## 2. 验收条件

| ID | 场景/输入 | 预期行为 | 验证方式 | 证据/结果 |
|---|---|---|---|---|
| AC-01 | 启动使用临时 MySQL 的完整应用测试上下文 | 测试不自动运行邮件轮询或过期 Session 清理；显式调用 worker 仍有效 | 定向配置测试及邮件 worker 集成测试 | 通过：目标断言先 RED 后 GREEN；同轮 7 个定向测试通过 |
| AC-02 | 连续两次运行后端全量测试并关闭各类级 MySQL 容器 | 测试通过；日志中不再出现上述两类后台任务访问已停止测试库的连接错误 | Maven 退出码、Surefire 报告及分次保存的日志 | 通过：两轮各 64 个测试、0 失败/错误；两份日志及最终 Surefire XML 无目标错误 |
| AC-03 | 邮件轮询在真实数据库故障下运行 | 故障仍能被调用方/调度错误处理器观察，不被静默吞掉 | 失败注入的定向测试及生产配置核对 | 通过：注入 `DataAccessResourceFailureException` 后由 `MailPoller` 原样传播；生产源码无修改 |

## 3. 业务规则与未决事项

- 不改变学员、教练、邮件验证码和 Session 的业务规则、身份权限或用户界面。仅隔离自动后台执行与测试容器生命周期。
- 状态变化：N/A；邮件任务和会话数据状态不因本次改动改变。
- 未决业务问题：N/A。若发现根因需要修改生产任务调度或 Session 策略，先修订计划并重新 review。

## 4. 模块、端口与依赖

- 涉及 backend 的测试配置/测试类；既有 `MailPoller` 为 identity 入站 adapter，继续经入站端口调用 worker。现有 Spring Session JDBC 归身份基础设施。
- 不新增端口、模块或跨模块依赖；六类架构违规基线继续为 0。适用 ARCH-04/05/09、DEP-03、ASYNC-02/03、WORK-02/09。

## 5. API 与前端契约

N/A：不变更 HTTP API、Cookie、CSRF、前端页面或客户端缓存。现有身份/API 回归仍须通过。

## 6. 数据、事务与并发

N/A：不修改 Flyway、表、事务、领取/退避策略或生产 Session 清理周期。只读使用现有 MySQL Testcontainers 数据。

## 7. 异步任务与外部依赖

- 测试上下文不自动执行轮询与会话清理；邮件队列测试继续显式调用 `runOnce`，测试真实 MySQL 中任务状态与重试。
- 生产环境保留现有默认调度与错误日志。无新依赖、云资源或付费成本。

## 8. 实施计划关联与实际变更

- [x] 调查 TODO-0011、0003/0004 验证日志、现有测试配置与 Spring Session 4.1.1 清理配置。
- [x] 用户回复“开始实现”，批准实施计划 0005 revision 1；按测试先行流程实施。
- [x] 记录 RED/GREEN、两次全量回归和日志检查，再同步 TODO 与索引。

实际变更：`LocalApplicationStartupTest` 增加两个测试配置行为断言；新增 `MailPollerTest` 保留数据库故障信号；六个使用类级 MySQL 的完整应用测试上下文显式禁用测试环境的自动邮件轮询和 Spring Session 清理。无生产源码改动、依赖或数据迁移。

## 9. 验证证据

| 日期 | 环境/步骤 | 实际结果 | 限制 |
|---|---|---|---|
| 2026-09-30 | 实施前只读检查上一轮 `./mvnw -q test` 的 Surefire XML 与源码 | 61 个测试通过；日志分别指向 `MailPoller.poll` 与 Spring Session JDBC 清理任务 | 当时尚未实施；旧 `HikariPool-1` 在后续测试类日志报错支持生命周期交错判断 |
| 2026-09-30 | 先新增目标断言，运行 `LocalApplicationStartupTest` | RED：3 个测试中 2 个因自动轮询 bean 存在、Session 清理 cron 仍为每分钟一次而失败 | MySQL 容器和应用上下文启动正常，属于有效 RED |
| 2026-09-30 | 设置测试专属属性后运行 `LocalApplicationStartupTest,MailPollerTest,MailWorkerTest` | GREEN：7 个测试通过；故障注入仍向外传播 | 显式 worker 测试覆盖邮件发送和重试 |
| 2026-09-30 | Java 25、MySQL 8.4 Testcontainers，连续两次 `./mvnw -q test` | 每轮 20 套件、64 测试、0 失败/错误/跳过；两份输出日志及最终 Surefire XML 均无两类后台任务连接错误 | 本地验证；正式环境调度与数据库故障仍由生产监控观察 |

命令、日志检查关键字和各步骤结果详见配对计划第 8 节。架构基线六类仍为 0；无权限/API/前端变更。

## 10. 部署、成本与恢复

无新增资源、密钥、迁移或持续费用；测试配置可随代码回退。没有生产部署授权，本次也不执行部署。

## 11. 交付状态与后续

VERIFIED：已批准的 revision 1 完成本地验收，TODO-0011 已结案。尚未发布生产；用户在本地验证后另行明确要求提交并推送本次改动。
