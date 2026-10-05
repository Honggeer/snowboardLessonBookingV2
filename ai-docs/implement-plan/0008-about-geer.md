---
id: "0008"
title: "关于 GEER：教练主页与媒体管理"
status: IMPLEMENTING
revision: 3
approved_revision: 3
created: 2026-10-02
updated: 2026-10-04
feature: "../features/0008-about-geer.md"
---

# 0008 — 关于 GEER 实施计划

## 本次 revision 3：微信仅二维码与社交字段自由填写（已批准，实施中）

关联 [功能 revision 3](../features/0008-about-geer.md)、[TODO-0029](../todo/0029-coach-social-contact-simplification.md)。用户当前指令确定微信只提供二维码，取消小红书/抖音链接和账号业务校验。该变更替代 revision 1 的社交格式/成对/微信号规则，属于 WORK-06/08/09 要求先 review 的行为修订。用户于 2026-10-04 回复“开始实现 批准”，明确批准本 revision 3 及 review 问题所列的 commit/push/既有自动部署。当前 `approved_revision: 3`，按目标 RED/GREEN 实施；revision 2 历史批准和已上线事实保留。

### 行为与未决范围

1. 微信卡片依二维码显示，直接展示二维码缩略图、可放大查看；移除微信号输入、公开文字和复制功能。仅二维码即可发布；未配置时公开隐藏，草稿预览可显示二维码待配置提示。
2. 小红书/抖音账号、链接均为可选文本，取消格式、指定平台域名、必须 HTTPS 与成对填写校验。保留账号名和链接字段，可只填任一项；仅账号有值也显示对应卡片。链接输入使用普通文本输入，不添加原生 URL 校验或规则提示。
3. 保存/发布接受普通文本；浏览器只对可打开的 HTTP/HTTPS 地址提供跳转（包含 HTTP、其他平台/短链域名），非地址文本/其他协议仅显示文本，不影响提交。保留文本渲染及外链 `noopener noreferrer`，不添加外部请求、抓取或自动确认账号真实性。
4. **长度选择已确认**：2026-10-04 用户选择“保留长度上限，取消其余校验（推荐）”。账号保留 80 字、链接保留 2048 字的前后端上限，其余社交校验取消；其他主页字段限额继续生效。该答复确认业务条件；后续“开始实现 批准”已批准本次具体计划。
5. 已保存 JSON 与旧客户端携带的 `wechatId` 兼容；旧值不参与展示/发布，新保存忽略该字段。现有字段/版本/API 路径、草稿/公开快照及媒体引用协议保留；不加数据库迁移，不批量改生产数据或自动发布主页。

### 具体文件与步骤

| 步骤 | 实际文件/模块 | 内容 | 完成判定 | 状态 |
|---|---|---|---|---|
| R3-00 | 本配对文档、索引、TODO-0029 | 记录已确认长度选择、review revision 3 具体实施范围；单独明确本次 commit/push/部署授权 | 用户明确答复，记录日期、原话、revision 与条件 | 完成 |
| R3-01 | 新 `backend/src/test/java/com/geer/snowboard/v2/coachprofile/CoachProfileRulesTest.java`；既有 `CoachProfileApiTest.java`、`media/MediaLifecycleTest.java`；前端 `AboutGeerPage.test.tsx`、`CoachProfileEditor.test.tsx` | 获批后先加测试：仅二维码发布、任一社交字段独立保存/发布、HTTP/非平台域名/文本、旧微信号兼容、账号单独展示、编辑页无微信号、二维码放大、账号 80/链接 2048 字边界 | 因目标行为缺失实际 RED，编译/环境故障不计；原权限/引用控制用例仍有效 | 待批准 |
| R3-02 | `backend/src/main/java/com/geer/snowboard/v2/coachprofile/domain/ProfileRules.java` | 调整字段规范化/可选规则，去掉两平台链接校验和账号/链接配对、微信二维码依赖微信号；兼容并忽略旧 `wechatId`；保留账号 80/链接 2048 字上限，并兼容旧字段 | 同一领域/API 测试 GREEN；二维码仍须正确归属、用途和 READY，必填资料/视频封面/证书规则保留 | 待批准 |
| R3-03 | `frontend/src/CoachProfileEditor.tsx`、`AboutGeerPage.tsx`、`coachProfileApi.ts`、`about-geer.css` | 移除微信号/复制；微信 QR 缩略图及弹层；社交字段文本输入、独立展示与点击处理；调整卡片和长文本换行 | 同一前端测试 GREEN；390/768/1440 px 无溢出，弹层关闭/键盘、编辑保存预览通过 | 待批准 |
| R3-04 | 功能/计划/索引/ticket、`backend/README.md`；已存在 CI/CD | 回归与证据归档，状态分开；仅本次获明确授权时 commit/push 到 main 并跟踪既有流水线 | 实际测试、生产 SHA/健康/公开页展示按授权记录；不擅自改用户草稿或发布内容 | 待批准 |

### 目标验证与回归

- 获批后目标后端：`mvn -Dtest=CoachProfileRulesTest,CoachProfileApiTest,MediaLifecycleTest test`；前端：`npm test -- AboutGeerPage.test.tsx CoachProfileEditor.test.tsx`。先记录旧行为 RED，再实现到同一命令 GREEN；真实 MySQL 只在隔离测试库准备 READY 图片/二维码和发布数据，不对生产造数据。
- API 验证不仅单独调用规则：正确教练/CSRF 下保存并发布仅二维码、账号-only、链接-only、跨域/HTTP/普通文本，确认匿名公开结果；旧微信号内容可编辑，错误角色、他人/非 READY 媒体及版本冲突仍拒绝。社交长度边界验证 80/2048 字仍拒绝超限。
- 适用全量：后端 `mvn test`（架构/真实 MySQL/媒体含 90 秒视频修复）；前端 `npm run typecheck`、`npm test`、`npm run lint`、`npm run build`；`python3 ai-docs/check_docs.py`、`git diff --check`。部署配置未变，不重复无关恢复演练。
- 实际浏览器 390/768/1440 px 核对 QR-only、账号-only、非平台链接和长文本、弹层键盘/关闭；可用本地匿名 API 替身或隔离测试资料，不改生产主页。生产上线仅在本次明确授权后用受测 SHA，检查 CI/CD、服务器当前 SHA、HTTPS 健康和现有公开展示；用户自行保存/发布新资料。

### 兼容、成本与回退

- 保留 `coachprofile`/`media` 现有模块与端口，架构例外增加 0；无新依赖、ADR、云资源或月费配置变化。
- 账号名和链接不做真实性核验；错误/普通文本由教练维护，保存发布不再由旧规则阻止。保留 80/2048 字上限；允许范围内的长文本通过 CSS 换行并验证移动端。
- 不批量删除历史 JSON 中微信号；新保存忽略旧值，二维码引用仍通过已有媒体规则。回退旧应用时，只有二维码/账号或放开链接的新草稿可能再次触发旧发布规则；采用现有应用回退并由教练补齐旧条件，保留数据库和媒体对象。
- 原视频 revision 2 的 90 秒完整校验与有限重试不改动。revision 2 的已完成验收与生产发布证据如下保留。

## revision 2 历史：生产视频校验超时修复（已发布，验收完成）

本修订针对 [TODO-0028](../todo/0028-production-video-probe-timeout.md)。现有生产版本 `944d1a5` 完整逐帧校验用户上传的 89,123,600 字节视频需 50.024 秒，原 30 秒预算将合法 MP4 错误判为 REJECTED；本地成功不能证明小规格 EC2 也能在同一时限完成。用户于 2026-10-04 明确选择“批准修复并上线（推荐）”，批准本修订及 commit/push/既有自动部署；当前 `approved_revision: 2`。revision 1 的历史批准和执行记录保留，目标 RED/GREEN、本地/CI 回归与实际生产部署已完成；ARM64 应用探测同一素材通过，用户自行重传的新任务已达到 READY。实现、验收和已授权发布分别有实际证据，当前 RELEASED。

