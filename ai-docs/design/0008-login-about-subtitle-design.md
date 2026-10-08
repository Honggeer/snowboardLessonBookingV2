# 0008 — 登录标题下方的「关于 GEER」入口：手机设计草图

- 日期：2026-10-07
- 状态：VERIFIED（本地实际页面）；用户“直接开始实现然后推送提交”并要求“继续”，最新实现/验证以配对计划当前维护节为准，设计历史保留。
- 用户原话：“我还是觉得有点奇怪，要不在登陆界面表单上方 欢迎回来标题下面，把那个继续你的滑雪旅程换成：第一次访问？关于GEER【箭头】，关于GEER用连接，其他的是灰色的，跟原有字体一样”。
- 关联：[功能 0008](../features/0008-about-geer.md)、[实施计划 0008](../implement-plan/0008-about-geer.md)、[TODO-0016](../todo/0016-login-about-geer-visibility.md)。

## 当前草图

[实际手机页面](0008-login-about-subtitle-implemented-mobile.png) · [实际桌面页面](0008-login-about-subtitle-implemented-desktop.png) · [320px 页面](0008-login-about-subtitle-implemented-320.png)。副标题方案已实现，浏览器 10/10、目标 RED→GREEN、前端 89/89 及适用检查通过；生产状态另据实际流水线结果记录。

[查看精确手机设计图](0008-login-about-subtitle-exact-mobile-v2.png) · [独立静态预览](0008-login-about-subtitle-review.html)。[AI 首轮图](0008-login-about-subtitle-mobile-concept.png)与[字形修正图](0008-login-about-subtitle-mobile-concept-v2.png)保留追溯，不作为最终字体依据。

在「欢迎回来」正下方、邮箱字段上方，将原「继续你的滑雪旅程」副标题替换为一行「第一次访问？ 关于 GEER ↗」。其中「第一次访问？」沿用原灰色 #7B899B；「关于 GEER ↗」为蓝色 #0072D7 的文字链接，箭头跟随链接，轻下划线帮助辨认可点击区域。

整行与原副标题使用相同字体、字号、常规字重、行高和字距，左对齐；链接不额外加粗、放大或斜体。当前手机源码在 390px 时副标题约 13px，常规 400 字重，480px 以下字距 .1em；设计以实际页面的原副标题视觉为准，不改变标题/表单的字体系统。

移除顶部入口导航行和表单底部独立入口，保留一处副标题链接。登录按钮、创建账号、忘记密码、品牌照片和页脚沿用当前视觉。设计阶段先制作手机全页草图；用户随后要求实现并提交推送，当前共享登录页的手机和桌面均已采用同一句话，实际结果以上述截图及配对计划为准。

## 范围与证据

- 内置 image_gen 先以当前实际手机登录截图 `.local/mobile-about-entry/topbar/after-390.png` 生成位置提案，再针对字形进行一次修正；英文仍有生成偏差，故最终交付独立 HTML 排版预览的 Chrome 截图。
- 静态预览复用现有 `frontend/src/style.css` 和原品牌素材，固定为 390 CSS px 手机草图；副标题 13px、常规字重、.1em 字距，链接通过 `font: inherit` 与 `letter-spacing: inherit` 继承同一字体，仅颜色/下划线区分。它是设计资料，不接入实际页面或业务导航，也不代表响应式/交互验收。
- 上述草图阶段不作为实现或交互验证证据；当时只制作设计资料。用户随后批准实施和提交推送，实际 App/CSS、两项目标交互测试及浏览器验证已完成；未改应用依赖、API 或数据。
- [忘记密码下方的两版历史草图](0008-login-about-below-recovery-design.md)保留追溯，已由本次最新位置和文案要求替代。
- 当前入口已实现并完成本地验证，功能/计划 VERIFIED，TODO-0016 DONE；未根据设计稿或本地检查提前宣称生产 RELEASED。
- 最终交付 780×1888 PNG（390 CSS px、2 倍比例），已目视核对照片/Logo/表单完整、副标题位置/配色/常规字形、全角问号与唯一入口。生成器输出不作为精确字形证据；最终以静态预览截图为准。
- `python3 ai-docs/check_docs.py`（13 对功能/计划、39 tickets）与 `git diff --check` 通过；实现阶段的源码、目标测试及回归证据见配对计划。用户独立 frontend README 保留，不纳入本次提交。

