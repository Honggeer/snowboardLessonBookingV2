---
id: "0000"
title: "独立项目与 AI 文档骨架"
status: VERIFIED
revision: 1
approved_revision: 1
created: 2026-09-27
updated: 2026-09-27
feature: "../features/0000-project-bootstrap.md"
---

# 0000 — 独立项目与 AI 文档骨架实施记录

## 1. Review 摘要

用户当时直接要求创建新项目、AI 文档目录与每个功能的可 review 实施计划。本项仅落地文档基础设施，不创建业务应用。范围见 [功能 0000](../features/0000-project-bootstrap.md)。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-09-27 | 用户本轮：“干脆直接创建新的project吧，然后把这些写到那个project专门有一个AI doc的文件夹里，然后再加一个，implement plan” | 1 | 创建独立项目与文档流程；每项后续功能的计划须另行 review。 |

本项为用户直接下达的项目骨架任务；记录此授权不代表对 [工程底座计划 0001](0001-project-foundation.md) 的批准。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | 根入口、README、Git | 独立目录与仓库 | 文件落地、原 v1 未改变 | 完成 |
| P-02 | ai-docs/ | 契约、功能模板/索引、ADR | 文档完整、链接有效 | 完成 |
| P-03 | 计划目录（现为 `ai-docs/implement-plan/`） | 模板、索引、0000/0001 计划 | 0001 AWAITING_REVIEW、无业务代码 | 完成 |
| P-04 | ai-docs/check_docs.py | 结构、状态、审批一致性检查 | 命令返回 0 | 完成 |

## 4. 数据、API、架构与兼容影响

没有 API、数据库、依赖或生产环境改动。根 AGENTS.md 限定后续行为；新项目与 v1 分离。

## 5. 验收与验证计划

运行 `python3 ai-docs/check_docs.py`；核对新项目实际文件、Git 状态、旧项目 Git 状态与 0001 待 review 状态。对应功能 AC-01 至 AC-04。

## 6. 风险、成本、部署与恢复

没有云费用/部署。目标新目录若意外已存在则中止，保留已有文件；当前独立文档骨架可由 Git 追踪。文件系统权限按工具审批流程执行。

## 7. 实际执行与偏差

| 日期 | 步骤/实际文件 | 实际结果 | 偏差与 review |
|---|---|---|---|
| 2026-09-27 | 根入口、ai-docs/、计划目录、backend/frontend/deploy README、Git 初始化 | 完成项目与文档骨架 | 计划目录后来按用户要求移至 `ai-docs/implement-plan/`；应用代码待 0001 review |

## 8. 实际验证证据

2026-09-27，在新项目根运行 `python3 ai-docs/check_docs.py`：通过。校验 2 对功能/计划、本地链接、两个索引、状态与审批修订。新项目 `git status --porcelain` 显示待首次提交文档；原项目安装前后 porcelain 一致。未运行应用测试：没有应用代码。

## 9. 完成状态与后续

当前 VERIFIED：新项目与文档检查完成，没有应用代码或生产发布。后续用户 review 0001 再开始工程代码。
