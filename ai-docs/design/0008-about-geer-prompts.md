# 0008 关于 GEER 概念图生成记录

- 日期：2026-10-02。
- 工具：内置 `image_gen`，通过 `imagegen` skill 执行；非 CLI/API fallback。
- 用途：桌面与手机设计 review 草稿；不代表已实现页面或用户已批准版式。
- 输入：`0006-student-booking-home-concept.png` 为改版目标/品牌参考；`0002-geer-photo-original.png` 为雪上动作照片素材。二者均已查看。
- 人物照片用于版式示意；简介、CASI 等级/证书及社交账号是明确占位内容，不构成真实资质或身份声明。

## 桌面稿完整提示词

```text
Use case: ui-mockup.
Task: Redesign the existing GEER lesson-booking UI screenshot (reference image 1) into a polished, realistic public "关于 GEER" coach profile webpage. Reference image 1 is the edit target: retain its italic GEER wordmark, cobalt-blue brand family and clean readable Chinese UI typography, but replace the booking form layout completely with the profile-page design below. Reference image 2 is the user's existing snowboarding photograph, a supporting insert; use that photograph in the hero image, keeping the snowboarder wearing dark outerwear and goggles, snowboard, snow and natural wintry setting recognizable. Do not invent a new face or an actual portrait of the user.
This is a design-review mockup, not a live website. The main goal is to communicate how the page will look, with substantial realistic imagery, careful spacing, clear hierarchy and crisp legible simplified Chinese.
Palette: white, light ice blue #F3F7FB, strong GEER blue #006DD0, deep navy #10273E for headlines and video section. Restrained rounded corners, thin borders, minimal shadows. Premium and personal outdoor coaching aesthetic. Avoid generic dashboard density, purple gradients, excessive pill cards and floating ornaments.
Visitor-facing public page, no signed-in student avatar and no admin or upload controls. Header: italic black GEER, "关于 GEER" active, "预约课程", and a compact outlined "登录" button.
Hero: small "ABOUT GEER"; main heading exactly "你好，我是 GEER。"; subheading "单板滑雪教练"; supporting line "我的滑行、教学与热爱。"; primary blue "预约课程" and secondary "联系我" buttons. A prominent photograph based on reference 2. Small neutral caption "图片示意 · 可更换个人照片".
Next: one and only one highlight-video section. Heading "高光滑行", small "HIGHLIGHT FILM". Use a wide snowboarding still based on the supplied action photo, with a large centered white circular play button and subtle dark overlay. Tiny "视频封面示意" label. This is one video, not a playlist or gallery. No fabricated video duration.
Next: "关于我的教学" profile area, with this honest placeholder paragraph: "这里将展示你的滑雪经历、教学理念与擅长方向。" Followed by small fields "授课语言 · 待填写" and "服务区域 · 待填写".
Next: "CASI 资质与认证" section, with a clean certificate-shaped placeholder thumbnail containing "CASI", "证书图片待上传", and a conspicuous "示意" label. No forged credential, invented level, signature, seal, award date, membership number or official verification badge. Beside it show "认证等级 · 待填写" and link "查看证书 ↗".
Next: "在这里找到我" social contact area with three clearly labeled compact cards or rows: "小红书", "抖音", "微信". First two show "账号待填写" and "查看主页 ↗". WeChat shows "微信号待填写", "查看二维码" and "复制微信号". Use subtle recognizable platform-color accents but keep GEER blue dominant. Do not invent working handles, phone numbers or QR codes.
Finish with a light-blue call-to-action strip: "一起开启下一次滑行" and button "预约课程". Minimal footer GEER and "设计草稿 · 资料与素材待替换".
All requested sections must be fully visible in the long-page mockup. No fake teaching statistics, ratings, testimonials, extra courses, extra videos or invented qualifications. Keep body text minimal and render exact provided Chinese copy carefully. Flat front-on website screenshot, no browser chrome, no laptop/phone frame and no perspective distortion.
Output composition: a high-resolution full-page desktop screenshot, approximately 1440 px wide by 2160 px tall. The viewport is desktop width, NOT a narrow mobile page. Center a maximum-width 1160 px content column. White slim header. Hero is a generous two-column layout: text on the left, a tall action photo on the right. Wide 16:9 video frame below; compact teaching introduction and certificate blocks may share a balanced two-column row. Three social cards side by side. Consistent large side margins and generous vertical breathing room. Keep entire footer and contact area visible.
```

