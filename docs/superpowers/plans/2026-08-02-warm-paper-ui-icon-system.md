# 暖纸媒体库界面与 Image2 图标系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 Image2 生成、紧边界切分并光学居中一套完整透明图标，将 MediaViewer 整个 Android 应用重写为已确认的暖纸媒体库布局，同时保持现有播放、目录、GIF、图片和队列行为不变。

**Architecture:** 资产层以四张分组母版、imagegen 标准去色脚本和可复现 Python 紧边界管线产出统一 `192 × 192 px` alpha mask PNG；Compose 层通过 `MediaIcon` 资源类型集中加载和 tint，不再让业务页面直接引用 Material `Icons.*`。视觉层只替换主题、共享表面和页面布局，继续消费现有 ViewModel、MediaSession/LibVLC、目录仓库与图片加载状态，不复制或改写业务状态机。

**Tech Stack:** Image2 / 内置 imagegen、Python 3 + Pillow、Kotlin 2.3.21、Android Gradle Plugin 9.3.0、Jetpack Compose BOM 2026.06.00、Material 3、Media3 1.10.1、LibVLC 4.0.0-eap29、Coil 3.5.0、JUnit 4、AndroidX Compose Test、Android SDK 36。

## Global Constraints

- 最低系统版本保持 Android 10 / API 29，`compileSdk` 与 `targetSdk` 保持 36，`versionCode = 3`、`versionName = "1.1.0"`。
- Release ABI 继续只包含 `arm64-v8a`；本计划不主动生成 Release APK，构建发布属于单独请求。
- 不新增 Android 第三方运行时依赖，不修改 Room schema、稳定 `mediaKey`、逻辑 URL、队列身份或网络协议。
- 图标必须来自 Image2 生成母版；只允许本地做去背景、噪点清理、alpha mask 归一、紧边界裁切、缩放和居中，不能用 Material 图标替代失败结果。
- “最小分割”必须以所有有效前景像素的联合包围盒为准，不能按母版单元格中心直接导出，也不能丢失多圆点、斜线或循环箭头等独立语义组件。
- 每个最终图标固定为 `192 × 192 px` 透明 PNG，最长可见边占画布 `70%–74%`，几何居中后 alpha 质量中心修正不超过 `4 px`。
- 常规图标以 `24dp` 展示，主操作可为 `32–40dp`；所有可点击入口的触控区域至少 `48dp`。
- 除“后退 10 秒”和“前进 10 秒”的固定数字外，图标不得包含文字、数字、水印、阴影、发光、复杂纹理、3D 材质或品牌标志。
- 浅色主题使用暖米白、奶油、陶土橙、鼠尾草绿和深棕；深色主题使用暖棕黑、焦糖、浅奶油和橄榄绿，不回退到冷青色。
- 视频与图片内容区域不叠加纸纹；必要控件消费 `WindowInsets.safeDrawing`，媒体背景可以延伸到窗口边缘。
- 保留视频单击显隐、双击播放或暂停、自动隐藏时长、半透明功能区、后台播放、暂停恢复、进度、播放速度、播放模式、画面比例和队列行为。
- 保留空目录成功并显示“空文件夹”、GIF 动画、单图左右切换、缩放不重载和漫画连续阅读。
- 音频页面不得出现画面比例、全屏、锁定或其他视频专属控制。
- 不提交现有未跟踪的 `.superpowers/brainstorm/`、`artifacts/` 旧概念产物、`dist/`、`docs/analysis/` 或 `docs/verification/2026-07-30-arm64-compressed-release.md`。
- 不替换 Launcher 应用图标、自适应图标 XML、Manifest 启动图标或 mipmap 资源。
- 只做基础功能性审查；首次运行目标测试，修复后只重验失败的测试目标，不重复运行已经通过的人工测验。
- Windows 上 Gradle 串行运行，并传入 `'-Pkotlin.incremental=false'`，不得并行启动多个 Gradle 进程。

---

## 文件结构与职责

### 新增资产与工具

| 文件 | 职责 |
|---|---|
| `tools/icon_pipeline/icon_manifest.json` | 定义母版、行列、资源名和 row-major 图标顺序 |
| `tools/icon_pipeline/process_icon_sheet.py` | 消费透明母版，保留多连通块、紧边界裁切、alpha 归一、统一画布居中和质检 |
| `tools/icon_pipeline/tests/test_process_icon_sheet.py` | 用合成图验证多圆点、斜线、噪点、几何中心和 4px 光学修正上限 |
| `artifacts/warm-paper-icons/source/common.png` | Image2 通用与导航母版 |
| `artifacts/warm-paper-icons/source/media.png` | Image2 媒体与浏览母版 |
| `artifacts/warm-paper-icons/source/player.png` | Image2 播放器母版 |
| `artifacts/warm-paper-icons/source/reader.png` | Image2 图片阅读母版 |
| `artifacts/warm-paper-icons/transparent/*.png` | imagegen 标准去色脚本产出的透明母版 |
| `artifacts/warm-paper-icons/processed/contact-sheet.png` | 浅色、深色和棋盘格质检联系表 |
| `app/src/main/res/drawable-nodpi/ic_wp_*.png` | Android 最终透明图标资源 |

### 新增或重构的 Compose 基础文件

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/local/mediaviewer/ui/icons/MediaIcon.kt` | `@DrawableRes` 图标值类型 |
| `app/src/main/java/com/local/mediaviewer/ui/icons/MediaIcons.kt` | 通用、媒体、阅读图标目录及 `all` 清单 |
| `app/src/main/java/com/local/mediaviewer/ui/player/PlayerIcons.kt` | 播放器图标目录及 `all` 清单，值类型同为 `MediaIcon` |
| `app/src/main/java/com/local/mediaviewer/ui/components/WarmPaperSurface.kt` | 页面纸张背景、分组标题、卡片和统一列表容器 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaFilterChips.kt` | 浏览筛选胶囊 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaSegmentedControl.kt` | 图片、动图、漫画分段切换器 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaBottomNavigation.kt` | 媒体源与设置两个一级入口 |

### 主要修改文件

