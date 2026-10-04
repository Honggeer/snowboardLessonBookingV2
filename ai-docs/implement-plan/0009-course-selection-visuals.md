---
id: "0009"
title: "选课页品牌与课程封面交互"
status: VERIFIED
revision: 3
approved_revision: 3
created: 2026-10-03
updated: 2026-10-04
feature: "../features/0009-course-selection-visuals.md"
---

# 0009 — 选课页品牌与课程封面交互实施计划

## 1. Review 摘要

关联 [功能契约](../features/0009-course-selection-visuals.md)、[0006 约课流程](0006-post-login-booking-home.md)、[0008 媒体能力](0008-about-geer.md)、[ADR 0001](../decisions/0001-v2-baseline.md)及 [TODO-0018](../todo/0018-course-cover-right-half.md)。revision 2 已完成本地验证，以下 **revision 3 已获批准并完成本地验证**：

1. **左右各半**：左侧白底深色文字及蓝色价格，右侧固定卡片宽度的 50%；照片仅在右半边沿柔和斜向渐变展开/收起，动画和最终边缘保持同一倾斜度。悬停不改变左侧颜色、不放大照片。
2. **按宽度等比显示**：图片宽度等于右侧区域宽度，高度按自然宽高比计算。过高只裁上下；过矮保留浅色背景，不另行放大或横向裁切。
3. **只上下调整**：桌面和手机预览同步左右布局；拖动、垂直位置滑杆、重置居中共同调整 Y。API/数据库保留原 X/Y 字段；X 仅兼容既有数据，展示忽略 X，移除水平控件。
4. **交互与兼容**：选中保留照片和蓝边，触屏直接显示右半边照片，reduced-motion 静态切换。名称、介绍、时长、价格集中在左半边，窄屏换行。上传/READY/失败恢复与课程预约流程继续沿用已验证行为。

可审阅的[静态桌面/手机设计稿](../design/0009-right-half-review.html)及[预览图](../design/0009-right-half-review.png)。只调整前端课程卡及构图展示，复用现有存储、上传和位置契约。原[概念图](../design/0009-course-selection-concept.png)和 revision 2 截图保留为历史，不能作为本修订验收证据。

根据 AGENTS.md 及 WORK-06，本次验收变化已更新为 revision 3 并提交整体 review；用户于 2026-10-03 明确回复“开始实现”，授权执行本修订。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-10-03 | “只针对选课页面……左上角的和登录界面的logo一致……教练添加课程的时候，可以上传图片……鼠标放到上面就会露出来他的图片……我想酷一些” | N/A | 授权调查和设计建议，未批准此具体计划 |
| 2026-10-03 | “再加一个 “拖动调整构图”，然后开始实现” | 2 | 批准已展示的 revision 1 方案并加入拖动调整封面构图、桌面/手机预览及展示位置保存；本地实现验证，不含提交推送或生产部署 |
| 2026-10-03 | “有个小问题……只有一半，右半边渲染图片，中间是过度色……按照等比例缩小，可以上下调整” | N/A | 请求修改视觉及构图验收；形成 revision 3 待 review |
| 2026-10-03 | “固定右半边 50%，图片按这部分宽度等比显示” | N/A | 确认右侧宽度为 50%；不是完整修订批准 |
| 2026-10-03 | “开始实现” | 3 | 批准已展示的右侧 50% 等比图片、中间渐变、原色信息/价格、桌面/手机一致及纵向构图；仅本地实现验证 |
| 2026-10-04 | “提交推送” | 3（提交授权） | 授权提交并推送当前工作区的课程封面与构图、卡片样式、教练课程列表维护及关联验证/视频诊断文档；不授权生产部署或新增云资源 |

当前 `approved_revision: 3`。保留 revision 2 历史批准和验证，revision 3 已按 WORK-09 完成目标测试 RED → GREEN 和适用回归，记录见 8.2。

## 3. 实现步骤与预计文件

### 3.1 Revision 3

