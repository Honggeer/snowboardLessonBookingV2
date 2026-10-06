---
id: "0011"
title: "首次预约前填写联系电话"
status: VERIFIED
revision: 2
approved_revision: 2
created: 2026-10-06
updated: 2026-10-06
feature: "../features/0011-student-contact-phone.md"
---

# 0011 — 首次预约前填写联系电话实施计划（revision 2）

## 1. Review 摘要

revision 1 已实现并完成本地验收，源码当前要求学员手动填写完整国际号码。用户先指出“我只在加拿大教课，只要输入号码就行了”，随后调整为“分隔开，国家前缀让学员选，然后后面输入手机号”，并询问现成组件。本 revision 2 已实现：

1. 改为 **`[加拿大 +1 ▼] [416 555 0123]`**。没有已存电话时默认加拿大；学员只输入号码，可选择其他国家/地区，列表用国家名称和区号，优先展示加拿大、美国、中国。
2. 引入现成 `react-phone-number-input` **3.4.18**，使用其号码输入/格式化能力搭配原生国家下拉；手机上区号与号码分开，320px 起不溢出，键盘和触控可操作。国家列表/中文名称与区号由组件数据提供，无需手写全球规则或引入整套 UI 框架。
3. 页面自动组合完整号码提交。例如加拿大 + `4165550123` 保存为 `+14165550123`，中国 + `13800138000` 保存为 `+8613800138000`。后端格式、API 和 V11 数据字段维持当前契约，无迁移或后台数据回填。
4. 已存号码回填国家和号码；同属 +1 的值默认显示加拿大，不把国家视作独立持久资料。支持粘贴完整号码，识别其区号而不重复添加；无法识别国家的旧值保留完整国际格式，不静默清空或改写。切换国家时保留已输入的号码部分，并让用户核对新组合。
5. 用途说明继续为“用于教练联系你、沟通并确认预约。” 去掉要求手输国家区号的提示和示例；空值/非法值仍给清楚错误，不增加按运营商或号段的严格有效性校验。服务端格式错误文案改为“请填写有效的联系电话。”。

保存/申请串行、本人修改、教练授权查看及 tel、切号清理等已验证行为保留。此次无短信、拨号、付费服务、后端 SDK 或 React 升级。新增前端依赖和输入体验属于 revision 1 的实质调整，**revision 2 已获批，并完成目标 RED→GREEN 和适用回归**。实现及本地验证已完成；用户随后于同日要求“提交推送”，授权提交本次改动并推送 main，由既有 CI/Production delivery 流水线交付。

关联[功能契约 0011](../features/0011-student-contact-phone.md)、[TODO-0038](../todo/0038-phone-country-prefix-input.md)、[身份 0002](../features/0002-student-identity.md)、[约课 0006](../features/0006-post-login-booking-home.md)及 [ADR 0001](../decisions/0001-v2-baseline.md)。第 3、5 节新增 revision 2 的具体步骤和验收，原 revision 1 的已完成步骤、批准及运行证据保留为历史。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-10-06 | 用户在收到 0011 revision 1 后回复：“批准” | 1 | 本修订本地实现和验证；不含 Git commit/push、生产发布/迁移或新增付费资源 |
| 2026-10-06 | 用户在实现与验证完成后回复：“提交” | 1 | 授权提交本次功能代码、测试和关联文档；不含 push、生产发布/迁移或新增付费资源 |
| 2026-10-06 | 用户在收到 revision 2 后回复：“批准” | 2 | 授权本修订本地实现、目标 RED/GREEN、适用回归及文档维护；不含 Git commit/push、生产发布或新增付费资源 |
| 2026-10-06 | 用户在本地实现/验证完成后回复：“提交推送” | 2 | 授权提交本次电话输入改动、测试/文档并推送 origin/main，触发现有 CI 及成功后的自动交付；不包含用户独立 frontend README 修改，不新增资源或数据操作 |

当前 `approved_revision: 2`，用户于 2026-10-06 原话“批准”，revision 2 已完成本地实现和验收，状态 VERIFIED。历史批准与 revision 1 验证保留；用户随后明确授权本次提交推送，实际生产完成以流水线结果另验。

## 3. 实现步骤与预计文件

### revision 2 — 已完成步骤

