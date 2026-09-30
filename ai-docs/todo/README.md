# 待办 Ticket 索引

开发中发现尚未记录的缺陷、改进、遗漏或待确认需求时，先建立一张 ticket，避免它只留在对话或代码注释里。已在功能文档或计划中明确列出的步骤无需重复建票；同一问题已有 ticket 时更新原票并链接新证据。

## 编号与格式

- 使用 [模板](TEMPLATE.md)，文件名为 `NNNN-short-kebab-name.md`，文档 ID 为 `TODO-NNNN`。编号从 `0001` 开始，按本目录现有最大编号加一；独立于功能和 ADR 编号，关闭或取消后也不复用。
- 标题写具体待解决的问题。记录发现来源、可核实的背景、问题及影响、为什么要处理、期望结果和可观察的完成判定。未知事项明确标注并向用户澄清，不填猜测。
- ticket 只描述**做什么、为什么**。实现方案、文件清单、技术步骤和测试代码放在相应功能文档与实施计划中。必要的日志、页面、文件位置可作为问题证据引用。
- 创建或更新 ticket 时同步下方索引、状态和日期；保留历史 ticket，不靠删除表示完成。

## 状态与 review 边界

| 状态 | 含义 |
|---|---|
| OPEN | 已记录，尚未开始处理；未确认的信息在票内列出。 |
| IN_PROGRESS | 已有覆盖此工作的用户批准计划，正在按计划处理。 |
| DONE | 期望结果已按完成判定验证，记录证据和相关计划。 |
| CANCELLED | 不再处理；记录原因或替代 ticket。 |

ticket 是待办记录，**不批准实现代码**。若工作已在获批计划范围内，可关联该计划并按其流程处理；若会改变范围、验收、架构、数据或成本，先更新相应功能/计划并按 review 规则获批。状态改为 IN_PROGRESS 不代表自动获得批准。

## Ticket 索引

| ID | Ticket | 状态 | 关联功能/计划 | 更新时间 |
|---|---|---|---|---|
| TODO-0001 | [文档检查忽略生成的依赖文件](0001-doc-checker-ignores-generated-files.md) | DONE | [0001](../implement-plan/0001-project-foundation.md) | 2026-09-27 |
| TODO-0002 | [本地后端启动失败](0002-local-backend-startup-failure.md) | DONE | [0001](../implement-plan/0001-project-foundation.md) | 2026-09-27 |
| TODO-0003 | [前端开发代理无法连接本地后端](0003-frontend-dev-proxy-reaches-backend.md) | DONE | [0001](../implement-plan/0001-project-foundation.md) | 2026-09-27 |
| TODO-0004 | [同源 API 入口未保留演示接口路径](0004-api-proxy-preserves-route.md) | DONE | [0001](../implement-plan/0001-project-foundation.md) | 2026-09-27 |
| TODO-0005 | [基础页向访客显示内部开发流程](0005-visitor-copy-avoids-internal-process.md) | DONE | [0001](../implement-plan/0001-project-foundation.md) | 2026-09-27 |
| TODO-0006 | [本地容器缺少可复用的启动配置](0006-local-stack-restart-configuration.md) | DONE | [0001](../implement-plan/0001-project-foundation.md) | 2026-09-27 |
| TODO-0007 | [演示预览写请求无法获取 CSRF token](0007-demo-preview-csrf-token-access.md) | DONE | [0001](../implement-plan/0001-project-foundation.md) | 2026-09-27 |
| TODO-0008 | [已注册账号缺少安全的密码找回流程](0008-password-recovery.md) | OPEN | [0002](../implement-plan/0002-student-identity.md) | 2026-09-28 |
| TODO-0009 | [IDEA 本地数据库凭据不易核对](0009-idea-db-credentials-check.md) | DONE | [0002](../implement-plan/0002-student-identity.md) | 2026-09-29 |
| TODO-0010 | [身份页面与已选蓝色设计稿明显不一致](0010-identity-visual-mismatch.md) | DONE | [0002 revision 2](../implement-plan/0002-student-identity.md) | 2026-09-29 |
| TODO-0011 | [集成测试收尾时邮件轮询任务记录数据库连接错误](0011-mail-poller-test-shutdown-noise.md) | OPEN | [0003](../implement-plan/0003-architecture-violation-baseline.md) | 2026-09-29 |