| 步骤 | 预计文件 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| R3-01 | 0009 配对文档、索引、TODO-0018、design 静态稿 | 记录宽度澄清与新验收，提交 review | 用户批准 revision 3 | 完成：用户已批准 |
| R3-02 | frontend/src/CourseVisuals.test.tsx | 先验证纵向拖动按固定宽度计算比例、水平移动不改 X、Y 保存/取消/重置/失败保留 | 因行为缺失的有效 RED 及同一用例 GREEN | 完成 |
| R3-03 | CourseCard.tsx、CourseCoverEditor.tsx、booking.css；必要时 BookingHome.tsx | 右半边容器、左侧文字/价格、白色渐变、宽度缩放与纵向位移；去掉整卡变色/照片放大/水平控件，预览一致 | 按新 AC-02/03/07 验收 | 完成 |
| R3-04 | 0009 配对文档、索引、frontend/README.md、TODO 索引与新截图 | 相关回归、浏览器验收和证据同步 | 如实区分 IMPLEMENTED/VERIFIED，完成票据 | 完成 |

### 3.2 已完成的 revision 2（历史）

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-01 | 0009 文档/索引、design 概念图 | 设计默认/悬停/选中/触屏状态，记录审核依据 | 用户批准 revision 2 | 完成 |
| P-02 | catalog/media 测试、前端课程测试 | 按第 5 节建立目标行为 RED，分批实现 GREEN | 有效失败及同一测试通过证据 | 完成 |
| P-03 | media 用途/端口/持久化、V10 | COURSE_COVER、按 consumer 隔离引用、维持 GEER 兼容和清理 | 主页与多门课程引用互不覆盖，真实 MySQL 回归通过 | 完成 |
| P-04 | catalog 用例/持久化/Web DTO、新 CourseMedia/module adapter | 可选封面创建/更新/移除/下架；原子引用、签名展示降级 | 授权/幂等/旧请求/故障/并发通过 | 完成 |
| P-05 | frontend/src/BookingHome.tsx、booking.css、媒体上传组件、独立 Logo 图片 | 创建/编辑封面、卡片展开、触屏/键盘/缺图降级；整理复用上传逻辑 | 功能与品牌对照、实际浏览器通过 | 完成 |
| P-06 | 两个索引、0006/0008 关联说明、README/验证记录 | 迁移版本断言、架构扫描、相关回归和文档同步 | IMPLEMENTED/VERIFIED 按证据区分 | 完成 |

- [x] 只读调查现有实现与媒体接入边界。
- [x] 用户批准 revision 2：原方案加拖动构图。
- [x] 每批目标测试 RED → 同一测试 GREEN，完成适用回归与浏览器验收。
- [x] 同步实际变更、验证、索引及交付状态。

## 4. 数据、API、架构与兼容影响

### 4.1 Revision 2 实施前调查与接入（历史）

- 登录 Logo 嵌于 `frontend/public/images/geer-blue-*.png`；选课页 `.booking-logo` 是斜体文字。实施时先整理独立透明字标，核对轮廓和蓝色细节，再使用图片；概念图不作为最终 Logo 精度证明。
- 课程类型、创建/更新请求及 `catalog_course` 当前均无图片字段。增加可空资产 ID，列表 DTO 可带封面短时地址、到期时间和宽高；数据库不存 URL 或图片二进制。
- `JdbcMediaStore` 当前将 `consumer='GEER'` 硬编码；直接复用会互相替换引用。将引用访问扩展为明确 consumer+slot，主页仍为 GEER，课程为 `COURSE:<UUID>`，用途 COURSE_COVER；引用写入和已保存图片的读取均按 consumer 校验。
- `catalog` 通过自己的 `CourseMedia` 出站端口和 `adapter.out.module` 调用 media 发布的入站用例。`media` 不读取课程表，不导入 catalog；既有 coachprofile 通过适配器传递固定 GEER。新增架构例外目标 0。

### 4.2 API、权限与旧请求