| 步骤 | 预计文件 | 改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| R2-P01 | `frontend/src/StudentContactBooking.test.tsx` | 先写默认加拿大、无区号号码提交、切换 +86、历史回填、完整号码粘贴的行为测试；复用已有 UI/API 替身，不先安装库或改实现 | 当前界面因缺少国家选择/裸号码不能保存而有效 RED，不计编译/环境故障 | 已完成 |
| R2-P02 | `frontend/package.json`、`package-lock.json`；`frontend/src/ContactPhoneInput.tsx`（若抽取）、`BookingHome.tsx` | 批准后精确安装 3.4.18 并锁定传递依赖；国家原生下拉 + 现成号码输入，默认 CA、中文名称/区号、粘贴/切换/历史回填；受控值仍为规范完整号码 | 同一目标 GREEN；不变更 React 或 API，不发外部国旗/地理识别请求 | 已完成 |
| R2-P03 | `frontend/src/booking.css`、`BookingHome.tsx`；`backend/src/main/java/com/geer/snowboard/v2/identity/application/service/StudentContactService.java` | 改示例与错误说明，宽度自适应/触控 44px/可访问标签与错误关联；后端仅改泛化错误文案，ContactPhone 规则、角色、CSRF 和存储不变 | 无必须手输区号的提示；号码真实归属/可拨通未被宣称验证 | 已完成 |
| R2-P04 | 目标前端测试、现有联系人/API/架构与前端回归；本地 Chrome | 默认/换国家/旧值/失败/切号/重复提交；320/390/768/1440px 实际浏览器与键盘检查；不真实拨号或发邮件 | R2-AC01～07、前端全量/typecheck/lint/build 及后端适用回归通过 | 已完成 |
| R2-P05 | 本配对文档、两份索引、TODO-0038、backend/前端说明（注意已有用户 README 修改） | 填批准、RED/GREEN、实际依赖及浏览器/回归结果；关闭 ticket，记录实现和发布状态 | VERIFIED 有实际证据；不修改用户独立 README 内容、不提前记 RELEASED | 已完成 |

- [x] 用户明确批准 revision 2，并记录原话/日期/条件。
- [x] R2-P01 有效 RED，R2-P02～03 同一测试 GREEN。
- [x] R2-P04～05 完成适用回归、手机验收与文档/索引。

### revision 1 — 已完成步骤与历史文件

路径前缀：后端生产类 `backend/src/main/java/com/geer/snowboard/v2/`，后端测试 `backend/src/test/java/com/geer/snowboard/v2/`。

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | `identity/ContactPhoneTest.java`、`identity/StudentContactApiTest.java`、`bookings/BookingStudentContactApiTest.java`、`frontend/src/StudentContactBooking.test.tsx` | 获批后先添加目标领域/API/交互测试；新预约无电话拒绝、本人保存/恢复、教练有权读取、旧幂等重放、两步写入失败与重试 | 各层得到因目标行为缺失而失败的有效 RED，记录命令；测试本身/环境故障先解决 | 已完成 |
| P-02 | `identity/domain/ContactPhone.java`、`application/port/in/StudentContactOperations.java`、`application/service/StudentContactService.java`、`application/port/out/IdentityStore.java`、`adapter/out/persistence/JdbcIdentityStore.java`；`backend/src/main/resources/db/migration/V11__student_contact_phone.sql` | 新增可空电话列、规范化规则、本人保存/查询与内部教练批量查询；账号资料归 identity，单行更新不碰认证信息，不扩大注册/AccountView | 目标领域、本人授权、长度边界、存储和迁移测试 GREEN | 已完成 |
| P-03 | `identity/adapter/in/web/StudentContactController.java` 及请求/响应 DTO | 新增 GET/PATCH `/api/student/contact`；从既有 actor 获取本人身份，用例重复校验；沿用 Cookie/CSRF/问题响应，未提供任何任意账号 HTTP 查询入口 | 真实 Session、错误角色、CSRF、本人作用域测试 GREEN | 已完成 |
| P-04 | `bookings/application/port/out/BookingStudentContacts.java`、`adapter/out/module/BookingStudentContactAdapter.java`、`application/service/BookingService.java`、`application/port/in/BookingOperations.java`、`adapter/in/web/BookingController.java` 及教练响应 DTO | 幂等重放之后、排课锁之前查本人电话；教练列表/详情先授权，再有界批量读取当前电话；新增专用教练用例投影，响应保持扁平并增加 studentPhone；无跨模块 SQL、预约电话快照或邮件端口混用 | 新申请门槛/旧请求重放/教练授权与分页 GREEN，保留既有锁协议与学生响应 | 已完成 |
| P-05 | `frontend/src/BookingHome.tsx`、`frontend/src/booking.css`、相关 API 测试替身 | 账号作用域读取/预填，输入及用途说明，独立保存和申请前串行保存；读写失败、未知网络结果与幂等重试；教练卡片电话/tel，确认拒绝合并保留电话；手机布局 | 同一目标交互测试 GREEN；登录、浏览、错误提示与账号切换兼容 | 已完成 |
| P-06 | 现有 identity/bookings/scheduling/catalog/notifications 集成测试的学员 fixture、`frontend/src/App.test.tsx` 及相关预约 fixture | 为创建新预约的既有 fixture 提供合规电话；单独保留无电话历史数据兼容场景，不放宽目标断言；检查双申请、确认、取消、通知与日期远期规则 | 适用全量回归、真实 MySQL、架构零基线、类型/lint/build 通过 | 已完成 |
| P-07 | 0011 配对文档、两个索引、TODO-0036；0002/0006 关联说明、`backend/README.md`、`frontend/README.md` | 记录实际文件、RED/GREEN、浏览器截图和限制；IMPLEMENTED 与 VERIFIED 分开；结案索引同步 | 必要验收通过、文档检查通过、明确未发布与提交状态 | 已完成 |

