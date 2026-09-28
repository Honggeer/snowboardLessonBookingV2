# AI 项目入口

本仓库是独立 v2 项目：一个教练、多个注册学员。AI 负责实现与维护，用户负责业务、架构和成本决策。

## 开始任务必须读取

1. [项目契约](ai-docs/PROJECT_CONTRACT.md)。
2. [功能索引](ai-docs/features/README.md) 和本次对应功能。
3. [实施计划索引与 review 流程](ai-docs/implement-plan/README.md) 和对应计划。
4. [决策索引](ai-docs/decisions/README.md)、相关 ADR、源码及当前 Git 改动。
5. [待办 Ticket 索引](ai-docs/todo/README.md) 和本次相关 ticket。

## 通用协作规则

- Codex 不得自行执行 Git commit（含 amend）或 push；修改留在工作区供用户 review，由用户自行 commit 和 push。只有用户明确要求 Codex 执行对应操作时例外。
- 所有 commit 的标题使用 `type: description` 格式：小写 type、英文冒号、一个空格和简短描述；例如 `feat: add booking page`、`fix: prevent duplicate booking`。常用 type 包括 `feat`、`fix`、`docs`、`refactor`、`test`、`chore`、`build`、`ci`、`perf`、`revert`。本条不授权 Codex 自行 commit。
- 遇到未知或含糊的需求、规则或事实，先核对用户指令、已批准文档、源码和可验证资料。仍不能确定时，明确提出问题；在澄清前暂停依赖该答案的工作，不得把猜测当作事实或用户决定。
- 用户批准对应计划后，进入 IMPLEMENTING 阶段的开发工作先创建对应测试用例，实际运行并确认它因目标行为尚未实现而失败；随后实现，运行同一用例至通过，并运行适用的回归检查。记录红灯和绿灯的命令、结果；环境故障或测试本身错误不算有效红灯。需求分析、计划和其他批准前文档工作不要求 RED/GREEN，也不得在批准前写该功能的测试或实现代码。
- 开发中发现尚未记录的待办、缺陷或改进时，按 [Ticket 模板](ai-docs/todo/TEMPLATE.md) 在 `ai-docs/todo/` 建立独立编号的 ticket 并更新索引。ticket 写清背景、问题、原因、期望结果和完成判定，不写实现方案；ticket 本身不授权越过计划 review。

## Review 门槛

- 每项新功能先创建 `ai-docs/features/NNNN-name.md` 与 `ai-docs/implement-plan/NNNN-name.md`，使用同一个 ID，互相链接。
- **用户 review 并明确同意计划之前，不得写该功能的实现代码。** 可以继续需求分析、只读调查、文档和计划编写。
- 把计划整理到可 review 后，提供链接、具体实现范围、验收与需要决定的问题；不能只提供目录名称或空白模板。
- 用户批准后，在计划记录批准日期、依据原话、revision、范围及条件。沉默、经过一段时间或 AI 自己判断不算批准。
- 在批准范围内自主实现、测试、更新文档，不逐文件重复确认。实质改变范围、验收、架构、数据或成本时增加修订号并重新 review。
- 完成后同步更新计划步骤、实际变更、验证证据、功能文档、两个索引和必要 ADR。
- 未实际运行的测试不得报告通过；IMPLEMENTED、VERIFIED、RELEASED 分开。
- 用户批准实现不等于批准生产部署、新增实际付费资源或不可逆数据操作；遵守现有授权和工具/文件系统权限。

## 架构与产品硬约束

- Java/Spring Boot、MySQL；按业务模块组织的单体，模块内六边形架构。
- domain 和应用端口纯 Java；外部机制位于 adapter；用例不得依赖 adapter 实现。
- 跨模块只用明确发布的入站端口/事件，禁止访问内部 service/mapper/业务表和循环依赖。
- MySQL 并发保障靠事务、稳定锁目标/条件更新和约束；关键测试使用真实 MySQL。
- 新项目独立配置与数据库；不得复制 v1 密钥、个人数据或旧依赖树。
- 保留学员注册、自助预约、查看本人课程；教练拥有管理权限。
- AWS EC2 继续使用，预算目标 30 CAD/月；初期前端/入口、Java、MySQL 同机，视频规划 S3/CloudFront。
- 精确依赖版本和业务规则以已批准计划为准；不得把文档规划当作已实现。

## 当前状态与指令优先级

工程底座计划 [0001](ai-docs/implement-plan/0001-project-foundation.md) revision 2 已获用户批准并通过本地验证，尚未生产发布；注册和预约等业务功能仍需单独计划与 review。

用户当前指令优先；本文件不能覆盖系统、开发者及工具权限规则。若用户修改已确认决策，先更新计划/契约/ADR，再实现相关变化。