- 复用 `/api/coach/media/uploads` 请求/完成/状态协议，新增 COURSE_COVER 用途；完整保留用户归属、READY/目的类型、配额与 CSRF 校验。
- 创建课程增加可选 `coverAssetId`。更新通过 Web DTO 区分“未提供”与“显式 null”：未提供保留旧封面，null 清除，非空替换；转换为纯 Java 更新命令，不把 JSON 框架类型带入端口。
- `/api/courses` 仍需 STUDENT，教练列表仍需 COACH；无新的匿名课程端点。学员只能拿到当前有效课程已保存引用的签名地址，不能由任意资产 ID 取得草稿预览。
- 列表采用专用展示结果/DTO；`findPublished`/`lockPublished` 等预约内部协作保持课程业务信息，不因封面签名故障影响下单事务。按当前分页逐门课程检查 consumer 并准备图片信息，媒体失败返回空封面，课程本身保持可用。
- 签名沿用 0008 的 15 分钟到期及已有本地/生产分发配置；头像、课程封面和主页草稿的引用范围不能混用。包含签名的响应使用 `Cache-Control: no-store`。前端仅加载悬停/聚焦/选中的桌面卡片图片，触屏按可见区域懒加载；URL 失效有界刷新一次，持续失败时降级，重取地址不重置已选课程/日期/时段/雪场。已签发图片地址可被转发，替换/下架后到期前仍可用，与 0008 的访问约定一致。

### 4.3 迁移、原子性与锁顺序

- V10：`catalog_course.cover_asset_id CHAR(36) NULL`、`cover_position_x/y DECIMAL(5,2) NOT NULL DEFAULT 50` 且 CHECK 0–100；`media_reference.consumer` 从 VARCHAR(40) 扩到 VARCHAR(64)。不改 V1–V9，不引入跨模块表 JOIN/FK，不给旧课程批量补图。
- 创建先按原有幂等键检查/插入锁定课程；更新/下架先 `FOR UPDATE` 锁课程，随后在 media 内按资产 ID 升序锁旧/新资产。检查 owner、COURSE_COVER、READY、非删除状态后，同一 READ COMMITTED 事务更新课程字段和其引用，任意失败全回滚；外部 IO 不进入此事务。
- 课程封面保存引用使用该课程的 PUBLISHED 槽；上传未保存只属于上传者预览。移除/下架清空封面字段和对应引用，重复下架保持幂等。宽限清理继续检查所有 consumer，不能仅检查 GEER。
- 创建指纹纳入封面 ID 和规范化后的展示位置；无图请求保持旧版指纹计算兼容，避免升级后同一旧请求键被误判冲突。并发重复创建只有一个课程与一套引用；幂等插入未成功后在 READ COMMITTED 下重新读取并发赢家的已提交课程及指纹，同键不同封面返回 409。
- 并发更新遵循既有串行锁定与后提交覆盖语义；不新增课程版本协议。清理只锁媒体、不回读课程，验证替换/下架与清理之间无死锁或引用被误删。
- 已有预约仍使用课程名称和价格快照，不跟随封面变化；此轮不扩展预约快照或邮件内容。V10 后必要迁移版本断言同步。

### 4.4 前端

Revision 3 按 4.6 修改照片区域和缩放规则，其余通用交互继续沿用。卡片仍为单一可聚焦选择控件，图片层不截获指针，不嵌套按钮；明确选中语义和可访问名称。采用 CSS 遮罩/裁切、透明度和 transform，控制在 280–360ms；预留图片尺寸、边框宽度保持一致。关键文字必须通过对比度检查，焦点和已选有区别。手机/粗指针直显图片，reduced-motion 取消展开、抬升和缩放。

教练课程表单的封面区显示上传进度、校验状态、缩略图、替换/移除和重试；保存失败保留文本和已验证的待保存封面。取消编辑不修改已保存课程，未引用文件交给既有清理。若抽取 0008 上传组件/Hook，仅在已验证契约下复用，并回归主页上传/发布。无需新组件/动画库。

### 4.5 Revision 2：拖动调整构图（历史，显示规则由 4.6 替代）

上传 READY 后使用保留的原图，在桌面长卡和手机 16:9 预览中显示构图。拖动图片改变 cover 展示位置（Pointer Events，鼠标/触屏），对可裁切方向移动，范围限制 0–100%；提供水平/垂直调整控件和“重置居中”支持键盘。两种预览共用一组位置，不做旋转、缩放、像素裁剪或额外对象上传。

