# 0008 — 登录页「关于 GEER」入口放在忘记密码下方：手机设计草图

- 日期：2026-10-07
- 状态：SUPERSEDED，历史设计提案；用户最新要求改为[标题下方的副标题链接](0008-login-about-subtitle-design.md)。
- 用户原话：“我觉得aboutgeer入口位置那个不如放到忘记密码下面，帮我设计一下，先做个design草图出来”。
- 最新反馈：“不好，我觉得和创建账号和忘记密码风格保持一致就好”。
- 关联：[功能 0008](../features/0008-about-geer.md)、[实施计划 0008](../implement-plan/0008-about-geer.md)、[TODO-0016](../todo/0016-login-about-geer-visibility.md)。

## 草图

[查看第二版手机全页设计图](0008-login-about-below-recovery-mobile-concept-v2.png)。[第一版胶囊草图](0008-login-about-below-recovery-mobile-concept.png)保留供追溯，用户已否定其样式。

本轮先展示此前讨论的手机登录页。保留现有 GEER 品牌照片、Logo、登录标题和字段，取消顶部入口占用的导航行；底部操作依次为「登录」「创建账号」「忘记密码」「关于 GEER」。入口居中，紧接忘记密码之后，页脚之前。

按最新反馈，「关于 GEER」采用与「创建账号」「忘记密码」相同的纯蓝色文字链接：正常直立字体、同一字号/字重/颜色、居中排列和一致的纵向节奏；移除浅蓝底、边框、胶囊、常驻下划线、斜体和箭头。现有源码文字链接为 16px、700 字重、蓝色 #0072D7，后续实现应复用同组样式；触控及焦点效果留待实际页面核验。文案只保留「关于 GEER」。

这是 AI 生成的全页设计提案，尺寸和字形需在获准后的实际页面中核验。短屏可能需要正常纵向滚动才能看到表单底部入口；全页草图不证明入口在所有设备的首屏可见。桌面是否同样移到表单底部留待后续 review，本轮不修改桌面或手机实现。

## 本轮范围与实际结果

- 使用内置 image_gen 工具，第一版以当前登录页截图为编辑目标；第二版以第一版草图为编辑目标，只调整关于 GEER 入口样式。
- 第一版输出为 924×1702 PNG，技术位置核对通过但胶囊样式被用户否定；保留此记录，不作为第二版尺寸或视觉验收证据。生成图不作为 CSS 像素级布局证据。
- 第二版实际输出为 924×1702 PNG；已目视核对关于 GEER 在忘记密码下方、三项均为居中蓝色文字、无胶囊/边框/背景/斜体/下划线/箭头，品牌与表单完整。文档检查与空白检查再次通过。DRAFT 状态保留，目视核对不代表用户已认可或页面已实现。
- 文档验证：`python3 ai-docs/check_docs.py` 通过，13 对功能/计划、39 tickets 的链接、状态、索引和 review 门槛一致；`git diff --check` 通过。本轮仅设计，不运行功能测试或声称交互验证。
- 只新增设计资料并同步既有功能/计划及 ticket；未修改应用源码、测试、依赖、API 或数据。
- 功能/计划的 VERIFIED 仍指已经实现并验证的上一版；本草图不是新实现、批准或生产发布证据。
- Git/生产操作不属于本轮范围。用户已有工作区改动保留。

## 最终生成提示词（第二版）

使用内置工具，编辑目标为第一版 `0008-login-about-below-recovery-mobile-concept.png`；第二版另存为 `0008-login-about-below-recovery-mobile-concept-v2.png`，不覆盖历史。