## 手机稿完整提示词

```text
Use case: ui-mockup.
Task: Redesign the existing GEER lesson-booking UI screenshot (reference image 1) into a polished, realistic public "关于 GEER" coach profile webpage. Reference image 1 is the edit target: retain its italic GEER wordmark, cobalt-blue brand family and clean readable Chinese UI typography, but replace the booking form layout completely with the profile-page design below. Reference image 2 is the user's existing snowboarding photograph, a supporting insert; use that photograph in the hero image, keeping the snowboarder wearing dark outerwear and goggles, snowboard, snow and natural wintry setting recognizable. Do not invent a new face or an actual portrait of the user.
This is a design-review mockup, not a live website. The main goal is to communicate how the page will look, with substantial realistic imagery, careful spacing, clear hierarchy and crisp legible simplified Chinese.
Palette: white, light ice blue #F3F7FB, strong GEER blue #006DD0, deep navy #10273E for headlines and video section. Restrained rounded corners, thin borders, minimal shadows. Premium and personal outdoor coaching aesthetic. Avoid generic dashboard density, purple gradients, excessive pill cards and floating ornaments.
Visitor-facing public page, no signed-in student avatar and no admin or upload controls. Header: italic black GEER, "关于 GEER" active, "预约课程", and a compact outlined "登录" button.
Hero: small "ABOUT GEER"; main heading exactly "你好，我是 GEER。"; subheading "单板滑雪教练"; supporting line "我的滑行、教学与热爱。"; primary blue "预约课程" and secondary "联系我" buttons. A prominent photograph based on reference 2. Small neutral caption "图片示意 · 可更换个人照片".
Next: one and only one highlight-video section. Heading "高光滑行", small "HIGHLIGHT FILM". Use a wide snowboarding still based on the supplied action photo, with a large centered white circular play button and subtle dark overlay. Tiny "视频封面示意" label. This is one video, not a playlist or gallery. No fabricated video duration.
Next: "关于我的教学" profile area, with this honest placeholder paragraph: "这里将展示你的滑雪经历、教学理念与擅长方向。" Followed by small fields "授课语言 · 待填写" and "服务区域 · 待填写".
Next: "CASI 资质与认证" section, with a clean certificate-shaped placeholder thumbnail containing "CASI", "证书图片待上传", and a conspicuous "示意" label. No forged credential, invented level, signature, seal, award date, membership number or official verification badge. Beside it show "认证等级 · 待填写" and link "查看证书 ↗".
Next: "在这里找到我" social contact area with three clearly labeled compact cards or rows: "小红书", "抖音", "微信". First two show "账号待填写" and "查看主页 ↗". WeChat shows "微信号待填写", "查看二维码" and "复制微信号". Use subtle recognizable platform-color accents but keep GEER blue dominant. Do not invent working handles, phone numbers or QR codes.
Finish with a light-blue call-to-action strip: "一起开启下一次滑行" and button "预约课程". Minimal footer GEER and "设计草稿 · 资料与素材待替换".
All requested sections must be fully visible in the long-page mockup. No fake teaching statistics, ratings, testimonials, extra courses, extra videos or invented qualifications. Keep body text minimal and render exact provided Chinese copy carefully. Flat front-on website screenshot, no browser chrome, no laptop/phone frame and no perspective distortion.
Output composition: a high-resolution full-page MOBILE screenshot, approximately 900 px wide by 2700 px tall, representing a 390 CSS-pixel-wide page with readable mobile typography. Do not shrink a desktop layout. Single-column layout throughout. Compact header with GEER at left and "登录" at right, then a short nav line with "关于 GEER" active and "预约课程". Hero text, two buttons side-by-side, then a compact action-photo crop. One landscape video frame. Short profile section. One compact certificate card. Three full-width social contact rows stacked vertically, then final booking CTA and footer. Modest mobile padding. Fit all requested sections in the full-page image without tiny body text.
```
