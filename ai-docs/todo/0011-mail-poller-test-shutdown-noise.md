---
id: TODO-0011
title: "集成测试收尾时邮件轮询任务记录数据库连接错误"
status: DONE
created: 2026-09-29
updated: 2026-09-30
---

# TODO-0011 — 集成测试收尾时邮件轮询任务记录数据库连接错误

## 1. 来源与关联

- 发现于：2026-09-29 执行 0003 后端全量 `./mvnw -q test`，Testcontainers 数据库关闭前后，调度线程记录 `CannotCreateTransactionException` 和 `HikariPool-1 - Connection is not available`。该次 Maven 退出码为 0，所有测试报告仍通过。
- 相关功能/计划：[身份功能 0002](../features/0002-student-identity.md)、[架构基线计划 0003](../implement-plan/0003-architecture-violation-baseline.md)、已验证的[功能 0005](../features/0005-test-scheduler-lifecycle.md)与[计划 0005](../implement-plan/0005-test-scheduler-lifecycle.md)。

## 2. 背景

本地集成测试使用 MySQL 8.4 Testcontainers。邮件轮询任务默认启用；部分测试关闭了它，但完整运行中仍有测试上下文启动了定时任务。2026-09-30 查看通过 61 个测试的 Surefire 日志，除 `MailPoller.poll` 外，还确认 Spring Session JDBC 的 `cleanUpExpiredSessions` 产生同类连接错误。旧 `HikariPool-1` 在后续测试类阶段报错，与类级容器停止后缓存的应用上下文任务继续执行相符。

## 3. 问题与影响

定时任务在数据库不可用时产生错误日志，会干扰测试输出，也可能掩盖真正的回归失败。当前证据不表明邮件或生产数据已丢失；需要确认发生条件与影响。

## 4. 期望结果

集成测试启动和结束时，邮件轮询与测试数据库的生命周期协调一致；正常测试不再记录预期内的连接错误。若数据库真实故障，仍应保留可定位的错误信号。

## 5. 完成判定

- 能复现或解释本次日志发生条件。
- 相关集成测试连续运行时不出现由测试资源关闭顺序导致的邮件轮询连接错误。
- 邮件任务遇到真实数据库故障时仍可观察。

## 6. 未决事项与状态记录

- 需要用户澄清的问题：N/A。
- 2026-09-29：OPEN；仅记录问题，未授权超出 0003 计划的修复。
- 2026-09-30：OPEN；用户询问能否处理，已建立 0005 revision 1 供 review。两种后台任务的日志均已确认；批准前不改测试或实现代码。
- 2026-09-30：IN_PROGRESS；用户在 0005 revision 1 计划后回复“开始实现”，进入测试先行实施。
- 2026-09-30：DONE；目标测试先 RED 后 GREEN，邮件故障注入仍能向外传播；后端全量连续两轮各 64 测试通过，输出日志与最终 Surefire XML 均无上述后台连接错误。具体命令与证据见计划 0005 第 8 节，未生产发布。