```text
Use case: ui-mockup.
Input image 1 is the edit target: the existing GEER mobile login design sketch.
Make ONE targeted design change: replace the pale-blue outlined capsule button below "忘记密码" with a plain centered blue text link reading exactly "关于 GEER". Match the existing "创建账号" and "忘记密码" links exactly in font family, font size, font weight, upright font style, blue color (#0072D7), baseline treatment, and alignment. The three secondary links should form one consistent vertically stacked group with similar vertical rhythm. About is a text link in the same family, not a separate button.
Remove the old capsule's entire rounded border and light-blue background; remove the underline, italic/slant, and diagonal arrow. White background behind the new text, no visible box, no icon, no divider or extra label. Show "关于 GEER" once, directly below "忘记密码", above the existing footer.
Strict invariants: keep everything else unchanged from the edit target, including the GEER black logo and snowy snowboard photograph, page proportions, Chinese heading "欢迎回来", subtitle "继续你的滑雪旅程", email and password labels/placeholders, input outlines, password eye icon, blue "登录" primary button, the positions and appearance of "创建账号" and "忘记密码", and the footer "MORE THAN A RIDE". Do not redraw the action photograph or redesign the form. Preserve existing composition and approximate image dimensions 924 x 1702. No top-right About entry, no duplicate link, no new copy, no device frame, no annotations, no shadows, no gradients. Exact legible Chinese text. This is a design-review sketch, not an implemented webpage screenshot.
```

## 第一版生成提示词（历史，样式已被用户否定）

使用内置工具，参考图为仓库本地 `.local/mobile-about-entry/topbar/after-390.png`；最终文件保存为本目录的 `0008-login-about-below-recovery-mobile-concept.png`。

```text
Use case: ui-mockup.
Asset type: a mobile GEER snowboard lesson website login design-review mockup.
Input image 1 is the EDIT TARGET: the current implemented mobile login full-page screenshot. Preserve its exact GEER action photograph, black italic GEER logo, Chinese login form, white background, fonts, brand-blue palette, field widths, password eye icon, and small footer styling.
Primary request: move the single "关于 GEER ↗" entry from the upper-right top bar to a centered position immediately BELOW the existing "忘记密码" link. Remove the former top-right entry AND the 60px white top navigation bar completely, so the snowy photograph starts at the top of the page as before. Keep the logo and snowboard action scene recognizable and do not crop or redraw them unnecessarily.
Composition: one full-page mobile web screenshot, straight-on, about 390 CSS px wide and 944 CSS px tall, rendered at high resolution around 780 x 1888 pixels. No smartphone/device frame, no browser chrome, no side-by-side variants, no annotations or design labels. Entire page visible with comfortable bottom whitespace.
Layout from top to bottom: existing snowy GEER hero (same height and composition as screenshot after removing its white top bar), then left-aligned heading "欢迎回来"; small gray subtitle "继续你的滑雪旅程"; label "邮箱"; outlined field with placeholder "请输入邮箱地址"; label "密码"; outlined field with placeholder "请输入密码" and crossed-eye icon on right; full-width solid-blue main button "登录"; centered blue text link "创建账号"; centered blue text link "忘记密码"; centered SMALL tertiary outlined capsule button reading exactly "关于 GEER ↗"; then existing restrained footer text "MORE THAN A RIDE" flanked by thin horizontal lines.
About entry styling: approximately 164 CSS px wide and 44 CSS px tall, soft blue-white fill #F2F7FC, thin light-blue border #B8D7F0, full rounded ends; blue #0059AA text, 18 CSS px font, weight 600, slight italic matching current entry; subtle underline only under "关于 GEER", small diagonal arrow. Center horizontally with about 16 CSS px clear space from the forgot-password link's touch area. It is compact and quieter than the filled login button. Keep "创建账号" and "忘记密码" as plain text links, do not turn them into buttons.
Constraints: show exactly ONE About entry, only under forgot password. No extra text, subtitle, helper copy, portrait, promotional card, gradient, drop shadow, new menu, extra border panel, checkboxes, social login, statistics, invented slogan, or decorative elements. Do not redesign the form or brand artwork. Chinese characters must be accurate and crisp. Preserve all original UI text verbatim. This image is a proposal for visual review, not evidence of an implemented page.
```
