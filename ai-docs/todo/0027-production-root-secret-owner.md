---
id: TODO-0027
title: "生产 MySQL root 密码文件所有者不符合约定"
status: DONE
created: 2026-10-04
updated: 2026-10-04
---

# TODO-0027 — 生产 MySQL root 密码文件所有者不符合约定

## 1. 来源与关联

- 用户询问服务器环境参数的传输和安全性。SSM 只读元数据检查 `c3bf2d4c-d2a4-45c1-b46f-00eb72970025` Success / 0，发现一项生产密钥文件所有者与已有运维约定不一致。
- 关联 [0010 功能](../features/0010-production-delivery.md)、已批准的 [0010 revision 3](../implement-plan/0010-production-delivery.md) 与 [生产运维手册](../../deploy/docs/PRODUCTION_RUNBOOK.md#4-实例文件与密钥已配置)。

## 2. 背景

生产参数和密钥存放在 `/etc/snowboard-v2/`。手册约定 `secrets/mysql.root.password` 为 root:root / 0600。实际检查该文件权限为 0600，但 UID/GID 为 501/20；其他已核对的 host.json、production.env、应用数据库/verification/SMTP 秘密及 CloudFront 私钥符合各自约定。全程只输出元数据，不读取密码内容。

## 3. 问题与影响

文件所有者未按生产主机权限约定设置，权限 0600 会授予该 UID 读取资格，无法以模式数字单独证明只有 root 可读。该项所有者偏差在首发后未被发现；历史传输过程产生该 UID/GID 的具体步骤未单独复现。本次发现不等于已证明密码泄露。

## 4. 期望结果

MySQL root 密码文件由生产主机的 root 独占管理，普通主机账号不能读取，既有数据库和应用继续正常运行。

## 5. 完成判定

- 所有者和组均为 root，权限为 0600。
- 密码内容不改变、不输出，不重启业务容器或改变账号凭据。
- 修正后生产 HTTPS 健康端点保持 UP。

## 6. 未决事项与状态记录

- 需要用户澄清的问题：N/A。本项恢复已批准 revision 3 的文件权限约定，没有新增业务、架构、资源或费用范围。
- 2026-10-04：发现所有者不符，按既有生产权限约定完成修正并关闭为 DONE。SSM `c6c256eb-6110-4d91-bb42-db9bf6d6b708` Success / 0；实际元数据断言先 RED（所有者/组不符），修正后 GREEN（UID/GID 0/0、0600）。文件大小和内容修改时间不变；未读取密码；生产 HTTPS health UP；未请求容器重启。