- `app/src/main/java/com/local/mediaviewer/ui/theme/Color.kt`、`Shapes.kt`、`Tokens.kt`、`Typography.kt`、`Theme.kt`：暖纸明暗主题、播放器颜色和尺寸。
- `app/src/main/java/com/local/mediaviewer/ui/components/*.kt`：图标资源接入、纸张表面、菜单、弹窗、状态面板、Snackbar 和安全区。
- `app/src/main/java/com/local/mediaviewer/ui/home/*.kt`、`ui/settings/*.kt`：首页和设置布局。
- `app/src/main/java/com/local/mediaviewer/ui/browser/*.kt`：面包屑、筛选、媒体列表和空目录。
- `app/src/main/java/com/local/mediaviewer/ui/player/*.kt`：普通/全屏视频、音频、迷你播放器、队列和播放器图标。
- `app/src/main/java/com/local/mediaviewer/ui/image/*.kt`：单图、GIF、漫画工具条、翻页和缩放布局。
- `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`：只接入底部一级导航和现有导航回调，不改播放器生命周期策略。
- `app/build.gradle.kts`：所有页面迁移完成后移除不再使用的 Material extended icons 依赖。
- 对应 `app/src/test` 和 `app/src/androidTest` 定向测试文件：固定资源、布局语义、触控尺寸和行为边界。

---

### Task 1: Image2 图标母版与可复现紧边界处理管线

**Files:**
- Create: `tools/icon_pipeline/icon_manifest.json`
- Create: `tools/icon_pipeline/process_icon_sheet.py`
- Create: `tools/icon_pipeline/tests/test_process_icon_sheet.py`
- Create: `artifacts/warm-paper-icons/source/common.png`
- Create: `artifacts/warm-paper-icons/source/media.png`
- Create: `artifacts/warm-paper-icons/source/player.png`
- Create: `artifacts/warm-paper-icons/source/reader.png`
- Create: `artifacts/warm-paper-icons/transparent/common.png`
- Create: `artifacts/warm-paper-icons/transparent/media.png`
- Create: `artifacts/warm-paper-icons/transparent/player.png`
- Create: `artifacts/warm-paper-icons/transparent/reader.png`
- Create: `artifacts/warm-paper-icons/processed/contact-sheet.png`
- Create: `app/src/main/res/drawable-nodpi/ic_wp_*.png`

**Interfaces:**
- Produces: `IconSpec(name: str, row: int, column: int, optical: bool = True)`。
- Produces: `extract_icon(cell: Image.Image, target_size: int = 192) -> Image.Image`，返回 RGBA 透明图标。
- Produces: `process_sheet(sheet_path: Path, specs: list[IconSpec], rows: int, columns: int, output_dir: Path) -> list[QualityResult]`。
- Produces: `inspect_icon(name: str, image: Image.Image) -> QualityResult`、`corner_alphas(image: Image.Image) -> tuple[int, int, int, int]` 和 `count_components(alpha: Image.Image) -> int`。
- Produces: `QualityResult(name: str, width: int, height: int, corner_alpha: int, center_dx: float, center_dy: float, foreground_ratio: float)`。
- Consumes: Image2 生成的四张固定网格母版；Android 侧只消费最终 `ic_wp_*.png`。

- [ ] **Step 1: 写紧边界、多连通块和居中失败测试**

创建合成洋红底图片，其中一个测试图标由三个互不相连圆点组成，另一个由扬声器与独立斜线组成：

```python
class ProcessIconSheetTest(unittest.TestCase):
    def test_keeps_all_semantic_components_and_centers_union_bounds(self):
        cell = Image.new("RGBA", (320, 320), (0, 0, 0, 0))
        draw = ImageDraw.Draw(cell)
        for x in (88, 160, 232):
            draw.ellipse((x - 12, 142, x + 12, 166), fill="#201711")

        result = extract_icon(cell)
        bbox = result.getchannel("A").getbbox()

        self.assertEqual((192, 192), result.size)
        self.assertIsNotNone(bbox)
        self.assertLessEqual(abs((bbox[0] + bbox[2]) / 2 - 96), 1)
        self.assertEqual(3, count_components(result.getchannel("A")))
        self.assertEqual(0, max(corner_alphas(result)))

    def test_optical_shift_is_clamped_to_four_pixels(self):
        cell = Image.new("RGBA", (320, 320), (0, 0, 0, 0))
        draw = ImageDraw.Draw(cell)
        draw.polygon(((70, 72), (250, 160), (70, 248)), fill="#201711")
        result = extract_icon(cell)
        quality = inspect_icon("forward", result)
        self.assertLessEqual(abs(quality.center_dx), 4.0)
        self.assertLessEqual(abs(quality.center_dy), 4.0)
```

- [ ] **Step 2: 运行 Python 测试并确认 RED**

Run:

```powershell
python -m unittest discover -s tools/icon_pipeline/tests -p 'test_*.py' -v
```

Expected: FAIL，`process_icon_sheet`、`extract_icon`、`count_components` 和质检类型尚不存在。

- [ ] **Step 3: 实现 manifest 和核心处理函数**

`icon_manifest.json` 固定四组网格和 row-major 顺序：

```json
{
  "common": {
    "rows": 4,
    "columns": 5,
    "icons": [
      "back", "forward", "home", "chevron_right", "more",
      "close", "search", "refresh", "settings", "add",
      "recent", "grid", "check", "info", "error",
      "offline", "empty", "connected", "sort"
    ]
  },
  "media": {
    "rows": 3,
    "columns": 4,
    "icons": [
      "network_share", "folder", "video", "audio",
      "image", "gif", "unknown_file", "reader_mode",
      "play_now", "play_next", "add_queue"
    ]
  },
  "player": {
    "rows": 5,
    "columns": 5,
    "icons": [
      "play", "pause", "replay", "back_10", "forward_10",
      "previous", "next", "queue", "delete", "drag",
      "volume", "muted", "brightness", "lock", "unlock",
      "fullscreen_enter", "fullscreen_exit", "speed", "scale", "sequential",
      "repeat_all", "repeat_one", "shuffle", "playing", "background_play"
    ]
  },
  "reader": {
    "rows": 2,
    "columns": 3,
    "icons": [
      "zoom_out", "zoom_in", "fit_screen",
      "image_mode", "gif_mode", "comic_mode"
    ]
  }
}
```

核心实现必须读取已经由 imagegen 标准脚本去色的透明母版，再按单元格边界切图；按 alpha 阈值找所有有效区域，移除面积小于单元格 `0.05%` 的孤立噪点但保留所有语义组件。将联合包围盒最长边缩放至 `138 px`，置于 `192 px` 画布几何中心；计算 alpha 质量中心并将修正量 clamp 到 `-4..4 px`。

