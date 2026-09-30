---
id: "0003"
title: "六边形架构违规基线追踪"
status: VERIFIED
revision: 1
approved_revision: 1
created: 2026-09-29
updated: 2026-09-29
feature: "../features/0003-architecture-violation-baseline.md"
---

# 0003 — 六边形架构违规基线追踪实施计划

## 1. Review 摘要

关联[功能契约 0003](../features/0003-architecture-violation-baseline.md)、[项目契约](../PROJECT_CONTRACT.md)与[ADR 0001](../decisions/0001-v2-baseline.md)。用户希望任何新的六边形架构违规都被分类、使该类别基线数字逐条增加 1，并有解释为什么暂留的注释；应尽量通过修复让基线保持 0。

现有 ArchitectureRules/ArchitectureTest 已有 5 条 ArchUnit 规则、真实生产类检查和两个反例，2026-09-29 运行 3/3 通过。它们缺少可追踪的违规身份、分类数字与理由检查；入站适配器依赖同模块应用服务也未被检查。源码调查确认 MailPoller → VerificationMailWorker，以及 IdentityProblemHandler → IdentityService.RateLimited 两处实例。计划优先通过入站端口/应用契约修正，不将其登记成长期基线。

**拟采用的基线契约：**

1. 以现有项目架构规则为依据，给每个检查分配稳定类别代码。预定类别为 DOMAIN_PURITY、PORT_PURITY、APPLICATION_BOUNDARY、INBOUND_BOUNDARY、CROSS_MODULE_INTERNAL、MODULE_CYCLE。APPLICATION_BOUNDARY 同时检查应用服务对 adapter、Servlet/JDBC/MyBatis/AWS 等外部机制的依赖；INBOUND_BOUNDARY 检查入站适配器对出站适配器和同模块 service 的绕过。保留对非空实际生产类的断言。
2. 普通违规以类别、源类、目标类的规范化组合为唯一身份；同一边触犯多个检查时按“跨模块内部 → domain/port → application → inbound”的优先级选主类别，同时报告全部规则 ID。循环以参与模块及其有向依赖边作为稳定身份。避免源码行号、报告顺序或文字变化使基线漂移。
3. 每类维护显式整数基线与精确例外条目。当前目标均为 0，无预先接受的例外。每次新违规先修；确实无法在已批准范围内修复时，按实际新增条数增加该类别数字，每条登记规范化身份、非空具体理由、关联计划/ADR 或 ticket、消除条件，并在登记处写解释性注释。修掉一条时同步删除条目、数字减 1。
4. 测试同时比较实际违规身份集合、登记集合和基线数字；新增违规、同数量替换、只加数字、空理由、过期登记都失败。测试可自动检查结构化理由是否存在；“为什么必须暂留”的注释质量由 review 判断。新增永久例外或修改架构硬约束仍走既有计划/ADR review 门槛，基线条目不构成自行批准。

本次只调整 backend 测试和上述两处依赖方向及其最小契约；不新增业务 API、数据库迁移、依赖、云资源或生产部署。若实施时发现无法保持初始零基线的其他遗留违规，先提交具体证据与修订计划 review，不自动提高数字。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-09-29 | 用户回复“开始实现” | 1 | 批准本计划 revision 1 所列架构测试、零基线追踪、两处入站边界修正与文档；无附加条件。 |

当前 approved_revision 为 1；已进入 IMPLEMENTING。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | ArchitectureTest、ArchitectureRules、测试夹具及基线守卫测试 | **批准后先写目标测试**：新增同模块 inbound → service 反例、分类、计数、同数量替换、缺理由和消除例外案例；先实际运行，确认因现有检查/守卫能力缺失而 RED。 | 有效 RED 记录，失败不是环境或夹具错误 | 完成：入站规则运行报 5 个成员级依赖；基线守卫目标类型缺失导致编译 RED |
| P-02 | backend 测试基础设施 | 用 ArchUnit 导入生产类并生成规范化违规集合；按稳定类别记录显式数字与精确例外条目；报告包含类别/规则/依赖。保留既有非空实际类检查和反例。 | 同一 P-01 测试 GREEN；当前每类基线 0 | 完成：6 类基线全为 0；精确身份与循环边检测通过 |
| P-03 | identity 的应用入站契约、MailPoller、IdentityProblemHandler、必要回归测试 | 将轮询触发能力通过明确入站端口发布；将限流错误类型放在稳定应用契约，入站适配器不引用具体 service。保持现有异常到 HTTP 429、邮件任务处理语义。 | 新架构规则 GREEN，相关行为测试通过 | 完成：新增邮件入站端口、应用限流错误契约；身份/API/邮件回归通过 |
| P-04 | 项目契约、AGENTS、backend README、0003 功能/计划与索引 | 批准后把“新增违规先修复；例外分类、数字 +1、具体注释/理由和消除条件”的流程写成通用规则；记录实际初始数字与 RED/GREEN 命令。 | 文档检查通过，状态与证据一致 | 完成：ARCH-09、AGENTS、README、证据与索引已同步 |

