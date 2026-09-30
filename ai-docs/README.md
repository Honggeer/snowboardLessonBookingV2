# AI 文档目录

- [项目契约](PROJECT_CONTRACT.md)：产品、架构、依赖、数据/API/权限/异步/运维约束。
- [功能模板](FEATURE_TEMPLATE.md)：描述“做什么、业务规则是什么、怎样验收”。
- [功能索引](features/README.md)：每个功能的设计与实际状态。
- [决策索引](decisions/README.md)：记录关键架构/业务取舍与变更。
- [实施计划](implement-plan/README.md)：描述“怎么做”，供用户 review，并记录实际执行与验证。
- [待办 Ticket](todo/README.md)：记录开发中发现的后续工作、问题背景和期望结果。
- [根 AI 入口](../AGENTS.md)：每次任务的必读路径与硬性 review 门槛。

## 一项功能，两份配对文档

```text
ai-docs/features/0002-student-identity.md
ai-docs/implement-plan/0002-student-identity.md
```

同 ID、同业务范围、互相链接。功能文档维护规则/接口/验收；计划维护步骤、文件、review、偏差和结果，避免同一细节在两处重复且相互矛盾。

## 事实与规划

工程底座 0001 已获批准并建立本地应用代码；实际完成状态以[对应计划](implement-plan/0001-project-foundation.md)中的验证证据为准。任何“将使用”“计划”“待验证”都不表示已经实现，AI 不能批准自己的计划。

运行 `python3 ai-docs/check_docs.py` 检查项目文档内部链接、配对、ticket 格式与索引、审批状态；功能代码另需执行适用的架构/业务/集成检查。