API 使用 `coverPositionX`、`coverPositionY` 两个数字，最多两位小数，默认 50。创建未提供位置则居中；PATCH 未提供位置保留，替换为不同图片未提供位置则重置 50；显式移除封面归中。拒绝 null、非数字、非有限值、越界/超精度位置；未提交文本编辑与构图均留在表单，失败保留、取消丢弃。保存后的课程列表返回位置，并用于 CSS object-position；仅修改位置不重复上传/验证图片。

### 4.6 Revision 3：右半边照片与纵向构图

- 卡片图片容器从全卡改为右侧 50% 宽、全卡高，桌面和窄屏/触屏相同。左侧课程名称、介绍、时长、价格与选中提示不跨入图片区；价格放在文字下方。保持单一选择按钮、原选择语义和稳定尺寸。
- 渐变只覆盖照片区域左沿：沿斜向边界由白色过渡到透明，连接左侧白底。默认无照片时采用纯白底（用户后续纯样式细化）；hover/focus/selected 通过移动同一条柔和斜向渐变边缘展开右侧照片，不再把全卡底色和文字变成深底白字，不使用照片 hover scale。
- 缩放只依赖显示宽度：`renderedHeight = naturalHeight × imageAreaWidth / naturalWidth`。不再使用同时按宽高填满的 `object-fit: cover` 规则；照片宽度始终填满右侧，原图完整保留。过高图片上下溢出裁切，过矮图片露出浅色底，不填满高度。Y=0/50/100 分别为上/中/下对齐，无水平裁切或横向移动。
- 垂直位置的显示偏移为 `(frameHeight - renderedHeight) × Y / 100`。拖动增量以这一有符号可移动距离换算，限 0–100%，保留最多两位小数；高度恰好相等时拖动不改变位置。水平移动不改 X/Y。鼠标和触屏均支持，滑杆支持键盘，重置 Y=50（同时沿用既有 X=50 重置行为）。
- 前端卡片/预览展示忽略 X；移除水平滑杆。新图默认 X/Y=50，编辑保存旧课程时保留其 X 数据，Y 更新沿用原有 API；替换/移除/重置仍按既有归中约定。后端继续兼容旧客户端 X/Y，无新迁移、端口、依赖、资源或上传成本改变。
- 实际选课卡片与教练桌面/手机预览共用显示规则；预览的照片区域用于计算拖动，不能用整个卡片宽度。READY 前禁用课程保存、图片预览恢复/重取和一次地址刷新保持不变。

## 5. 验收与验证计划

**Revision 3 目标**：批准后先新增有效行为测试，运行到 RED，再实现至同一测试 GREEN。覆盖按右侧宽度等比计算的纵向拖动、纯横向拖动不改变位置、Y 保存/取消/失败保留/重置；保留既有上传和有界刷新用例。纯 CSS 不加机械快照测试。

适用回归：frontend/ `npm test`、`npm run lint`、`npm run build`。Chrome 在 1440/768/390px 与粗指针、键盘、reduced-motion 下检查右侧区域恰为 50%、左侧颜色稳定、自然比例不变和图片无 hover 缩放；竖图/16:9/超宽图/长标题与图片失败均核对。用实际教练与学员表单验证垂直构图保存/重开且复用同一资产，不重新上传；hover 无写请求且不重置日期/时段/雪场。后端协议无变化，只有发现具体回归风险时扩大后端检查。最终运行 `python3 ai-docs/check_docs.py` 和 `git diff --check`，记录实际命令、截图、结果与限制。

以下为已完成的 **revision 2 历史测试计划**：

| 验收 ID | 目标测试与预期行为 RED | GREEN/回归与环境命令 |
|---|---|---|
| AC-01/02/03 | 前端课程卡目标用例：封面不存在、触屏/键盘图片呈现与选中语义缺失；纯 Logo/CSS 不用机械快照做 RED | frontend/ `npm test`、`npm run lint`、`npm run build`；Chrome 390/768/1440px，hover/focus/selected/touch/reduced-motion |
| AC-04 | 课程带 READY 封面创建/替换/清空、上传失败保留表单行为缺失 | catalog API、前端交互，真实浏览器上传→验证→保存→学员选择 |
| AC-05 | 两门课及 GEER 引用隔离、非本人/未 READY/错误用途被拒绝；课程写入后故障必须回滚引用 | MySQL 8.4 Testcontainers，课程/媒体目标用例，独立事务并发创建/替换/清理 |
| AC-06 | V9→V10 后旧课程无图可读，旧 PATCH 不清空已有封面，图片失败仍可选课 | 迁移/兼容、前端图片错误回退；既有预约快照、深链接、0008 上传/发布回归 |
| AC-07 | 拖动/键盘构图、位置持久化及越界拒绝行为缺失 | 前端交互 RED/GREEN、API 位置边界、Chrome 鼠标/触屏拖动后重开与学员卡片位置核对 |

