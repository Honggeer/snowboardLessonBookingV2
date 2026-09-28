---
id: "0001"
title: "前后端与部署工程底座"
status: VERIFIED
revision: 2
approved_revision: 2
created: 2026-09-27
updated: 2026-09-27
feature: "../features/0001-project-foundation.md"
---

# 0001 — 前后端与部署工程底座实施计划

## 1. Review 摘要

在 [功能契约 0001](../features/0001-project-foundation.md) 与 [ADR 0001](../decisions/0001-v2-baseline.md) 的范围内，创建可启动的 Java 后端、TypeScript 前端、MySQL 开发环境、非空六边形架构检查与 CI。成功后可以开始单独规划学员注册与预约。

本计划采用已批准的 Java 25 LTS + Spring Boot 4.1.1、React + Vite + TypeScript、MySQL 8.4、Maven、MyBatis、Flyway、Spring Security 与 ArchUnit。Spring Boot 4.1.1 官方支持 Java 25；实际组合已在本地编译、测试、真实 MySQL 与三容器启动中验证。本计划没有使用 Modulith starter，也没有改变主要版本决策。[Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)。

本次包含：本地启动、一个真实（非空包）用例垂直切片、架构规则、数据库连接、Flyway baseline、基本 CI 和文档检查。暂不包含注册、预约、登录实现、媒体、邮件、AWS 资源或生产发布。

Revision 2 补充用户新要求的测试先行流程：本计划获批后，各实现切片先创建对应测试并运行至因目标行为缺失而失败，再写实现、运行同一测试通过及适用回归检查。失败和通过的实际命令、结果均写入本计划；环境或测试错误不算有效失败。

用户已于 2026-09-27 同意 revision 2 的版本路线与本地三容器骨架。此次完成仅为本地工程底座验证，不含生产发布。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-09-27 | 用户：“0001开始实现吧，把项目搭起来” | 2 | 批准本计划 revision 2 的本地工程底座：Java 25 / Spring Boot 4.1.1 路线、前端、独立 MySQL、本地三容器、架构检查与 CI；不含 AWS 部署、付费资源或业务功能。 |

`approved_revision: 2`。用户已明确同意当前 revision 2；后续实质修订须再次提交 review。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | `backend/pom.xml`、Wrapper、`bootstrap/` | 核实兼容矩阵，固定可运行依赖；健康接口仅给状态 | Maven 编译/启动通过 | 完成 |
| P-02 | `backend/.../scheduling/` | 一个验证时间范围的真实用例：纯 Java domain/port、application 实现、Web 入口与内存适配器；无需 DB 写入 | 用例测试与 Web 测试通过 | 完成 |
| P-03 | `backend/src/test/.../ArchitectureTest` | ArchUnit 检查 domain/端口依赖、应用到适配器依赖、跨模块访问和循环；测试中包含反例验证 | 规则有效，非空实际类受检 | 完成 |
| P-04 | `backend/src/main/resources/db/migration/`、`deploy/compose.yaml` | 独立 v2 MySQL 开发库、空业务库 baseline 与健康检查；版本化 Flyway | 真实 MySQL 启动和迁移检查通过 | 完成 |
| P-05 | `frontend/package.json`、`src/`、锁文件 | TypeScript/React/Vite 基础页面，请求本地同源健康接口；基本 loading/error | 类型检查、交互/构建通过 | 完成 |
| P-06 | `deploy/`、`.github/workflows/`、README | NGINX `/api` 代理，本地三容器流程，测试/文档 CI；不部署 EC2 | Compose/CI 同等检查通过 | 完成 |
| P-07 | `ai-docs/features/`、`ai-docs/implement-plan/` | 更新实际文件、检查命令、结果、偏差与两个索引 | 计划与实现一致 | 完成 |

顺序：先版本/依赖核实；每个实现切片先写测试并确认有效 RED，再实现至 GREEN；之后数据库/前端/容器集成，最后验证文档。批准后实施中可调整不改变验收的内部顺序，并记录理由。