- [x] 完成源码调查与具体配对方案。
- [x] 用户明确批准 revision 1。
- [x] P-01：先运行有效 RED。
- [x] P-02 至 P-05：实现并将同一目标测试运行至 GREEN。
- [x] P-06：适用回归和手机端检查。
- [x] P-07：实际证据、功能/计划/索引与交付状态同步。

## 4. 数据、API、架构与兼容影响

revision 2 只改变学员输入方式及说明，前端组合完整号码；PATCH 仍提交 `{ "phone": "+14165550123" }`，ContactPhone 原 7–15 位/64 字符基本约束、VARCHAR(16)、账号资料及预约权限/事务不变，不新增 migration。新增一项前端 MIT 依赖，精确版本及传递依赖由 lockfile 固定；后端依赖和六类架构基线仍 0。下列数据/授权/事务条目是已验证 revision 1 的保留契约；“不新增依赖”历史说明仅适用于 revision 1。

- **数据**：V11 追加 `identity_account.contact_phone VARCHAR(16) NULL`；每账号一个当前电话，旧行 NULL，无唯一约束、回填或历史快照。V1–V10 不修改。`IdentityStore` 增加专用电话操作，原 `AccountView`、注册必填字段和账号凭据不改变。
- **输入**：原始电话上限 64 字符；去除空格/括号/连字符后，须为 `+` 加 7–15 位 ASCII 数字，首位非 0；规范化保存。不把基本格式检查宣称为真实号码验证。
- **API**：GET/PATCH `/api/student/contact` 仅本人 STUDENT；PATCH 需 CSRF；400 格式错误、401 未认证、403 错误角色/CSRF。POST `/api/bookings` 缺电话的新请求 400；既有课程/时段/雪场输入和幂等指纹不变。教练 GET 列表/详情加可空 `studentPhone`，其他学生/公开 API 不新增电话。
- **架构**：identity 公开 `StudentContactOperations`，bookings 通过自己的出站端口与模块适配器调用；教练批量查询只消费已经授权的预约学员集合，每页最多 50。身份端口不反向依赖 bookings，领域/端口纯 Java；Web DTO 与用例投影、数据库记录分开。架构例外无，ADR 0001/契约 1.6 不改基线。
- **授权**：联系人修改没有目标账号参数，仅使用 actor；用例层再次校验角色。教练查询先确认本人预约再查电话，错误资源返回既有 404；批量内部接口不开放 HTTP。测试覆盖直接调用用例，避免只靠控制器。
- **事务/幂等**：先独立提交电话保存，再申请预约；保存失败不创建预约，预约失败仍保留已保存电话。预约请求在原幂等查找之后、排课锁之前读已提交电话；不新增 identity 锁。联系资料不能清空，单行 UPDATE 并发最后提交值生效。原申请唯一/锁/邮件事务不变。
- **前端**：用途说明紧邻输入；当前账号内存状态、退出/401/切号清理；联系人初始读取失败可重试，课程浏览继续；普通预约列表刷新不覆盖未保存输入。提交响应不明时保留键并说明可重试；不把未知结果说成预约一定失败。
- **兼容**：旧预约查阅/确认/拒绝/取消不检查电话；历史幂等申请可重放。无电话旧行在教练端显示“未提供电话”，身份无需重新注册/验证。新增电话查询的真实故障不能降级伪装为 NULL。
- **外部**：不新增依赖或升级版本，不新增 SMS、后台任务、云资源、环境变量或密钥。v1 不复用旧手机号或验证逻辑；现有通知任务/邮件正文不加入电话。