批准后先以可编译的目标场景运行失败；编译错误、环境问题不计 RED。新增测试名称在实际执行时记录，预计新增 `CourseCoverApiTest`/`CourseCoverLifecycleTest` 和前端封面交互用例。后端命令使用本机 Testcontainers 环境，在 backend/ 串行运行目标用例，再 `./mvnw -q test`；生产与用户数据库不作为测试库。

浏览器验收记录默认/悬停/选中和触屏截图；实际测 hover 不产生网络写请求、不重置当前预约选择，点击仍沿用课程选择行为。验证图片慢/失败、签名过期、有无封面、长标题、分页以及取消编辑。文档 `python3 ai-docs/check_docs.py`、`git diff --check`。架构检查随后端全量运行；没有生产验收。

## 6. 风险、成本、部署与恢复

Revision 3 文字集中在左侧白底，实际检查极亮/极暗图片不会影响左侧可读性；触屏直接展示右半边照片，不用悬停才能访问的操作。窄屏文字区域减半，需要检查长标题换行和价格不溢出；超宽照片按宽度等比显示时会露出上下底色，这是本修订的明确展示规则。大图片可能影响加载，限定沿用上限、建议压缩的横图、可见卡片按需加载；本轮不新增自动转码或缩略图服务。

复用现有总 1 GiB/20 次每小时/单活动上传限额和存储基础设施，无新依赖、云资源或密钥；新增实际图片会带来未来存储/流量费用，预算需部署阶段验证。本轮仅本地，8080 继续留给 IDEA。

应用回退保留新增列和 consumer；旧程序不能维护课程封面引用，回退期间暂停媒体清理并暂停封面编辑，恢复到支持 0009 的版本后核对引用再开启清理。数据库与对象备份/恢复仍属生产部署计划，不能直接撤销已应用 V10。

## 7. 实际执行与偏差

Revision 3 已获用户批准并实现验证：CourseVisuals 目标行为先 RED 再 GREEN；CourseCard/CourseSummary、CourseCoverEditor、BookingHome 与 booking.css 完成右半边照片、宽度比例、纵向构图和真实课程信息双预览。只更新前端和文档，不改 API/后端/数据库；没有架构或成本变化，无新 ADR。以下表格保留 revision 2 的已执行记录。

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-10-03 | 只读调查、配对 0009 文档与索引、概念设计 | 形成 revision 1 建议 | 设计阶段未批准，不写功能测试/实现 |
| 2026-10-03 | P-02/03/04：CourseCoverApiTest、CourseCoverMigrationTest；V10；catalog CourseMedia/adapter、用例/DTO/持久化；media 用途及 consumer 扩展 | 11 个 API 用例先有效 RED 后 GREEN；原子引用/权限/构图/并发/回滚、升级兼容通过 | 保留 GEER 旧端口兼容；列表按分页逐个 consumer 校验，最多当前分页上限，未增加批量签名协议；内部约课不签名 |
| 2026-10-03 | P-05：CourseCard、CourseCoverEditor、BookingHome、booking.css、coachProfileApi、geer-logo.png、CourseVisuals | 上传/READY 保存、鼠标与触屏构图、键盘/重置、失败恢复、卡片斜切与触屏直显完成 | Logo 使用内置 imagegen 提取后目视核对，非像素级复刻；纯 Logo/CSS 不加机械测试；窄窗口监听与移动 tap 高亮按浏览器证据调整 |
| 2026-10-03 | P-06：索引、0006/0008 关联、三个 README、验收截图 | 本地 VERIFIED、V10 说明与回退条件同步 | 无新依赖、云资源或部署；独立测试服务已清理 |

