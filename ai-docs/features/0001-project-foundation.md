---
id: "0001"
title: "前后端与部署工程底座"
status: VERIFIED
plan: "../implement-plan/0001-project-foundation.md"
created: 2026-09-27
updated: 2026-09-27
contract_version: "1.3"
modules: [bootstrap, identity, scheduling, bookings, notifications, frontend, deploy]
---

# 0001 — 前后端与部署工程底座

## 1. 目标、触发与范围

在独立 v2 项目建立可运行、可测试的工程底座，让后续注册和预约功能按已确认的六边形模块边界增长。关联 [实施计划 0001](../implement-plan/0001-project-foundation.md)，revision 2 已获用户批准且本地验收已通过。

范围：后端最小启动、架构示例及检查、前端最小启动、真实 MySQL 的本地环境、API 契约骨架、CI 与文档检查。没有学员注册、预约、媒体功能或真实 AWS 部署。

## 2. 验收条件

| ID | 场景 | 预期行为 | 验证方式 | 证据 |
|---|---|---|---|---|
| AC-01 | 本地启动 | 前端与后端健康接口经同源入口可访问 | Compose 启动与 HTTP 检查 | 三容器 healthy；`python3 deploy/smoke.py` 通过；Vite 开发代理返回 UP。 |
| AC-02 | 架构约束 | domain/端口不依赖框架或适配器、模块无循环，反例会失败 | 非空 ArchUnit 规则与反例验证 | `ArchitectureTest` 3/3 通过，包含禁止依赖反例。 |
| AC-03 | 数据库 | 独立 MySQL 可启动，Flyway 建立新库，无 v1 数据 | 本地 MySQL 集成检查 | `FoundationMigrationTest` 在 MySQL 8.4 通过；实际 Compose 库 V1 `success=1`，无业务表。 |
| AC-04 | CI | 后端、前端、文档检查实际通过；记录测试先行的 RED/GREEN 证据 | CI 或本地同等命令与测试执行记录 | 本地后端 16/16、前端 4/4、类型/构建/lint、文档与 Compose 配置通过；CI YAML 经 actionlint 验证，GitHub 托管 CI 未运行。RED/GREEN 见计划第 8 节。 |
| AC-05 | 隔离 | 不复制 v1 凭证/数据，不改 v1；无生产部署 | 代码与配置差异检查 | 新 v2 Git 工作区、本地 `snowboard_v2` 库与示例环境变量；没有 AWS 调用或 v1 数据/密钥迁入。 |

## 3. 业务规则与未决事项

本项不决定预约状态、人数、取消政策或价格。用户已批准 Java 25 / Spring Boot 4.1.1 路线；MyBatis 4.0.0、Flyway、React 19 / Vite 8 等实际依赖见锁文件/POM，经过本地编译、测试与容器验证。本次未引入 Modulith starter。

## 4. 模块、端口与依赖

`scheduling` 已包含纯 Java 时间范围领域对象、入站/出站端口、应用服务、Web 入口与本地内存适配器。业务行为只验证 `[start, end)` 时间范围与预览，不开放约课。规划的 identity/bookings 等模块只在文档中保留边界，避免空包令架构测试虚假通过。跨模块规则见 [项目契约](../PROJECT_CONTRACT.md)。

## 5. API 与前端契约

基础页同源请求 `/api/actuator/health`，返回健康状态。`local` profile 的演示 API 为 `GET /api/demo/time-window-previews/csrf`、`POST /api/demo/time-window-previews` 和 `GET /api/demo/time-window-previews/{id}`，由 NGINX 保留 `/api` 路径转发；请求使用 UTC `start`/`end`，响应给出临时 ID 与 `durationSeconds`。演示请求受 Spring Security 保护，客户端先获取 CSRF token 并带会话 cookie 再提交写请求；当前没有生产身份功能，演示数据重启即失。首个正式业务 API 的响应/错误结构随独立计划 review，不自动延用 v1 envelope。

## 6. 数据、事务与并发

新 `snowboard_v2` 数据库与 Flyway V1 baseline 已在真实 MySQL 8.4 上验证；只有迁移历史表，没有业务表或 v1 数据。本项不宣称预约并发已解决。容量/锁规则由预约功能另行设计。

## 7. 异步任务与外部依赖

本项不实现生产通知、S3 或 CloudFront。仅保留模块边界与必要配置规范，不创建 AWS 资源。

## 8. 实施计划关联与实际变更

所有步骤、实际文件、RED/GREEN 与偏差见 [计划 revision 2](../implement-plan/0001-project-foundation.md)。状态 VERIFIED；后端、前端、MySQL、三容器、CI 配置和文档均已在本地验证。开发中发现并处理的七个问题见[待办索引](../todo/README.md)。

## 9. 验证证据

Java 25 下 `./mvnw test` 16 个通过；Node 24 下 `npm ci`、`npm run typecheck`、`npm test`（4 个通过）、`npm run build`、`npm run lint` 全部通过。`docker-compose ... up --build -d --wait` 三容器 healthy，`python3 deploy/smoke.py` 通过；带本地认证的冒烟检查完成 token、创建与读取预览。Flyway 历史记录 V1 `success=1`。`python3 ai-docs/check_docs.py` 与 actionlint 通过。GitHub 托管 CI 尚未触发，未声称远端检查通过。

## 10. 部署、成本与恢复

只在本地建立可运行底座；真实云资源/生产部署需另行授权。EC2 三容器是目标拓扑，t3.micro 容量需要测量。

## 11. 交付状态与后续

当前 VERIFIED，`approved_revision` 为 2；尚未 RELEASED。学员注册和预约等业务功能各自生成配对文档/计划。
