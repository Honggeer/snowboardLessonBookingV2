# Snowboard Lesson Booking App v2

面向一个教练和多个注册学员的授课平台：学员自助预约并查看个人课程，教练管理课程、日程、预约、学员与宣传内容。

## 当前状态

已完成并本地验证工程底座、注册/邮箱验证与登录、密码找回、学员约课、教练排班/课程管理和预约邮件；当前状态以[功能索引](ai-docs/features/README.md)为准。“关于 GEER”与媒体管理见 [0008](ai-docs/implement-plan/0008-about-geer.md)，提供公开主页及教练编辑、草稿预览和发布。选课页的品牌、课程封面与拖动构图见 [0009](ai-docs/implement-plan/0009-course-selection-visuals.md)。

2026-10-04 首次生产技术发布已完成；2026-10-05 按已批准的 [0010 revision 4](ai-docs/implement-plan/0010-production-delivery.md) 接入主域名 [https://ridewithgeer.com](https://ridewithgeer.com)，www 与旧 IP 地址保留。main 的 CI 成功后自动部署，HTTPS 自动续期，每日数据库备份到私有 S3、保留 7 天，原真实异机恢复已通过。域名切换的实际进展与证据见计划和[运维手册](deploy/docs/PRODUCTION_RUNBOOK.md)；完整账号/业务/媒体测试继续由用户自行进行，不以技术检查替代完整生产验收。

本地启动和验证命令见 [部署说明](deploy/README.md)、[后端说明](backend/README.md) 和 [前端说明](frontend/README.md)。

首次预约前填写联系电话及联系确认用途说明见 [0011](ai-docs/implement-plan/0011-student-contact-phone.md)，已完成本地验证；保存后自动带入，教练在授权预约卡片查看当前电话。

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
├── deploy/                   # 本地环境、自动发布、备份恢复及运维手册
└── .github/workflows/        # CI 检查和 main 自动发布
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