## 5. 验收与验证计划

### revision 2 — 目标验收

| ID | 输入/场景 | 预期结果 | 验证 |
|---|---|---|---|
| R2-AC01 | 新账号首次约课，输入 `4165550123` 或 `416 555 0123` | 默认加拿大 +1；无需键入 +1，PATCH 值 `+14165550123`，保存后申请仍依次完成 | 目标前端 RED→GREEN/API 替身，现有真实 MySQL API 回归 |
| R2-AC02 | 选择中国 +86，输入 `13800138000`；切换已填写号码的国家 | 输出 `+8613800138000`；切换时不丢号码部分，展示选中区号，避免重复前缀 | 前端交互 |
| R2-AC03 | 加载既有 +1/+86；粘贴 `+1 (416) 555-0123`/完整 +86；无法识别国家的旧完整值 | 正确回填国家/号码且不因加载自动保存；+1 默认加拿大；粘贴完整值不重复前缀；未知旧值不清空或篡改 | 目标前端/历史值兼容 |
| R2-AC04 | 空值、超长输入/组合结果超出旧基本格式、只选国家没有号码 | 清楚报错且不保存/申请；合法号码不因新增严格号段规则被拒绝；原 API 非法输入仍 400 | 前端及 ContactPhoneTest/BookingStudentContactApiTest |
| R2-AC05 | 读取/保存失败、预约失败、未修改已有电话、切账号、列表刷新及未知预约结果重试 | 保留原串行写入/幂等、电话草稿/错误/切号清理；未修改规范值不重复保存；教练 tel 仍完整 | 原 StudentContactBooking 与联系人/用例回归 |
| R2-AC06 | 320/390/768/1440px、键盘与触控 | 国家和号码分隔清楚、无横向溢出、点击区域至少 44px；标签/错误关联可访问，手机国家选择可操作 | Chrome 实际检查/截图、前端交互 |
| R2-AC07 | 新依赖构建及既有功能 | React 19.3 运行/类型兼容、全量前端与后端适用联系人/架构通过；无生产数据/发信/拨号 | 命令与实际结果记录 |

批准后先在当前 UI 写目标测试并运行 `cd frontend && npm test -- src/StudentContactBooking.test.tsx` 到有效 RED，再安装精确依赖并实现，同一命令 GREEN；编译/依赖/环境问题不计 RED。完成后运行 `npm test`、`npm run typecheck`、`npm run lint`、`npm run build`，以及真实 MySQL 环境下 `./mvnw -q -Dtest=ContactPhoneTest,BookingStudentContactApiTest,StudentContactServiceTest,ArchitectureTest,ArchitectureBaselineTest test`；新增 UI 不改数据库/锁，不要求再建迁移或重复并发测试。若实际改变规则/数据/依赖选型，先修订计划重审。

### revision 1 — 历史验收及命令

| 验收 ID | 具体验证 | 环境/命令 | 预期结果 |
|---|---|---|---|
| AC-01/02/03 | 无电话账号仍登录浏览；API 无电话新申请拒绝且预约/通知为零；保存后 PENDING，刷新恢复 | `backend/`：`./mvnw -q -Dtest=StudentContactApiTest,BookingStudentContactApiTest test`；`frontend/`：`npm test -- src/StudentContactBooking.test.tsx` | 首次 RED 为既有预约 API 错误接受无电话或缺少电话填写 UI；实现后同一用例 GREEN |
| AC-04/05 | 修改电话、旧预约查询显示最新值、分页批量映射、详情与 tel、确认/拒绝后仍显示 | 同上；本地 Chrome，模拟 API 与后端真实 MySQL 分开记录 | 首次 RED 为电话接口不存在/教练响应缺少电话或卡片未显示；GREEN 后按学员正确映射 |
| AC-06 | 未认证、教练修改学员电话、学员访问他人预约、请求伪造目标账号、无 CSRF；直接用例 actor 权限 | 同上 + 既有 identity/bookings 权限与架构回归 | 拒绝越权，不修改或泄露其他学员电话；错误角色不清有效会话 |
| AC-07 | 联系保存失败不申请；保存成功预约 409 保留电话；响应丢失再试同键同预约、一次通知；未改电话不重复写 | 目标前端故障替身与真实 MySQL 幂等/API | 有效 RED 为写入顺序/错误反馈/重试不符合新行为；同一用例 GREEN |
| AC-08 | 从 V10 含旧账号/预约的数据运行 V11；旧预约仍处理，旧幂等键无电话可重放；并发修改与申请无新增锁冲突 | MySQL 8.4 隔离 Testcontainers，目标 API 及全量回归 | 旧行完整/phone NULL，新条件只影响新申请；既有并发、锁和取消保障通过 |
| AC-09 | +1/+86、分隔符、空白、非法字符、7/15 位接受、6/16 位拒绝、64/65 原始长度边界 | `./mvnw -q -Dtest=ContactPhoneTest test` + 目标前端/API | 规则一致且输出规范；领域规则缺失是有效 RED，测试编译/环境错误不是 RED |
| AC-10 | 320/390/768/1440px 看输入/用途/错误/tel；键盘、触控、私有状态切换与手机点击 | 本地 Chrome；`npm test`、`npm run typecheck`、`npm run lint`、`npm run build` | 无溢出，标签可访问，操作触控尺寸足够，不跨账号残留 |