```python
def center_offset(alpha: Image.Image) -> tuple[int, int]:
    bbox = alpha.getbbox()
    geometric_x = (bbox[0] + bbox[2]) / 2
    geometric_y = (bbox[1] + bbox[3]) / 2
    mass_x, mass_y = alpha_centroid(alpha)
    return (
        round(max(-4, min(4, geometric_x - mass_x))),
        round(max(-4, min(4, geometric_y - mass_y))),
    )
```

- [ ] **Step 4: 运行处理管线测试并确认 GREEN**

Run:

```powershell
python -m unittest discover -s tools/icon_pipeline/tests -p 'test_*.py' -v
```

Expected: PASS；三个圆点和静音斜线都保留，四角透明，中心修正不超过 4px。

- [ ] **Step 5: 三个子 Agent 与主 Agent 分组并行生成母版**

文件所有权必须分离：Agent A 只写 `common.png`，Agent B 只写 `media.png`，Agent C 只写 `player.png`，主 Agent 只写 `reader.png` 和处理脚本。所有 Agent 使用同一基础提示词，按 manifest 的 row-major 顺序替换图标列表：

```text
Create one exact icon sprite sheet for an Android media browser. Flat saturated
magenta #ff00ff background, perfectly uniform. Exact {columns} columns by {rows}
rows, equal cells, no visible grid lines. One centered monochrome near-black
glyph per occupied cell, in this exact row-major order: {icons}. Warm editorial
rounded icon family matching an ivory paper and terracotta media-library UI;
medium consistent stroke, rounded caps, clear silhouette at 24dp, no shadow,
glow, texture, words, captions, labels, borders, mockup, watermark, brand mark or
3D. Only back-10 and forward-10 may contain the number 10. Leave unused final
cells completely magenta. Icons must not touch cell edges or each other.
```

每个 Agent 完成后只报告文件路径和肉眼发现的错位/错义项，不改动其他组。

- [ ] **Step 6: 标准去色、处理四张母版并对失败图标单独重生成**

Run:

```powershell
$chromaScript = 'C:\Users\Administrator\.codex\skills\.system\imagegen\scripts\remove_chroma_key.py'
@('common', 'media', 'player', 'reader') | ForEach-Object {
    python $chromaScript `
        --input "artifacts/warm-paper-icons/source/$_.png" `
        --out "artifacts/warm-paper-icons/transparent/$_.png" `
        --key-color '#ff00ff' `
        --soft-matte `
        --transparent-threshold 12 `
        --opaque-threshold 96 `
        --despill `
        --force
}
python tools/icon_pipeline/process_icon_sheet.py --manifest tools/icon_pipeline/icon_manifest.json --source artifacts/warm-paper-icons/transparent --output app/src/main/res/drawable-nodpi --report artifacts/warm-paper-icons/processed
```

Expected: 所有 manifest 图标均产生 `ic_wp_<name>.png`；报告中 `192 × 192`、四角 alpha 为 0、前景占比在允许区间、中心偏差不超过 4px。错义、粘连、缺数字或缺连通块的图标用同风格单图提示词重生成，只替换对应资源，再重跑处理和联系表。

- [ ] **Step 7: 提交图标处理管线与最终资源**

```powershell
git add tools/icon_pipeline app/src/main/res/drawable-nodpi artifacts/warm-paper-icons
git commit -m "feat: generate warm paper icon asset set"
```

---

### Task 2: 统一位图图标类型并移除页面内 Material 图标引用

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/icons/MediaIcon.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/icons/MediaIcons.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerIcons.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaGlyph.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaIconButton.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/NeonPlayerIcon.kt`
- Modify: all direct `Icons.*` call sites reported by `rg -n 'Icons\.' app/src/main/java`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/local/mediaviewer/ui/icons/MediaIconsTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/player/PlayerIconsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaComponentsTest.kt`

**Interfaces:**
- Produces: `@JvmInline value class MediaIcon(@DrawableRes val resourceId: Int)`。
- Produces: `@Composable fun MediaIconImage(icon: MediaIcon, contentDescription: String?, tint: Color, modifier: Modifier = Modifier)`。
- Changes: `MediaIconButton`、`PlayerIconButton`、`MediaGlyph`、`NeonPlayerIcon` 的 `icon` 参数从 `ImageVector` 改为 `MediaIcon`。
- Preserves: content description、selected、loading、disabled、state description 和最小触控尺寸语义。

- [ ] **Step 1: 将图标清单测试改为资源类型并确认 RED**

```kotlin
@Test
fun `warm paper icon inventory uses unique drawable resources`() {
    assertEquals(36, MediaIcons.all.size)
    assertEquals(MediaIcons.all.size, MediaIcons.all.map { it.resourceId }.distinct().size)
    assertTrue(MediaIcons.all.all { it.resourceId != 0 })
}

@Test
fun `player icon inventory uses generated drawable resources`() {
    assertEquals(25, PlayerIcons.all.size)
    assertEquals(PlayerIcons.all.size, PlayerIcons.all.map { it.resourceId }.distinct().size)
}
```

Run:

```powershell
.\gradlew.bat testDebugUnitTest '-Pkotlin.incremental=false' --tests '*MediaIconsTest' --tests '*PlayerIconsTest'
```

Expected: FAIL，现有清单仍为 `ImageVector` 且数量不符。

- [ ] **Step 2: 实现统一资源类型和渲染组件**

```kotlin
@JvmInline
value class MediaIcon(@DrawableRes val resourceId: Int)

@Composable
fun MediaIconImage(
    icon: MediaIcon,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(icon.resourceId),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}
```

`MediaIcons` 公开通用、媒体和阅读资源，`PlayerIcons` 公开 25 个播放资源。二者的 `all` 顺序与 manifest 一致，便于资源审计。

- [ ] **Step 3: 更新图标按钮测试并迁移所有调用点**

在 `MediaComponentsTest` 增加：

```kotlin
@Test
fun generatedIconButtonKeepsTouchTargetAndDescription() {
    rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            MediaViewerTheme {
                MediaIconButton(
                    icon = MediaIcons.Search,
                    contentDescription = "搜索",
                    onClick = {},
                )
            }
        }
    }
    val bounds = rule.onNodeWithContentDescription("搜索")
        .fetchSemanticsNode().boundsInRoot
    assertTrue(bounds.width >= 48f)
    assertTrue(bounds.height >= 48f)
}
```