## 4. 数据、API、架构与兼容影响

- 模块与端口：`scheduling` 提供演示用例；domain/端口纯 Java，application 只负责用例编排，Web 和内存 repository 位于 adapter。未来用 MyBatis 替换存储实现时端口保持稳定。
- API：健康接口与演示用例 API 仅用于本地验证；演示用例不得被误认为已实现可预约时间。新的生产业务 API 格式待后续功能计划决定。
- 数据：v2 独立 MySQL 库，第一版 Flyway 基线不含 v1 用户/预约数据。MySQL 连接集成检查用真实 MySQL；数据库适配器对应实际后续需求再加。
- 并发/异步：不实现预约，故不声称占用锁和通知任务已完成。
- 安全：Spring Security 依赖/最小安全基线；认证/授权业务由后续单独计划实现。健康端点范围明确，私有接口不默认匿名开放。
- 版本：验证 Java 25 与 Spring Boot 4.1.1，以及 MyBatis/Flyway/测试库实际组合；若官方不支持或构建失败且需要改主版本，修订计划重新 review。
- v1 复用：阅读需求/测试场景，不复制旧密码、生产数据、部署秘密或全局三层目录。

## 5. 验收与验证计划

| 验收 ID | 具体验证 | 环境/命令 | 预期结果 |
|---|---|---|---|
| AC-01 | 前端/健康接口 | 本地 `docker compose` + HTTP 请求 | 同源页面与健康响应成功 |
| AC-02 | 实际类受架构检查、禁止依赖反例 | `cd backend && ./mvnw test` 的架构测试 | 规则通过，反例会失败 |
| AC-03 | 新 MySQL 库与 Flyway | 真实 MySQL 集成测试 | 空库按迁移建立且无 v1 数据 |
| AC-04 | 后端/前端/文档检查 | Maven test；`npm run typecheck/test/build/lint`；`python3 ai-docs/check_docs.py` | 实际命令均通过，CI 同步运行 |
| AC-05 | v1 隔离与云部署状态 | 新旧仓库 diff、配置检查 | v1 不变，无真实 AWS 变更 |

上述命令与本地同等验证均已实际执行，结果见第 8 节。GitHub 托管 CI 尚未运行；工作流文件已通过静态校验，用户自行 push 后才会触发。

对 AC-01 至 AC-04 的可执行改动，先建立相关失败用例并记录 RED（目标行为缺失，而非依赖/环境或测试错误），再完成实现并记录同一用例 GREEN 和适用回归结果。新增容器、CI、文档检查规则时也先定义可执行失败条件。验收证据按 RED、GREEN 分别填写第 8 节。

## 6. 风险、成本、部署与恢复

