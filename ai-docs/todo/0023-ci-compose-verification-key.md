---
id: TODO-0023
title: "CI 的 Compose 配置检查缺少验证密钥"
status: DONE
created: 2026-10-04
updated: 2026-10-04
---

# TODO-0023 — CI 的 Compose 配置检查缺少验证密钥

## 1. 来源与关联

- 2026-10-04 上线准备审查发现并在干净配置环境复现。
- 关联[工程底座 0001](../features/0001-project-foundation.md)、[上线计划 0010 revision 1](../implement-plan/0010-production-delivery.md)。
- 证据位置：`.github/workflows/ci.yml` 的 `docs-and-compose` job 及 `deploy/compose.yaml` 的 backend 环境变量。

## 2. 背景

workflow 仅为 Compose 校验设置 DB_PASSWORD 和 MYSQL_ROOT_PASSWORD。当前 Compose 还要求非空 VERIFICATION_KEY，Git 忽略的 `deploy/.env` 不存在于普通 CI 检出。开发机可能因已有该文件而掩盖缺失。

## 3. 问题与影响

在不读取本地 `.env` 的环境运行现有配置检查：

```sh
env -u VERIFICATION_KEY DB_PASSWORD=ci-config-only MYSQL_ROOT_PASSWORD=ci-config-only-root docker-compose --env-file /dev/null -f deploy/compose.yaml config --quiet
```

实际 exit 1，错误为 required variable VERIFICATION_KEY is missing a value。原因是 workflow 提供的环境变量未覆盖当前 Compose 必填配置；这会阻碍干净环境的配置检查及后续发布门禁。

2026-10-04 恢复会话后，通过 GitHub REST 查询当前 main 提交 `448b5c51e4c708edc4bd2fe28191aa7b651abd5b` 的 [run 37176068847](https://github.com/Honggeer/snowboardLessonBookingV2/actions/runs/37176068847) 及 jobs：backend/frontend success，docs-and-compose 的 Compose configuration failure，check annotation 报 exit code 1。本地同日再次运行上述命令，仍明确缺 VERIFICATION_KEY。远端详细日志 connector 要求重新认证，未核实远端具体错误文本；元数据验证了失败步骤，与源码缺失和本地失败一致。

## 4. 期望结果

普通 CI 检出即可完成配置检查，不依赖开发机 `.env` 或真实生产密钥，同时保留应用必须配置验证密钥的约束。

## 5. 完成判定

- 在无本地 `.env` 的条件下，实际运行与 workflow 一致的 Compose 配置检查成功。
- workflow 不注入、不输出真实 SMTP/数据库/验证密钥。
- 未配置必要密钥时，正常应用部署仍明确拒绝缺失配置。
- 关联计划记录实际检查命令与结果，更新 ticket 状态和索引。

## 6. 未决事项与状态记录

- 2026-10-04：OPEN。只完成配置审查和本地复现，未修改 workflow 或实现代码。
- 2026-10-04 恢复会话：OPEN；已核实最新远端 CI 的失败步骤并复核本地错误，未修改 workflow。具体证据见第 3 节。
- 2026-10-04：用户明确要求“开始实现”，批准 [0010 revision 2](../implement-plan/0010-production-delivery.md) P-02 至 P-07。目标用例 `test_workflow_configures_clean_compose_without_local_secrets` 先实际 RED（缺 VERIFICATION_KEY），workflow 注入仅配置校验的虚构 key 并明确空 env-file 后同测试 GREEN；缺 key 的正常部署仍实际失败。
- 2026-10-04：DONE。`python3 -m unittest discover -s deploy -p 'test_ci_config.py'` 2 项通过，完整交付 24 项通过；不使用、不打印真实秘密。最新 GitHub CI 仍是旧 SHA 的失败结果，本次未 commit/push，用户推送后再核对新运行，不将本地 GREEN 写为远端已更新。