将 `HomeScreen`、`ShareCard`、`MediaBreadcrumbs`、`PlayerBootstrapContent`、`VideoPlayerScreen`、`VideoControlsOverlay`、`PlaybackQueueSheet` 和 Snackbar 中的直接 Material 图标映射到 `MediaIcons` 或 `PlayerIcons`。

- [ ] **Step 4: 运行定向测试和源代码门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest '-Pkotlin.incremental=false' --tests '*MediaIconsTest' --tests '*PlayerIconsTest'
rg -n 'androidx\.compose\.material\.icons|Icons\.(Default|Filled|Outlined|AutoMirrored)' app/src/main/java
```

Expected: 单元测试 PASS；`rg` 无业务源码命中。随后从 `app/build.gradle.kts` 删除 `implementation(libs.androidx.compose.material.icons)` 并运行 `:app:compileDebugKotlin`。

- [ ] **Step 5: 提交统一图标接入**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui app/src/test app/src/androidTest app/build.gradle.kts
git commit -m "refactor: use generated warm paper icons"
```

---

### Task 3: 暖纸主题、纸张表面与共享选择组件

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/theme/Shapes.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/theme/Tokens.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/theme/Typography.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/WarmPaperSurface.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaFilterChips.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaSegmentedControl.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaBottomNavigation.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaScreenScaffold.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaTopAppBar.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaStatePanel.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaOptionMenu.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaConfirmDialog.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaSnackbarHost.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/theme/ThemeTokensTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaComponentsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaScaffoldTest.kt`

**Interfaces:**
- Produces: 暖色 `LightMediaColorScheme`、`DarkMediaColorScheme`、`LightMediaExtendedColors`、`DarkMediaExtendedColors` 和 `DefaultPlayerColors`。
- Produces: `@Composable fun WarmPaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`。
- Produces: `@Immutable data class FilterChipItem(val id: String, val label: String, val icon: MediaIcon? = null)`。
- Produces: `@Composable fun MediaFilterChips(items: List<FilterChipItem>, selectedId: String, onSelected: (String) -> Unit)`。
- Produces: `@Immutable data class SegmentItem(val id: String, val label: String, val icon: MediaIcon? = null)`。
- Produces: `@Composable fun MediaSegmentedControl(items: List<SegmentItem>, selectedId: String, onSelected: (String) -> Unit)`。
- Produces: `enum class TopLevelDestination { MEDIA_SOURCES, SETTINGS }`。
- Produces: `@Composable fun MediaBottomNavigation(selected: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit)`。

- [ ] **Step 1: 写暖色色值、形状和播放器浮层失败测试**

```kotlin
@Test
fun `theme uses approved warm paper palette`() {
    assertEquals(Color(0xFFF5EAD3), LightMediaColorScheme.background)
    assertEquals(Color(0xFFFFF7E8), LightMediaColorScheme.surface)
    assertEquals(Color(0xFFC96B2C), LightMediaColorScheme.primary)
    assertEquals(Color(0xFF2E2118), LightMediaColorScheme.onBackground)
    assertEquals(Color(0xFF77835F), LightMediaExtendedColors.folder)
    assertEquals(Color(0xB82E2118), DefaultPlayerColors.bottomScrimEnd)
    assertEquals(RoundedCornerShape(16.dp), MediaShapes.medium)
    assertEquals(RoundedCornerShape(24.dp), MediaShapes.large)
}
```

Run:

```powershell
.\gradlew.bat testDebugUnitTest '-Pkotlin.incremental=false' --tests '*ThemeTokensTest'
```

Expected: FAIL，当前主题仍为冷青色。

- [ ] **Step 2: 实现暖纸明暗主题和低干扰纸纹**

将全部角色色集中在 `Color.kt`，把圆角改为 `10/14/18/24/28dp` 级别。`WarmPaperSurface` 使用 `drawWithCache` 生成固定位置、alpha 不超过 `0.025f` 的细颗粒；禁止每帧随机生成。播放器 `canvas`、scrim 和控制颜色改为暖棕黑、奶油白和陶土橙。

```kotlin
val LightMediaColorScheme = lightColorScheme(
    primary = Color(0xFFC96B2C),
    onPrimary = Color(0xFFFFF7E8),
    background = Color(0xFFF5EAD3),
    onBackground = Color(0xFF2E2118),
    surface = Color(0xFFFFF7E8),
    onSurface = Color(0xFF2E2118),
    surfaceVariant = Color(0xFFEAD9BA),
    onSurfaceVariant = Color(0xFF756454),
)
```

- [ ] **Step 3: 写共享胶囊、卡片和安全区 Compose 失败测试**

```kotlin
@Test
fun filterChipsExposeOneSelectedItemAndReachableTouchTargets() {
    rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            MediaViewerTheme {
                MediaFilterChips(
                    items = listOf(
                        FilterChipItem("all", "全部"),
                        FilterChipItem("video", "视频"),
                    ),
                    selectedId = "video",
                    onSelected = {},
                )
            }
        }
    }
    rule.onNodeWithTag("filter_video").assertIsSelected()
    listOf("filter_all", "filter_video").forEach { tag ->
        val bounds = rule.onNodeWithTag(tag)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("$tag width", bounds.width >= 48f)
        assertTrue("$tag height", bounds.height >= 48f)
    }
}

@Test
fun screenScaffoldKeepsTopActionsInsideSafeDrawing() {
    rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            MediaViewerTheme {
                Box(Modifier.size(360.dp, 640.dp).testTag("safe_root")) {
                    MediaScreenScaffold(
                        title = "安全区",
                        actions = {
                            Box(Modifier.size(48.dp).testTag("top_action_more"))
                        },
                        contentWindowInsets = WindowInsets(
                            left = 12.dp,
                            top = 42.dp,
                            right = 18.dp,
                            bottom = 24.dp,
                        ),
                    ) { }
                }
            }
        }
    }
    val root = rule.onNodeWithTag("safe_root").fetchSemanticsNode().boundsInRoot
    val action = rule.onNodeWithTag("top_action_more").fetchSemanticsNode().boundsInRoot
    assertTrue(action.top >= root.top + 42f)
    assertTrue(action.right <= root.right - 18f)
}
```

- [ ] **Step 4: 实现共享表面、筛选、分段器和底部导航**

选择控件必须以稳定 id 标记选中态，不把显示文字当作状态键。`MediaBottomNavigation` 只公开 `MEDIA_SOURCES` 和 `SETTINGS`；现有导航栈仍由 `MediaViewerApp` 拥有。

```kotlin
@Immutable
data class FilterChipItem(
    val id: String,
    val label: String,
    val icon: MediaIcon? = null,
)

