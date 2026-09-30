---
id: "0003"
title: "六边形架构违规基线追踪"
status: VERIFIED
plan: "../implement-plan/0003-architecture-violation-baseline.md"
created: 2026-09-29
updated: 2026-09-29
contract_version: "1.4"
modules: [backend]
---

# 0003 — 六边形架构违规基线追踪

关联[实施计划 0003](../implement-plan/0003-architecture-violation-baseline.md)、[项目契约](../PROJECT_CONTRACT.md)与[ADR 0001](../decisions/0001-v2-baseline.md)。用户已批准 revision 1，现已完成本地验证，未发布生产。

## 1. 目标、触发与范围

- 用户要求：“帮我写一个六边形架构的test，规定基线，任何新的violation要分类然后基线数字+1 并且comment要写清楚为什么，尽可能避免，这个是架构测试追踪。”
- 目标是在每次后端测试/CI 中检查已确认的六边形和模块边界，同时明确追踪无法立即消除的例外。违规先修复；确需暂留时，逐条分类、登记稳定标识、在对应类别的基线数加 1，并写清理由与消除条件。
- 当前 ArchitectureTest 使用 ArchUnit 检查生产类和 5 条边界规则，已有两个反例测试；2026-09-29 只读运行通过。它目前没有分类基线或例外记录，也没有检查入站适配器直接依赖同模块应用服务。源码中发现 MailPoller 和 IdentityProblemHandler 各有一处此类直接依赖；新规则启用前优先修正，不把它们直接算作允许例外。
- 本次包含后端架构测试、必要的小范围依赖方向修正、基线追踪规则及说明文档。既有身份 API、邮件投递行为与数据库结构保持原契约。
- 不包含预约业务、生产部署、数据库迁移、新依赖或向 v1 复制代码。

## 2. 验收条件

| ID | 场景/输入 | 预期行为 | 验证方式 | 证据/结果 |
|---|---|---|---|---|
| AC-01 | 扫描实际生产类 | domain、端口、应用服务、入站适配器、跨模块访问与模块循环按类别检查；初始无已接受例外 | ArchUnit 实际类测试，确认受检类非空 | 通过；6 类实际违规均为 0，零例外 |
| AC-02 | 新增一个未登记违规 | 测试失败并报告类别、源类、目标类或循环边、现有基线数字 | 测试夹具注入禁止依赖 | 通过；6 类夹具分类测试与未登记违规断言 |
| AC-03 | 新违规替换旧违规但数量不变 | 测试仍失败；数量相等不能掩盖违规身份变化 | 基线守卫的替换案例 | 通过；精确 ID 集合阻止替换 |
| AC-04 | 确有必要暂留违规 | 该类别数字逐条 +1，精确登记违规标识，非空理由、关联决策或 ticket、消除条件；代码旁写清原因；缺少任一项时测试失败或 review 不通过 | 基线元数据单测、人工 review | 通过结构校验；当前无实际例外，未来具体注释质量仍由 review 判断 |
| AC-05 | 已登记违规被修复 | 测试要求删除相应例外并把基线数字减 1，不保留过期豁免 | 基线守卫的减少案例 | 通过；过期条目及只保留旧数字均失败 |
| AC-06 | 现有身份/邮件和架构回归 | 两处直接依赖改由已发布入站端口或稳定应用契约承接，原 API/任务行为通过 | 相关单元/API 测试及后端全量回归 | 通过；全量 45 个测试和独立 429 映射检查均成功 |

## 3. 规则与未决事项

- 分类至少覆盖：领域与端口纯净性、应用层依赖外部机制或适配器、入站适配器绕过入站端口、跨模块访问内部类型、模块循环。最终分类与项目契约的 ARCH/DEP 规则对应，不能因为更改显示名称而漏检。
- 一个普通违规按稳定的“源类 → 目标类”依赖边识别，不把源码行号作为身份；模块循环按参与模块与有向边识别。同一依赖触犯多条规则时报告所有规则 ID，但只记一个主类别，避免重复抬高基线。
- 分类基线是显式数字；例外还须有精确标识。测试同时核对实际违规集合、例外集合和各类别数字。只把数字加 1、只写宽泛忽略规则、或用另一个违规替换原违规都不能使测试通过。
- 例外记录包含具体原因和消除条件，并在对应代码旁有可读注释。Java 测试无法判断注释解释是否充分，因此结构化理由做自动检查，注释质量由代码 review 检查。违反既有架构硬约束的永久例外仍需相应计划/ADR review，不能仅靠基线变更自行批准。
- 初始目标为各类别 0 条已接受例外；如果新增检查发现其他遗留违规，应先定位与修复。无法在已批准范围内修复的个案提交具体原因和计划修订供 review，不自动提高基线。
- 这项工作没有用户角色、业务状态转换、金额、预约容量或个人数据处理；对应产品规则不适用。