## 8. 实际验证证据（历史 revision 2）

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-10-03 | 仓库 | 读取契约、0006/0008、ADR、当前 Git、品牌图、课程与媒体源码 | 确认目前无封面字段、Logo 来源和 GEER 引用耦合点 | 仅设计调查，不代表功能或媒体验证 |
| 2026-10-03 | 内置 imagegen / 根目录 | 生成概念图并目视检查；`python3 ai-docs/check_docs.py`、`git diff --check` | 图保存到 design；10 组配对、17 个 ticket、索引/状态/链接/review 门禁通过；无空白错误 | 概念图偏差在提示词文件和 review 摘要注明；本轮未写功能测试或实现 |
| 2026-10-03 | backend；`DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock`、`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` | `./mvnw -q -Dtest=CourseCoverApiTest test` | 有效 RED 10 个断言失败及缺少引用的查询错误；新增用途/字段/校验/幂等/原子引用实现后 11/11 GREEN | 初始编译错误和单教练夹具冲突不计 RED，修正后实际运行；故障注入通过 HTTP 500 并校验旧字段/引用保留 |
| 2026-10-03 | frontend | `npm test -- --run src/CourseVisuals.test.tsx` | 初始 6/6 RED；实现后 GREEN；图片预览恢复追加 1 RED；最终该文件 8/8 GREEN | 上传 PUT/完成/READY、提交禁用、保存失败保留、取消/重置、位置保存、单次刷新与无图降级 |
| 2026-10-03 | backend / MySQL 8.4 | `./mvnw -q test` | 全量 129 项通过，0 失败/错误/跳过，包含预约快照、主页媒体、清理与权限回归 | 最终 Web 封面 DTO 映射与追加迁移测试后再定向检查 |
| 2026-10-03 | backend / MySQL 8.4 + ArchUnit | `./mvnw -q -Dtest=CourseCoverMigrationTest,CourseCoverApiTest,ArchitectureTest test`；`./mvnw -q -DskipTests package` | 最终 16 项和 jar 构建通过；累计 130 个不同后端用例，0 失败/错误/跳过；架构基线各类 0 | V9→V10 验证旧课程/指纹/金额和默认无图位置，DB CHECK 拒绝越界；V1–V9 不变 |
| 2026-10-03 | frontend | `npm test`、`npm run build`、`npm run lint` | 全量 44 项通过、tsc/Vite 生产构建与 lint 通过 | 无依赖变化 |
| 2026-10-03 | Chrome / 本轮独立服务 | `/tmp/geer-0009-browser.mjs` 上传/保存段及修正后的 `/tmp/geer-0009-browser-resume.mjs`；1440/768/390；粗指针/触屏；键盘与 reduced-motion | 真实 PNG 1.9 MB→S3Mock PUT→真实校验→保存；鼠标位置 50→55.92 并重开/学生卡一致，取消保留；触屏进一步构图后保存；hover 不写 API、不改预约选择和尺寸；Enter/tap、0s 动画、无横向溢出、0 pageerror | 初始浏览器夹具密码算法、退出文案和教练/学员 context 复用错误已修正，不计通过；图片内容复用已有品牌照片，仅属测试资料。头像/证书/视频仍按 0008 |
| 2026-10-03 | Chrome 动画抽查 / CSS 数值 | `/tmp/geer-0009-animation-check.mjs`；白图最亮条件下计算渐变遮罩对比度 | 展开 100ms、收起 100/400ms 文字底色同步且可读，reduced-motion 无延迟；窄窗口自动加载封面通过；白字最差对比度 6.07:1，默认小字 5.42:1 | 动画使用即时暗底和 320ms 同步恢复底色/文字，避免白字在白底或黑字在照片上闪现；无新范围 |

截图：[桌面默认](../design/0009-implemented-desktop.png)、[悬停](../design/0009-implemented-hover.png)、[768px](../design/0009-implemented-tablet.png)、[390px](../design/0009-implemented-mobile.png)、[编辑器](../design/0009-implemented-editor.png)。本轮 backend 19099/frontend 5175、独立 MySQL 容器和两个专用 S3Mock 桶均已清理；用户 IDEA 8080/Vite 5173 保留。S3Mock 不校验真实签名过期，过期/失败恢复通过前端失败注入；生产 CORS/IAM/CloudFront 仍需部署验收。