@Immutable
data class SegmentItem(
    val id: String,
    val label: String,
    val icon: MediaIcon? = null,
)

enum class TopLevelDestination {
    MEDIA_SOURCES,
    SETTINGS,
}
```

- [ ] **Step 5: 运行主题与共享组件测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest '-Pkotlin.incremental=false' --tests '*ThemeTokensTest'
.\gradlew.bat connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaComponentsTest,com.local.mediaviewer.MediaScaffoldTest'
```

Expected: 主题单元测试 PASS；有设备时 Compose 测试 PASS。无设备时 connected 项记录 `NOT RUN`，不能写 PASS。

- [ ] **Step 6: 提交主题和共享组件**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/theme app/src/main/java/com/local/mediaviewer/ui/components app/src/test/java/com/local/mediaviewer/ui/theme app/src/androidTest/java/com/local/mediaviewer/MediaComponentsTest.kt app/src/androidTest/java/com/local/mediaviewer/MediaScaffoldTest.kt
git commit -m "feat: add warm paper design system"
```

---

### Task 4: 重写媒体源首页与设置页布局

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/home/ConnectionStatusCard.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/home/ShareCard.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsSection.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/settings/MediaUrlField.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`

**Interfaces:**
- Preserves: `HomeScreen(state, onRetry, onOpenSettings, onOpenShare)`。
- Preserves: `SettingsScreen` 的地址、测试、保存、图片模式、自动隐藏、返回和放弃回调。
- Produces: 首页分组 tag `home_saved_shares`、`home_quick_actions`、`home_settings_entry`。
- Produces: 底部导航 tag `bottom_nav_sources`、`bottom_nav_settings`。
- Produces: 首页本地搜索 tag `home_search_action`、`home_search_field`；搜索只按已加载共享的 `displayName` 与 `urlPrefix` 过滤，不发网络请求。

- [ ] **Step 1: 写首页分组和底部导航失败测试**

```kotlin
@Test
fun connectedHomeUsesWarmPaperSectionsAndTopLevelNavigation() {
    showConnectedHome()
    rule.onNodeWithText("媒体源").assertIsDisplayed()
    rule.onNodeWithTag("home_saved_shares").assertExists()
    rule.onNodeWithTag("home_quick_actions").assertExists()
    rule.onNodeWithTag("bottom_nav_sources").assertIsSelected()
    rule.onNodeWithTag("bottom_nav_settings").assertHasClickAction()
}

@Test
fun homeSearchFiltersOnlyLoadedShares() {
    showConnectedHome()
    rule.onNodeWithTag("home_search_action").performClick()
    rule.onNodeWithTag("home_search_field").performTextInput("Middle")
    rule.onNodeWithText("MiddleDir").assertExists()
    rule.onNodeWithText("私有目录").assertDoesNotExist()
}
```

在 `HomeSettingsScreenTest` 中加入固定 fixture，避免测试依赖网络或 ViewModel：

```kotlin
private fun showConnectedHome() {
    rule.setContent {
        MediaViewerTheme {
            HomeScreen(
                state = HomeUiState.Connected(
                    ipv4 = "192.0.2.10",
                    shares = listOf(HOME_ANONYMOUS_SHARE, HOME_BASIC_SHARE),
                ),
                onRetry = {},
                onOpenSettings = {},
                onOpenShare = {},
            )
        }
    }
}
```

保留原测试对连接、禁用共享、错误重试、2× 字体和第 30 个共享可达性的断言。

- [ ] **Step 2: 运行首页/设置测试并确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.HomeSettingsScreenTest,com.local.mediaviewer.MediaViewerNavigationTest'
```

Expected: 新分组和底部导航断言 FAIL；原有行为断言保持当前结果。

- [ ] **Step 3: 实现首页纸张列表和一级导航**

首页 TopBar 显示“媒体源”、搜索和更多。搜索按钮展开本地输入框，用 `rememberSaveable` 保存查询文本，并对当前 `HomeUiState.Connected.shares` 按 `displayName`、`urlPrefix` 做不区分大小写的过滤，不触发服务器请求；关闭搜索时清空查询。更多菜单提供“设置”并复用 `onOpenSettings`。共享行使用统一大卡片内分隔，不伪造远程缩略图；无缩略图时显示媒体共享图标色块。

`MediaViewerApp` 只把底部设置入口映射到现有 Settings route，把媒体源映射到现有 Home route；播放器、图片阅读和 Browser 页面不显示一级底部导航。

- [ ] **Step 4: 写设置卡片和五个自动隐藏选项保持测试**

```kotlin
@Test
fun settingsKeepsAllVideoAutoHideChoicesInsidePaperSection() {
    showSettings(SettingsUiState(videoControlsAutoHide = VideoControlsAutoHide.TEN_SECONDS))
    rule.onNodeWithTag("settings_video_section").assertExists()
    listOf("3 秒", "5 秒", "10 秒", "15 秒", "不隐藏")
        .forEach { rule.onNodeWithText(it).assertExists() }
    rule.onNodeWithTag("video_controls_auto_hide_10").assertIsSelected()
}
```

同一测试类的 `showSettings` 固定调用真实页面签名：

```kotlin
private fun showSettings(state: SettingsUiState) {
    rule.setContent {
        MediaViewerTheme {
            SettingsScreen(
                state = state,
                onInputChanged = {},
                onTest = {},
                onSave = {},
                onDefaultImageModeChanged = {},
                onVideoControlsAutoHideChanged = {},
                onBack = {},
            )
        }
    }
}
```

- [ ] **Step 5: 实现设置页卡片布局并运行失败项**

服务器连接、视频播放和图片阅读分别使用 `SettingsSection` 暖纸卡。URL 错误紧邻输入框，保存失败保留 live region。运行 Step 2 中失败的测试类；已经通过的人工首页操作不重复执行。

- [ ] **Step 6: 提交首页与设置页**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/home app/src/main/java/com/local/mediaviewer/ui/settings app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt
git commit -m "feat: redesign home and settings screens"
```

---

