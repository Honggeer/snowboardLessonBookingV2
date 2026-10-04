---
id: TODO-0024
title: "现有 EC2 根盘未启用加密"
status: IN_PROGRESS
created: 2026-10-04
updated: 2026-10-04
---

# TODO-0024 — 现有 EC2 根盘未启用加密

## 1. 来源与关联

- 2026-10-04 用户要求 Codex 远程准备服务器，并提供目标实例 ID；通过 AWS CLI 核对现有卷时发现。
- 关联[上线功能 0010](../features/0010-production-delivery.md)、[上线计划 0010 revision 1](../implement-plan/0010-production-delivery.md)及 [EC2 准备指南](../../deploy/EC2_SETUP.md)。
- 实际证据：在 `ca-central-1` 对目标实例执行 `aws ec2 describe-volumes --filters Name=attachment.instance-id,Values=i-0c7978984740cbd58 --profile snowboard-v2 --region ca-central-1`，返回根盘 `vol-01afc752b9b52dd07`，Size 20、VolumeType gp3、Encrypted false、IOPS 3000、Throughput 125。

## 2. 背景

用户已经创建 ARM64 Amazon Linux 2023 服务器。准备指南建议使用加密的 20 GiB gp3 根盘，但现有卷的实际配置未满足这项建议。当前仅准备 Docker/Compose，尚未部署网站或初始化正式数据库。

## 3. 问题与影响

后续同机 MySQL 的正式数据和服务器配置将依赖此磁盘；未加密的现状与已提供的存储建议不一致。需要在正式数据落盘前明确存储保护要求与实际配置，不能把建议配置写成已完成事实。

## 4. 期望结果

正式上线所用的持久化存储符合用户确认的保护要求，文档记录实际加密状态及其验证证据，保留已有数据和可用的服务器管理通道。

## 5. 完成判定

- 上线计划明确正式持久化存储的加密要求及用户决定。
- 对实际使用的卷核对加密状态，结果符合已确认要求。
- 如处理过程中更换存储或服务器，验证系统与 SSM 可访问、所需数据完整，并说明遗留资源的费用和处理状态。
- 关联功能、计划和 ticket 索引记录实际结果，未经授权不删除或覆盖资源。

## 6. 未决事项与状态记录

- 正式存储保护要求及涉及资源变化的具体操作尚待上线方案 review。
- 2026-10-04：OPEN。仅完成现有卷的只读核对，未创建快照、卷或实例，未停止、替换或删除资源。

- 2026-10-04：用户批准 0010 revision 3。新根盘 `vol-00a6aa379a31df78d` 已核对 Encrypted true / 20 GiB gp3；换盘后 SSM `8ce008a8-4055-4542-8244-62f29a77cb37` Success / 0，系统/Docker/Compose 可用。旧盘及一致快照保留用于回退，暂时额外计费，48 小时后的限定清理仍待执行；IN_PROGRESS，未把保留资源写成已删除。