### 8.1 Revision 3 设计与文档检查（不代表功能验收）

- 2026-10-03：Chrome 打开 `ai-docs/design/0009-right-half-review.html`，在 1280px 保存静态桌面/手机设计截图；五张示例卡的图片区域占卡片内部宽度均为 0.5，四张照片保持自然比例，左侧背景为白色；目视核对中间渐变和文字/价格位置。
- `python3 ai-docs/check_docs.py`：10 组配对、18 个 ticket、链接/索引/状态和 review 门禁通过；`git diff --check` 通过。
- 以上为提交 review 时的静态设计证据；之后用户“开始实现”批准，实际实施验证见 8.2。

### 8.2 Revision 3 实际执行与验证

| 日期 | 环境/目录 | 命令/步骤 | 实际结果 | 限制与偏差 |
|---|---|---|---|---|
| 2026-10-03 | frontend / Vitest | `npm test -- --run src/CourseVisuals.test.tsx`；初始输出 `/tmp/geer-0009-r3-red.log` | 有效 RED：4 失败、8 通过。旧位置仍用 object-position、仍有水平控件；短图拖动 Y 留在 75 而应为 95；横向拖动把旧 X=18.25 改成 56.82。修改后同一 12 用例 GREEN | 4 个新增指针用例覆盖短图/横移/竖图边界和触屏/等高不动；保留并更新键盘保存、取消/重置/失败、上传/READY 和有界刷新用例 |
| 2026-10-03 | frontend | `npm test`、`npm run build`、`npm run lint` | 全量 48/48、tsc/Vite 构建与 lint 通过 | 无依赖/版本变化；后端和存储协议未变，因此沿用 revision 2 后端/真实媒体验证，不重复整套后端回归 |
| 2026-10-03 | Chrome，Vite 5173，隔离浏览器 API 夹具 | `node /tmp/geer-0009-r3-browser.mjs`；1440/768/390px；实际 React 页面 | 右侧 CSS 50%，左侧信息不跨入照片；图片显示宽度等于图片区，按自然比例；hover 100ms/结束/收起颜色稳定、尺寸和已选日期/时段/雪场不变、无写请求；长标题/极大合法价格/竖图/16:9/超宽图、Enter/tap、reduced-motion 通过 | 浏览器使用状态化 API 夹具，PATCH 后返回列表供教练重开及学员重载核对；不代表新增真实数据库/S3/生产验证。首次超宽图比例检查采用过紧的比值误差，修正为 0.05px 高度误差以允许 Chrome 亚像素布局，后完整运行通过 |
| 2026-10-03 | Chrome 教练/学员表单 | 鼠标横移/上下拖动、保存/重开、重置/取消；CDP 原生触屏拖动和保存 | 鼠标 Y=50→43.16，触屏再到 70.05；旧 X=18.25 和同一 asset-portrait 保留，0 重上传；学生读取保存 Y；0 pageerror | 卡片和预览共享照片/渐变 CSS 及 CourseSummary；预览显示当前课程文本和价格，属于已批准预览一致性的局部实现选择 |
| 2026-10-03 | 仓库 | `python3 ai-docs/check_docs.py`、`git diff --check` | 配对/索引/票据/批准与链接通过；无空白错误 | TODO-0018 已按验收关闭，8080/5173 用户进程保持 |

实际 UI 截图：[桌面默认](../design/0009-r3-desktop.png)、[悬停](../design/0009-r3-hover.png)、[平板](../design/0009-r3-tablet.png)、[手机](../design/0009-r3-mobile.png)、[构图编辑器](../design/0009-r3-editor.png)。本修订先完成 IMPLEMENTED，再依据上述测试/构建/浏览器证据更新为 VERIFIED；历史截图与 revision 2 证据保留。

### 8.3 Revision 3 局部样式细化：斜向渐变与纯白底

