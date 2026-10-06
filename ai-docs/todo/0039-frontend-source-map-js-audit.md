---
id: TODO-0039
title: "前端构建和测试依赖 source-map-js 出现高等级审计告警"
status: OPEN
created: 2026-10-06
updated: 2026-10-06
---

# TODO-0039 — 前端构建和测试依赖 source-map-js 出现高等级审计告警

## 1. 来源与关联

- 0011 revision 2 按批准方案安装电话组件时，npm 提示 1 项 high 告警；随后只读 `npm audit --json` 与 `npm ls source-map-js` 核对。
- 关联 [0011](../implement-plan/0011-student-contact-phone.md)、[工程底座 0001](../implement-plan/0001-project-foundation.md)。本票不授权依赖修改。

## 2. 背景

当前 package-lock 中 source-map-js 为 1.2.1、dev=true，由 Vite/PostCSS 和 jsdom/css-tree 引用。`git show HEAD:frontend/package-lock.json` 证实安装电话组件前已有相同版本，电话组件没有引入或升级它。

## 3. 问题与影响

npm audit 报 high，关联 [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q)。官方告警描述索引 source map 的 section offset 可导致事件循环拒绝服务；受影响版本为 >=1.0.0、<1.2.2。当前证据只证明构建/测试开发依赖受影响，不证明生产站点可被利用，也未对真实环境进行攻击验证。

## 4. 期望结果

受影响依赖得到处理，构建和测试保持正常；核对实际使用路径及审计结果，清楚记录生产影响及尚未证实的限制。

## 5. 完成判定

- 受影响版本不再留在实际依赖树及 lockfile，或明确记录经核对的处置结论。
- 相关依赖审计、前端类型/lint/测试/构建通过，必要运行场景兼容。
- 不以构建通过替代审计结果，也不把未验证的生产攻击路径写为事实。

## 6. 未决事项与状态记录

- 2026-10-06：OPEN。实际 audit exit 1，1 high；证据 `.local/phone-country-prefix/npm-audit.json`。当前电话组件实现范围不包含既有工具链依赖升级，未执行 npm audit fix 或额外依赖修改，后续需具体处置 review。
