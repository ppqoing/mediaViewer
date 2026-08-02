# MediaViewer 应用图标概念生成实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用内置 `imagegen` 生成并保存 10 张广度优先、彼此明显不同的 MediaViewer Android 应用图标概念图。

**Architecture:** 每个概念使用一次独立的内置图片生成调用，固定应用图标通用约束，只改变核心隐喻、构图、材质和配色。每张结果单独保存、逐张目视检查，最终从工作区交付 10 个 PNG 文件；本阶段不修改 Android 应用资源。

**Tech Stack:** 内置 `imagegen`、Codex `view_image`、PowerShell 文件核对、PNG。

## Global Constraints

- 设计依据：`docs/superpowers/specs/2026-08-02-app-icon-concepts-design.md`。
- 恰好产出 10 张独立 PNG，保存到 `artifacts/icon-concepts/`。
- 每张为 1:1 方形 Android 圆角方形应用图标构图，主体居中并保留安全边距。
- 每张只保留一个主要隐喻，不堆叠 MediaViewer 的全部功能。
- 不含文字、字母、数字、品牌名、水印、设备边框、应用商店截图或多图联系表。
- 不照搬 VLC、Google Photos、YouTube 或其他现有产品标志。
- 使用内置 `imagegen`，不用 CLI、API 密钥或透明背景流程。
- 每个方向单独调用一次；只有明确违反约束时，才使用一次针对性修正重新生成该方向。
- 概念阶段不修改 `app/src/main/res/`、Manifest 或应用代码，也不把生成图片加入 Git。

---

### Task 1: 生成 01 棱镜播放

**Files:**
- Create: `artifacts/icon-concepts/01-prism-play.png`

**Interfaces:**
- Consumes: 已确认的广度优先策略与全局图标约束。
- Produces: 第一张独立 PNG，供最终十图清单使用。

- [ ] **Step 1: 创建输出目录**

```powershell
New-Item -ItemType Directory -Path '.\artifacts\icon-concepts' -Force
```

- [ ] **Step 2: 调用内置 imagegen**

使用以下完整提示词：

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Create a MediaViewer app icon where vivid spectral light beams pass through a clean geometric prism and converge into one bold play triangle, representing images, video, audio, and GIF content meeting in one viewer.
Scene/backdrop: integrated deep charcoal-to-navy rounded-square icon background
Style/medium: premium modern gradient, crisp vector-friendly geometry, subtle luminous depth
Composition/framing: one centered symbol, strong silhouette, generous Android adaptive-icon safe padding, readable at 48 px
Color palette: full-spectrum cyan, magenta, violet, amber highlights over deep navy
Constraints: exactly one square app icon; no mockup sheet; no device frame; no text, letters, numbers, watermark, or brand logo; original design; do not imitate VLC, YouTube, or Google Photos
Avoid: tiny details, photorealistic objects, multiple icon alternatives, clutter, shadows outside the rounded-square boundary
```

- [ ] **Step 3: 保存并检查**

将本次 `imagegen` 返回的最终 PNG 复制为 `artifacts/icon-concepts/01-prism-play.png`，再用 `view_image` 检查主体、文字、水印、品牌相似性和缩略图轮廓。只有检查失败时，针对唯一失败点重新生成一次并覆盖该目标文件。

### Task 2: 生成 02 媒体保险库

**Files:**
- Create: `artifacts/icon-concepts/02-media-vault.png`

**Interfaces:**
- Consumes: 全局图标约束。
- Produces: 目录与媒体入口隐喻的独立 PNG。

- [ ] **Step 1: 调用内置 imagegen**

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Create an original MediaViewer icon combining a compact secure media vault or folder doorway with one glowing viewing window and a subtle play shape inside, suggesting opening media from organized directories.
Scene/backdrop: deep navy rounded-square icon field
Style/medium: refined soft 3D, solid simple forms, restrained luminous edges
Composition/framing: centered vault-folder silhouette with one clear inner media window, broad shapes, generous safe padding, readable at 48 px
Color palette: deep navy, teal, turquoise glow, small cool-white highlight
Constraints: exactly one square app icon; no mockup sheet; no device frame; no text, letters, numbers, watermark, padlock cliché, or existing brand logo
Avoid: realistic safe hardware, excessive hinges, tiny folder tabs, clutter, multiple alternatives
```