## 4. 模块、端口与依赖

- 架构检查属于 backend 测试基础设施，不新增业务模块或数据表。
- 当前 MailPoller 对 VerificationMailWorker 的直接依赖与 IdentityProblemHandler 对 IdentityService.RateLimited 的直接依赖，是入站边界检查需覆盖的具体案例；修正方式以[实施计划](../implement-plan/0003-architecture-violation-baseline.md)为准。
- 相关契约：ARCH-02 至 ARCH-08、DEP-01 至 DEP-06。保持 domain 和应用端口纯 Java；应用服务仅允许已批准的 Spring DI/事务注解，外部机制留在 adapter。
- 本计划不批准新增架构例外。相关 ADR 0001 保持 ACCEPTED；若发现必须改变架构基线的情形，另行 review。

## 5. API 与前端契约

N/A：本项不新增或修改 HTTP 路径、请求/响应 DTO、前端页面、Cookie 或 CSRF 规则。受影响的现有身份错误处理与邮件轮询必须保持原外部行为。

## 6. 数据、事务与并发

N/A：不修改 Flyway、MySQL 表、事务语义或数据；没有数据迁移与恢复步骤。回退仅涉及测试及小范围 Java 依赖方向调整，仍需保持当前版本可构建。

## 7. 异步任务与外部依赖

邮件轮询的调度频率、失败重试和持久队列行为保持不变。无新外部服务、付费资源或密钥；后端全量回归继续使用现有 MySQL Testcontainers 环境。

## 8. 实施计划关联与实际变更

- [x] 调查既有 ArchUnit 规则和生产代码依赖，建立可 review 的 0003 功能/计划。
- [x] 用户批准 0003 revision 1。
- [x] 按计划先运行有效 RED，再实现、运行同一测试 GREEN 与后端回归。
- [x] 同步基线数字、理由、计划证据、索引和必要契约。

实际变更：新增 `ArchitectureBaseline` 扫描/守卫、分类夹具和精确零基线；新增 `VerificationMailOperations` 与 `RateLimited` 应用契约，使邮件任务入口与限流错误处理不引用具体 service。无架构例外。新增 [TODO-0011](../todo/0011-mail-poller-test-shutdown-noise.md) 记录集成测试收尾日志。

## 9. 验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制 |
|---|---|---|---|---|
| 2026-09-29 | backend / Java 25 | 只读调查阶段运行现有 ArchitectureTest | 现有 3 个架构测试通过 | 现有规则不检查分类基线与入站适配器对同模块 service 的依赖；新用例尚未运行 |
| 2026-09-29 | backend / Java 25 | 先运行 `./mvnw -q -Dtest=ArchitectureTest#inboundAdaptersUsePublishedApplicationContracts test` | RED：5 个成员级依赖对应两条类边，退出码 1 | 非环境故障 |
| 2026-09-29 | backend / Java 25 | 先运行 `./mvnw -q -Dtest=ArchitectureBaselineTest test` | RED：目标 `ArchitectureBaseline` 类型尚不存在，编译退出码 1 | 缺失目标实现，不是环境故障 |
| 2026-09-29 | backend / Java 25 | `./mvnw -q -Dtest=ArchitectureBaselineTest,ArchitectureTest test` | GREEN：11 个架构测试通过，各类别基线为 0 | 无例外 |
| 2026-09-29 | backend / Java 25 / Colima | 设置 `DOCKER_HOST` 和 Testcontainers socket override 后运行 `./mvnw -q test` | 退出码 0；45 个测试、0 失败/错误/跳过 | 初次未设置 Docker socket 导致集成测试环境失败，已修正并重跑；测试收尾日志另记 TODO-0011 |
| 2026-09-29 | backend / Java 25 | `./mvnw -q -Dtest=ArchitectureBaselineTest,ArchitectureTest,IdentityProblemHandlerTest test` | 最终 14 个测试通过，含循环边身份、多规则归类和 429 回归 | 三个新增回归用例在全量测试后单独运行 |
| 2026-09-29 | 仓库根目录 | `python3 ai-docs/check_docs.py`、`git diff --check` | 文档链接、状态、索引与差异空白检查通过 | 未 commit/push |

## 10. 部署、成本与恢复

测试在现有 Maven/CI 中运行，不引入生产资源或费用。本项不执行部署。若后续需允许违规，先尽力修复，再按分类、数字、具体理由与消除条件 review；修复后及时减少基线。

## 11. 交付状态与后续

VERIFIED：已批准的架构基线追踪、两处依赖方向修正和文档均完成本地验证；6 类基线为 0，无已批准违规例外。未发布生产。后续发现的测试收尾日志见 TODO-0011。