### Task 5: 重写目录浏览、面包屑、筛选和空目录布局

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/MediaBreadcrumbs.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/MediaFileRow.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserFormatters.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/browser/BrowserFormattersTest.kt`

**Interfaces:**
- Preserves: `BrowserScreen` 的现有状态、返回、刷新、目录打开、媒体打开和行菜单回调。
- Produces: 纯 UI `enum class BrowserFilter { ALL, FOLDERS, VIDEO, AUDIO, IMAGE, GIF }`，只过滤当前已加载列表，不发起网络请求。
- Produces: filter tags `browser_filter_all|folders|video|audio|image|gif`。
- Preserves: `MediaStateKind.EMPTY` 标题固定为“空文件夹”，路径与工具条仍可见。

- [ ] **Step 1: 写筛选、列表容器和空目录失败测试**

```kotlin
@Test
fun emptyDirectoryKeepsPathAndShowsCenteredEmptyFolder() {
    rule.setContent {
        BrowserScreen(
            state = BrowserUiState.Empty(
                browserPage(
                    breadcrumbs = listOf(
                        Breadcrumb("根", "http://media.example/"),
                        Breadcrumb("Ayame", "http://media.example/MiddleDir/Ayame/"),
                    ),
                    entries = emptyList(),
                ),
            ),
            onEntryClick = {},
            onBreadcrumbClick = {},
            onRetry = {},
            onBack = {},
        )
    }
    rule.onNodeWithText("Ayame").assertExists()
    rule.onNodeWithText("空文件夹").assertIsDisplayed()
    rule.onNodeWithText("加载子目录失败").assertDoesNotExist()
}

@Test
fun videoFilterOnlyChangesVisibleLoadedRows() {
    rule.setContent {
        BrowserScreen(
            state = BrowserUiState.Content(
                browserPage(
                    entries = listOf(
                        browserEntry("clip.mp4", MediaKind.VIDEO),
                        browserEntry("cover.jpg", MediaKind.IMAGE),
                    ),
                ),
            ),
            onEntryClick = {},
            onBreadcrumbClick = {},
            onRetry = {},
            onBack = {},
        )
    }
    rule.onNodeWithTag("browser_filter_video").performClick()
    rule.onNodeWithText("clip.mp4").assertExists()
    rule.onNodeWithText("cover.jpg").assertDoesNotExist()
}
```

- [ ] **Step 2: 运行 Browser 定向测试并确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest'
```

Expected: 新筛选断言 FAIL；现有目录打开、媒体菜单和空目录业务断言不应因本任务改动提前改变。

- [ ] **Step 3: 实现圆角面包屑、胶囊筛选和单卡片列表**

筛选只使用 `rememberSaveable(currentPath) { mutableStateOf(BrowserFilter.ALL) }`，切换目录时回到全部。映射规则由已有 `MediaKind` 和 GIF 扩展名判断函数提供，不修改 Repository 返回值。

```kotlin
val visibleEntries = remember(entries, selectedFilter) {
    entries.filter { entry -> selectedFilter.accepts(entry) }
}
```

文件夹行显示鼠尾草绿；视频为陶土橙；音频为低饱和棕；图片/GIF 为橄榄绿；未知文件为灰棕。菜单点击和禁用语义保持原样。

- [ ] **Step 4: 运行 Browser 失败项和格式化单元测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest '-Pkotlin.incremental=false' --tests '*BrowserFormattersTest'
.\gradlew.bat connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest'
```

Expected: 目标测试 PASS；若 connected 首轮部分通过、部分失败，后续只用测试方法过滤重验失败方法。

- [ ] **Step 5: 提交目录浏览重写**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/browser app/src/test/java/com/local/mediaviewer/ui/browser app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt
git commit -m "feat: redesign warm paper browser"
```

---

### Task 6: 重写普通/全屏视频、音频、迷你播放器和播放队列

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerControls.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackPrimaryAction.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerUtilityRow.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTimeline.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/AudioArtworkPlaceholder.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackSpeedMenu.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoScaleMenu.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackModeButton.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/VideoGestureLayerTest.kt`

**Interfaces:**
- Preserves: 全部现有 Player state、controller callback、gesture reducer、自动隐藏、后台播放、Surface 和队列接口。
- Preserves: 普通视频更多菜单包含后台播放、倍速、模式、比例；全屏更多菜单只包含后台播放。
- Preserves: 全屏进度条下方显示倍速、模式和比例；音频不显示视频专属项。
- Produces: 暖棕半透明 overlay、陶土主播放按钮、奶油白控制和暖色时间轴。

- [ ] **Step 1: 写普通/全屏菜单和视觉层级失败测试**

```kotlin
@Test
fun normalVideoKeepsOptionsInMoreMenuAndUsesPrimaryTransportAction() {
    showVideo(playerState(name = "movie.mp4", kind = MediaKind.VIDEO))
    rule.onNodeWithTag("video_primary_action").assertExists().assertHasClickAction()
    rule.onNodeWithContentDescription("更多播放选项").performClick()
    listOf("后台播放", "播放速度", "播放模式", "画面比例")
        .forEach { rule.onNodeWithText(it).assertExists() }
}

@Test
fun fullscreenKeepsConfigurationBelowTimelineAndSafeFromInsets() {
    showFullscreen(
        hasShownGestureHint = true,
        safeDrawingInsets = WindowInsets(
            left = 16.dp,
            top = 48.dp,
            right = 20.dp,
            bottom = 32.dp,
        ),
    )
    rule.onNodeWithTag("fullscreen_playback_configuration").assertExists()
    rule.onNodeWithText("倍速").assertExists()
    rule.onNodeWithText("播放模式").assertExists()
    rule.onNodeWithText("画面比例").assertExists()
    val root = rule.onNodeWithTag("fullscreen_root").fetchSemanticsNode().boundsInRoot
    val controls = rule.onNodeWithTag("fullscreen_bottom_controls")
        .fetchSemanticsNode().boundsInRoot
    assertTrue(controls.left >= root.left + 16f)
    assertTrue(controls.right <= root.right - 20f)
    assertTrue(controls.bottom <= root.bottom - 32f)
}
```

- [ ] **Step 2: 运行播放器 Compose 测试并确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.PlaybackControlsTest'
```

Expected: 新暖纸层级/tag 断言 FAIL；现有手势和菜单职责测试保持当前结果。

- [ ] **Step 3: 实现普通与全屏视频暖棕浮层**