- [ ] **Step 2: 保存并检查**

保存为 `artifacts/icon-concepts/02-media-vault.png`，用 `view_image` 执行与 Task 1 相同的五项目视检查；只对明确失败点进行一次修正生成。

### Task 3: 生成 03 像素之窗

**Files:**
- Create: `artifacts/icon-concepts/03-pixel-window.png`

**Interfaces:**
- Consumes: 全局图标约束。
- Produces: 图片与视频统一浏览隐喻的独立 PNG。

- [ ] **Step 1: 调用内置 imagegen**

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Design a MediaViewer icon where a simple gallery image window transitions across its surface into a cinematic play window through a few large pixel blocks, expressing seamless browsing between pictures, GIFs, and video.
Scene/backdrop: warm off-white or pale sand rounded-square field
Style/medium: bold flat editorial illustration, clean cut-paper geometry
Composition/framing: one centered window symbol, transition reads left-to-right without becoming two separate icons, generous safe padding
Color palette: coral, warm orange, indigo, muted sky blue
Constraints: exactly one square app icon; original symbol; no text, letters, numbers, watermark, device frame, photo-real landscape, or brand logo
Avoid: fine pixel noise, film-strip clichés, multiple panels, crowded scenery
```

- [ ] **Step 2: 保存并检查**

保存为 `artifacts/icon-concepts/03-pixel-window.png`，用 `view_image` 检查单一焦点、图形转换是否清晰以及全部全局约束。

### Task 4: 生成 04 轨道媒体

**Files:**
- Create: `artifacts/icon-concepts/04-media-orbit.png`

**Interfaces:**
- Consumes: 全局图标约束。
- Produces: 多媒体聚合与连续浏览隐喻的独立 PNG。

- [ ] **Step 1: 调用内置 imagegen**

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Create a futuristic MediaViewer icon with one central play core and three abstract orbiting media forms: a picture card, a compact waveform arc, and a video frame, simplified into a coherent orbital emblem.
Scene/backdrop: very dark blue-black rounded-square icon field
Style/medium: futuristic geometric neon, crisp controlled glow, polished software icon
Composition/framing: centered orbital emblem, only three large orbiting forms, clear hierarchy and safe padding, readable at small size
Color palette: electric cyan, vivid violet, cool white
Constraints: exactly one square app icon; no text, letters, numbers, watermark, device frame, solar-system realism, or existing brand symbol
Avoid: many satellites, thin tangled lines, excessive glow, multiple icon alternatives
```

- [ ] **Step 2: 保存并检查**

保存为 `artifacts/icon-concepts/04-media-orbit.png`，用 `view_image` 检查轨道图形在缩略尺寸下不显杂乱。

### Task 5: 生成 05 光圈画廊

**Files:**
- Create: `artifacts/icon-concepts/05-gallery-aperture.png`

**Interfaces:**
- Consumes: 全局图标约束。
- Produces: 图片、GIF 与视觉媒体隐喻的独立 PNG。

- [ ] **Step 1: 调用内置 imagegen**

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Design an elegant MediaViewer icon where a simplified camera aperture forms a gallery frame, and the central negative space subtly becomes a play triangle, emphasizing images, animated GIFs, and visual media.
Scene/backdrop: creamy pale rounded-square background
Style/medium: refined minimal vector geometry, calm premium editorial identity
Composition/framing: one centered aperture-frame emblem using five or six broad blades, strong negative space, generous safe padding
Color palette: forest green, mint, cream, one restrained dark accent
Constraints: exactly one square app icon; no text, letters, numbers, watermark, camera body, lens realism, or copied photography-app logo
Avoid: thin aperture blades, metallic photo realism, clutter, multiple alternatives
```

- [ ] **Step 2: 保存并检查**

保存为 `artifacts/icon-concepts/05-gallery-aperture.png`，用 `view_image` 确认负形播放符号自然且不近似已有摄影应用图标。

### Task 6: 生成 06 流动卡片

**Files:**
- Create: `artifacts/icon-concepts/06-flowing-cards.png`

**Interfaces:**
- Consumes: 全局图标约束。
- Produces: 连续浏览与播放队列隐喻的独立 PNG。

- [ ] **Step 1: 调用内置 imagegen**

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Create a MediaViewer icon from three broad translucent media cards flowing forward in a smooth sequence and merging into one play direction, representing browsing, ordering, and a persistent playback queue.
Scene/backdrop: saturated blue-to-violet rounded-square gradient field
Style/medium: polished glassmorphism with controlled transparency and soft depth, still icon-simple
Composition/framing: three large overlapping cards only, centered diagonal flow, strong outer silhouette, safe padding
Color palette: cobalt blue, violet, cyan glass highlights
Constraints: exactly one square app icon; no text, letters, numbers, watermark, UI screenshots, device frame, or existing logo
Avoid: too many cards, unreadable tiny thumbnails, excessive reflections, multiple alternatives
```