- [x] 用户明确批准 revision 1 并记录日期、原话、范围及条件。
- [x] 对目标行为先写测试并实际确认 RED，再实现至同一测试 GREEN；目标类型缺失造成的编译 RED 与环境故障分别记录。
- [x] 运行 backend ArchitectureTest 及新增基线守卫测试、受影响身份/API/邮件测试、后端全量回归，使用现有 MySQL 8.4 Testcontainers。
- [x] 更新实际基线、变更与文档证据；不自行 commit/push。

## 4. 数据、API、架构与兼容影响

- 模块与端口：仍是同一模块化单体。MailPoller 应调用 identity 发布的入站端口；IdentityProblemHandler 引用稳定应用错误类型。业务服务只依赖端口，外部机制仍在 adapter。新检查不扫描测试夹具为生产违规，但单独用夹具证明会报错。
- API/前端：路径、DTO、Cookie/CSRF 与用户可见文案保持不变；对限流 429、邮件轮询相关行为做回归。
- 数据/并发：无 Flyway、表或事务语义变化；不修改已落库数据。
- 分类与数量：按规范化边计一条，减少同一依赖在多个成员中出现造成的重复；新增同目标类的第二个成员调用视为原有结构边，改目标类或新增模块有向边视为新违规。循环检测保留 ArchUnit 能力或等价模块图检查，身份包含有向边，以防同一组模块新增边被忽略。
- 技术依赖：继续使用当前 POM 中的 ArchUnit 1.5.0、JUnit 5 与 Java 25；不加库。
- 架构例外：本计划不批准任何具体违规。初始目标 0；若出现无法修复的例外，按本计划第 1 节记录并遵守契约的 review 门槛。ADR 0001 不变。

## 5. 验收与验证计划

| 验收 ID | 具体验证 | 环境/命令 | 预期结果 |
|---|---|---|---|
| AC-01 | 实际生产类、全部类别及零基线 | Java 25；backend Maven 定向架构测试 | 扫描域/端口/服务/适配器非空，各类别 0，测试通过 |
| AC-02 | 新禁止依赖 | 先写夹具/测试，运行定向架构测试 RED，再实现并运行 GREEN | RED 报类别和稳定标识；实现后守卫能识别并拒绝 |
| AC-03 | 同数量违规替换 | 基线守卫单测先 RED 后 GREEN | 仅比较总数无法蒙混 |
| AC-04 | 数字、理由和消除条件 | 基线守卫单测先 RED 后 GREEN；代码 review | 单独加数字、空理由或占位符、缺关联或消除条件被拒；解释是否充分及代码注释由人工审查 |
| AC-05 | 修复后基线减少 | 基线守卫单测先 RED 后 GREEN | 多余登记或数字不一致失败 |
| AC-06 | 429 与邮件轮询行为 | 相关现有/必要新增测试；backend 全量 Maven test | 对外行为保持，ArchUnit 与身份回归通过 |

定向命令预计为 backend 目录下以 Java 25 执行 Maven 的 ArchitectureTest 与基线守卫测试；真实 MySQL 全量回归沿用现有 Colima/Docker 环境变量。实际 RED/GREEN 命令和测试数在实施时写入第 8 节。前端、生产部署和云环境检查不适用，因为本次无对应代码/资源变更。

## 6. 风险、成本、部署与恢复

