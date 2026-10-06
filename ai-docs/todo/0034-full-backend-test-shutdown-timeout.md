---
id: TODO-0034
title: "后端全量测试结束后 JVM 关停超时"
status: OPEN
created: 2026-10-05
updated: 2026-10-05
---

# TODO-0034 — 后端全量测试结束后 JVM 关停超时

## 1. 来源与关联

- 2026-10-05 验证 [0006 revision 5](../implement-plan/0006-post-login-booking-home.md) 时，后端全量 37 个测试类、152 个测试均通过，Maven 最终返回 0 / BUILD SUCCESS，但 Surefire 记录关停超时。
- 关联[测试生命周期功能 0005](../features/0005-test-scheduler-lifecycle.md)及[计划 0005](../implement-plan/0005-test-scheduler-lifecycle.md)。此前 [TODO-0011](0011-mail-poller-test-shutdown-noise.md)处理邮件轮询/Session 清理访问已停止测试库；本票记录不同的 JVM 退出症状。
- 本地证据：`.local/availability-revision5/backend-test.log`；`backend/target/surefire-reports/2026-10-05T20-14-12_650-jvmRun1.dump`。这些是忽略的运行产物，不包含于提交。

## 2. 背景

本轮使用本机 ARM Java 25.0.4.1、Maven 3.9.16 与 MySQL 8.4 Testcontainers。全部测试完成后，日志出现 `Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0).`。线程转储显示 main 在 ApplicationShutdownHooks 中等待关停 hook；具体阻塞源尚未确认。同一环境单独运行 AvailabilityRevision5ApiTest 的 8 个测试正常退出，没有该日志。

## 3. 问题与影响

全量测试收尾需额外等待约 30 秒并由 Surefire 强制结束测试 JVM，降低测试资源清理的可判断性并产生错误级日志。该症状发生在测试进程关停，不能据此认定生产排班功能异常；本轮业务断言的失败/错误/跳过数均为 0。

## 4. 期望结果

后端完整测试运行后，测试 JVM 与临时资源能正常结束，不依赖退出超时或强制停止；测试结果与关停日志可清晰核对。

## 5. 完成判定

- 相同环境全量测试全部通过，Maven 正常返回，日志不再出现这条 Surefire 关停超时。
- 有证据说明实际阻塞源和处理结果，测试临时资源正常清理。
- 测试收尾维护不改变生产业务、调度默认值或数据。

## 6. 未决事项与状态记录

- 具体阻塞 hook 和复现条件尚未确认；本票不将推测作为原因结论。
- 2026-10-05：OPEN；已保存实际日志/线程转储并核实目标测试正常退出，尚未创建或批准本问题的具体修订计划。