## AI 生成提示词（字形修正，中间参考）

使用内置工具，编辑目标为首轮生成图 `0008-login-about-subtitle-mobile-concept.png`，针对链接的常规字形与全角问号校正，另存为 `0008-login-about-subtitle-mobile-concept-v2.png`。保留首轮与历次设计资料。

```text
Edit target: the provided mobile GEER login design image.
Make only a tiny typography correction to the sentence immediately below "欢迎回来". Its exact text must be "第一次访问？ 关于 GEER ↗", using the FULL-WIDTH Chinese question mark "？".
Keep the first phrase "第一次访问？" gray. The words "关于 GEER" and the diagonal arrow are blue and the words have a fine underline, indicating a hyperlink.
CRITICAL: the entire subtitle sentence must use the same single regular upright sans-serif font and exactly the same font size and NORMAL font weight, matching the gray phrase. In particular the small blue word GEER in this subtitle MUST be UPRIGHT, not slanted, not italic, not bold, not a stylized logo. Its G, E, E, R strokes should be vertical/regular like the normal subtitle font. The blue Chinese words should also be regular, not bold. A hyperlink color/underline is the only typographic distinction. Preserve the large black italic GEER brand logo in the HERO PHOTO unchanged; this correction is solely to the small blue GEER word in the subtitle.
Keep the subtitle on one baseline and one line in its present location, left-aligned under the heading. Everything else must stay exactly unchanged: hero image and brand logo, heading, all form labels and fields and their geometry, main blue login button, create-account and forgot-password links, footer, white background, overall page proportions. Do not move the link to the footer or top bar. Do not add anything. No devices or design annotations. Output one full-page mobile design image, preserving source dimensions as closely as possible.
```

## 位置与文案生成提示词（首轮）

使用内置工具，最终文件保存为本目录的 `0008-login-about-subtitle-mobile-concept.png`。

```text
Use case: ui-mockup.
Input image 1 is the EDIT TARGET: the actual current GEER mobile login full-page screenshot. Make a faithful design-review revision of this screenshot.
User's exact new layout: the About entry belongs directly BELOW the heading "欢迎回来", above the email/password form, replacing the old subtitle "继续你的滑雪旅程". The replacement is ONE LEFT-ALIGNED inline sentence: "第一次访问？ 关于 GEER ↗".
Render "第一次访问？" in the same muted gray #7B899B as the original subtitle. Render ONLY "关于 GEER ↗" as a blue text hyperlink #0072D7, with a fine subtle underline under "关于 GEER" and a small diagonal arrow. Both parts use the SAME original subtitle font family, small font size (approximately 13 CSS px at a 390px viewport), normal 400 font weight, upright style, line height and restrained letter spacing as the current gray subtitle. Do not make the About text larger, bolder or italic. Keep gray question and blue link on the same baseline, one continuous short sentence with a small natural space between them. Use the exact original subtitle position below the heading, not a separate box or a row beside the heading.
Remove the former top-right About capsule AND the entire 60 CSS px white top bar, letting the existing hero photograph start at the very top. Show the snowy GEER hero at the original displayed size/composition, with existing logo and snowboarder intact; move remaining content up naturally. There must be exactly ONE About link in the whole page, in the subtitle line; no About link below "忘记密码" and no duplicated top entry.
STRICT INVARIANTS: preserve the original snowy action photo, black italic GEER logo and "CARVE YOUR LINE", title "欢迎回来", email label "邮箱" and placeholder "请输入邮箱地址", password label "密码" and placeholder "请输入密码", crossed-eye icon, input widths/borders, solid-blue "登录" button, centered blue "创建账号" and "忘记密码" links and footer "MORE THAN A RIDE" with thin horizontal lines. Preserve existing relative proportions, spacing, font appearances and full-page layout as much as possible. Do not redesign the form, enlarge typography, invent labels or redraw the scene.
Output: a single high-resolution flat front-facing mobile web full-page design sketch, approximately a 390 CSS px wide page; everything visible. White page background. No device frame, browser chrome, captions, annotations, marketing card, border/background around the new subtitle, pill, extra copy, new social controls, shadows or gradients. Render accurate simplified Chinese. This is a visual proposal, not an implemented-page verification screenshot.
```
