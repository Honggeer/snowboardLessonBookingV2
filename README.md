# Snowboard Lesson Booking App v2

面向一个教练和多个注册学员的授课平台：学员自助预约并查看个人课程，教练管理课程、日程、预约、学员与宣传内容。

## 当前状态

独立 v2 工程底座 [0001](ai-docs/implement-plan/0001-project-foundation.md) revision 2 已获批准并通过本地验证。当前只包含基础页面、健康接口、时间范围演示用例、架构检查与独立 MySQL 基线；注册、登录、预约及生产部署尚未实现。

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
├── deploy/                   # 本地三容器配置与冒烟检查
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
