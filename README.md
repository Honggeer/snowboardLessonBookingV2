# Snowboard Lesson Booking App v2

面向一个教练和多个注册学员的授课平台：学员自助预约并查看个人课程，教练管理课程、日程、预约、学员与宣传内容。

## 当前状态

已完成并本地验证工程底座、注册/邮箱验证与登录、密码找回、学员约课、教练排班/课程管理和预约邮件；当前状态以[功能索引](ai-docs/features/README.md)为准。“关于 GEER”与媒体管理见 [0008](ai-docs/implement-plan/0008-about-geer.md)，提供公开主页及教练编辑、草稿预览和发布。选课页的品牌、课程封面与拖动构图见 [0009](ai-docs/implement-plan/0009-course-selection-visuals.md)。

2026-10-04 按 [0010 revision 3](ai-docs/implement-plan/0010-production-delivery.md) 已完成生产技术发布：[https://52.60.174.156](https://52.60.174.156)。main 的 CI 成功后自动部署，HTTPS 自动续期，每日数据库备份到私有 S3、保留 7 天，真实异机恢复已通过。用户明确自行处理账号与业务/媒体测试，这些完整流程未宣称生产验收通过；运行证据及后续限制见计划和[运维手册](deploy/PRODUCTION_RUNBOOK.md)。

本地启动和验证命令见 [部署说明](deploy/README.md)、[后端说明](backend/README.md) 和 [前端说明](frontend/README.md)。

## 结构

```text
snowboardLessonBookingApp-v2/
├── AGENTS.md                 # AI 统一入口
├── CLAUDE.md                 # 同一规则的兼容入口
├── ai-docs/
│   ├── PROJECT_CONTRACT.md   # 项目硬约束
│   ├── FEATURE_TEMPLATE.md   # 功能设计模板
│   ├── features/             # 功能规则与验收证据
│   ├── decisions/            # ADR：架构与业务取舍
│   ├── todo/                 # 开发中发现的待办 Ticket
│   └── implement-plan/       # 用户 review、批准与实际执行记录
│       ├── TEMPLATE.md       # 实施计划模板
│       └── NNNN-name.md      # 功能实施计划
├── backend/                  # Spring Boot 六边形单体
├── frontend/                 # React/Vite 页面
├── deploy/                   # 本地 MySQL、后端、前端、邮件沙箱与冒烟检查
└── .github/workflows/ci.yml # 后端、前端、文档与 Compose 检查
```

## 已确认方向

- Java 后端、MySQL、模块化单体与模块内六边形架构。
- 保留学员注册、自助预约和个人课程页面。
- 前后端分离；初期同一台 AWS EC2 上运行入口/前端、Java、MySQL 三个容器。
- 预算目标 30 CAD/月；宣传媒体规划 S3 + CloudFront。
- AI 承担开发维护，用户负责决策；每个功能先设计与 review，再实现并更新文档。

## 文档导航

[AI 文档](ai-docs/README.md) · [项目契约](ai-docs/PROJECT_CONTRACT.md) · [功能索引](ai-docs/features/README.md) · [实施计划索引](ai-docs/implement-plan/README.md) · [待办 Ticket](ai-docs/todo/README.md) · [架构决策](ai-docs/decisions/README.md)

v1 位于同级 `snowboardLessonBookingApp`，本次未修改。其代码/业务仅供逐项核对和复用，生产数据迁移与生产切换需要独立计划及授权。

文档检查：在项目根运行 `python3 ai-docs/check_docs.py`。它检查内部链接、功能/计划配对与状态、ticket 格式与索引、审批修订；不能替代业务测试、架构测试或用户 review。