- [ ] **Step 2: 保存并检查**

保存为 `artifacts/icon-concepts/06-flowing-cards.png`，用 `view_image` 检查玻璃层仍有清晰轮廓，不出现缩小版界面截图。

### Task 7: 生成 07 马赛克播放

**Files:**
- Create: `artifacts/icon-concepts/07-mosaic-play.png`

**Interfaces:**
- Consumes: 全局图标约束。
- Produces: 多格式组合与高辨识度隐喻的独立 PNG。

- [ ] **Step 1: 调用内置 imagegen**

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Build an original bold play symbol from a small set of large interlocking geometric mosaic pieces, suggesting multiple media formats forming one viewer.
Scene/backdrop: clean warm-white rounded-square icon field
Style/medium: Bauhaus-inspired flat geometry, crisp vector-friendly edges, playful but professional
Composition/framing: one oversized centered play silhouette made from four to six large pieces, generous outer safe padding
Color palette: cobalt, vermilion, golden yellow, teal, black accent
Constraints: exactly one square app icon; no text, letters, numbers, watermark, checkerboard, puzzle-piece cliché, or brand imitation
Avoid: tiny tiles, gradients, 3D effects, multiple alternatives, busy pattern
```

- [ ] **Step 2: 保存并检查**

保存为 `artifacts/icon-concepts/07-mosaic-play.png`，用 `view_image` 确认播放轮廓在 48 px 仍立即可识别。

### Task 8: 生成 08 文件夹波形

**Files:**
- Create: `artifacts/icon-concepts/08-folder-waveform.png`

**Interfaces:**
- Consumes: 全局图标约束。
- Produces: 目录浏览与音频播放隐喻的独立 PNG。

- [ ] **Step 1: 调用内置 imagegen**

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Design a MediaViewer icon where one broad folder silhouette naturally rises into a smooth audio waveform along its top edge, joining directory browsing and media playback in one simple emblem.
Scene/backdrop: deep warm-brown rounded-square field
Style/medium: tactile paper-cut relief with soft controlled depth, simple handcrafted geometry
Composition/framing: centered folder-wave emblem, three waveform peaks maximum, broad readable shapes, safe padding
Color palette: amber yellow, burnt orange, brick red, deep brown
Constraints: exactly one square app icon; no text, letters, numbers, watermark, musical note, realistic paper fibers, or existing brand logo
Avoid: dense equalizer bars, tiny folder details, excessive shadows, multiple alternatives
```

- [ ] **Step 2: 保存并检查**

保存为 `artifacts/icon-concepts/08-folder-waveform.png`，用 `view_image` 检查文件夹与波形确实形成单一连续符号。

### Task 9: 生成 09 无限画框

**Files:**
- Create: `artifacts/icon-concepts/09-infinite-frame.png`

**Interfaces:**
- Consumes: 全局图标约束。
- Produces: GIF 循环与连续图片浏览隐喻的独立 PNG。

