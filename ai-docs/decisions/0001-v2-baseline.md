# ADR 0001 — 独立项目与架构基线

- 日期：2026-09-27
- 状态：ACCEPTED
- 关联：[项目契约](../PROJECT_CONTRACT.md)、[项目建立记录](../features/0000-project-bootstrap.md)、[工程底座计划](../implement-plan/0001-project-foundation.md)

## 依据与背景

用户明确选择保留学员注册、自助预约与个人课程，保留 Java、使用 MySQL、继续 AWS EC2，预算约 30 CAD/月；要求六边形架构、严格 AI 契约。随后明确要求新建独立项目，并要求每个功能先生成实施计划让用户 review，实现后更新。

## 决策

1. 在同级目录建立独立 `snowboardLessonBookingApp-v2` 与独立 Git 仓库，v1 不变。
2. 后端模块化单体，模块内采用 domain/application/adapter。domain/端口纯 Java；application.service 默认允许 Spring DI/事务注解，数据库/HTTP/云 SDK 放适配器。
3. Java 与 MySQL 保留；精确版本、前端工具和兼容组合由后续工程底座计划决定。0001 revision 2 已获用户批准并进行了本地兼容验证。
4. 前后端独立构建，通过同源 API 协作；初期入口/前端、Java、MySQL 同一 EC2。媒体规划 S3/CloudFront，通知执行器规划在 Java 应用内配合 MySQL 持久任务。
5. `ai-docs/` 保存契约/功能/ADR，`ai-docs/implement-plan/` 保存可 review 的计划与执行记录，根 AGENTS.md 提供每次任务入口。计划目录最初位于仓库根目录，按用户后续要求移入 `ai-docs/`。
6. 每项功能的代码实现必须等待用户明确批准对应计划修订；批准后常规工作自主完成，实质变化重新 review。

## 替代方案与影响

- 在 v1 原地重写：容易混淆既有生产行为与目标规则；新项目允许逐功能审查复用范围。
- 全局三层：入口简单，但容易混合业务边界；六边形结构需保持端口有真实意义，避免无价值转发。
- 微服务：提供独立部署，但增加一致性与运维成本；先建立业务模块边界。
- PostgreSQL/全栈 TypeScript：用户已选 Java/MySQL，避免不必要的语言/数据库迁移。
- 首期托管数据库/多机：可增强运维隔离，但本阶段沿用用户熟悉的 EC2；不承诺单机高可用。

## 尚未验证

0001 的 Java/框架组合及 MySQL 8.4 已在本地编译、测试和容器启动；t3.micro 容量、云账单、预约业务规则、MySQL 预约并发及生产部署仍未验证。工程代码从已 review 的计划开始。
