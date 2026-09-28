---
id: TODO-0007
title: "演示预览写请求无法获取 CSRF token"
status: DONE
created: 2026-09-27
updated: 2026-09-27
---

# TODO-0007 — 演示预览写请求无法获取 CSRF token

## 1. 来源与关联

- 发现于：0001 演示 API 联调检查；Web 测试可注入 CSRF token，但实际客户端只有受保护的写接口，没有获取 token 的入口。
- 相关功能/计划：[工程底座 0001](../features/0001-project-foundation.md)、[实施计划 0001](../implement-plan/0001-project-foundation.md)。

## 2. 背景

本地演示接口验证六边形用例可以从 HTTP 调用。安全基线要求写请求携带 CSRF token，且接口需要认证。

## 3. 问题与影响

实际客户端无法完成受保护的创建请求，导致“可调用的 Web 入口”只在模拟测试里成立。

## 4. 期望结果

已认证的本地客户端能先获取 CSRF token，再创建和读取临时预览；未提供 token 的写请求仍被拒绝。

## 5. 完成判定

- 通过同源入口执行“获取 token → 创建预览 → 读取预览”的完整流程。
- 未携带 token 的写请求仍被拒绝，演示接口仍仅在本地配置中可用。

## 6. 未决事项与状态记录

- 需要用户澄清的问题：N/A。
- 2026-09-27：IN_PROGRESS；在批准的 0001 Web 演示与安全基线范围内处理。Web 用例 RED：token 入口返回 404；实际容器入口的完整流程也在获取 token 时返回 404。
- 2026-09-27：DONE；Web 用例 GREEN，实际三容器入口完成“获取 token → 创建预览 → 读取预览”，无 token 写请求的原有 403 测试仍通过。