- [ ] **Step 1: 调用内置 imagegen**

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Create a fluid MediaViewer icon where one luminous infinity ribbon passes through and becomes the sides of a simple picture frame, representing animated GIF loops and continuous image browsing.
Scene/backdrop: dark plum-to-midnight rounded-square gradient field
Style/medium: smooth neon ribbon, elegant controlled glow, modern digital identity
Composition/framing: one centered infinity-frame symbol, thick ribbon, large negative spaces, generous safe padding
Color palette: hot pink, violet, aqua blue on deep plum
Constraints: exactly one square app icon; no text, letters, numbers, watermark, separate repeat arrows, photo content, or copied brand logo
Avoid: thin tangled ribbon, excessive lens flare, multiple frames, multiple alternatives
```

- [ ] **Step 2: 保存并检查**

保存为 `artifacts/icon-concepts/09-infinite-frame.png`，用 `view_image` 检查循环与画框两个语义同时清楚且不显复杂。

### Task 10: 生成 10 队列脉冲

**Files:**
- Create: `artifacts/icon-concepts/10-queue-pulse.png`

**Interfaces:**
- Consumes: 全局图标约束。
- Produces: 播放列表、下一项和后台播放隐喻的独立 PNG。

- [ ] **Step 1: 调用内置 imagegen**

```text
Use case: logo-brand
Asset type: Android app icon concept, one single icon
Primary request: Create a high-energy MediaViewer icon where three clean queue tracks accelerate forward, briefly form an audio pulse, and converge into one play arrow, representing playlist order, next item, and background playback.
Scene/backdrop: near-black rounded-square icon field
Style/medium: high-contrast kinetic graphic design, sharp vector-friendly geometry, subtle electric glow
Composition/framing: three broad tracks only, centered forward movement, clear play-arrow endpoint, safe padding, readable at 48 px
Color palette: near-black, fluorescent yellow, electric blue, small white accent
Constraints: exactly one square app icon; no text, letters, numbers, watermark, racing-brand motif, UI list, or existing logo
Avoid: speedometer, many thin lines, excessive sparks, multiple alternatives
```

- [ ] **Step 2: 保存并检查**

保存为 `artifacts/icon-concepts/10-queue-pulse.png`，用 `view_image` 检查轨迹、脉冲和播放方向在一个简洁轮廓中完成。

### Task 11: 最终数量、格式与交付核验

**Files:**
- Verify: `artifacts/icon-concepts/01-prism-play.png`
- Verify: `artifacts/icon-concepts/02-media-vault.png`
- Verify: `artifacts/icon-concepts/03-pixel-window.png`
- Verify: `artifacts/icon-concepts/04-media-orbit.png`
- Verify: `artifacts/icon-concepts/05-gallery-aperture.png`
- Verify: `artifacts/icon-concepts/06-flowing-cards.png`
- Verify: `artifacts/icon-concepts/07-mosaic-play.png`
- Verify: `artifacts/icon-concepts/08-folder-waveform.png`
- Verify: `artifacts/icon-concepts/09-infinite-frame.png`
- Verify: `artifacts/icon-concepts/10-queue-pulse.png`

**Interfaces:**
- Consumes: Tasks 1-10 的最终 PNG。
- Produces: 可供用户逐张选择的十图交付清单。

- [ ] **Step 1: 核对精确文件清单与 PNG 签名**

```powershell
$expected = @(
  '01-prism-play.png',
  '02-media-vault.png',
  '03-pixel-window.png',
  '04-media-orbit.png',
  '05-gallery-aperture.png',
  '06-flowing-cards.png',
  '07-mosaic-play.png',
  '08-folder-waveform.png',
  '09-infinite-frame.png',
  '10-queue-pulse.png'
)
$actual = @(Get-ChildItem '.\artifacts\icon-concepts' -File -Filter '*.png' |
  Sort-Object Name |
  Select-Object -ExpandProperty Name)
if (Compare-Object $expected $actual) {
  throw '图标概念文件清单与预期不一致'
}
foreach ($name in $expected) {
  $bytes = [System.IO.File]::ReadAllBytes(
    (Join-Path '.\artifacts\icon-concepts' $name)
  )
  if ($bytes.Length -lt 8 -or
      $bytes[0] -ne 0x89 -or
      $bytes[1] -ne 0x50 -or
      $bytes[2] -ne 0x4E -or
      $bytes[3] -ne 0x47) {
    throw "$name 不是有效 PNG 文件"
  }
}
```

Expected: 命令退出码 0，目录中恰好存在上述 10 张 PNG。

- [ ] **Step 2: 逐张最终目视复核**

依次使用 `view_image` 打开 10 张最终文件，检查单图、正方形构图、安全边距、无文字/水印/商标、主体小尺寸辨识度以及十图差异性。发现问题时只回到对应 Task，不能无条件重生成已经通过的其他图。

- [ ] **Step 3: 交付**

在最终回复中按 01-10 的顺序嵌入 10 张本地 PNG，提供每张中文名称和绝对保存目录；明确这些是概念图，尚未替换 Android Launcher 图标，也没有加入 Git。