批准后先测试再实现；RED 应优先用现有 API/渲染触发预期行为断言失败。新增 Java 类型尚不存在造成的编译错误不能作为 RED：纯领域测试可在边界建立后运行，先让真实缺失校验的行为断言失败，再补规则。

- 后端全量：`DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q test`，Java 25 / MySQL 8.4，包含架构、身份、通知和预约回归；实际工具路径与测试数量按运行结果记录。
- MySQL/并发适用：追加迁移、新预约门槛、电话原子更新、幂等重复与既有预约竞争；全部使用隔离数据，无生产操作。
- 文档：根目录 `python3 ai-docs/check_docs.py`、`git diff --check`；配对状态、批准、链接和证据同步。
- 已有 [TODO-0034](../todo/0034-full-backend-test-shutdown-timeout.md) 记录全量测试 JVM 关停超时；如复现，测试断言结果与进程正常退出证据分开报告，不能假称已解决。
- 不适用：短信送达、真实拨号、S3/SMTP 新配置、生产发布测试；本功能不增加这些行为。

## 6. 风险、成本、部署与恢复

revision 2 新增前端组件包和随包号码元数据，无新云服务/费用/密钥。React 19.3 的实际运行、类型和手机兼容已按第 8 节验证。仅本地国家名称/区号，不使用 IP 定位/国旗 CDN。格式化不验证真实所有者或可拨通；不启用比旧基本约束更严格的号段检查。回退输入组件只影响填写体验，已存完整号码继续兼容，无 DDL 回退。本地实现/验证后用户授权“提交推送”，通过既有 main 流水线交付；流水线完成前不标 RELEASED。安装时发现既有 source-map-js 1.2.1 开发依赖 high 审计告警，已记录 [TODO-0039](../todo/0039-frontend-source-map-js-audit.md)，未越过本修订范围升级工具链；检查通过不等于 audit 无告警。下列风险/部署内容为 revision 1 历史记录。

- 保存和预约为两个请求，故意允许电话保存后预约失败；明确各自状态并保留幂等键。初始联系人读取未成功前不覆盖未知值；读取故障有单独重试。
- 当前电话会更新历史预约的联系显示，此为本 revision 的 review 取舍；金额、课程、雪场等历史快照不变。基本格式不能保证电话真实性。
- 电话是私人资料，只通过授权 API 到本人或教练浏览器；不进公共页面、URL、持久浏览器缓存、日志、邮件或统计事件。沿用当前 HTTPS、数据库权限与备份，不新增密钥或云月费项目。
- V11 是可空追加列，旧数据不回填。需要回退时使用旧应用、保留列和 Flyway 历史；旧版会恢复为不强制电话，记录这一行为影响。生产迁移前备份，不能把 DDL 当成可自动逆转事务。
- 本次获批范围是本地实现/验证及用户随后要求的本地 Git commit；push、生产发布/迁移和新付费资源需要本任务额外明确授权。

## 7. 实际执行与偏差

2026-10-06 revision 2：方案阶段只读调查现有输入规则和官方组件资料，保持用户已有 `frontend/README.md` 改动；收到用户“批准”后实施以下内容。