- 2026-10-03 用户原话：“边缘渐变最好是斜着的 然后不展示的时候卡片的纹理就不要了”；关联 [TODO-0019](../todo/0019-course-cover-diagonal-fade.md)。本次是既有左右分区与渐变的局部 CSS 外观细化，不改变区域/比例/构图/上传/选择的行为验收、架构、数据或成本，按 WORK-06 已批准范围内的局部实现处理，保留 revision 3。
- 实际文件仅 `frontend/src/booking.css`：移除卡片默认几何伪元素，照片/预览底色为白色；白色渐变层按 -20° 倾斜，底部固定衔接、顶部向右延伸；撤掉动画中的硬 clip-path，改为移动同一个渐变遮罩，展开/最终/收起的边缘柔和且斜度一致；桌面 64px、手机 40px 的渐变带共享于选课与教练预览。右侧容器仍为 50%，图片不作几何变换。
- 用户追加原话：“整体展开动画那个边界线我也想搞成模糊的，然后倾斜度和最后的边界一致，比现在边界倾斜度大一些”；归入同一票据与本次纯样式细化。角度从临时 12° 调整为 20°，不修改图片自身比例或构图。
- 纯 CSS 不创建机械样式测试或新的 RED/GREEN；按项目契约“纯样式调整”门禁验证：frontend/ `npm run build` 通过；`node /tmp/geer-0009-style-browser.mjs` 在实际 React 页面及隔离 API 夹具下，1440/768/390px 的右侧 50%、比例/颜色/尺寸、无纹理、教练双预览一致通过；键盘选择后保留图片，reduced-motion 的遮罩 transition=0s，0 API 写请求、0 pageerror。
- Chrome 读取纯色夹具截图像素：桌面 386×166px 区域，展开中/完全展开/收起中，上下两条采样线的边缘位移均为 30px（符合 20°），三个阶段过渡带均有 59 个中间色像素；手机 162×202px 的位移为 37px、过渡带为 36–37px。隐藏后图片区内部像素全白，确认没有纹理或照片残留。实际结果存于 `/tmp/geer-0009-style-result.json`。
- 初次像素读取的 PIL 不可用，改为 Chrome Canvas 读取截图；收起 80ms 时斜边已部分离开容器，改在 20ms 的可见阶段采样后完整检查通过；这些是验证夹具调整，不计功能 RED。
- 最新实际 UI：[默认纯白](../design/0009-style-desktop.png)、[柔和斜向展开](../design/0009-style-hover.png)、[手机](../design/0009-style-mobile.png)、[教练预览](../design/0009-style-editor.png)。`python3 ai-docs/check_docs.py` 与 `git diff --check` 通过；TODO-0019 已关闭。

### 8.4 2026-10-04 提交前统一检查

用户“提交推送”授权后，对当前工作区统一运行以下检查；历史有效 RED → GREEN 及浏览器证据保留。本轮没有新增功能实现、重启 IDEA 后端或修改视频配置。

| 工作目录 | 实际命令 | 结果及边界 |
|---|---|---|
| `frontend/` | `npm test && npm run build && npm run lint` | 5 个文件、51/51 测试通过；构建包含 `tsc --noEmit`，Vite 生产构建和 ESLint 均通过。 |
| `backend/` | `env DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q test` | 35 套件、130/130 通过，失败/错误/跳过均为 0；包含课程封面 API、V9→V10 迁移、真实 MySQL 及架构检查。日志 `/tmp/geer-20261004-prepush-backend.log`，结果来自本轮 Surefire XML。 |
| 仓库根目录 | `python3 ai-docs/check_docs.py`、`git diff --check` | 10 对功能/计划、22 张 ticket、链接/索引/状态/批准门槛通过；无空白错误。 |

本轮同步提交 0006 教练课程列表维护和 0008 视频诊断记录；TODO-0022 保持 OPEN，没有将后端/前端回归通过作为真实长视频卡顿修复或 AWS 播放验收。生产未部署，功能仍为本地 VERIFIED。

## 9. 完成状态与后续

**VERIFIED，revision 3，approved_revision: 3**。右半边等比照片与纵向构图实现完成，目标测试 RED → GREEN、48 项前端回归、构建/lint 和浏览器验收通过；TODO-0018 已关闭，斜向软边动画和纯白底的局部样式检查完成，TODO-0019 已关闭。revision 2 已实现并本地 VERIFIED，原代码和历史证据保留。2026-10-04 用户已明确授权提交与推送当前工作区，实际结果以 Git 记录为准；未部署生产，状态不是 RELEASED。