### 范围与文件

| 步骤 | 实际目标文件/位置 | 具体内容 | 验证与完成判定 | 状态 |
|---|---|---|---|---|
| R2-01 | `backend/src/test/java/com/geer/snowboard/v2/media/MediaContentValidatorTest.java`、`MediaLifecycleTest.java`；`frontend/src/CoachProfileEditor.test.tsx` | 批准后先写目标测试：合法慢探测、超时临时失败与有限重试、损坏输入仍拒绝、超时界面提示 | 实际行为缺失 RED；同一用例修复后 GREEN，测试配置/编译错误不算 RED | 完成 |
| R2-02 | `backend/src/main/java/com/geer/snowboard/v2/media/adapter/out/inspection/MediaContentProbe.java`、`backend/src/main/resources/application.properties` | 探测默认预算 30→90 秒，使用可注入短预算进行超时测试；允许配置的预算范围限定 1–90 秒。保留 `-count_frames` 全片校验、file-only 协议、无 shell、输出上限、公私格式/品牌/编码/尺寸/时长检查。超时 `MediaFailure` 改为临时失败，复用现有 worker 有限重试，仍清理子进程和输出线程 | 慢探测通过、预算超限不转 READY、不误判 REJECTED、重试耗尽 FAILED；原 MP4/JPG/PNG 和不合法输入回归通过 | 完成 |
| R2-03 | `frontend/src/CoachProfileEditor.tsx`、`backend/README.md` | 根据已有 `errorCode=PROBE_TIMEOUT` 展示“视频校验暂时超时，请稍后重试。”，兼容历史 REJECTED 和新 FAILED；其他真实格式拒绝保留原提示。更新时限说明 | 前端状态测试/typecheck/lint/build 通过；不展示 ffprobe、私有路径或供应商错误 | 完成 |
| R2-04 | 配对文档、索引、ticket；既有 CI/CD | 后端媒体/真实 MySQL/架构及全量回归、前端全量回归、ARM64 镜像/真实素材验证；只有用户另明确包含 commit/push/上线时才推送既有 main 自动部署。发布后核对同 SHA 的 CI/CD、健康与同素材探测 | 分别记录 IMPLEMENTED、VERIFIED、实际授权后的 RELEASED；业务上传由用户会话再次操作，Codex 读取任务结果，不索取密码 | 完成；CI/CD、同素材完整探测与实际重传 READY 均确认 |

### 保留条件、风险与回退

- 视频仍为真实 MP4、H.264/yuv420p、最多一条 AAC 音轨、100 MiB/120 秒/1080p/60 fps 内；继续完整逐帧校验，不以快速读取元数据代替损坏检查。不升级 FFmpeg 9.0.2，不添加转码、云资源或更大实例。
- worker 总预算 180 秒、租约 300 秒、验证最多 4 次及 1/5/15 分钟退避保留；前端原 120 秒轮询窗口可覆盖正常 90 秒探测。高负载或更复杂合法文件仍可能超时，有限重试后明确失败，不承诺每个满足规格的文件都在资源预算内完成。
- 单项视频任务最多占用 90 秒探测，CPU 占用时间可能增加；继续单 worker 和原容器内存上限、超时终止/临时文件清理。容量和月账单仍按 0010 实际运行观察，不把延长时限当作扩容。
- 无业务 API 形状、角色/归属/CSRF、schema、引用、素材或密钥改动；不重置已有生产失败任务，不自动修改草稿或公开主页。用户再次选择同一文件即可走新验证链路。
- 发布回退使用既有兼容上一版本流程，数据库卷和媒体对象保留。默认预算恢复旧版会重新出现本次超时拒绝，应在回退记录中说明。
- 复用原六边形端口，无架构例外/新 ADR；部署和付费授权不从本地实现批准自动推导；本次用户已同时明确批准 revision 2 实现、commit/push 与既有自动部署。

### 目标命令与验收