| 日期 | 步骤/实际文件 | 实际结果 | 偏差与处理 |
|---|---|---|---|
| 2026-10-06 | R2-P01；StudentContactBooking.test.tsx | 初始 18 项中 16 个行为断言有效 RED；后续补齐粘贴焦点、旧基本格式、本地非法/超长输入和完整号码写入断言 | 新增测试等待联系人读取完成后输入；加载期间禁用控件的测试时序不计为功能缺失 |
| 2026-10-06 | R2-P02～03；package.json/lock、ContactPhoneInput、BookingHome、booking.css、StudentContactService | 精确安装 react-phone-number-input 3.4.18，锁定 libphonenumber-js 1.13.14/input-format 0.3.14；原生国家选择与现成格式化输入，默认 CA，前端输出完整号码 | 手输完整号码时暂用组件国际输入模式，失焦后回到独立国家/号码展示，修复 +86 被再次加 +1；未修改后端规则、API 或数据库 |
| 2026-10-06 | R2-P04；目标/全量前端、后端联系人与架构、本地 Chrome | 目标 24/24、全量 87/87、后端 27/27、8 个浏览器场景与类型/lint/build 通过 | 新事件处理器首次类型检查 TS7006，补齐 React 事件类型后 typecheck/build exit 0；保留失败与最终结果的区分 |
| 2026-10-06 | R2-P05；配对文档/索引、TODO-0038/0039、backend README | 记录 VERIFIED、TODO-0038 DONE；既有开发依赖审计问题另记 OPEN，不擅自扩大依赖升级范围 | 用户 frontend README 独立修改未触碰；无 commit/push 或生产操作 |

以下为 revision 1 已完成的实现记录：

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-10-06 | 只读调查、0011 配对文档、TODO-0036、索引与相关扩展链接 | 形成 revision 1 待 review；需求调查时 HEAD b5fc50d、Git 干净 | 此阶段仅文档，不提前扩大已批准 0002/0006 历史范围 |
| 2026-10-06 | P-01 至 P-05；新 identity/booking 生产文件、V11、BookingHome/CSS 及目标测试 | 用户“批准”后按有效 RED→GREEN 完成本地实现 | 身份/API 目标合并同一测试类使用一个隔离库，降低重复容器启动；不改变验收或架构 |
| 2026-10-06 | P-06；既有预约 fixtures 和迁移测试 | 新申请 fixture 保存合法电话；Foundation/Legacy 核对最新 V11，CourseCoverMigrationTest 固定自身 V10 测试目标 | 历史场景另外保留无电话用例；没有放宽既有业务断言 |
| 2026-10-06 | P-06；StudentContactController | 初次架构检查发现 identity→bootstrap→identity，新入口改为在身份 Web adapter 中从认证和 IdentityOperations 构造 actor | 当场消除循环，基线仍为 0、无需临时例外；不更改授权或业务范围 |
| 2026-10-06 | P-06；BookingService 与新增联系人并发/预约重试场景 | 两个同键申请同时到达，第二个等待日锁后返回 409 而非重放 200；在已存在日锁内重新查询幂等结果后继续业务检查 | 保持 BOOK-07 与获批 AC-07/08 的原有语义，未新增锁、表或事务；本地行为修复，不改变业务规则或范围 |

## 8. 实际验证证据

### revision 2 — 本次实际结果