- Spring Boot 4.1.1 与 MyBatis/周边 starter 版本兼容是首个核对点；若不可用，提出可 review 的备选组合。
- t3.micro 资源限制：本计划只做本地容器验证，不声称它能稳定承载 Java + MySQL；EC2 规格与实际账单另行测量。
- 不创建云资源，不部署；无新增账单。使用独立本地数据库，配置从 `.env.example` 生成，不纳入 Git。数据库可由开发环境重建，未来实际数据备份与恢复需单独计划。
- 实施计划批准不授权把 v2 上线或迁移 v1 生产数据。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差与 review |
|---|---|---|---|
| 2026-09-27 | P-01：`backend/pom.xml`、Maven Wrapper、启动类/安全配置 | Java 25 / Spring Boot 4.1.1 / MyBatis 4.0.0 / Flyway / Spring Security 实际编译与启动通过 | 未改变获批主要版本；本机另外安装了 Java 25、Maven、Docker/Colima 作为验证工具，未加入仓库。 |
| 2026-09-27 | P-02/P-03：`scheduling` domain、应用端口/服务、Web 与内存适配器；`ArchitectureTest` | 时间边界、预览用例、Web 权限与错误、架构依赖方向及反例均通过 | 演示 API 仅 `local` profile 开放，不代表预约可用。实际客户端获取 CSRF token 的入口后补并验证，见 [TODO-0007](../todo/0007-demo-preview-csrf-token-access.md)。 |
| 2026-09-27 | P-04：Flyway V1、MySQL 8.4、整应用启动测试 | 新库只生成 Flyway 历史表；真实 MySQL 测试与容器启动通过 | 初次 Colima socket 配置错误属于环境失败；随后在有效 RED 后实现迁移。启动时发现 `final` 内存组件无法被 Spring 代理，补整应用测试 RED/GREEN 后修复，记录 [TODO-0002](../todo/0002-local-backend-startup-failure.md)。 |
| 2026-09-27 | P-05：React/Vite/TypeScript 页面与锁文件 | 加载、成功、错误/重试、访客文案交互测试及类型/构建/lint 通过 | 浏览器开发代理起初不可达，见 [TODO-0003](../todo/0003-frontend-dev-proxy-reaches-backend.md)；访客文案剔除内部流程，见 [TODO-0005](../todo/0005-visitor-copy-avoids-internal-process.md)。 |
| 2026-09-27 | P-06：NGINX、三容器 Compose、冒烟脚本、CI 工作流、README | 本地三容器 healthy；同源页面/API 与 Vite 开发代理通过；CI YAML 通过 actionlint | 初次普通 API 代理路径未保留，按 [TODO-0004](../todo/0004-api-proxy-preserves-route.md) 补冒烟用例并修复；当前环境的本地凭据配置按 [TODO-0006](../todo/0006-local-stack-restart-configuration.md) 留在被 Git 忽略的 `deploy/.env`。GitHub 托管 CI 尚未运行，生产部署未执行。 |
| 2026-09-27 | P-07：功能/计划/索引、契约状态文字、待办 ticket | 文档检查通过；误扫第三方依赖文档的问题已处理，见 [TODO-0001](../todo/0001-doc-checker-ignores-generated-files.md) | 未新增架构 ADR；既有 ADR 已更新事实状态。 |

## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-09-27 RED | Java 25 / Maven 3.9.16 | `mvn -Dtest=TimeWindowTest test`；`...TimeWindowPreviewServiceTest`；`...TimeWindowPreviewControllerTest`；`...ArchitectureTest` | 分别因目标 domain、端口/服务、Web 入口、安全配置、架构规则缺失而失败 | 均为预期目标行为/类型缺失，之后各自同一测试 GREEN：3、2、5、3 个通过。 |
| 2026-09-27 RED→GREEN | 真实 MySQL 8.4 / Testcontainers | `mvn -Dtest=FoundationMigrationTest test` | RED：空库没有 V1，当前迁移版本为空；GREEN：V1 成功且除历史表无业务表，1/1 通过 | 初次 Colima socket 错误不计作 RED；设置 `DOCKER_HOST` 与 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 后重跑。 |
| 2026-09-27 RED→GREEN | 真实 MySQL 8.4 / Spring 全量上下文 | `mvn -Dtest=LocalApplicationStartupTest test` | RED：Spring 无法代理 `final` 内存适配器；GREEN：整应用启动，1/1 通过 | 此测试是实际容器启动故障后补的回归用例。 |
| 2026-09-27 RED→GREEN | Node 24 / frontend | `npm test` | RED：`App` 页面缺失；GREEN：加载、健康成功、错误与重试 3/3 通过 | 初次实现后一个异步断言与 TS CSS 类型配置不正确，修正测试等待与类型配置后重新通过；这些测试问题不计作目标 RED。 |
| 2026-09-27 RED→GREEN | 本地 Compose 入口 | `python3 deploy/smoke.py` | RED：启动前 8088 连接被拒绝；GREEN：静态页 200、同源健康为 UP | 沙箱内首次出现网络权限错误，不计 RED；获准访问本机端口后的连接拒绝才是有效 RED。 |
| 2026-09-27 RED→GREEN | 前端开发代理与文档 | `python3 -c` 请求本机 8080；`python3 ai-docs/check_docs.py` | RED：本机后端端口未开放、文档检查误扫 `node_modules`；GREEN：本机/Vite 代理返回 UP，文档检查通过且失效项目链接探针仍报错 | 关联 TODO-0003 与 TODO-0001。 |
| 2026-09-27 RED→GREEN | 本地普通 API 代理 | 带本地 Basic 凭据的 `deploy/smoke.py`，访问 `GET /api/demo/time-window-previews` | RED：同源入口返回 404，未到达后端已知路由；GREEN：容器入口与 Vite 代理均返回路由应有的 405，原有页面/健康检查通过 | 凭据只从本地后端启动日志在验证进程中读取，未写入仓库或测试输出；见 TODO-0004。 |
| 2026-09-27 RED→GREEN | 前端访客文案 | `npm test` 新增访客文案用例 | RED：页面缺少“课程预约功能正在准备中”且展示内部计划措辞；GREEN：同一用例通过，前端合计 4/4 | 见 TODO-0005。 |
| 2026-09-27 RED→GREEN | 当前本地环境重启配置 | `docker-compose -f deploy/compose.yaml config --quiet` | RED：缺少必需的本地数据库变量；GREEN：同一命令及 `up -d --wait` 通过、三容器 healthy，`git check-ignore deploy/.env` 确认文件不入 Git | 当前机器使用已有 MySQL 卷对应的本地测试凭据；新检出按 `deploy/.env.example` 另设密码。见 TODO-0006。 |
| 2026-09-27 RED→GREEN | 本地演示 API 的 CSRF 流程 | `./mvnw -Dtest=TimeWindowPreviewControllerTest test`；带本地 Basic 凭据的 `deploy/smoke.py` | RED：token 入口在 Web 测试和旧容器中返回 404；GREEN：Web 测试 6/6，通过容器入口取得 token、创建并读取临时预览；无 token POST 仍返回 403 | 只针对 `local` 演示接口；见 TODO-0007。 |
| 2026-09-27 回归 | Java 25 / Colima Docker / MySQL 8.4 | `./mvnw -q -o -Dmaven.repo.local=<本机临时仓库> test`，设置 `JAVA_HOME`、`DOCKER_HOST`、`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` | 最终 16 个后端测试通过，0 失败、0 错误、0 跳过；Wrapper 实际运行 Maven 3.9.16 | Testcontainers 需要 Docker；CI 的 Linux runner 由 Docker 提供环境。 |
| 2026-09-27 回归 | Node 24 / frontend | `npm ci`；`npm run typecheck`；`npm test`；`npm run build`；`npm run lint` | 全部通过；最终 4 个交互测试通过 | 依赖版本由 `package-lock.json` 锁定。 |
| 2026-09-27 回归 | Colima Docker / Compose | `docker-compose -f deploy/compose.yaml config --quiet`；`up --build -d --wait`；`python3 deploy/smoke.py`；查询 `flyway_schema_history` | 三容器 healthy；页面/API 冒烟通过；实际库 V1 `success=1` | 本地容器运行；未执行 EC2 或 AWS 变更。 |
| 2026-09-27 回归 | 项目根目录 | `python3 ai-docs/check_docs.py`；`actionlint .github/workflows/ci.yml` | 文档检查与 CI YAML 静态检查通过 | GitHub 工作流没有在远端执行；用户尚未 push。 |

## 9. 完成状态与后续

状态 VERIFIED：0001 revision 2 的本地工程底座、测试与文档验收已完成。未进行生产部署，故不标记 RELEASED；GitHub 托管 CI 等用户自行 push 后才可运行。后续注册和预约各自需要独立功能文档、实施计划与用户批准。
