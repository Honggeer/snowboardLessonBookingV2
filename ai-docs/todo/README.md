# 当前待办 Ticket

开发中发现尚未记录的缺陷、改进、遗漏或待确认需求时，先建立一张 ticket，避免它只留在对话或代码注释里。已在功能文档或计划中明确列出的步骤无需重复建票；同一问题已有 ticket 时更新原票并链接新证据。

本页只列 `OPEN` / `IN_PROGRESS`。已完成或取消的记录见[归档索引](DONE.md)；ticket 正文仍按原编号保存在本目录，已有链接无需修改。日常任务只读本页及相关 ticket，需要追溯时再读归档。

## 编号与格式

- 使用 [模板](TEMPLATE.md)，文件名为 `NNNN-short-kebab-name.md`，文档 ID 为 `TODO-NNNN`。编号从 `0001` 开始，按本目录现有最大编号加一；独立于功能和 ADR 编号，关闭或取消后也不复用。
- 标题写具体待解决的问题。记录发现来源、可核实的背景、问题及影响、为什么要处理、期望结果和可观察的完成判定。未知事项明确标注并向用户澄清，不填猜测。
- ticket 只描述**做什么、为什么**。实现方案、文件清单、技术步骤和测试代码放在相应功能文档与实施计划中。必要的日志、页面、文件位置可作为问题证据引用。
- 创建或更新 ticket 时同步本页或[归档索引](DONE.md)的状态和日期；结案时把索引行移到归档，保留原 ticket 文件。新编号始终取本目录所有 ticket 文件的最大编号加一。

## 状态与 review 边界

| 状态 | 含义 |
|---|---|
| OPEN | 已记录，尚未开始处理；未确认的信息在票内列出。 |
| IN_PROGRESS | 已有覆盖此工作的用户批准计划，正在按计划处理。 |
| DONE | 期望结果已按完成判定验证，记录证据和相关计划。 |
| CANCELLED | 不再处理；记录原因或替代 ticket。 |

ticket 是待办记录，**不批准实现代码**。若工作已在获批计划范围内，可关联该计划并按其流程处理；若会改变范围、验收、架构、数据或成本，先更新相应功能/计划并按 review 规则获批。状态改为 IN_PROGRESS 不代表自动获得批准。

## 当前 Ticket 索引

| ID | Ticket | 状态 | 关联功能/计划 | 更新时间 |
|---|---|---|---|---|
| TODO-0022 | [本地高光视频接近一分钟时传输中断](0022-local-highlight-video-stalls.md) | OPEN | [0008 revision 1](../implement-plan/0008-about-geer.md) | 2026-10-03 |
| TODO-0024 | [现有 EC2 根盘未启用加密](0024-ec2-root-volume-unencrypted.md) | IN_PROGRESS | [0010 revision 3](../implement-plan/0010-production-delivery.md) | 2026-10-04 |
| TODO-0028 | [生产合法视频校验超时后被当作格式错误拒绝](0028-production-video-probe-timeout.md) | IN_PROGRESS | [0008 revision 2](../implement-plan/0008-about-geer.md) | 2026-10-04 |