选型调查使用 `npm view react-phone-number-input version peerDependencies dependencies license --json`，返回 3.4.18 / MIT / React 与 ReactDOM >=16.8；官方资料见 [react-phone-number-input](https://github.com/catamphetamine/react-phone-number-input)（独立号码输入、自定义国家下拉和完整号码输出）。批准后 `npm install --save-exact react-phone-number-input@3.4.18`，保留 React/ReactDOM 19.3.0，传递依赖由 lockfile 固定；实际兼容以以下运行结果为准。

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-10-06 | frontend，安装/实现前 | `npm test -- src/StudentContactBooking.test.tsx` | 有效 RED：18 项，16 failures、2 passed、exit 1；无国家选择、裸号码不能保存、旧区号提示/显示行为 | 不是编译或环境故障；日志 frontend-red.log |
| 2026-10-06 | frontend，实现后 | 同一目标命令 | 最终 GREEN：24/24、exit 0 | 额外断言曾发现手输 +86 写成 +186，已修复并以完整 PATCH 值确认；新增旧基本格式/粘贴焦点/本地非法与超长回归 |
| 2026-10-06 | frontend | `npm test` | 6 个测试文件、87/87、exit 0 | API 替身验证；读写失败、重试/切号、教练 tel 等既有回归保留 |
| 2026-10-06 | frontend | `npm run typecheck`、`npm run lint`、`npm run build` | 最终均 exit 0；Vite 139 modules，JS 485.99 kB/gzip 138.73 kB | 首次类型检查 3 个 TS7006 已补事件类型，构建结果含 tsc；没有 React 升级 |
| 2026-10-06 | backend，Java 25/真实 MySQL 8.4 | `mvn -Dmaven.repo.local=/private/tmp/snowboard-v2-toolchain/m2 -Dtest=ContactPhoneTest,BookingStudentContactApiTest,StudentContactServiceTest,ArchitectureTest,ArchitectureBaselineTest test` | 27/27（领域 4、API 7、用例 3、架构 13），0 failures/errors/skips；BUILD SUCCESS、exit 0 | 架构六类基线 0；只改后端错误文案，本次未重复全后端/迁移回归，也不把历史 167 项计为本次运行 |
| 2026-10-06 | 本地 Chrome | 根目录 `node .local/phone-country-prefix/browser-check.mjs` | 最终 8/8：320/390/768/1440px × 学员/教练；无 overflow/pageerror，触控至少 44px | 真实浏览器、模拟 API；默认 CA/国家切换保留号码、保存/申请顺序/刷新、手输完整 +1/+86、tel/确认检查通过；已查看 320/390px 输入截图，未真实拨号或发信 |
| 2026-10-06 | frontend | `npm audit --json`、`npm ls source-map-js`、`git show HEAD:frontend/package-lock.json` | audit exit 1，1 high；source-map-js 1.2.1 为既有 dev 依赖 | 安装电话组件前版本相同，未引入/升级；独立 [TODO-0039](../todo/0039-frontend-source-map-js-audit.md) OPEN；不声称审计清洁或已证实生产攻击路径 |
| 2026-10-06 | 根目录 | `python3 ai-docs/check_docs.py`、`git diff --check` | 文档检查通过：13 对功能/计划、39 tickets；空白检查 exit 0 | 文档与实现/发布证据分开 |

R2-AC01～07 均通过以上目标测试、适用回归和浏览器验收。Maven 实际可执行路径和 Java/Docker 设置沿用下方历史工具路径；本次后端命令含上述 test 筛选。浏览器使用 `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` 与已有 `/tmp/geer-0008-browser/node_modules/playwright/index.mjs`，独立 Vite 端口 5174，未修改 IDEA 或运行生产数据。

本次本地证据在 Git 忽略的 `.local/phone-country-prefix/`：`frontend-red.log`、`npm-install.log`、`frontend-green-final.log`、`frontend-full.log`、`typecheck.log`、`lint.log`、`build.log`、`backend-regression.log`、`browser-check.mjs`、`browser.log`、`browser-results.json`、`contact-320.png`、`contact-390.png`、`npm-audit.json`。本次最终通过前曾有目标测试和类型检查失败，最终日志为修正后的实际退出结果；中断或失败运行不作为 GREEN。

### revision 1 — 历史证据

以下 RED/GREEN、测试数量和浏览器证据均为 revision 1 历史结果，不代表 revision 2 已验收：

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-10-06 | `backend/`，Java 25/MySQL 8.4 | `mvn -q -Dtest=BookingStudentContactApiTest#missingPhoneRejectsNewBookingBeforeWritesIncludingDirectUseCase+contactsAreSelfOnlyRequireCsrfAndDoNotChangeCredentials test` | RED：2 个断言失败，0 errors；缺电话返回 201、联系人 GET 404 | 测试先前调用错误密码端口方法的编译失败不计 RED，修正为 encode 后才记录行为失败 |
| 2026-10-06 | `backend/` | `mvn -q -Dtest=ContactPhoneTest test` | RED：4 个行为失败，0 errors；号码不规范化或非法输入未拒绝 | 在已有效 API RED 后建立可编译边界，再补领域规则；未将编译失败视为 RED |
| 2026-10-06 | `frontend/` | `npm test -- src/StudentContactBooking.test.tsx` | RED 11/11：缺少电话填写、错误/加载提示和教练 tel；实现后同一测试 GREEN 11/11 | 模拟 API 的目标行为验证 |
| 2026-10-06 | `backend/`，真实 MySQL 8.4 | `mvn -q -Dtest=ContactPhoneTest,BookingStudentContactApiTest,StudentContactServiceTest,StudentContactMigrationTest test` | GREEN：14 tests，0 failures/errors/skips；进程 exit 0 | 身份/API 测试合并在 BookingStudentContactApiTest 共用同一个隔离 MySQL，避免重复启动；验收范围不减少 |
| 2026-10-06 | `frontend/` | `npm test`、`npm run lint`、`npm run build` | 76/76 通过，lint/build（含 tsc）exit 0 | 新测试首次 lint 的未使用变量已修正；另增账号切换、列表刷新保留未存草稿用例 |
| 2026-10-06 | 本地 Chrome | 根目录 `node .local/student-contact-phone/browser-check.mjs` | 8/8：320/390/768/1440px × 学员/教练，无 pageerror/横向溢出；用途、空值拒绝、保存/申请顺序、刷新预填、tel 和确认后电话检查通过 | 模拟 API；电话点击被拦截，未实际拨号；320px 输入/390px 教练截图已实际查看 |
| 2026-10-06 | `backend/`，第一次全量 | `mvn test` | 166 tests，2 failures、0 errors/skips，exit 1 | 旧 LegacyAvailabilityMigrationTest 期望 V10；新增身份 controller 依赖 ActorResolver 导致模块循环，均已修正；第二轮已验证这两项通过 |
| 2026-10-06 | `backend/`，第二次全量 | `mvn test` | 167 tests，1 failure、0 errors/skips；架构和旧迁移通过 | 新增并发用例有效 RED：同键申请结果 [409,201] 而非 [200,201]；在现有日锁内复查幂等结果，第三轮同一用例 GREEN；复现 TODO-0034 退出超时 |
| 2026-10-06 | `backend/`，最终全量 | `mvn test` | GREEN：41 类、167 tests，0 failures/errors/skips；Maven exit 0 / BUILD SUCCESS；新增并发同键申请与原有预约、身份、通知、迁移、架构全部通过 | 架构 13 项/六类零基线；本轮未出现 Surefire 30 秒退出超时，实际正常返回 0；此前第二轮复现记录在 TODO-0034，未将一次未复现视为根因已修复 |
| 2026-10-06 | 仓库根目录 | `python3 ai-docs/check_docs.py`、`git diff --check` | 文档检查通过：12 对记录、36 tickets，链接/索引/状态/批准门槛一致；空白检查通过 | 文档证据，不代表功能 GREEN |

实际 Maven 可执行路径为 `/private/tmp/geer-delivery-toolchain/apache-maven-3.9.16/bin/mvn`，各次均传 `-Dmaven.repo.local=/private/tmp/snowboard-v2-toolchain/m2`；设置 `JAVA_HOME=/Users/geerhong/Library/Java/JavaVirtualMachines/ms-25.0.4.1-1/Contents/Home`，MySQL 运行设置 `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock` 与 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。上表 mvn 是这些实际调用的简写。

本地证据均在被 Git 忽略的 `.local/student-contact-phone/`：`backend-red.log`、`domain-red.log`、`frontend-red.log`、`backend-green.log`、`frontend-regression-final.log`、`frontend-lint-final.log`、`frontend-build-final.log`、`browser-results.json`、`contact-320.png`、`coach-390.png`；第一次完整回归日志为 `backend-regression.log`，第二次为 `backend-regression-second.log`，最终为 `backend-regression-final.log`。文档检查与功能/生产结论分别记录。

## 9. 完成状态与后续

- 当前 revision 2：VERIFIED，approved_revision 为 2；2026-10-06 用户“批准”后完成 R2-P01～05 和 R2-AC01～07。IMPLEMENTED 已完成，VERIFIED 有上述本地证据；用户随后授权“提交推送”，正在执行本次提交/推送；实际结果由 Git/CI 确认，生产验收前不标 RELEASED。
- [TODO-0038](../todo/0038-phone-country-prefix-input.md) DONE，已移到归档索引；功能/计划索引已同步。既有依赖审计 [TODO-0039](../todo/0039-frontend-source-map-js-audit.md) OPEN，等待独立处置 review。
- 用户已有 frontend README 修改保留。下列记录为 revision 1 历史完成状态和证据。

- VERIFIED：revision 1 本地实现、AC-01 至 AC-10 和必要检查已完成。
- P-01 至 P-07 已完成；后端 167/167、前端 76/76、lint/build（含类型检查）、Chrome 8 场景和文档检查通过。
- 用户已要求本地 Git commit，实际提交结果以 Git 历史为准；未 push、生产部署或生产迁移。生产 RELEASED 另需实际授权和验证；本地后端重启会自动应用 V11，无需改环境变量。
- 配对功能、索引、相关扩展链接和 [TODO-0036](../todo/0036-student-contact-phone.md) 已同步。

- 既有 [TODO-0034](../todo/0034-full-backend-test-shutdown-timeout.md) 仍 OPEN：第二轮仍复现过，最终一轮没有退出超时日志；尚未定位根因，未以一次未复现将其计为已修复。
