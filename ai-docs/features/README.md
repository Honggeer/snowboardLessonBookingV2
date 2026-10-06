# 功能索引

每项新功能必须先按 [功能模板](../FEATURE_TEMPLATE.md) 创建设计，并按 [实施计划模板](../implement-plan/TEMPLATE.md) 创建同 ID 计划。先 review 后代码。

- 文件名：`NNNN-short-kebab-name.md`；ID 唯一，按当前最大 ID + 1 分配，不重用取消编号。
- 前后端共用一份功能和一份计划；迭代更新现有文件，实质范围变化重新 review。
- 新文档在 AWAITING_REVIEW 前填完必需内容；不适用写明原因，未决事项标注影响和是否阻塞。
- 状态与对应计划及索引保持一致，状态定义见 [计划流程](../implement-plan/README.md)。

| ID | 功能 | 实施计划 | 状态 | 更新时间 |
|---|---|---|---|---|
| 0000 | [独立项目与文档骨架](0000-project-bootstrap.md) | [0000](../implement-plan/0000-project-bootstrap.md) | VERIFIED | 2026-09-27 |
| 0001 | [工程底座](0001-project-foundation.md) | [0001](../implement-plan/0001-project-foundation.md) | VERIFIED | 2026-09-28 |
| 0002 | [学员注册、邮箱验证与账号登录](0002-student-identity.md) | [0002](../implement-plan/0002-student-identity.md) | VERIFIED | 2026-10-05 |
| 0003 | [六边形架构违规基线追踪](0003-architecture-violation-baseline.md) | [0003](../implement-plan/0003-architecture-violation-baseline.md) | VERIFIED | 2026-09-29 |
| 0004 | [邮箱验证码找回密码](0004-password-recovery.md) | [0004](../implement-plan/0004-password-recovery.md) | VERIFIED | 2026-09-30 |
| 0005 | [集成测试后台任务与数据库生命周期](0005-test-scheduler-lifecycle.md) | [0005](../implement-plan/0005-test-scheduler-lifecycle.md) | VERIFIED | 2026-09-30 |
| 0006 | [登录后约课主界面](0006-post-login-booking-home.md) | [0006](../implement-plan/0006-post-login-booking-home.md) | VERIFIED | 2026-10-05 |
| 0007 | [预约邮件通知与取消规则提示](0007-booking-email-notifications.md) | [0007](../implement-plan/0007-booking-email-notifications.md) | VERIFIED | 2026-10-06 |
| 0008 | [关于 GEER：教练主页与媒体管理](0008-about-geer.md) | [0008](../implement-plan/0008-about-geer.md) | VERIFIED | 2026-10-05 |
| 0009 | [选课页品牌与课程封面交互](0009-course-selection-visuals.md) | [0009](../implement-plan/0009-course-selection-visuals.md) | VERIFIED | 2026-10-05 |
| 0010 | [首次生产上线与 CI/CD](0010-production-delivery.md) | [0010](../implement-plan/0010-production-delivery.md) | IMPLEMENTED | 2026-10-05 |
| 0011 | [首次预约前填写联系电话](0011-student-contact-phone.md) | [0011](../implement-plan/0011-student-contact-phone.md) | VERIFIED | 2026-10-06 |
| 0012 | [已确认预约课前邮件提醒](0012-lesson-reminder-emails.md) | [0012](../implement-plan/0012-lesson-reminder-emails.md) | VERIFIED | 2026-10-06 |

0010 的 deploy 目录整理已完成本地验证；当前目录与命令见 [部署入口](../../deploy/README.md)，实际证据保存在配对记录中。目录整理已由用户提交推送；生产密钥文件权限维护已实际执行并验证，代码发布状态另按远端 CI/CD 结果核对。
