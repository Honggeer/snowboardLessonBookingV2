---
id: TODO-0025
title: "当前 Git remote 的 SSH 身份认证失败"
status: IN_PROGRESS
created: 2026-10-04
updated: 2026-10-04
---

# TODO-0025 — 当前 Git remote 的 SSH 身份认证失败

## 1. 来源与关联

- 2026-10-04 用户请求首次上线，检查已配置 Git remote 的只读访问时发现。
- 关联[功能 0010](../features/0010-production-delivery.md)及[计划 0010 revision 3](../implement-plan/0010-production-delivery.md)。

## 2. 背景

仓库 origin 为 `git@github.com:Honggeer/snowboardLessonBookingV2.git`。生产交付文件和 CI 修复已在工作区完成，但没有 commit/push；公开 GitHub REST 可读取当前 main 和 CI，不能证明具备 Git 写入或 Actions 配置权限。

## 3. 问题与影响

实际执行 `env GIT_SSH_COMMAND='ssh -o BatchMode=yes -o ConnectTimeout=8 -o StrictHostKeyChecking=yes' git ls-remote origin refs/heads/main`，exit 128，返回 `Permission denied (publickey)`。当前 transport 没有可用的 GitHub SSH 认证，无法沿此通道获取远端 refs；具体是密钥配置还是账户权限原因尚未确认，不能推断其他身份或 HTTPS 通道都不可用。这会影响获明确 commit/push 授权后的首发交付；不能将本地通过写成远端 CI 已成功。

## 4. 期望结果

用户授权的身份能访问正确仓库，并在用户明确要求提交/推送后交付受测试的生产版本；所需 GitHub 发布配置权限可实际核验，不暴露私钥、token 或长期凭据。

## 5. 完成判定

- 对目标仓库的实际 Git 认证/refs 读取成功，记录采用的通道和非密钥证据。
- 获明确授权后，实际提交/推送结果及准确远端 SHA 可核对；没有 amend/force push 或操作其他仓库。
- 首发所需 Actions/environment/OIDC 配置权限经实际核验，不把公开 REST 访问当成写入权限。
- 功能、计划和 ticket 索引记录处理结果；用户授权范围继续受 WORK-07 约束。

## 6. 未决事项与状态记录

- 当前可用 GitHub 写入身份尚未核验；恢复认证不代表用户已经批准 commit/push。
- 2026-10-04：OPEN。只读 SSH 检查失败；未新增密钥、改 remote、提交或推送。

- 2026-10-04：用户明确允许 commit/push，已安装 GitHub CLI 并发起 HTTPS/browser device 登录；第一次登录过期，用户要求重新验证，已重启登录。尚未得到实际认证/推送/Actions 配置结果；IN_PROGRESS。

- 2026-10-04：第二次 browser device 登录成功为 Honggeer；目标仓库 API 确认 admin/push 权限，origin 改为同一仓库的 HTTPS，实际 git ls-remote 成功返回当前 main SHA。OIDC 配置 API 可读；实际推送/发布配置结果仍待完成，IN_PROGRESS。