普通视频 Surface 继续占满竖屏可用画布，Top/Bottom 区域只使用半透明 `Brush.verticalGradient` 覆盖。控制显示状态仍来自现有 reducer；不得为了重排新增计时任务。主播放按钮使用 `64dp`/全屏 `72dp` 容器，图标为 `32dp`/`40dp`。

全屏中央只保留后退、播放或暂停、前进；底部按“时间轴 → 配置 → 工具”排列。所有按钮继续调用现有 controller callback。

- [ ] **Step 4: 写音频和队列边界失败测试**

```kotlin
@Test
fun audioUsesWarmArtworkAndNeverShowsVideoOnlyControls() {
    showAudio(playerState(name = "song.flac", kind = MediaKind.AUDIO))
    rule.onNodeWithTag("audio_artwork_card").assertExists()
    rule.onNodeWithText("画面比例").assertDoesNotExist()
    rule.onNodeWithContentDescription("进入全屏").assertDoesNotExist()
    rule.onNodeWithContentDescription("锁定控制").assertDoesNotExist()
}

@Test
fun queueKeepsDragDeleteAndCurrentItemSemantics() {
    val current = item("current", "当前曲目.flac", MediaKind.AUDIO)
    showQueue(
        PlaybackQueue(
            items = listOf(current),
            currentMediaKey = current.mediaKey,
        ),
    )
    rule.onNodeWithTag("queue_row:current").assertIsSelected()
    rule.onNodeWithContentDescription("拖动排序").assertExists()
    rule.onNodeWithContentDescription("从播放队列移除").assertExists()
}
```

- [ ] **Step 5: 实现音频、迷你播放器和播放队列暖纸布局**

音频复用 `PlaybackTimeline`、`PlaybackTransportControls` 和工具行，但只传速度、模式、队列和音量。`NowPlayingBar` 放在底部导航上方的暖纸浮层中；队列保持现有 drag session、删除、更多和持久化调用，仅替换表面和当前项强调。

- [ ] **Step 6: 运行播放器与队列失败项**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.PlaybackControlsTest,com.local.mediaviewer.PlaybackQueueUiTest,com.local.mediaviewer.VideoGestureLayerTest'
```

Expected: 目标测试 PASS；暂停恢复、进度、单击/双击和前后台只验证既有测试，不修改业务实现来迎合视觉断言。

- [ ] **Step 7: 提交播放器重写**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/player app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt app/src/androidTest/java/com/local/mediaviewer/VideoGestureLayerTest.kt
git commit -m "feat: redesign warm paper players and queue"
```

---

### Task 7: 重写单图、GIF 与漫画阅读布局

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderToolbar.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImagePager.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageItemErrorPanel.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageDecodePolicy.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/GifImageLoaderInstrumentedTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ComicReaderDynamicLoadingTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/image/ZoomStateTest.kt`
- Create: `app/src/test/java/com/local/mediaviewer/ui/image/SingleImageDecodePolicyTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/image/ComicReaderPolicyTest.kt`

**Interfaces:**
- Preserves: 当前 `ImageReaderScreen` state 和事件回调、`SingleImagePager` 左右切换、`SingleImageViewer` zoom state、GIF 请求、ComicReader 稳定请求键与动态加载。
- Produces: overlay tag `image_reader_controls`、缩放工具条 tag `image_zoom_toolbar`、模式分段器 tag `image_reader_modes`。
- Produces: 缩小、当前比例、放大、适合屏幕回调，只调用现有 zoom state，不重建 image request。
- Changes: `ImageReaderScreen` 新增可测试默认参数 `safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing`，只用于必要控件 inset，不进入图片请求。
- Produces: `SingleImageDecodePolicy.target(viewportWidthPx: Int, viewportHeightPx: Int, scale: Float, animatedGif: Boolean): ImageDecodeSize`；GIF 始终使用 `scale = 1f` 的稳定解码尺寸，静态图沿用现有缩放分档。

- [ ] **Step 1: 写单图翻页、缩放工具条和模式分段器失败测试**

```kotlin
@Test
fun singleImageShowsEdgeNavigationZoomToolbarAndModeSegments() {
    setScreen(contentState(ImageReaderMode.SINGLE))
    rule.onNodeWithContentDescription("上一张").assertExists()
    rule.onNodeWithContentDescription("下一张").assertExists()
    rule.onNodeWithTag("image_zoom_toolbar").assertExists()
    rule.onNodeWithTag("image_reader_modes").assertExists()
}

@Test
fun controlsStayInsideDisplayCutoutSafeArea() {
    setScreen(
        state = contentState(ImageReaderMode.SINGLE),
        safeDrawingInsets = WindowInsets(
            left = 14.dp,
            top = 48.dp,
            right = 18.dp,
            bottom = 28.dp,
        ),
    )
    val root = rule.onNodeWithTag("image_reader_root").fetchSemanticsNode().boundsInRoot
    val controls = rule.onNodeWithTag("image_reader_controls")
        .fetchSemanticsNode().boundsInRoot
    assertTrue(controls.left >= root.left + 14f)
    assertTrue(controls.right <= root.right - 18f)
    assertTrue(controls.top >= root.top + 48f)
    assertTrue(controls.bottom <= root.bottom - 28f)
}
```

- [ ] **Step 2: 运行图片阅读定向测试并确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest'
```

Expected: 新缩放工具条和模式分段器断言 FAIL；已有左右滑动和模式切换断言保持当前结果。

- [ ] **Step 3: 实现内容优先的顶部、边缘和底部控件**

图片背景延伸到边缘，必要控件位于 `safeDrawing`。顶部为返回、标题、网格/阅读模式和更多；左右按钮只在存在相邻项时出现；底部使用暖棕半透明缩放胶囊和图片/动图/漫画分段器。单击内容继续切换控件显隐。

- [ ] **Step 4: 写 GIF 稳定解码尺寸和漫画请求键保持测试**

```kotlin
@Test
fun `animated gif keeps same decode size across pinch zoom`() {
    val original = SingleImageDecodePolicy.target(
        viewportWidthPx = 1080,
        viewportHeightPx = 1920,
        scale = 1f,
        animatedGif = true,
    )
    val zoomed = SingleImageDecodePolicy.target(
        viewportWidthPx = 1080,
        viewportHeightPx = 1920,
        scale = 4f,
        animatedGif = true,
    )
    assertEquals(original, zoomed)
}
```

