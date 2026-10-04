# 0009 选课页面交互概念图

- 日期：2026-10-03
- 用途：功能与实施计划 review 的静态视觉参考；不是已实现页面截图。
- 生成工具：内置 `image_gen.imagegen`，默认内置模式；未使用 CLI 或 API 密钥。
- 输出：[0009-course-selection-concept.png](0009-course-selection-concept.png)，1536 × 1024。
- 原始输出：`/Users/geerhong/.codex/generated_images/01a103ad-85b5-7501-b735-c119c687a6d3/exec-b0dd3f68-c727-40c3-b0f8-e53c012eb83c.png`，复制到仓库，保留原图。
- 图片参数：`transparent_background: false`。

## 原图参考

1. [登录页面品牌图](../../frontend/public/images/geer-blue-mobile-login.png)：GEER 锐角字标、G 内蓝色横笔、蓝黑冰雪品牌风格。
2. [0006 学员约课页面概念图](0006-student-booking-home-concept.png)：左侧课程/日期和右侧预约信息的总体布局。

两张图片通过 `referenced_image_paths` 作为视觉参考传入工具。它们没有被覆盖。

## 检查与局限

- 主要展示默认、鼠标悬停、已选择三种课程卡片，照片带深色渐变以保持标题与价格清晰；下方附状态演示，右下角附手机卡片。
- 顶部 GEER 保留了锐角黑字及 G 内蓝色横笔，仍是生成图片中的近似再现。实现时应使用登录品牌的同源精确资产，而非把本概念图裁切为生产 Logo。
- 主画面第一张默认卡片被工具增加了一小块斜切雪山照片，与提示词“默认不显示照片”的要求不完全一致。最终交互以功能和计划文档为准：默认隐藏照片，悬停/键盘聚焦显露，选中后保持。
- 工具保留了一条较大的雪山标题横幅。标题横幅大小可在实现 review 时调整，不构成新增内容或功能承诺。
- 照片、课程名、价格、日期与学员信息均为设计演示，不能当作真实课程数据。
- 图片不能验证动画、键盘可用性、触屏行为、减少动画设置或真实响应式布局，须在获批实现后的实际页面验证。

## 精确提示词

```text
Use case: ui-mockup.
Asset type: High-fidelity UX concept board for GEER snowboard lesson booking, clearly a design concept rather than a production screenshot.
Input images: Image 1 is the authoritative GEER brand reference (logo at the top of the mobile login artwork); Image 2 is the student booking-page layout reference to redesign. Redesign image 2 while keeping its broad left booking / right summary structure, taking the exact logo appearance and the clean blue-black snow sports character from image 1.

Primary request: create one refined, wide landscape presentation board. The upper 78% is a crisp desktop student course-selection page; the bottom 22% is a compact component-state strip. White navigation, pale icy blue canvas, black typography and rich electric blue accents. Snowboarding photography and sharply angled image reveals provide athletic energy. Elegant clean spacing; no big decorative hero. Layout should feel plausible as a real React booking interface.

Top navigation: white bar. At upper left reproduce the image 1 GEER wordmark accurately: broad angular black custom letters, forward-cut geometry, distinctive bright blue horizontal inner stroke in the G. Do not replace this with ordinary bold italic text. Logo is compact, approximately 130px wide. Menu items “约课”, “我的预约”, “关于 GEER”; “约课” is blue and selected. User avatar and “学员” at upper right.

Page header, modest scale: “选择你的下一堂课”; small subtext “两小时 · 一对一 · 线下付款”. Below: left section approximately 67% width, right summary approximately 30% width.

Left section title “选择课程”. Show three stacked wide horizontal cards of IDENTICAL height and width, with generous padding, all course information legible:
1. DEFAULT CARD: white surface, black title “单板基础课”, small level chip “初级”, grey line “从第一次站上雪板，建立稳定基础”, metadata “2 小时 · 一对一”, price aligned to the right “CAD 150”. No image visible; a tiny blue diagonal slash can hint at the image on its right edge.
2. HOVER CARD: title “进阶转弯”, level “进阶”, metadata “2 小时 · 一对一”, price “CAD 180”. A dramatic realistic snowboard carving photograph sweeps in FROM THE RIGHT with a diagonal leading edge. Snow spray and the snowboarder on the right. A dark navy gradient provides strong contrast for white text on the left. Thin blue outline and very slight raised shadow; draw a small normal mouse cursor over this card. Card dimensions and neighboring positions are unchanged. Keep the title, metadata, and price clearly readable. This is the hovered but unselected card: NO selection checkmark.
3. SELECTED CARD: title “刻滑提升”, level “提高”, metadata “2 小时 · 一对一”, price “CAD 200”. Snowboard photography remains visible, strong dark gradient behind white text. A crisp rich blue border and blue circular checkmark in the top right visibly mark selection. The same dimensions as the other cards; use a different snowboard photograph with cold mountains.

Below cards, show just a compact “选择日期” row with 5 date chips, e.g. “周一 10/12”, “周二 10/13”, “周三 10/14”, “周四 10/15”, “周五 10/16”, one selected blue. Do not invent extra page workflows.

Right: clean white booking summary titled “预约信息”. Thin separators; rows “课程 刻滑提升”, “时长 2 小时”, “日期 10月14日”, “时间 10:00 – 12:00”, “地点 Blue Mountain”. A total “CAD 200” and wide solid-blue “申请预约” button. Small grey note “申请后等待教练确认”. Keep whitespace and aligned text.

Bottom board strip, separated by a thin line, labeled subtly “卡片交互 · 概念示意”. Show three small examples of THE SAME course card “进阶转弯” across: “默认” (white text card), arrow, “悬停” (diagonal photo reveal, dark gradient, blue border), arrow, “已选择” (photo visible, blue selection check). Tiny captions “照片从右侧显露” and “选中后持续展示”. At far right a narrow simple mobile card inset shows a photo already visible with caption “手机端 · 直接展示图片”. This inset is a tiny component illustration, not a second full screen.

Composition: high resolution wide landscape, straight-on flat interface, no browser chrome, no physical monitor, no 3D perspective. All Chinese must be crisp and correct. Consistent 8px-ish rounded corners, thin cool-grey borders, natural restrained shadows, around 24px gaps in the desktop page. Brand should feel cool, premium and sporty yet uncluttered.
Avoid: neon, glowing lights, purple palette, 3D flips, particle effects, overly rounded bubble UI, extra text, huge hero banners, card resizing, moving adjacent cards, fake notifications, excessive badges, stock photo watermarks.
```


## 独立页首 Logo（实现资源）

- 内置 imagegen，background-extraction；源图 `frontend/public/images/geer-blue-mobile-login.png`，保留原图。
- 结果保存 `frontend/public/images/geer-logo.png`，2172 × 724、真实 alpha；页首按 132 × 26 / 108 × 22 显示，cover 隐去输出的透明上下留白。
- 目视对照：保留向右倾斜的黑色锐角 GEER 字形、G 内蓝色横条，移除背景和口号。图像提取经过模型重建，不声明像素级相同。
- 完整提示词：

> Use case: background-extraction. Input image is the edit target, existing GEER login brand art. Extract ONLY the exact large GEER wordmark from the top of the image as a clean independent transparent PNG. Text verbatim: "GEER". Preserve precisely the angular forward slanted thick black glyphs, proportions and the blue horizontal bar inside the G; preserve black letter counters and negative spaces as transparent. Remove the entire sky, mountains, person, snow, slogan CARVE YOUR LINE and every other element. Do not redesign or add text or decorations. Wide tight framing around the four letters with only a small transparent margin, isolated on actual transparent background, suitable for a website header.
