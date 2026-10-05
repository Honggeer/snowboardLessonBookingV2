# 实施计划与 review 流程

本目录是用户 review 的入口，AI 不能自行越过它。规则依据用户要求：“每次设计新功能都要在里面生成 plan 让我 review，然后实现了以后要更新一下”。

## 文件与内容

- 使用 [TEMPLATE.md](TEMPLATE.md)，命名 `NNNN-short-kebab-name.md`，与 [功能文档](../features/README.md) 共用 ID 和名称。
- 一个业务功能跨前后端共用一份计划。计划必须包含具体步骤、预计文件、验收/检查、数据/兼容影响、成本/恢复和需要决定的问题。
- AI 必须做足够的只读调查，让方案可评估；未定事项明确标注，不捏造用户决定。
- 开发中新发现的后续工作按 [待办 Ticket 规则](../todo/README.md) 记录；ticket 不代替本目录的计划或用户批准。
- 计划列出对应测试用例及 RED/GREEN 的执行方式；这是用户批准计划后进入 IMPLEMENTING 阶段的开发流程。先创建测试、运行到预期失败，再实现、运行同一测试通过及适用回归检查；实际命令和结果写回计划。需求分析与计划编写阶段不执行此流程。
- 不适用章节保留并写明原因。实际结果不得提前填写为通过。

## 状态与批准

| 状态 | 进入条件 |
|---|---|
| DRAFT | 整理需求或存在阻塞决策。 |
| AWAITING_REVIEW | 计划可审阅，等待用户明确同意；不允许功能实现。 |
| APPROVED | 记录用户批准依据、批准 revision、范围与条件。 |
| IMPLEMENTING | 在已批准修订范围内实现，持续更新步骤。 |
| IMPLEMENTED | 实现步骤完成，但必要验证尚未全部通过。 |
| VERIFIED | 验收与必要检查实际通过，证据完整；不等于生产发布。 |
| RELEASED | 实际获授权部署且发布后验证通过。 |
| REJECTED / CANCELLED | 记录决定和原因，保留历史与编号。 |

- APPROVED 及其后续状态必须有匹配的 `approved_revision` 与用户批准记录；AI 不得自行批准。
- 用户可直接在对话中批准，AI 把原话、日期、修订号与范围写回计划。
- 用户要求修改时先更新计划。实质改动增加 revision，新修订回到 AWAITING_REVIEW；历史批准记录保留，但当前 approved_revision 置空。
- 用户明确撤回/取消时立即停止相关实现；仅文档和已独立授权工作可继续。
- 实现完成更新 checkbox、实际文件、测试先行的 RED/GREEN 命令/结果/限制、验收对应、成本与恢复说明，再同步功能和两个索引。
- 批准实现计划不自动授权生产部署、数据导入或实际付费云资源。

## 计划索引

| ID | 计划 | 功能设计 | 状态 | revision | 更新时间 |
|---|---|---|---|---|---|
| 0000 | [独立项目与文档骨架](0000-project-bootstrap.md) | [0000](../features/0000-project-bootstrap.md) | VERIFIED | 1 | 2026-09-27 |
| 0001 | [工程底座](0001-project-foundation.md) | [0001](../features/0001-project-foundation.md) | VERIFIED | 2 | 2026-09-28 |
| 0002 | [学员注册、邮箱验证与账号登录](0002-student-identity.md) | [0002](../features/0002-student-identity.md) | VERIFIED | 2 | 2026-09-30 |
| 0003 | [六边形架构违规基线追踪](0003-architecture-violation-baseline.md) | [0003](../features/0003-architecture-violation-baseline.md) | VERIFIED | 1 | 2026-09-29 |
| 0004 | [邮箱验证码找回密码](0004-password-recovery.md) | [0004](../features/0004-password-recovery.md) | VERIFIED | 1 | 2026-09-30 |
| 0005 | [集成测试后台任务与数据库生命周期](0005-test-scheduler-lifecycle.md) | [0005](../features/0005-test-scheduler-lifecycle.md) | VERIFIED | 1 | 2026-09-30 |
| 0006 | [登录后约课主界面](0006-post-login-booking-home.md) | [0006](../features/0006-post-login-booking-home.md) | VERIFIED | 4 | 2026-10-04 |
| 0007 | [预约邮件通知与取消规则提示](0007-booking-email-notifications.md) | [0007](../features/0007-booking-email-notifications.md) | VERIFIED | 2 | 2026-10-01 |
| 0008 | [关于 GEER：教练主页与媒体管理](0008-about-geer.md) | [0008](../features/0008-about-geer.md) | VERIFIED | 1 | 2026-10-04 |
| 0009 | [选课页品牌与课程封面交互](0009-course-selection-visuals.md) | [0009](../features/0009-course-selection-visuals.md) | VERIFIED | 3 | 2026-10-04 |
| 0010 | [首次生产上线与 CI/CD](0010-production-delivery.md) | [0010](../features/0010-production-delivery.md) | RELEASED | 3 | 2026-10-04 |

## 可运行文档检查

在新项目根目录运行：

```sh
python3 ai-docs/check_docs.py
```

检查链接、功能/计划配对、状态与索引、批准修订和证据章节。不批准计划、不生成业务代码、不代替架构/业务测试。基础 CI 将在计划 0001 获准后接入该检查。

0010 的 deploy 目录整理已完成本地验证；当前目录与命令见 [部署入口](../../deploy/README.md)，实际证据保存在配对记录中，尚未触发新的生产发布。