漫画策略单元测试继续断言实时缩放不进入请求尺寸 key，单图 pager 在 `scale == 1f` 时把横向拖动交给翻页，在放大时交给图片平移。

- [ ] **Step 5: 实现缩放按钮对现有 ZoomState 的薄适配**

按钮只调用现有 zoom/pan 状态：缩小和放大按固定步长 clamp 到允许范围，“适合屏幕”回到 `1f`。`SingleImageViewer` 通过文件扩展名识别 GIF，并使用 `SingleImageDecodePolicy` 的稳定 GIF 尺寸；不得改变 Coil model、request data、GIF decoder、ComicReader decode size 或 key。

- [ ] **Step 6: 运行图片/GIF/漫画失败项**

Run:

```powershell
.\gradlew.bat testDebugUnitTest '-Pkotlin.incremental=false' --tests '*ZoomStateTest' --tests '*SingleImageDecodePolicyTest' --tests '*ComicReaderPolicyTest'
.\gradlew.bat connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.GifImageLoaderInstrumentedTest,com.local.mediaviewer.ComicReaderDynamicLoadingTest'
```

Expected: 目标测试 PASS；如 connected 无设备，记录 `NOT RUN`。

- [ ] **Step 7: 提交图片阅读重写**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/image app/src/test/java/com/local/mediaviewer/image/ZoomStateTest.kt app/src/test/java/com/local/mediaviewer/ui/image/SingleImageDecodePolicyTest.kt app/src/test/java/com/local/mediaviewer/ui/image/ComicReaderPolicyTest.kt app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt app/src/androidTest/java/com/local/mediaviewer/GifImageLoaderInstrumentedTest.kt app/src/androidTest/java/com/local/mediaviewer/ComicReaderDynamicLoadingTest.kt
git commit -m "feat: redesign image gif and comic reader"
```

---

### Task 8: 静态门禁、失败项重验与基础功能验收记录

**Files:**
- Create: `docs/verification/2026-08-02-warm-paper-ui-icon-system.md`
- Modify only if a gate fails: files owned by Tasks 1–7 that caused that specific failure

**Interfaces:**
- Consumes: Tasks 1–7 的所有已提交资产、组件和页面。
- Produces: 自动化结果、未运行项、人工基础功能结果、图标联系表路径和构建状态的证据文档。

- [ ] **Step 1: 运行图标静态质检**

Run:

```powershell
python -m unittest discover -s tools/icon_pipeline/tests -p 'test_*.py' -v
python tools/icon_pipeline/process_icon_sheet.py --manifest tools/icon_pipeline/icon_manifest.json --source artifacts/warm-paper-icons/transparent --output app/src/main/res/drawable-nodpi --report artifacts/warm-paper-icons/processed --verify-only
rg -n 'androidx\.compose\.material\.icons|Icons\.(Default|Filled|Outlined|AutoMirrored)' app/src/main/java
```

Expected: Python tests PASS；全部图标通过 alpha/尺寸/中心检查；`rg` 无业务源码命中。

- [ ] **Step 2: 运行 Android 编译和 JVM 定向套件**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin testDebugUnitTest '-Pkotlin.incremental=false' --tests '*ThemeTokensTest' --tests '*MediaIconsTest' --tests '*PlayerIconsTest' --tests '*BrowserFormattersTest' --tests '*ZoomStateTest' --tests '*SingleImageDecodePolicyTest' --tests '*ComicReaderPolicyTest'
```

Expected: BUILD SUCCESSFUL。若某个测试失败，只修复并重跑对应 `--tests` 目标；不要因为单项失败重复运行已通过测试。

- [ ] **Step 3: 有可用 API 36 设备时运行定向 Compose 套件**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaComponentsTest,com.local.mediaviewer.MediaScaffoldTest,com.local.mediaviewer.HomeSettingsScreenTest,com.local.mediaviewer.MediaViewerNavigationTest,com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.PlaybackControlsTest,com.local.mediaviewer.PlaybackQueueUiTest,com.local.mediaviewer.VideoGestureLayerTest,com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.GifImageLoaderInstrumentedTest,com.local.mediaviewer.ComicReaderDynamicLoadingTest'
```

Expected: 目标类 PASS。无设备、设备 API 不符或真实媒体不可用时分别记录 `NOT RUN`/`BLOCKED`，不能把编译成功等同于设备验收。

- [ ] **Step 4: 执行一次基础功能人工检查**

按顺序检查：首页进入共享；普通目录、子目录和空目录；视频单击/双击、暂停恢复、进度、全屏和前后台；音频和队列编辑；静态图、GIF、漫画、左右翻页和缩放不重载；自动隐藏设置持久化。记录每项 `PASS`、`FAIL`、`NOT RUN` 或 `BLOCKED`，失败后只重验失败项。

- [ ] **Step 5: 对比概念图做一次基础视觉检查**

只检查暖米白画布、陶土主色、鼠尾草媒体色、暖棕半透明播放器、纸张卡片层级、图标视觉居中、刘海/挖孔安全区、320dp + 2× 字体和横屏可达性。不扩展为多轮设计审查。

- [ ] **Step 6: 写中文验收记录并提交**

```powershell
git add docs/verification/2026-08-02-warm-paper-ui-icon-system.md
git commit -m "docs: verify warm paper UI and icon system"
```

记录必须列出实际命令、退出码、通过/失败数量、设备信息、联系表路径、APK 是否未构建，以及所有未执行的真实服务器或设备项目。

---

## 执行依赖与并行边界

```text
Task 1 Image2 资产
        ↓
Task 2 图标资源类型
        ↓
Task 3 主题与共享组件
        ↓
   ┌────┼─────────────┐
Task 4 Task 5       Task 7
首页设置 目录浏览     图片阅读
   └────┼─────────────┘
        ↓
Task 6 播放器与队列
        ↓
Task 8 验证与记录
```

- Task 1 内的四组 Image2 母版可以由三个子 Agent 和主 Agent 并行生成，因为各自只写不同 PNG。
- Task 4、Task 5、Task 7 可在 Task 3 提交后并行，但不得共同修改 `MediaViewerApp.kt` 或共享组件；`MediaViewerApp.kt` 只归 Task 4 所有。
- Task 6 最后接入播放器，以便直接消费已经稳定的主题、图标和共享组件。
- Task 8 串行执行，验证期间不再发起新的 Image2 风格探索。