- 最主要风险是“只加基线数字让 CI 变绿”；精确违规身份、集合比对和非空理由共同防止。注释是否充分需 review；任何无法维护为零的例外都要有可消除计划。
- 更严格规则可能发现计划未识别的遗留依赖。先修正；若涉及更大架构或业务改动，增加 revision 并重新 review。
- 不新增付费资源、密钥或生产操作。测试代码可在工作区撤回；不触及数据库恢复。用户未授权 Git commit/push。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-09-29 | P-01：ArchitectureTest 新入站规则、ArchitectureBaselineTest 六个守卫场景 | 先于实现运行；入站检查 RED，基线类型不存在导致目标测试编译 RED | 无范围变更 |
| 2026-09-29 | P-02：ArchitectureBaseline、ArchitectureTest、各层测试夹具 | 按直接依赖边合并成员级报告，生成 6 类稳定身份、规则 ID 和模块循环边；显式基线为 `0/0/0/0/0/0`，无例外 | 用分类扫描与精确集合取代旧的实际生产类 `all()` 断言；保留旧反例测试与非空检查，覆盖同等边界并增加入站 service/应用外部机制检查 |
| 2026-09-29 | P-03：VerificationMailOperations、RateLimited、MailPoller、VerificationMailWorker、IdentityService、IdentityProblemHandler、IdentityFlowTest、IdentityProblemHandlerTest | 两处入站适配器改依赖应用契约；邮件调度与限流 429 语义保持 | 无 API、表或事务变化 |
| 2026-09-29 | P-04：项目契约、AGENTS、backend README、0003 文档及索引 | 写入基线维护流程；测试期间观察到邮件轮询与测试 DB 关闭阶段的连接错误日志，另记 [TODO-0011](../todo/0011-mail-poller-test-shutdown-noise.md) | 日志不使测试失败；问题是否仅属测试资源收尾尚未确认，不越过本计划修复 |

## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-09-29 | backend / Java 25 | 只读调查运行现有 ArchitectureTest | 现有 3 个测试通过 | 这不是新基线规则的 GREEN；新规则测试尚未创建或运行 |
| 2026-09-29 | backend / Java 25 | `./mvnw -q -Dtest=ArchitectureTest#inboundAdaptersUsePublishedApplicationContracts test` | RED，退出码 1；找到 5 个成员级依赖，属于 MailPoller→VerificationMailWorker 与 IdentityProblemHandler→IdentityService.RateLimited 两条稳定类边 | 目标行为缺失，非环境故障 |
| 2026-09-29 | backend / Java 25 | `./mvnw -q -Dtest=ArchitectureBaselineTest test`，先于守卫实现 | RED，退出码 1；编译器报告目标 `ArchitectureBaseline` 类型不存在 | 目标类尚未实现造成的编译 RED；不是运行时断言或环境故障 |
| 2026-09-29 | backend / Java 25 | `./mvnw -q -Dtest=ArchitectureBaselineTest,ArchitectureTest test` | GREEN，11 个测试通过，6 类实际违规均为 0 | 定向架构测试，不代替 MySQL 回归 |
| 2026-09-29 | backend / Java 25 / Colima / MySQL 8.4 Testcontainers | `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q test` | 退出码 0；Surefire 报告 45 个测试、0 失败、0 错误、0 跳过，包含身份 API、流程、邮件和架构 | 首次未设置 DOCKER_HOST 的集成运行因找不到 Docker socket 失败，配置 Colima socket 后重跑成功；运行中观察到 TODO-0011 日志 |
| 2026-09-29 | backend / Java 25 | `./mvnw -q -Dtest=ArchitectureBaselineTest,ArchitectureTest,IdentityProblemHandlerTest test` | 最终 14 个测试通过；包括循环内有向边身份、多规则单主类别与 429 映射回归 | 三个新增回归用例在全量回归后单独运行通过 |
| 2026-09-29 | 仓库根目录 | `python3 ai-docs/check_docs.py`、`git diff --check` | 文档检查通过：4 组配对文档、11 张 ticket、索引/状态/review 门槛正确；Git 差异无空白错误 | 未执行 commit/push |

## 9. 完成状态与后续

VERIFIED：0003 revision 1 已按批准范围完成，生产架构扫描的 6 类基线均为 0，未接受例外。测试先行的 RED、同一目标测试 GREEN、MySQL 全量回归和 429 映射回归均有实际证据。未生产发布；测试收尾的调度日志见 TODO-0011。