- 批准后目标 RED/GREEN：`./mvnw -Dtest=MediaContentValidatorTest,MediaLifecycleTest test`；前端 `npm test -- CoachProfileEditor.test.tsx`。测试将以受控短预算触发真实子进程超时和任务重试，避免单纯断言实现常量；合法慢探测与生产真实素材覆盖默认 90 秒预算。
- 回归：后端全量 `./mvnw test`（含真实 MySQL/架构）；前端 `npm run typecheck`、`npm test`、`npm run lint`、`npm run build`；`python3 -m unittest discover -s deploy/tests -p 'test_*.py'`、文档检查、`git diff --check`。
- 实際 ARM64 镜像在原限额下对同一 S3 素材调用完整内容探测器，确认成功和耗时；不只验证 `ffprobe -version`。上线仅用 GitHub 已受测 SHA，经 ECR/OIDC/SSM，健康失败遵循既有回退。
- 本次只读诊断不是实现 RED/GREEN：SSM `0ea2da67-2cf3-4d30-9b58-df89c91472cd` 发现两条 `PROBE_TIMEOUT`；`b3d2f91e-01c5-444d-9f4a-8fd310d389f8` 元数据 0.132 秒，完整计数超过 31 秒后终止；`9601d72e-b769-4bab-89bb-ec6f3064b0e3` 原程序完整计数 50.024 秒、exit 0、stderr 0、视频/音频帧数 1867/2812。均未改生产数据库记录或 S3 对象，临时诊断副本已清理。
- 首次元数据诊断因 dotenv 引号解析错误失败，修正诊断脚本后完成；该工具自身错误不作为产品缺陷或 RED。ffprobe 的完整帧计数行为参考 [官方文档](https://ffmpeg.org/ffprobe.html#Main-options)。


### revision 2 实际执行证据（2026-10-04）

- **RED**：批准后先加测试，实际运行 `mvn -Dtest=MediaContentValidatorTest,MediaLifecycleTest test`：16 项，2 个断言失败、1 个行为错误。31 秒合法探测在旧 30 秒预算收到 `PROBE_TIMEOUT`；配置短预算及 1–90 秒边界被忽略。真实 MySQL 的重试基础能力已通过。前端修正 alert 同时包含按钮文字的测试断言后，`npm test -- CoachProfileEditor.test.tsx` 4 项中仅两个超时提示失败，真实格式拒绝仍通过；测试自身断言问题不算 RED。
- **实现/GREEN**：同一后端目标命令 16/16、同一前端命令 4/4 通过。默认 90 秒；配置限定 1–90 秒；超时临时失败并终止/等待子进程退出。保留完整帧计数；现有 worker 在 VERIFYING/PENDING 下以 1/5/15 分钟重试，可恢复 READY，四次耗尽 FAILED。前端按已有错误码兼容历史 REJECTED/新 FAILED。
- **本地回归**：后端全量 `mvn test` 135/135，包含真实 MySQL、S3Mock、权限/CSRF/引用/清理/发布和两套架构检查，无新增例外。前端 `npm run typecheck`、`npm test`（54/54）、`npm run lint`、`npm run build` 均通过；部署 `python3 -m unittest discover -s deploy/tests -p 'test_*.py'` 27/27；文档检查及 `git diff --check` 通过。
- Maven 实际执行器为 `/private/tmp/geer-delivery-toolchain/apache-maven-3.9.16/bin/mvn`，`JAVA_HOME=/private/tmp/snowboard-v2-toolchain/jdk-25.0.4.1+1/Contents/Home`，参数 `-Dmaven.repo.local=/private/tmp/snowboard-v2-toolchain/m2`；MySQL 回归同时设置 `DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock` 和 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。目标 RED、GREEN、全量日志位于被忽略的 `.local/media-prod-diagnosis/`，不含生产凭据。
- **完整内容回归**：另运行 `mvn -Dtest=MediaContentValidatorTest#rejectsCorruptedFramesEvenWhenTheContainerMetadataIsValid test` 1/1 通过。受控破坏真实 MP4 的末尾帧后，metadata-only 仍 exit 0/stderr 0，而完整帧计数报错；应用判为 INVALID_CONTENT，证明保留完整检查的必要性。该追加回归未改变实现，CI 将全量执行 136 项。
- **CI 与发布**：代码提交 `1cb7dba1d48ac2c44ed93fbedea7f23ca90f5700` 已推送 main。[CI 37253710384](https://github.com/Honggeer/snowboardLessonBookingV2/actions/runs/37253710384) success，后端全量 136/136（包含追加的损坏帧回归）、前端/文档/部署配置全部通过。[Production delivery 37254052380](https://github.com/Honggeer/snowboardLessonBookingV2/actions/runs/37254052380) success，原生 ARM64 构建、ECR、匹配 bundle 与实际 SSM 发布成功。
- **ARM64 同素材应用验证**：SSM `d1dffa22-eeda-4fd8-9048-cc92bed59224` success。服务器 current SHA 确认为同一 `1cb7dba`，运行镜像为 arm64。只读取原失败记录的同一 frozen 对象，在隔离且无网络的临时容器中，以现有 768 MiB 上限、发布镜像内完整应用类和依赖调用默认 `MediaContentProbe`，继续 `-count_frames` 全片检查。结果 exit 0/stderr 0，1920×1080、65.292993 秒，耗时 **51.451 秒**；检查前后 HTTPS health UP。临时容器/素材/JAR 副本均清理，未改生产业务记录、S3 对象、主页或引用。
- **实际上传 READY**：SSM `c33a1ca9-38b1-4a76-8f92-bac334b0c4d1` 发现用户新上传 `a49f5c5b-6f84-4a2c-95ed-7dd1bf545c6c` READY、attempts 1；后续 `aec0cca9-3e63-4d6d-a0a9-d2dbdcac5f3c` success，确认声明/实际大小均 89,123,600 字节、1920×1080、65.292993 秒、错误码为空、VERIFY job DONE/attempts 1。运行 SHA 同为 `1cb7dba`，HTTPS health UP。旧超时记录仍为原状态，无任务重置或自动改草稿/公开引用。
- **状态与边界**：完成实现后记录 IMPLEMENTED；目标/回归、生产同素材完整应用探测与实际上传 READY 完成后满足 VERIFIED；明确部署授权、CI/CD success 及发布后健康/业务检查齐全，现更新 RELEASED，TODO-0028 DONE。账号与上传由用户自行操作，Codex 仅只读核对，不索取密码或会话；保存草稿/发布主页仍由用户按需要操作。

---

以下第 1–9 节保留 revision 1 历史设计、批准与执行证据；本次 revision 2 的具体范围以本节为准。

## 1. Review 摘要

关联[功能契约 0008](../features/0008-about-geer.md)、[项目契约](../PROJECT_CONTRACT.md)及 [ADR 0001](../decisions/0001-v2-baseline.md)。以下为 2026-10-02 用户已批准的 revision 1 完整实现方案。

**已确认需求和视觉**：严格以用户认可的[桌面稿](../design/0008-about-geer-desktop-concept.png)和[手机稿](../design/0008-about-geer-mobile-concept.png)为基准，实现无需登录的 `/about-geer`。保留首屏大图、一个高光视频、profile、CASI 证书、三张社交卡片及底部蓝色约课区；桌面简介/证书并排，手机纵向排列。[制图提示词](../design/0008-about-geer-prompts.md)保留来源。学员导航和登录页增加入口，教练工作区增加“个人主页”。

本 revision 已批准的约定：

1. 教练编辑资料/上传素材 → 保存草稿 → 预览 → 发布。发布成功才改变公开内容，上传失败或未发布的草稿不影响旧版。
2. 一张人物照、一张证书、一张微信二维码、一张视频封面、一个当前视频。图片 JPG/PNG，每张 **8 MiB**，宽高各不超过 4096；视频 MP4/H.264、可选 AAC 音轨，**100 MiB / 120 秒 / 1080p**，兼容横竖屏，无在线转码。
3. 本轮完成本地实现/验证，新增 S3Mock **5.2.3** 容器使用 **9090**；后端仍留给 IDEA，不启动容器后端抢占 **8080**。增加 AWS SDK for Java **2.55.10** 和 ffprobe **9.0.2**，验证真实文件及存储链路。
4. 沿用私有 S3 + CloudFront OAC，补上草稿隔离、文件版本固定、失败恢复和清理。本轮不创建 AWS 资源、部署生产或产生云服务费用；生产区域/配置/费用在部署方案中单独 review。
5. 真实简介、CASI 等级/证书、社交账号、二维码、成片及封面可在后台完成后填写。开发验收使用明确的测试素材，不自动发布示例资质或账号。

视觉和完整 revision 1 均已获批准，按 WORK-09 完成测试先行实现；本地必要门禁全部通过，当前为 VERIFIED。

## 2. 批准记录

| 日期 | 用户原话/可定位消息 | 批准 revision | 范围与条件 |
|---|---|---|---|
| 2026-10-02 | “无需登录也能看，方便分享给潜在学员” | N/A | 确定访问范围 |
| 2026-10-02 | “就一个就够，我会专门制作一个视频，后台可自行编辑吧” | N/A | 确定视频数量和维护方式 |
| 2026-10-02 | “不错，完全按照这个模版生成” | N/A | 确认两张视觉稿；当时上传限额、本地环境及完整实施方案尚未定稿 |
| 2026-10-02 | “继续？”、“继续” | N/A | 继续已告知的方案补齐工作，形成此待 review 的 revision |
| 2026-10-02 | “开始实现” | 1 | 批准上一轮提交的完整 revision 1：按两稿实现、草稿发布、媒体上限、版本/任务/清理及本地依赖；本地开发验证，8080 留给 IDEA，无云创建或生产部署 |
| 2026-10-03 | “可以的，提交推送吧” | 1（提交授权） | 授权提交并推送当前 0008 实现、登录入口视觉调整、关联修复与验证文档；不改变本地 VERIFIED 状态或生产部署范围 |
| 2026-10-04 | “批准修复并上线（推荐）”（revision 2 完整方案确认） | 2 | 批准 90 秒有界完整校验、临时超时/有限重试和界面提示、目标 RED/GREEN/回归、commit/push 与现有 CI/CD 上线；无扩容、新资源、转码或生产历史任务重置 |
| 2026-10-04 | “保留长度上限，取消其余校验（推荐）” | N/A（revision 3 业务条件） | 确认社交账号 80 字、链接 2048 字上限继续保留，取消其他社交校验；具体实施计划及本次提交/上线仍待 review |
| 2026-10-04 | “开始实现 批准”（对 revision 3 review 与实施/提交推送/上线问题的回复） | 3 | 批准微信仅二维码、社交字段独立可选及取消业务校验；保留 80/2048 字上限，兼容旧数据；目标 RED/GREEN/必要回归、commit/push 与既有流水线上线，不自动改用户草稿或发布内容 |

revision 1/2 的批准与历史执行证据保留；revision 2 超时修复的 commit/push/上线已有明确授权并完成。本次 revision 3 已批准，当前 `approved_revision: 3`。

## 3. 实现步骤与预计文件

| 步骤 | 预计文件/模块 | 具体改动与边界 | 完成条件 | 状态 |
|---|---|---|---|---|
| P-00 | 配对文档、两个索引 | 锁定两稿，补齐字段、限额、生命周期、依赖与验证方案 | 完整 revision 交 review 并记录批准 | 完成 |
| P-01 | 前端展示/编辑测试；后端 profile/media 目标测试 | 按第 5 节分批先写测试，再实现对应行为 | 每批有行为缺失 RED 和同一测试 GREEN | 完成 |
| P-02 | 新 `coachprofile`、`media` 六边形模块；`db/migration/V9__coach_profile_media.sql` | 草稿/公开快照、媒体、任务、引用、纯 Java 端口；权限、版本和幂等 | 真实 MySQL 并发/原子发布测试通过 | 完成 |
| P-03 | `backend/pom.xml`、媒体 adapter、bootstrap 配置、`backend/Dockerfile`、`deploy/compose.yaml` 与本地配置示例 | 精确依赖、S3Mock、固定版本、真实探测、任务清理和 CloudFront 签名适配器 | 本地直传/检验/播放/恢复/清理通过 | 完成 |
| P-04 | `frontend/src/App.tsx`、`BookingHome.tsx`；新 `AboutGeerPage.tsx`、`CoachProfileEditor.tsx`、API/types/CSS | 独立公开页、按稿响应式、编辑/上传进度/预览/发布 | 390/768/1440 px 及实际媒体交互通过 | 完成 |
| P-05 | `SecurityConfig.java`、`ArchitectureRules.java`、`ArchitectureBaseline.java`、README | 精确放行公开 GET；新模块纳入架构清单；记录 IDEA 与媒体配置 | 架构、安全、路由、既有功能回归通过 | 完成 |
| P-06 | 功能/计划/索引及配置说明 | 汇总测试先行、回归、截图、真实内容与云验证限制 | 按实际证据标注交付状态 | 完成 |

- [x] 确定需求和视觉，完成具体实施方案。
- [x] P-00：用户批准 revision 1（2026-10-02，“开始实现”）。
- [x] P-01～05：逐批 RED → GREEN，适用全量回归全部通过。
- [x] P-06：同步文档、验证证据与状态。

## 4. 数据、API、架构与兼容影响

### 4.1 前端与模块

- `/about-geer` 独立加载，不依赖 `BookingHome` 的课程/预约 Promise.all。沿用现有 React/Vite、CSS 和导航机制，不增加路由/组件库或升级前端依赖；保留预约邮件 hash 深链接。
- 用真实 DOM/CSS 还原两稿的色彩、层级、卡片、间距和响应式，不能把 PNG 充当页面；补齐加载/空白/错误、键盘焦点和弹层关闭。预览复用展示组件并标注草稿。未配置区块按功能文档隐藏，不编造真实资质。
- 纯文本字段、HTTPS 社交链接、外链隔离；微信复制失败保留可选择文字。原生 video 使用 controls、playsInline、preload="none"、poster、object-fit: contain；访客主动点击后才加载并播放。媒体错误可重新获取展示数据并手动重试。
- 访客约课进入已有登录/注册再回约课页；学员直接约课；教练回工作区。编辑页提供上传/校验进度、错误原因、草稿状态和重复提交防护；登出清除私人缓存，保存失败保留输入。
- `coachprofile` 拥有主页和发布；`media` 拥有媒体生命周期。前者经自身出站端口和 `adapter.out.module` 调用 `media.application.port.in`，无反向依赖或跨表访问。SDK、ImageIO、外部进程均在出站适配器，domain/port 纯 Java。
- `coachprofile` 加入两份架构测试模块清单，预期新增架构例外 **0**；不改变 ADR 0001。

### 4.2 表归属、事务和并发

- 追加 V9，不改 V1–V8。coachprofile 拥有 `coach_profile_page`、`coach_profile_publish_request`；media 拥有 `media_asset`、`media_job`、`media_reference`、`media_quota`。文件二进制不进入 MySQL。
- 页面为单例行，分别保存草稿/已发布 JSON 快照及版本，JSON schemaVersion 首版 1。快照保存纯文本和 media ID，不保存短期 URL；API DTO、用例结果、数据库 record 分开。
- 媒体保存 owner、purpose、声明/实际大小、staging key、固定 source versionId、frozen key、探测结果、状态、期限、请求键/指纹。任务保存状态、attempt、nextRunAt、leaseUntil、claimToken 和安全错误码；引用保存使用方标识、DRAFT/PUBLISHED 槽位及 asset ID。
- 保存草稿携带 expectedVersion。READ COMMITTED 短事务先锁页面，再按 ID 升序锁相关媒体，通过 media 公开用例校验归属、用途、READY 且未删除，更新 DRAFT 引用和草稿版本。版本过旧 409，前端保留输入并提示重新载入。
- 发布携带 draftVersion 和 Idempotency-Key，按相同锁序验证并复制整个草稿为公开快照、替换 PUBLISHED 引用；模块用例加入同一 MySQL 事务。失败全部回滚，旧公开内容继续有效，事务中不做 S3/网络/ffprobe 调用。
- 发布请求唯一 `(actorId, idempotencyKey)`，指纹包含动作和规范化请求；同键同请求重放，同键不同请求 409。上传申请同样去重，重放过期申请返回原 ID/过期状态，不暗中延长凭证。完成按 asset ID 幂等；重复保存旧版本返回 409。死锁/锁超时回滚并返回可重试错误，不自动重复外部 IO。
- 清理只锁 media 行、检查引用并先标 DELETING，再释放事务删除；complete 建任务与此状态转换互斥，保存/发布拒绝 DELETING。清理不回读 profile，避免锁顺序循环。

### 4.3 上传、固定版本和真实校验

1. 教练授权后服务端生成随机 staging key，预留声明大小，签发 **10 分钟** PUT URL（签署 Content-Type）。最多 **1 个未终结上传/验证任务**、每小时 **20 次新申请**，保留资产与预留大小的逻辑配额 **1 GiB**，在 MySQL 中原子检查。页面显示限额及下一次可申请时间。
2. staging bucket 必须启用 Versioning。完成接口在事务外 HEAD 确认存在、大小与声明一致，并取得非空且不为字符串 `null` 的 versionId；短事务 CAS 将 UPLOADING → VERIFYING，并同时写入固定 versionId 和任务。并发完成仅第一次成功固定版本；后续返回既有状态。不接收客户端 object key/versionId，不允许失败时退回读取最新版。
3. worker 只能按数据库固定的源版本复制到后端专用 frozen bucket/key，然后验证冻结文件。重试只允许相同源版本、相同目标。浏览器 PUT URL 无权写 frozen；完成后再覆盖 staging 不会改变待验证/公开内容。桶版本配置不满足时失败关闭。
4. 下载按真实字节计数并有上限，使用随机临时路径、任务结束清理。图像用 ImageIO 检查签名、实际格式和可解码性，先读尺寸再解码；拒绝 SVG/HTML、伪装、损坏和超尺寸图。
5. ffprobe 通过固定参数 ProcessBuilder 调用，只允许本地文件协议，不经过 shell，不探测网络 URL；30 秒超时，stdout/stderr 各最多 1 MiB。视频必须实际 MP4、恰好一条 H.264/yuv420p 视频流、至多一条 AAC 音轨、无其他流，正时长 ≤120 秒、长边 ≤1920/短边 ≤1080、≤60 fps，拒绝无法探测或损坏输入。首版不转码。
6. 全部检查通过才 READY；失败 REJECTED/FAILED，不替换旧草稿或旧发布引用。READY 不等于已发布。

预签名 PUT 可以重复使用，版本桶会产生额外历史版本；上述逻辑配额和上传后校验**不是 S3 容量、写入字节或账单硬限制**。已开始的上传可能在签名到期后才完成，不能仅凭 URL 到期假定对象不会再出现。

### 4.4 任务、清理和草稿访问

- 一个专用 worker，一次领 1 项，READ COMMITTED + `FOR UPDATE SKIP LOCKED` 原子领取，5 分钟租约和递增 claimToken。SDK 单请求 30 秒、单任务外部操作总超时 3 分钟，所有结果写回检查 token；旧 worker 不得提交状态，过期任务可恢复。
- 临时故障在首次执行后最多重试 3 次，退避 1/5/15 分钟，然后 FAILED；无效媒体直接 REJECTED。有界任务不占满 HTTP 线程，日志不记录签名、密钥或供应商堆栈。
- 每小时清理，每批 10 个 asset，每次版本列表最多 100 项并保存分页进度。UPLOADING 超过 24 小时过期；终结任务的 staging 在授权到期满 24 小时后删除全部版本/delete markers，按该上传完整 key 精确匹配，保留未终结任务固定的源版本。READY 无 DRAFT/PUBLISHED 引用且最后解除引用满 7 天才清理 frozen；REJECTED/FAILED 无引用对象满 24 小时可清理。
- 删除要求无有效租约且终结后至少 10 分钟，先持久化 DELETING，再逐项删除。删除失败有限重试最多 8 次、指数退避上限 24 小时，最终失败留可观测记录，不提前释放逻辑配额；教练重新上传可使用新请求键。
- **DB fencing 不能撤销迟到的 S3 写入**。资产删除后保留带完整 key 的 tombstone，定时分页回扫 staging/frozen，回收迟到 PUT/CopyObject 的对象及其所有版本；不能把一次 DELETE 成功当作永久不存在。生产桶生命周期作为额外兜底，不删除仍被引用的 frozen。测试必须覆盖清理后晚到对象再次被回收。
- 生产两个桶均私有：staging 不接 CDN，frozen 对外分发仅经 CloudFront OAC，后台 IAM Role 按最小权限读取验证、写入及清理；整个 frozen 分发路径还要求 viewer 签名。公开 API 只为当前 PUBLISHED 引用签发 **15 分钟** URL；教练可获取本人草稿/READY 上传的预览 URL。OAC 单独不能保护草稿。
- 展示响应 Cache-Control: no-store；URL 过期可重新取数据。替换内容不立即撤销已发签名，旧 URL 到期前仍可用；7 天保留覆盖有效期。CloudFront 私钥以秘密文件注入，keyPairId 配置；S3 使用默认凭证链/EC2 IAM Role，IAM Role 不代替 viewer 签名私钥。

### 4.5 精确依赖与本地环境

| 依赖 | 选择与理由 | 接入与限制 |
|---|---|---|
| AWS SDK for Java | BOM **2.55.10**；s3、cloudfront、url-connection-client | SDK 只在 adapter；小型同步客户端，不引入 Spring Cloud AWS；Java 25 本地构建与镜像已验证 |
| Adobe S3Mock | `adobe/s3mock:5.2.3`，arm64/amd64 | 绑定 `127.0.0.1:9090:9090`，命名卷 `/s3mockroot`，本地假凭据；两个桶，staging 开启 versioning |
| ffprobe | FFmpeg **9.0.2** 的 ffprobe | IDEA 配置可执行文件路径；本地安装该版本。容器构建固定版本源码生成本地文件/MP4 探测二进制，记录来源、校验和/许可，非 root 运行 |

本地显式 local 模式/path-style；区分服务端 endpoint 与浏览器 endpoint，IDEA 均为 `http://localhost:9090`，容器内服务端为 `http://s3mock:9090`。生产缺区域/桶/CDN/签名密钥时失败关闭，不能自动回落 mock。启动说明为 `docker compose -f deploy/compose.yaml up -d db mailpit s3mock`，不启动 backend。

S3Mock 支持版本、预签名请求形状和 Range GET，但**不验证签名、到期、HTTP 动词**，CORS 宽松且不具备完整桶 CORS API。本地预览/公开媒体使用 mock 预签名 GET；只能证明应用授权、生命周期与播放，不能证明 AWS IAM、真实 URL 到期拒绝、CORS、OAC 或 viewer 签名配置有效。CloudFront 签名代码已用临时 RSA 密钥验证路径、到期时间及签名；签名无需调用 S3。真实 AWS 验收留给部署阶段。

2026-10-02 官方资料核查：[AWS SDK 2.55.10](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.55.10)、[SDK HTTP 客户端](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration.html)、[S3Mock 5.2.3](https://github.com/adobe/S3Mock/blob/5.2.3/README.md)、[FFmpeg 下载](https://ffmpeg.org/download.html)、[预签名上传](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html)、[CopyObject/versionId](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CopyObject.html)、[OAC](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html)、[Java CloudFront 签名](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_cloudfront_code_examples.html)。依赖组合、官方源码校验、实际 ffprobe 与镜像已通过本地验证，证据见第 8 节。

## 5. 验收与验证计划

已按以下入口分批先测试再实现。缺类/编译错误、缺工具或 Docker 不通不计有效 RED；实际行为 RED 与同一测试 GREEN 见第 8 节。

| 验收 | 测试和预期 RED 原因 | GREEN 与回归覆盖 |
|---|---|---|
| AC-01/07 | `AboutGeerPage.test.tsx`、`CoachProfileApiTest`：公开页面/接口缺失 | 匿名直达/刷新、学员入口、登录续接、预约故障隔离、邮件 hash 导航 |
| AC-02/03 | 展示测试：证书弹层、联系和播放器行为缺失 | 纯文本/安全外链、复制降级、焦点、封面/主动播放、错误重试/原比例、浏览器实际播放和 Range |
| AC-04/05 | `CoachProfileApiTest`、`CoachProfileEditor.test.tsx`：草稿/发布行为缺失 | 匿名 401、学员 403、CSRF、伪造 owner、草稿不泄露、幂等/回滚/失效会话 |
| AC-06 | `MediaUploadApiTest`、`MediaLifecycleTest`：上传/验证/配额行为缺失 | 同键重放/冲突、并发限额、过期上传、发布与清理竞争、领取租约/fencing、重启恢复及重试终止 |
| AC-06 | `S3MediaStorageTest`、`MediaContentValidatorTest`：新增可编译端口后以行为断言先 RED | 实际 PUT、版本 HEAD/复制/Range；固定 A 后写 B，重试和旧 worker 仍只能验证/发布 A；清理所有版本、晚到对象回收；合法/伪装/损坏/超限/多流素材 |

计划命令与人工验收：

- backend/：`env DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -q -Dtest=CoachProfileApiTest,MediaUploadApiTest,MediaLifecycleTest,S3MediaStorageTest,MediaContentValidatorTest test`；实现后同一目标及相同环境下 `./mvnw -q test`。真实 MySQL 8.4/S3Mock 5.2.3 用 Testcontainers 隔离，不占 8080。
- frontend/：`npm test -- --run src/AboutGeerPage.test.tsx src/CoachProfileEditor.test.tsx`，随后 `npm test -- --run`、`npm run typecheck`、`npm run lint`、`npm run build`。
- 根目录：`python3 -m unittest deploy.test_compose_mail -v`、`python3 ai-docs/test_check_docs.py`、`python3 ai-docs/check_docs.py`、`git diff --check`。架构检查在后端全量中，不允许新模块漏扫。
- 浏览器 390/768/1440 px 对比两稿，检查直达/刷新、上传/验证/保存/预览/发布；视频点击前无视频下载，点击后播放、拖动、暂停/全屏；证书/微信交互和错误恢复。明确测试素材，保存截图/网络证据。
- 验证前后端镜像构建、ffprobe 非 root 运行、NGINX `/about-geer` 回退；需要联调应用时使用随机测试端口，8080 留给 IDEA。

只有全部本地功能门禁通过，才记录 VERIFIED（本地、测试素材）；真实资料、AWS 配置和生产发布单列，不得把未提供的真实账号/证书/成片写成已验收。本轮实现和全部本地必要门禁已完成；状态为 VERIFIED，真实资料与生产发布尚未验收。

## 6. 风险、成本、部署与恢复

- 本轮无新增云费用，本地使用 Docker 磁盘/CPU。单 worker、单文件最多 100 MiB 临时空间、图像先尺寸后解码/有界资源；4096² 解码仍占内存，生产 EC2 容量另行实测。预签名重复 PUT 和版本不能靠逻辑配额限制实际云账单。
- 生产需 review 区域、两个私有桶/versioning/lifecycle、S3 IAM/CORS、CloudFront OAC/可信 key group、密钥保管及存储/请求/流量费用。50 MiB 视频完整播放 1,000 次约 48.8 GiB，仅为容量例子；没有报价或免费承诺，不表示已满足 30 CAD/月。
- 发布前教练确认可公开字段；已下载资料无法远程收回。迁移不填假证书/账号、不自动发布。用户真实内容可后补，不阻塞能力开发，阻塞对应真实内容验收。
- 应用回退保留 V9 表和对象，停止新上传/发布/worker，旧应用忽略新表；不执行现有数据批量清理。内容修正通过重新编辑/发布，本轮无历史版本恢复 UI。
- 命名卷不是备份；生产发布前须 review 元数据/对象备份和引用一致性的恢复演练。本轮不执行生产迁移、删除或恢复。
- 核心存储方案、业务上限、访问范围或成本发生实质变化须修订 review；相同契约内的局部实现选择记录理由后自主推进。

## 7. 实际执行与偏差

2026-10-02 用户要求“关于GEER在登录页面也太小了……要让他们一眼看见，并且知道能点进去”。本次属于 revision 1 已批准登录入口的视觉修正，关联 [TODO-0016](../todo/0016-login-about-geer-visibility.md)：初版将入口移至表单上方并使用蓝色卡片。2026-10-03 用户随后明确“不用花里胡哨的，就一行字，然后一个链接标志就行，不用写那么多，但是醒目一些”；最终 `App.tsx` 仅显示「关于 GEER ↗」，`style.css` 使用蓝色加粗字、下划线和键盘焦点反馈，桌面 24px / 手机 22px，删除背景、边框、圆形箭头、标签和说明。保留现有 `navigate('about')`，不改认证或访问约定。纯展示调整按契约验证规则不新增样式单元测试；运行既有前端回归/构建，并用 Chrome 检查首屏可见、整块点击和键盘访问。

| 日期 | 步骤/实际文件 | 实际结果 | 偏差、理由与是否需要重新 review |
|---|---|---|---|
| 2026-10-02 | 只读分析、需求澄清、0008 文档与索引 | 形成首版页面/管理建议 | DRAFT，无功能实现批准 |
| 2026-10-02 | 两张概念图、提示词与链接 | 桌面/手机稿已获用户确认 | 视觉已确认，媒体方案当时未齐 |
| 2026-10-02 | P-00、配对文档与索引 | 完成字段/上限、版本固定、原子发布、恢复清理、依赖及验收；独立只读复核补上迟到写入回扫 | AWAITING_REVIEW；未写功能代码/测试，未安装依赖/创建云资源 |


| 2026-10-02 | P-01/02：`coachprofile/`、`media/`、V9 与目标测试 | 先运行缺失行为 RED，再实现草稿/公开快照、角色/归属、并发限额、版本固定、持久任务和原子发布 | 按批准范围；保留 V1–V8；没有数据库记录跨层泄露 |
| 2026-10-02 | P-03：S3/CloudFront/内容探测适配器、配置、Compose、探测脚本、Dockerfile、CI | SDK 2.55.10、S3Mock 5.2.3、实际 ffprobe 9.0.2；冻结 A、Range、全版本清理和晚到对象回扫通过；临时 RSA 密钥验证签名 | 固定源码 SHA-256 与 LGPL 许可随脚本/镜像记录；采用项目本地编译以避免全局 Homebrew 变更，无新云资源 |
| 2026-10-02 | P-04：`AboutGeerPage`、`CoachProfileEditor`、API/types/CSS、导航、原滑行照副本 | 实际 DOM/CSS 按两稿还原；五类素材直传/校验、保存/预览/发布；390/768/1440 px 截图及播放交互通过 | 修复 768 px 视频卡宽度溢出与手机构图；真实证书/账号/影片未填入用户数据库 |
| 2026-10-02 | P-05：安全、架构清单、两项当前 Flyway 版本断言、README、[TODO-0013](../todo/0013-root-readme-feature-status-stale.md) | 精确匿名 GET；全部新模块进入架构扫描；新增例外 0；文档入口和启动说明同步 | `MediaConfig` 放媒体出站配置 adapter，消除 bootstrap/media 模块循环；V9 后断言更新为 9，旧迁移不变 |
| 2026-10-02 | P-04/05：[TODO-0014](../todo/0014-anonymous-session-bootstrap-race.md)、[TODO-0015](../todo/0015-media-poller-test-lifecycle.md) | 首次 CSRF 会话建立后再查询账号，共享初始化请求；临时库测试关闭媒体自动轮询，显式 worker 测试保持运行 | 均在已批准认证续接/新任务回归范围；目标行为先 RED 后 GREEN，生产本地自动轮询保留 |
| 2026-10-02 | P-06：配对文档/索引、验收截图 | 完成实现时记录 IMPLEMENTED；最终必要门禁通过后同步 VERIFIED | 无生产部署、commit 或 push |

## 8. 实际验证证据

| 日期 | 环境/目录 | 实际命令/步骤 | 实际结果 | 限制/失败与处理 |
|---|---|---|---|---|
| 2026-10-02 | 仓库和官方资料 | 读取契约/导航/认证/架构清单及 AWS/MDN/S3Mock/FFmpeg 文档 | 确认接入位置、依赖和能力缺口 | 只读方案依据，无功能 RED/GREEN/云验证 |
| 2026-10-02 | 根目录，初稿阶段 | `python3 ai-docs/check_docs.py`、`git diff --check` | 9 组配对、索引/链接通过，diff 无空白错误 | 仅初稿文档验证，定稿后再次运行 |
| 2026-10-02 | 内置 imagegen、目视检查 | 生成并检查两稿，PNG 存入 `ai-docs/design/` | 区块完整、资质/账号明确占位、提示词留存 | 不代表浏览器/交互验证 |
| 2026-10-02 | 根目录，revision 1 定稿 | `python3 ai-docs/check_docs.py`、`git diff --check`；检查 Git 状态 | 9 组配对、12 个 ticket、链接/索引/状态/review 门禁通过；diff 无空白错误；工作区仅 0008 文档、索引和设计稿改动 | 仅文档门禁；尚无功能实现或 RED/GREEN |


2026-10-03 登录入口第一版卡片验证（随后按用户意见简化）：frontend/ `npm test`、`npm run lint`、`npm run build` 全部通过（36/36；build 包含 TypeScript 检查）。Chrome 使用用户现有 Vite 5173，以匿名新会话检查 320/390/768/1024/1440 px：卡片均完整位于首屏、宽度至少 276 px、高度至少 93 px，无横向溢出；鼠标点击卡片留白和 Tab/Enter 均进入 `/about-geer`，返回后邮箱/密码输入正常，未提交登录。第一版结果保留作执行记录，截图已更新为最终简洁版。纯展示调整未新增单元测试，不将既有测试通过声称为新行为 RED/GREEN；相关 [TODO-0016](../todo/0016-login-about-geer-visibility.md) 完成。

2026-10-03 最终简洁版验证：`npm run lint`、`npm run build`（含类型检查）通过。原 Vite 5173 当时未运行，使用临时 Vite 5174 与匿名 API 测试替身核验实际 DOM；Chrome 在 320/390/768/1024/1440 px 下链接始终一行、首屏可见，无横向溢出，点击和 Tab/Enter 跳转正常，登录输入可用。点击区域高度 44.5px，最终截图已覆盖上述桌面/手机文件。纯样式与文案调整未新增测试；临时预览服务随后停止。此外恢复索引中的 IMPLEMENTING/IMPLEMENTED 定义标签，见 [TODO-0017](../todo/0017-plan-status-definitions-duplicated.md)。

2026-10-03 用户进一步要求“放在右上角比较好，字体大小14px,不要太加粗”。`App.tsx` 将单行入口置于登录页根布局，`style.css` 在页面右上角定位，当时桌面/手机均使用 14px、font-weight 400；保留文字、链接标志、下划线和键盘焦点。`npm run lint`、`npm run build`（含类型检查）通过；Chrome 在用户现有 Vite 5173 与匿名 API 测试替身下核验五种宽度：入口 y=20px（桌面）/4px（手机/平板）、右侧页边距内，计算字体 14px / 400，单行、无溢出，点击和 Tab/Enter 导航通过。该字号版本随后按下述用户反馈调整，前述卡片和大字版本仅保留历史执行记录。

2026-10-03 用户反馈“有点太小了，font weight:600 斜体字”。字号调整为 16px、font-weight 600、font-style italic；入口局部允许字体合成斜体，确保缺少原生斜体的中文字体也显示倾斜。保持右上角单行入口，链接标志随字号缩放。frontend/ `npm run lint`、`npm run build`（含类型检查）通过；`node /tmp/geer-0016-italic-browser.mjs` 使用现有 Vite 5173、匿名 API 测试替身与 Chrome，在 320/390/768/1024/1440 px 确认计算样式为 16px / 600 / italic、点击区域高 44px、首屏单行、右上角位置、无横向溢出，点击和 Tab/Enter 进入公开页；无页面脚本错误，未提交登录。桌面/手机截图更新，手机截图目视确认中文与 GEER 均为斜体。当前展示以此样式为准，纯样式修正未新增单元测试。

### 测试先行：实际 RED → GREEN

后端命令均在 `backend/`；MySQL/S3Mock 测试使用：

```sh
export DOCKER_HOST=unix:///Users/geerhong/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

| 目标/实际命令 | 有效 RED 与同一测试 GREEN | 后续覆盖 |
|---|---|---|
| `./mvnw -q -Dtest=CoachProfileApiTest,MediaUploadApiTest test` | 3 项缺失行为失败：公开 GET 被拒绝、草稿/上传缺接口；实现后同组 3 项通过 | 公开空状态、草稿隔离、版本、401/403、CSRF、类型和大小 |
| frontend/ `npm test -- --run src/AboutGeerPage.test.tsx src/CoachProfileEditor.test.tsx` | 3 项因缺页面/导航/编辑流程失败；同一用例实现后通过 | 公开错误重试、预约故障隔离、证书/复制、保存/预览/发布 |
| `./mvnw -q -Dtest=MediaLifecycleTest,MediaContentValidatorTest test` | 首批 5 项因 worker 未处理、尺寸返回 0、接受非法内容等失败；实现后同组通过 | 生命周期现为 8 项，真实 MySQL 独立事务并发；发布故障注入证明公开快照和引用一同回滚 |
| `./mvnw -q -Dtest=S3MediaStorageTest test` | S3Mock 正常启动，未实现的存储端口返回不可用；实现后通过 | 真实 PUT/HEAD/versionId、冻结 A 后覆盖 B、Range 206、全部版本与迟到对象清理 |
| `./mvnw -q -Dtest=MediaContentValidatorTest test` | 追加有效 H.264 与伪装 QuickTime 断言，原行为错误接受 `qt` major brand；限定实际 MP4 brand 后同组 3 项通过 | 固定测试蓝色视频，不使用个人成片；探测扫描本地帧，输出/时间有界 |
| frontend/ `npm test -- --run src/AboutGeerPage.test.tsx` | 新增首次访问顺序断言，账号查询发生在 CSRF 完成前而失败；修复后通过 | 首次登录 Chrome 全流程及既有认证/邮件导航回归 |
| `./mvnw -q -Dtest=MediaLifecycleTest#safetyWindowDoesNotRecordObjectsAsAlreadyCleaned test` | 清理保护期未删除却记录 `stage_cleaned_at`，断言失败；区分跳过后同一用例通过 | 跳过只安排后续扫描，不释放配额、不声称完成删除 |
| `./mvnw -q -Dtest=CloudFrontSigningTest test`，禁用环境 AWS 凭据/metadata | 本地签名错误依赖 S3 初始化而返回不可用；移除无关 S3 IO 后同一用例通过 | 临时 RSA 密钥验证冻结路径、15 分钟到期与完整签名；无真实 AWS 调用 |
| `./mvnw -q -Dtest=LocalApplicationStartupTest#temporaryDatabaseContextDoesNotStartAutomaticMediaPolling test` | 本地测试上下文仍有自动 MediaPoller，断言失败；显式关闭测试配置后同一用例通过 | `LocalApplicationStartupTest,MediaLifecycleTest` 合计 12 项 GREEN；本地应用自动媒体处理保持开启 |

日志保存在本机 `/tmp/geer-0008-*.log`，关键结果在上表；不是生产验证。初期重复 Maven 构建造成的 target 竞争、首次镜像下载网络失败、一次 Chrome 启动超时和测试触发器权限不足均属环境/测试问题，**不计 RED**：Maven 改为串行，镜像与浏览器重试通过；故障注入权限只在隔离测试 MySQL 容器开启，不改用户/生产数据库。

### 浏览器、构建与当前回归证据

| 环境/实际命令或步骤 | 实际结果 | 限制 |
|---|---|---|
| Java 25 + MySQL 8.4/S3Mock 5.2.3，后端最终 `./mvnw -q test` | 33 套件、118/118 测试通过，0 失败/错误/跳过；含架构和既有身份/预约/邮件回归；最终日志无 media-poller 条目 | 本地验证；六类架构例外仍为 0，未运行生产任务 |
| frontend/ `npm test`、`npm run typecheck`、`npm run lint`、`npm run build` | 36/36 测试通过；类型、lint、生产构建全部通过 | 不安装新前端依赖或变更 lockfile |
| 独立临时 MySQL、Java 19089、Vite 5174、真实 Chrome；登录教练→填写 13 字段→实际直传 5 种素材→校验→保存→预览→发布→新匿名页面 | 全流程通过；草稿未发布前匿名只显示空状态；发布后匿名可看；无页面 JS 错误 | 使用明确测试资料/示例证书说明与蓝色测试片段，未修改用户账号/预约/主页数据 |
| Chrome 1440/768/390 px；视频点击前网络无媒体请求，主动播放后暂停/静音/拖动/全屏；证书 Escape、微信二维码/复制 | 三种宽度无横向溢出，媒体请求只在点击后发生，实际 MP4 时长 1 秒且全部交互通过 | 浏览器网络/播放验证是本地 S3Mock；真实影片与其他设备生产播放后续验收 |
| `docker-compose -f deploy/compose.yaml build backend frontend`；backend image 内 `id -u`、`ffprobe -version` | 两个最终镜像构建通过；UID 100，ffprobe 9.0.2 | 只构建；未启动容器后端占用 8080 |
| 随机本机端口的临时前端镜像，GET `/about-geer` 与 `/about-geer?share=1` | 均 HTTP 200，返回与首页相同 SPA index | 测试容器随后删除；AWS/HTTPS 未部署 |
| 根目录 `python3 -m unittest deploy.test_compose_mail -v`、`python3 ai-docs/test_check_docs.py` | 分别 2/2、3/3 通过 | 测试配置与文档检查器回归 |
| 根目录 `python3 ai-docs/check_docs.py`、`git diff --check` | 9 组配对、15 个 ticket、索引/状态/review/链接通过；无空白错误 | 只反映本地工作区，未提交或部署 |

实际页面截图：[桌面 1440 px](../design/0008-implemented-1440.png)、[平板 768 px](../design/0008-implemented-768.png)、[手机 390 px](../design/0008-implemented-390.png)。这些是测试资料的实际 DOM 页面，不是真实资质或账号证明。

## 9. 完成状态与后续

当前 **VERIFIED（本地，测试素材），revision 1 已批准**。P-00～06 完成，后端 118/118、前端 36/36 和构建/架构/浏览器/镜像/路由/文档门禁通过。2026-10-03 用户已授权提交并推送当前实现与登录入口视觉调整；本提交包含实现、配置、关联修复、测试和验证文档。未部署生产，状态不是 RELEASED。真实简介、CASI 证书、账号、二维码和高光成片由用户后台填写，真实内容与 AWS 发布另验。

8080 仍由用户 IDEA 进程使用，未停止或替换。本次独立联调用的 Java 19089、Vite 5174、临时 MySQL 和专用测试桶已清理；用户 MySQL/Mailpit/S3Mock 保留。使用新功能前，IDEA 重新加载 Maven 并重启后端（加载 V9 和媒体模块），随后在现有 Vite 页面进入教练“个人主页”。固定版本 ffprobe 已安装在本机 `backend/.local/bin/ffprobe`；本地启动说明见 backend/README。

### 2026-10-03 真实长视频播放只读诊断

用户报告“每到一分钟就会卡一下”，补充“快满一分钟的时候，是5173”，关联 [TODO-0022](../todo/0022-local-highlight-video-stalls.md)。本轮目标为解释原因，仅读取已发布内容和现有服务、在独立 Chrome 播放，不修改功能、存储配置、素材或数据库；没有新增付费资源、重启 IDEA/S3Mock 或执行 Git commit/push。

| 检查与实际命令/方式 | 结果 | 限制 |
|---|---|---|
| 源码 `AboutGeerPage.tsx`、`MediaService.link` 及匿名 GET `/api/coach-profile` | 点击后挂载原生 video，主动播放；没有每分钟刷新或重置播放器的任务。签名约 899/900 秒，当前视频元数据 65.292993 秒、89,123,600 字节、1080p H.264/AAC | 用户口述约 63 秒，以当前公开文件为此次诊断对象 |
| Python urllib 读取 HEAD、`Range: bytes=0-65535`、最后 8 MiB 及完整文件 | HEAD 200，正确 Content-Length/video/mp4；两个 Range 为正确 206，分别约 0.007/0.062 秒；完整 89,123,600 字节下载约 0.268 秒 | 快速下载正常不代表持续缓慢消费的浏览器连接正常 |
| `backend/.local/bin/ffprobe -v error -show_entries format=duration,size,bit_rate:stream=index,codec_name,codec_type,width,height,avg_frame_rate,bit_rate,duration,start_time -of json /tmp/geer-0022-current-video.mp4`；解析 MP4 顶层 box、视频 packet 时间戳 | 平均约 10.92 Mbps；moov 在文件末尾；视频 PTS/DTS 没有超过 150ms 的间断 | 未把末尾索引或码率本身当作此次卡顿的确定原因 |
| `node /tmp/geer-0022-video-browser.mjs`：实际 5173/about-geer、当前公开内容、系统 Chrome headless、主动点击、原速连续播放，记录事件/缓冲/Range/网络错误 | 60.220114 秒出现 waiting/stalled；约 32 秒后尾段重请求，网络有 ERR_CONTENT_LENGTH_MISMATCH；随后硬件解码 code 3 / VTDecompressionOutputCallback，未正常结束。无页面 JS 错误 | 后续解码错误发生在异常传输后，不能单独据此断言原片损坏 |
| `docker logs --since 12m --tail 160 snowboard-v2-s3mock-1` | 同次视频请求约 30 秒后记录 AsyncRequestTimeoutException / Response already committed；也有此前相同告警 | 与异步响应中断吻合；未调整超时来验证修复 |
| `backend/.local/bin/ffprobe -v warning -select_streams v:0 -count_frames -show_entries stream=nb_read_frames,nb_frames -of json /tmp/geer-0022-current-video.mp4` | 软件读取全部 1867/1867 帧；只有非核心 UDTA 解析警告 | 不能代替所有浏览器解码验收。临时 ffmpeg 为旧测试片段生成器、未启用 MOV 输入，初次解码尝试的环境失败不算文件损坏证据 |
| `node /tmp/geer-0022-video-file-control.mjs`：同一 Chrome 读取完整下载文件，从 54 秒播放到结尾 | 正常到达 65.292993 秒 ended；仅开始 seek 时 waiting，播放后无停顿/解码错误；最后 totalVideoFrames=372，dropped/corrupted=0 | 对照覆盖一分钟与尾段，没有声称从头完整播放或真实 AWS 验收 |

临时诊断结果位于 `/tmp/geer-0022-video-browser-result.json` 和 `/tmp/geer-0022-video-file-control-result.json`，不记录完整签名 URL；上述表格保存实际结果，临时视频副本在诊断后删除。曾尝试浏览器拦截 Range 对照，因动态签名匹配及关闭的上下文没有形成有效证据，未将其算为通过；有效对照采用本地完整文件。

结论：已复现本地问题，证据指向 **S3Mock 异步下载超时导致响应截断**；Vite 不代理此次 localhost:9090 视频流。既有短测试片段（1 秒）的 VERIFIED 证据保留，当前约 65 秒真实素材的连续播放仍未通过，TODO-0022 OPEN。

官方核查：[S3Mock 5.2.3 GET/Range 源码](https://github.com/adobe/S3Mock/blob/5.2.3/server/src/main/kotlin/com/adobe/testing/s3mock/s3/controller/ObjectController.kt)使用 StreamingResponseBody；[该版本默认配置](https://github.com/adobe/S3Mock/blob/5.2.3/server/src/main/resources/application.properties)未显式设置异步超时；[Spring Boot 配置说明](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)的 `spring.mvc.async.request-timeout` 可控制异步请求超时。后续修复候选为本地 S3Mock 配置有限的较长超时（例如 300 秒），验证同一页面从头到 ended、无异常日志/响应截断/解码错误，并回归图片和上传；该候选尚未执行或验证，不保证真实 AWS 结果。若调整媒体预处理或引入自动转码，需另经计划 review。

### 2026-10-04 提交授权

用户明确要求“提交推送”，授权提交当前工作区及上述诊断文档；提交前统一检查见 [0009 配对计划](0009-course-selection-visuals.md)。TODO-0022 仍为 OPEN，本次不修改视频传输配置或宣称卡顿已修复，不改变本地 VERIFIED 与生产未发布的边界。
