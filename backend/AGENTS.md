# 后端 AI 入口

遵守 [根 AGENTS.md](../AGENTS.md) 与 [项目契约](../ai-docs/PROJECT_CONTRACT.md)。先读取已批准实施计划再改功能代码。

按业务模块划分，domain/端口纯 Java，application 编排用例，adapter 实现数据库/Web/云依赖。首个可运行骨架必须包含非空、可执行的架构依赖检查；不得让空包检查假装验证了真实模块。

计划 0001 revision 2 已获用户批准并完成本地验证；后续变更继续按根规则 review，获批实现时记录测试先行的 RED/GREEN 证据。
