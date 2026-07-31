# 媒体化设计系统、应用壳层与普通页面视觉统一 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立可复用的媒体化 Material 3 设计系统和不遮挡内容的应用壳层，并在不改变现有业务状态机的前提下完成首页、目录浏览、设置和图片阅读器的统一视觉、无障碍与屏幕适配。

**Architecture:** 主题层把明暗 `ColorScheme`、扩展语义色、播放器黑底色、排版、形状、间距、尺寸和动效集中在 `ui/theme`；共享 Compose 组件只接收窄化展示数据和回调，不读取 ViewModel、Repository、Activity 或播放控制器。根 `MediaAppScaffold` 统一 Snackbar、系统安全区和迷你播放器 bottom bar，普通页面通过 `MediaScreenScaffold` 消费 padding；本计划只消费现有 Home/Browser/Settings/Image 状态与回调，播放器、队列内部和流程 F1–F7 由后续计划实现。

**Tech Stack:** Kotlin 2.3.21、Jetpack Compose BOM 2026.06.00、Material 3、Navigation Compose 2.9.8、Coil 3.5.0、JUnit 4、Compose UI Test、Android SDK 36、minSdk 29、targetSdk 36、PowerShell 7

## Global Constraints

- 采用方案 A：保留 Material 3 的平台语义、焦点、返回、键盘和弹层行为，只包装具有跨页面产品语义或容易不一致的组件。
- 同时交付完整浅色与深色主题；视频黑底使用独立 `PlayerColors`，普通页面不得直接使用播放器亮青色硬编码。
- 普通文字对背景至少 4.5:1；大字、图标和关键非文字控制至少 3:1；状态不能只靠颜色表达。
- 继续使用系统字体；不下载自定义字体，不新增重型动画或截图依赖，不全面自绘 Material 基础控件。
- 形状固定为 8/12/16/24dp 与 pill；间距固定为 4/8/12/16/20/24/32dp；手机 gutter 16dp，宽度至少 600dp 时为 24dp。
- 所有可操作图标和拖动入口的触摸区至少 48dp；测试覆盖 `contentDescription`、`stateDescription`、selected/disabled/adjustable 语义。
- 根壳层必须让迷你播放器占用真实 bottom bar 空间；Home 和 Browser 最后一项不能被遮挡。
- 当前只有一个稳定顶层入口，不增加 Bottom NavigationBar 或 NavigationRail。
- 保持现有 `MediaViewerApp` 导航目的地和返回栈；页面只发出回调，不自行持有 NavController。
- 本计划不修改 `PlaybackStatus`、`PlayerUiState`、LibVLC/Media3、播放 Service、Surface 恢复、后台音频、播放器业务控件或 `PlaybackQueueSheet` 内部；Task 3 只提供无播放器依赖的通用 `MediaTimelineSlider` / `MediaVerticalLevelControl`，播放器计划负责把它们接入真实播放与音量回调。
- 本计划不修改 Home/Browser/Settings/Image 的 ViewModel、Repository 或流程 F1–F7；只消费执行时已经稳定的 UI 状态与一次性事件接口。
- 图片阅读器只统一主题、工具栏、状态面板和局部加载/错误视觉；不重写缩放、平移、条漫懒加载、排序、锚点或图片重试代数。
- 不增加搜索、筛选、收藏、历史、批量管理、文件删除、登录、多服务器、字幕、多音轨、投屏或在线封面。
- 不重新引入播放时间轴下方的第二加载进度条。
- 保持 `versionName = "1.1.0"`、`versionCode = 3`、`minSdk = 29`、`targetSdk = 36` 和 Release 仅 `arm64-v8a`。
- Windows 上所有 Gradle 调用串行执行并传入 `'-Pkotlin.incremental=false'`；禁止并行 Gradle 进程污染 Kotlin 增量缓存。
- 自动化结果、API 36 connected tests、人工视觉检查和真实设备/服务器验收分开记录，未执行项标记 `NOT RUN`。

## Dependency and Ownership Map

| Wave | Tasks | Shared-file rule | Gate |
|---|---|---|---|
| 0 | foundation Tasks 1–3 | 可并行准备测试，但 Task 2 只消费 Task 1，Task 3 只消费 Tasks 1–2 | 主题、共享组件、`MediaUrlField` / `SettingsSection` API 全部稳定 |
| 1 | flow plan Task 3/4/5 screen wiring | 分别独占 Browser、Settings、Image Screen/Test；其中 flow Task 4 消费本计划 Task 3 已创建的无状态设置原语 | flow 状态与页面接线提交完成 |
| 2 | foundation Tasks 4–7 | Home 可独立；Browser/Settings/Image 分别等待 flow Task 3/4/5 并接收对应文件所有权 | 页面视觉、尺寸与无障碍测试通过 |
| 3 | flow plan Task 7 root integration + foundation Task 8 verification | `MediaViewerApp.kt` 始终由 flow Task 7 单一负责人修改；Task 8 只增加回归断言、删除无人引用的旧壳层并写证据 | 所有前驱提交完成 |

固定执行顺序是 **foundation Tasks 1–3 → flow Task 4 Settings 状态与接线 → foundation Task 6 Settings 布局**。`SettingsScreen.kt` 与 `HomeSettingsScreenTest.kt` 必须在 flow Task 4 提交后才交给 foundation Task 6，两个任务不得并行修改。Browser 和 Image 采用同样的串行交接：flow Task 3 → foundation Task 5、flow Task 5 → foundation Task 7。

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/com/local/mediaviewer/ui/theme/Color.kt` | 明暗 Material 色板、`MediaExtendedColors`、`PlayerColors` 和对比度稳定值 |
| `app/src/main/java/com/local/mediaviewer/ui/theme/Typography.kt` | appTitle、screenTitle、sectionTitle、body、metadata、badge、playerTime 排版映射 |
| `app/src/main/java/com/local/mediaviewer/ui/theme/Shapes.kt` | 8/12/16/24dp 与 pill 形状 |
| `app/src/main/java/com/local/mediaviewer/ui/theme/Tokens.kt` | 间距、尺寸、动效和非 Material 令牌的 CompositionLocal |
| `app/src/main/java/com/local/mediaviewer/ui/theme/Theme.kt` | 将 Material 与扩展令牌安装到 Composition tree |
| `app/src/main/java/com/local/mediaviewer/MainActivity.kt` | 启用统一 edge-to-edge；播放器退出全屏后仍保持相同窗口策略 |
| `app/src/main/res/values/themes.xml`、`values-night/themes.xml` | 启动窗口和系统栏资源主题，避免平台默认主题闪烁 |
| `app/src/main/java/com/local/mediaviewer/ui/icons/MediaIcons.kt` | 导航、文件类型、连接、状态和浏览操作图标目录 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaGlyph.kt` | 文件/状态图标的双色 tonal 容器 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaButtons.kt` | 主、次、危险按钮的 loading/disabled 视觉与语义 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaIconButton.kt` | 48dp 图标按钮、selected/loading/stateDescription 语义 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaStatePanel.kt` | Loading/Empty/Offline/Error 的统一状态面板 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaOptionMenu.kt` | Browser 和 Image 共用的图标、选中标记与关闭语义 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaConfirmDialog.kt` | 普通/危险确认、焦点与显式关闭规则 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaBottomSheet.kt` | 标题、动作、安全区和滚动内容槽 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaTimelineSlider.kt` | 播放器计划消费的单轨时间轴与 adjustable 语义 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaVerticalLevelControl.kt` | 播放器计划消费的真实竖向调节与 adjustable 语义 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaTopAppBar.kt` | 普通/沉浸式 TopAppBar 的标题、省略和导航容器 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaAppScaffold.kt` | 全局 Snackbar、bottom bar、系统 inset 和 NavHost 内容槽 |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaScreenScaffold.kt` | 普通页面 TopBar、背景和 content padding |
| `app/src/main/java/com/local/mediaviewer/ui/components/MediaSnackbarHost.kt` | 成功/错误 Snackbar 外观以及 bottom bar 避让 |
| `app/src/main/java/com/local/mediaviewer/ui/settings/MediaUrlField.kt` | Task 3 先提供的无状态 URL 输入/连接结果原语，供 flow Task 4 接线 |
| `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsSection.kt` | Task 3 先提供的设置分区与可选择设置行容器 |
| `app/src/main/java/com/local/mediaviewer/ui/home/ConnectionStatusCard.kt` | 首页 Connecting/Connected/Error 状态卡 |
| `app/src/main/java/com/local/mediaviewer/ui/home/ShareCard.kt` | 可浏览/禁用共享的图标、说明和进入暗示 |
| `app/src/main/java/com/local/mediaviewer/ui/home/HomeScreen.kt` | LazyColumn 首页布局和现有 Home 回调接线 |
| `app/src/main/java/com/local/mediaviewer/ui/browser/MediaBreadcrumbs.kt` | 可横滚 breadcrumb、分隔符、当前段语义 |
| `app/src/main/java/com/local/mediaviewer/ui/browser/MediaFileRow.kt` | 文件类型、名称、元数据、点击和菜单锚点 |
| `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt` | 现有 Browser 状态的统一壳层、状态面板和浏览列表 |
| `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt` | 可滚动、IME 安全的设置页与现有回调接线 |
| `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt` | 深色沉浸壳层、目录状态和端点刷新视觉 |
| `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderToolbar.kt` | 渐变工具栏、模式和排序菜单 |
| `app/src/main/java/com/local/mediaviewer/ui/image/ImageItemErrorPanel.kt` | 单图错误的局部状态面板 |
| `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt`、`ComicReader.kt` | 只替换 loading/error 视觉，保留现有图片请求和手势 |
| `app/src/androidTest/java/com/local/mediaviewer/*UiTest.kt` | 共享组件、壳层、页面、无障碍与 320/600dp 适配 |
| `app/src/test/java/com/local/mediaviewer/ui/theme/ThemeTokensTest.kt` | 精确令牌值和对比度门禁 |
| `app/src/test/java/com/local/mediaviewer/ui/icons/MediaIconsTest.kt` | 图标目录尺寸和身份稳定性 |
| `docs/verification/2026-07-31-media-ui-foundation-pages.md` | 本计划自动化、connected、视觉和未执行项证据 |

---

### Task 1: Theme Tokens and Launch Theme

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/theme/Color.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/theme/Typography.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/theme/Shapes.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/theme/Tokens.kt`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/themes.xml`
- Create: `app/src/test/java/com/local/mediaviewer/ui/theme/ThemeTokensTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `@Composable fun MediaViewerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`
- Produces: `val DarkMediaColorScheme: ColorScheme`
- Produces: `val LightMediaColorScheme: ColorScheme`
- Produces: `data class MediaExtendedColors(...)`
- Produces: `data class PlayerColors(...)`
- Produces: `object MediaTheme`
- Produces: `val MediaTheme.extendedColors: MediaExtendedColors`
- Produces: `val MediaTheme.playerColors: PlayerColors`
- Produces: `val MediaTheme.spacing: MediaSpacing`
- Produces: `val MediaTheme.sizing: MediaSizing`
- Produces: `val MediaTheme.motion: MediaMotion`
- Produces: `val MediaTheme.elevation: MediaElevation`
- Produces: standard `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and `MaterialTheme.shapes`
- Consumes: no application state, Activity, ViewModel, Repository or player type.

- [ ] **Step 1: Write failing token and contrast tests**

Create `ThemeTokensTest.kt`:

```kotlin
package com.local.mediaviewer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {
    @Test
    fun `brand colors use approved light dark and video mappings`() {
        assertEquals(Color(0xFF67D9F0), DarkMediaColorScheme.primary)
        assertEquals(Color(0xFF006878), LightMediaColorScheme.primary)
        assertEquals(Color(0xFF67D9F0), DefaultPlayerColors.active)
        assertEquals(Color(0xFFF5FAFF), DefaultPlayerColors.control)
    }

    @Test
    fun `approved foreground pairs meet contrast gates`() {
        val textPairs = listOf(
            DarkMediaColorScheme.onBackground to DarkMediaColorScheme.background,
            DarkMediaColorScheme.onSurface to DarkMediaColorScheme.surface,
            LightMediaColorScheme.onBackground to LightMediaColorScheme.background,
            LightMediaColorScheme.onSurface to LightMediaColorScheme.surface,
        )
        textPairs.forEach { (foreground, background) ->
            assertTrue(
                "$foreground on $background",
                contrastRatio(foreground, background) >= 4.5f,
            )
        }
        assertTrue(
            contrastRatio(
                DefaultPlayerColors.control,
                DefaultPlayerColors.canvas,
            ) >= 3f,
        )
    }

    @Test
    fun `spacing shapes typography motion and dimensions stay on approved scale`() {
        assertEquals(16f, DefaultMediaSpacing.pageGutter.value)
        assertEquals(24f, DefaultMediaSpacing.widePageGutter.value)
        assertEquals(48f, DefaultMediaSizing.minimumTouchTarget.value)
        assertEquals(64f, DefaultMediaSizing.listRowMinHeight.value)
        assertEquals(72f, DefaultMediaSizing.miniPlayerHeight.value)
        assertEquals(RoundedCornerShape(8.dp), MediaShapes.small)
        assertEquals(RoundedCornerShape(12.dp), MediaShapes.medium)
        assertEquals(RoundedCornerShape(16.dp), MediaShapes.large)
        assertEquals(RoundedCornerShape(24.dp), MediaShapes.extraLarge)
        assertEquals(RoundedCornerShape(percent = 50), MediaPillShape)
        assertEquals(22.sp, MediaTypography.titleLarge.fontSize)
        assertEquals(28.sp, MediaTypography.titleLarge.lineHeight)
        assertEquals(FontWeight.SemiBold, MediaTypography.titleLarge.fontWeight)
        assertEquals(MediaTypography.titleLarge, MediaTextStyles.appTitle)
        assertEquals(MediaTypography.titleLarge, MediaTextStyles.screenTitle)
        assertEquals(MediaTypography.titleMedium, MediaTextStyles.sectionTitle)
        assertEquals(MediaTypography.bodyMedium, MediaTextStyles.body)
        assertEquals(MediaTypography.bodySmall, MediaTextStyles.metadata)
        assertEquals(MediaTypography.labelMedium, MediaTextStyles.badge)
        assertEquals(14.sp, MediaPlayerTimeStyle.fontSize)
        assertEquals("tnum", MediaPlayerTimeStyle.fontFeatureSettings)
        assertEquals(MediaPlayerTimeStyle, MediaTextStyles.playerTime)
        assertEquals(0.dp, DefaultMediaElevation.surface0)
        assertEquals(1.dp, DefaultMediaElevation.surface2)
        assertEquals(3.dp, DefaultMediaElevation.surface3)
        assertEquals(6.dp, DefaultMediaElevation.surface4)
        assertEquals(120, DefaultMediaMotion.pressMillis)
        assertEquals(180, DefaultMediaMotion.stateMillis)
        assertEquals(240, DefaultMediaMotion.overlayMillis)
        assertEquals(0, DefaultMediaMotion.durationMillis(180, durationScale = 0f))
        assertEquals(90, DefaultMediaMotion.durationMillis(180, durationScale = 0.5f))
        assertEquals(180, DefaultMediaMotion.durationMillis(180, durationScale = 1f))
        assertEquals(
            0,
            (DefaultMediaMotion.stateSpec<Float>(
                durationScale = 0f,
            ) as TweenSpec<*>).durationMillis,
        )
        assertEquals(
            240,
            (DefaultMediaMotion.overlaySpec<Float>(
                durationScale = 1f,
            ) as TweenSpec<*>).durationMillis,
        )
    }
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}
```

- [ ] **Step 2: Run the focused JVM test and verify RED**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.theme.ThemeTokensTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: FAIL during Kotlin compilation with unresolved references for `DarkMediaColorScheme`, `LightMediaColorScheme`, `DefaultPlayerColors`, `DefaultMediaSpacing`, and `DefaultMediaSizing`.

- [ ] **Step 3: Implement exact colors, typography, shapes and non-Material tokens**

Create `Color.kt` with the approved mappings:

```kotlin
package com.local.mediaviewer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val DarkMediaColorScheme = darkColorScheme(
    primary = Color(0xFF67D9F0),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFFA9EEFF),
    secondary = Color(0xFFBEC8D1),
    onSecondary = Color(0xFF28323A),
    secondaryContainer = Color(0xFF3E4851),
    onSecondaryContainer = Color(0xFFDAE4ED),
    tertiary = Color(0xFFC9BFFF),
    onTertiary = Color(0xFF312C61),
    tertiaryContainer = Color(0xFF484378),
    onTertiaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFF080C12),
    onBackground = Color(0xFFDFE3E8),
    surface = Color(0xFF111821),
    onSurface = Color(0xFFDFE3E8),
    surfaceVariant = Color(0xFF3F484C),
    onSurfaceVariant = Color(0xFFBFC8CC),
    outline = Color(0xFF899296),
    outlineVariant = Color(0xFF3F484C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFDFE3E8),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = Color(0xFF006878),
    scrim = Color.Black,
)

val LightMediaColorScheme = lightColorScheme(
    primary = Color(0xFF006878),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA9EEFF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF4F6169),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E5ED),
    onSecondaryContainer = Color(0xFF0B1E24),
    tertiary = Color(0xFF615B91),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6DEFF),
    onTertiaryContainer = Color(0xFF1D174B),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C1E),
    surface = Color.White,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF3F484C),
    outline = Color(0xFF6F797D),
    outlineVariant = Color(0xFFBFC8CC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFF0F1F3),
    inversePrimary = Color(0xFF67D9F0),
    scrim = Color.Black,
)

@Immutable
data class MediaExtendedColors(
    val success: Color,
    val warning: Color,
    val offline: Color,
    val folder: Color,
    val video: Color,
    val audio: Color,
    val image: Color,
    val unknown: Color,
)

val DarkMediaExtendedColors = MediaExtendedColors(
    success = Color(0xFF63D89A),
    warning = Color(0xFFF6C66A),
    offline = Color(0xFFF6C66A),
    folder = Color(0xFFF6C66A),
    video = Color(0xFF67D9F0),
    audio = Color(0xFFC9BFFF),
    image = Color(0xFF63D89A),
    unknown = Color(0xFFBFC8CC),
)

val LightMediaExtendedColors = MediaExtendedColors(
    success = Color(0xFF146C43),
    warning = Color(0xFF805600),
    offline = Color(0xFF805600),
    folder = Color(0xFF805600),
    video = Color(0xFF006878),
    audio = Color(0xFF615B91),
    image = Color(0xFF146C43),
    unknown = Color(0xFF596367),
)

@Immutable
data class PlayerColors(
    val canvas: Color,
    val control: Color,
    val active: Color,
    val accent: Color,
    val disabled: Color,
    val buffering: Color,
    val playedTrack: Color,
    val unplayedTrack: Color,
    val volume: Color,
    val brightness: Color,
    val topScrimStart: Color,
    val topScrimEnd: Color,
    val bottomScrimStart: Color,
    val bottomScrimEnd: Color,
)

val DefaultPlayerColors = PlayerColors(
    canvas = Color.Black,
    control = Color(0xFFF5FAFF),
    active = Color(0xFF67D9F0),
    accent = Color(0xFFC9BFFF),
    disabled = Color(0xFF7D878F).copy(alpha = 0.60f),
    buffering = Color(0xFF67D9F0),
    playedTrack = Color(0xFF67D9F0),
    unplayedTrack = Color(0xFF59636D).copy(alpha = 0.55f),
    volume = Color(0xFF67D9F0),
    brightness = Color(0xFFF6C66A),
    topScrimStart = Color(0xB3000000),
    topScrimEnd = Color.Transparent,
    bottomScrimStart = Color.Transparent,
    bottomScrimEnd = Color(0xCC000000),
)
```

Create `Typography.kt`, `Shapes.kt`, and `Tokens.kt`:

```kotlin
package com.local.mediaviewer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val MediaTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)

val MediaPlayerTimeStyle = MediaTypography.labelLarge.copy(
    fontFeatureSettings = "tnum",
)

object MediaTextStyles {
    val appTitle: TextStyle get() = MediaTypography.titleLarge
    val screenTitle: TextStyle get() = MediaTypography.titleLarge
    val sectionTitle: TextStyle get() = MediaTypography.titleMedium
    val body: TextStyle get() = MediaTypography.bodyMedium
    val metadata: TextStyle get() = MediaTypography.bodySmall
    val badge: TextStyle get() = MediaTypography.labelMedium
    val playerTime: TextStyle get() = MediaPlayerTimeStyle
}

val MediaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
val MediaPillShape = RoundedCornerShape(percent = 50)

@Immutable
data class MediaSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val pageGutter: Dp = 16.dp,
    val widePageGutter: Dp = 24.dp,
)

@Immutable
data class MediaSizing(
    val minimumTouchTarget: Dp = 48.dp,
    val listRowMinHeight: Dp = 64.dp,
    val miniPlayerHeight: Dp = 72.dp,
    val playerPrimaryButton: Dp = 64.dp,
    val fullscreenPrimaryButton: Dp = 72.dp,
    val timelineTouchHeight: Dp = 48.dp,
    val timelineTrackHeight: Dp = 4.dp,
    val timelineThumbSize: Dp = 18.dp,
    val verticalLevelWidth: Dp = 48.dp,
    val verticalLevelHeight: Dp = 160.dp,
    val volumePopupWidth: Dp = 72.dp,
    val volumePopupHeight: Dp = 224.dp,
    val miniPlayerProgressHeight: Dp = 2.dp,
)

@Immutable
data class MediaMotion(
    val pressMillis: Int = 120,
    val stateMillis: Int = 180,
    val overlayMillis: Int = 240,
)

fun MediaMotion.durationMillis(
    requestedMillis: Int,
    durationScale: Float,
): Int = (requestedMillis * durationScale.coerceAtLeast(0f))
    .toInt()

fun <T> MediaMotion.pressSpec(
    durationScale: Float,
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis(pressMillis, durationScale),
    easing = LinearOutSlowInEasing,
)

fun <T> MediaMotion.stateSpec(
    durationScale: Float,
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis(stateMillis, durationScale),
    easing = FastOutSlowInEasing,
)

fun <T> MediaMotion.overlaySpec(
    durationScale: Float,
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis(overlayMillis, durationScale),
    easing = FastOutSlowInEasing,
)

@Composable
fun platformMotionDurationScale(): Float {
    val scope = rememberCoroutineScope()
    return scope.coroutineContext[MotionDurationScale]
        ?.scaleFactor
        ?: 1f
}

@Immutable
data class MediaElevation(
    val surface0: Dp = 0.dp,
    val surface1: Dp = 0.dp,
    val surface2: Dp = 1.dp,
    val surface3: Dp = 3.dp,
    val surface4: Dp = 6.dp,
)

val DefaultMediaSpacing = MediaSpacing()
val DefaultMediaSizing = MediaSizing()
val DefaultMediaMotion = MediaMotion()
val DefaultMediaElevation = MediaElevation()

val LocalMediaExtendedColors = staticCompositionLocalOf {
    DarkMediaExtendedColors
}
val LocalPlayerColors = staticCompositionLocalOf {
    DefaultPlayerColors
}
val LocalMediaSpacing = staticCompositionLocalOf {
    DefaultMediaSpacing
}
val LocalMediaSizing = staticCompositionLocalOf {
    DefaultMediaSizing
}
val LocalMediaMotion = staticCompositionLocalOf {
    DefaultMediaMotion
}
val LocalMediaElevation = staticCompositionLocalOf {
    DefaultMediaElevation
}
```

Update `Theme.kt` so tests and future screenshots can force either mode:

```kotlin
@Composable
fun MediaViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) {
        DarkMediaExtendedColors
    } else {
        LightMediaExtendedColors
    }
    CompositionLocalProvider(
        LocalMediaExtendedColors provides extended,
        LocalPlayerColors provides DefaultPlayerColors,
        LocalMediaSpacing provides DefaultMediaSpacing,
        LocalMediaSizing provides DefaultMediaSizing,
        LocalMediaMotion provides DefaultMediaMotion,
        LocalMediaElevation provides DefaultMediaElevation,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                DarkMediaColorScheme
            } else {
                LightMediaColorScheme
            },
            typography = MediaTypography,
            shapes = MediaShapes,
            content = content,
        )
    }
}

object MediaTheme {
    val extendedColors: MediaExtendedColors
        @Composable get() = LocalMediaExtendedColors.current

    val playerColors: PlayerColors
        @Composable get() = LocalPlayerColors.current

    val spacing: MediaSpacing
        @Composable get() = LocalMediaSpacing.current

    val sizing: MediaSizing
        @Composable get() = LocalMediaSizing.current

    val motion: MediaMotion
        @Composable get() = LocalMediaMotion.current

    val elevation: MediaElevation
        @Composable get() = LocalMediaElevation.current
}
```

Every animated component obtains `val durationScale = platformMotionDurationScale()` and uses `MediaTheme.motion.pressSpec/stateSpec/overlaySpec(durationScale)`. A zero platform duration scale therefore produces a zero-duration tween and jumps directly to the final state; a non-default scale multiplies the approved base duration instead of being collapsed to a Boolean.

In `MainActivity.onCreate`, call `enableEdgeToEdge()` before `setContent`. Player plan Task 4 must make `FullscreenController.exit()` restore edge-to-edge (`setDecorFitsSystemWindows(false)`) after showing system bars rather than reverting the Activity to a second window policy.

Create both resource themes and point the manifest at `@style/Theme.MediaViewer`:

```xml
<!-- app/src/main/res/values/themes.xml -->
<resources>
    <style name="Theme.MediaViewer" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:statusBarColor">#F7F9FC</item>
        <item name="android:navigationBarColor">#F7F9FC</item>
        <item name="android:windowBackground">#F7F9FC</item>
    </style>
</resources>
```

```xml
<!-- app/src/main/res/values-night/themes.xml -->
<resources>
    <style name="Theme.MediaViewer" parent="android:style/Theme.Material.NoActionBar">
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:statusBarColor">#080C12</item>
        <item name="android:navigationBarColor">#080C12</item>
        <item name="android:windowBackground">#080C12</item>
    </style>
</resources>
```

- [ ] **Step 4: Run theme tests and compilation**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.theme.ThemeTokensTest' `
  :app:assembleDebug `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: PASS; Debug APK builds with `Theme.MediaViewer`, and the contrast assertions pass.

- [ ] **Step 5: Commit the theme foundation**

```powershell
git add app/src/main/AndroidManifest.xml `
  app/src/main/java/com/local/mediaviewer/MainActivity.kt `
  app/src/main/res/values/themes.xml `
  app/src/main/res/values-night/themes.xml `
  app/src/main/java/com/local/mediaviewer/ui/theme `
  app/src/test/java/com/local/mediaviewer/ui/theme/ThemeTokensTest.kt
git commit -m "feat: establish media viewer design tokens"
```

### Task 2: Shared Icons, Buttons, Icon Buttons, and State Panels

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/icons/MediaIcons.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaGlyph.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaButtons.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaIconButton.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaStatePanel.kt`
- Create: `app/src/test/java/com/local/mediaviewer/ui/icons/MediaIconsTest.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/MediaComponentsTest.kt`

**Interfaces:**
- Produces: `object MediaIcons`
- Produces: `@Composable fun MediaGlyph(icon: ImageVector, contentDescription: String?, tint: Color, modifier: Modifier = Modifier)`
- Produces: `@Composable fun MediaPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null)`
- Produces: `@Composable fun MediaSecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null)`
- Produces: `@Composable fun MediaDestructiveButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null)`
- Produces: `@Composable fun MediaIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean? = null, loading: Boolean = false, stateDescription: String? = null)`
- Produces: `@Composable fun PlayerIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean? = null, loading: Boolean = false, stateDescription: String? = null)`, consuming `MediaTheme.playerColors`.
- Produces: `enum class MediaStateKind { LOADING, EMPTY, OFFLINE, ERROR }`
- Produces: `data class MediaAction(val label: String, val onClick: () -> Unit)`
- Produces: `@Composable fun MediaStatePanel(kind: MediaStateKind, title: String, message: String? = null, primaryAction: MediaAction? = null, secondaryAction: MediaAction? = null, modifier: Modifier = Modifier)`
- Consumes: `MediaTheme.extendedColors`, `MediaTheme.playerColors`, `MediaTheme.spacing`, `MediaTheme.sizing`, and Material typography/colorScheme from Task 1.

- [ ] **Step 1: Write failing icon inventory and Compose semantic tests**

Create `MediaIconsTest.kt`:

```kotlin
package com.local.mediaviewer.ui.icons

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIconsTest {
    @Test
    fun `shared icon inventory is stable and uses 24dp viewport`() {
        assertEquals(19, MediaIcons.all.size)
        assertTrue(MediaIcons.all.all { it.defaultWidth == 24.dp })
        assertTrue(MediaIcons.all.all { it.defaultHeight == 24.dp })
        assertEquals(
            MediaIcons.all.size,
            MediaIcons.all.map { it.name }.distinct().size,
        )
    }
}
```

Create `MediaComponentsTest.kt`:

```kotlin
package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.components.MediaPrimaryButton
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun loadingButtonIsDisabledAndExposesState() {
        rule.setContent {
            MediaViewerTheme {
                MediaPrimaryButton(
                    label = "保存",
                    onClick = {},
                    loading = true,
                )
            }
        }
        rule.onNodeWithText("保存")
            .assertIsNotEnabled()
        rule.onNodeWithContentDescription("保存，正在处理")
            .assertIsDisplayed()
    }

    @Test
    fun selectedIconAndStatePanelExposeActions() {
        var calls = 0
        rule.setContent {
            MediaViewerTheme {
                MediaIconButton(
                    icon = MediaIcons.ReaderMode,
                    contentDescription = "阅读模式",
                    stateDescription = "条漫",
                    selected = true,
                    onClick = {},
                )
                MediaStatePanel(
                    kind = MediaStateKind.ERROR,
                    title = "无法连接服务器",
                    message = "请检查地址后重试",
                    primaryAction = MediaAction("重试") { calls++ },
                    secondaryAction = MediaAction("打开设置") {},
                )
            }
        }
        rule.onNodeWithContentDescription("阅读模式")
            .assertIsSelected()
        rule.onNodeWithText("无法连接服务器").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun ordinaryIconOmitsSelectionAndExplicitStateWinsWhileLoading() {
        rule.setContent {
            MediaViewerTheme {
                MediaIconButton(
                    icon = MediaIcons.Refresh,
                    contentDescription = "刷新",
                    onClick = {},
                )
                MediaIconButton(
                    icon = MediaIcons.Refresh,
                    contentDescription = "重新连接",
                    onClick = {},
                    loading = true,
                    stateDescription = "正在重新连接服务器",
                )
            }
        }

        rule.onNodeWithContentDescription("刷新")
            .assert(
                SemanticsMatcher.keyNotDefined(
                    SemanticsProperties.Selected,
                ),
            )
        rule.onNodeWithContentDescription("重新连接")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "正在重新连接服务器",
                ),
            )
            .assertIsNotEnabled()
    }
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run the JVM test first:

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.icons.MediaIconsTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: FAIL with unresolved reference `MediaIcons`.

Then compile the instrumentation test:

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: FAIL with unresolved references `MediaPrimaryButton`, `MediaIconButton`, and `MediaStatePanel`.

- [ ] **Step 3: Implement the stable icon inventory**

Create `MediaIcons.kt`:

```kotlin
package com.local.mediaviewer.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ViewStream

object MediaIcons {
    val Back = Icons.AutoMirrored.Filled.ArrowBack
    val Settings = Icons.Filled.Settings
    val Folder = Icons.Filled.Folder
    val Video = Icons.Filled.Movie
    val Audio = Icons.Filled.AudioFile
    val Image = Icons.Filled.Image
    val File = Icons.AutoMirrored.Filled.InsertDriveFile
    val Connected = Icons.Filled.CheckCircle
    val Offline = Icons.Filled.CloudOff
    val Empty = Icons.Filled.Inbox
    val Error = Icons.Filled.ErrorOutline
    val Refresh = Icons.Filled.Refresh
    val More = Icons.Filled.MoreVert
    val PlayNow = Icons.Filled.PlayArrow
    val PlayNext = Icons.Filled.SkipNext
    val AddQueue = Icons.Filled.AddToQueue
    val Queue = Icons.Filled.QueueMusic
    val ReaderMode = Icons.Filled.ViewStream
    val Sort = Icons.AutoMirrored.Filled.Sort

    val all = listOf(
        Back, Settings, Folder, Video, Audio, Image, File,
        Connected, Offline, Empty, Error, Refresh, More,
        PlayNow, PlayNext, AddQueue, Queue, ReaderMode, Sort,
    )
}
```

Do not move or rewrite `ui/player/PlayerIcons.kt`; the later player plan may consume `MediaTheme.playerColors` while retaining its approved media glyphs.

- [ ] **Step 4: Implement buttons, icon buttons, glyphs, and state panels**

Create `MediaIconButton.kt` with one internal renderer and the two required public entry points:

```kotlin
@Composable
fun MediaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    loading: Boolean = false,
    stateDescription: String? = null,
) {
    SemanticIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        loading = loading,
        stateDescription = stateDescription,
        tint = if (selected == true) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    loading: Boolean = false,
    stateDescription: String? = null,
) {
    SemanticIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        loading = loading,
        stateDescription = stateDescription,
        tint = if (selected == true) {
            MediaTheme.playerColors.active
        } else {
            MediaTheme.playerColors.control
        },
    )
}

@Composable
private fun SemanticIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    selected: Boolean?,
    loading: Boolean,
    stateDescription: String?,
    tint: Color,
) {
    val semanticDescription = contentDescription
    IconButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .sizeIn(
                minWidth = MediaTheme.sizing.minimumTouchTarget,
                minHeight = MediaTheme.sizing.minimumTouchTarget,
            )
            .semantics {
                this.contentDescription = semanticDescription
                selected?.let { this.selected = it }
                val effectiveStateDescription =
                    stateDescription ?: if (loading) {
                        "正在处理"
                    } else {
                        null
                    }
                effectiveStateDescription?.let {
                    this.stateDescription = it
                }
            },
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
        }
    }
}
```

Create `MediaButtons.kt` around a private `MediaButton` that selects `Button`, `OutlinedButton`, or error-colored `Button`. The loading branch must preserve the text and merge one description:

```kotlin
val semanticsModifier = Modifier.semantics(mergeDescendants = true) {
    contentDescription = if (loading) {
        "$label，正在处理"
    } else {
        label
    }
    if (loading) stateDescription = "正在处理"
}
```

Create `MediaStatePanel.kt` with the exact shared types:

```kotlin
enum class MediaStateKind {
    LOADING,
    EMPTY,
    OFFLINE,
    ERROR,
}

data class MediaAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun MediaStatePanel(
    kind: MediaStateKind,
    title: String,
    message: String? = null,
    primaryAction: MediaAction? = null,
    secondaryAction: MediaAction? = null,
    modifier: Modifier = Modifier,
) {
    val icon = when (kind) {
        MediaStateKind.LOADING -> null
        MediaStateKind.EMPTY -> MediaIcons.Empty
        MediaStateKind.OFFLINE -> MediaIcons.Offline
        MediaStateKind.ERROR -> MediaIcons.Error
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MediaTheme.spacing.xl)
            .semantics {
                if (kind == MediaStateKind.ERROR ||
                    kind == MediaStateKind.OFFLINE
                ) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
    ) {
        if (kind == MediaStateKind.LOADING) {
            CircularProgressIndicator(Modifier.size(32.dp))
        } else {
            MediaGlyph(
                icon = requireNotNull(icon),
                contentDescription = null,
                tint = when (kind) {
                    MediaStateKind.OFFLINE -> MediaTheme.extendedColors.offline
                    MediaStateKind.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        primaryAction?.let {
            MediaPrimaryButton(it.label, it.onClick)
        }
        secondaryAction?.let {
            MediaSecondaryButton(it.label, it.onClick)
        }
    }
}
```

`MediaGlyph.kt` must render a 40dp rounded tonal container with a 24dp icon; callers provide the semantic tint and may pass `null` content description when surrounding text already names the state.

- [ ] **Step 5: Run the focused JVM and connected tests**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.icons.MediaIconsTest' `
  :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: PASS.

On the API 36 x86_64 emulator, run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaComponentsTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: PASS; loading buttons are disabled and selected/state semantics are visible.

- [ ] **Step 6: Commit shared icon and state primitives**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/icons `
  app/src/main/java/com/local/mediaviewer/ui/components/MediaGlyph.kt `
  app/src/main/java/com/local/mediaviewer/ui/components/MediaButtons.kt `
  app/src/main/java/com/local/mediaviewer/ui/components/MediaIconButton.kt `
  app/src/main/java/com/local/mediaviewer/ui/components/MediaStatePanel.kt `
  app/src/test/java/com/local/mediaviewer/ui/icons/MediaIconsTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/MediaComponentsTest.kt
git commit -m "feat: add shared media ui primitives"
```

### Task 3: Shared Material Wrappers and Non-Overlapping Scaffolds

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaTopAppBar.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaAppScaffold.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaScreenScaffold.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaSnackbarHost.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaOptionMenu.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaConfirmDialog.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaBottomSheet.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaTimelineSlider.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaVerticalLevelControl.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/settings/MediaUrlField.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsSection.kt`
- Leave unchanged until Task 8 caller cleanup: `app/src/main/java/com/local/mediaviewer/ui/components/MediaRouteShell.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/MediaScaffoldTest.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/MediaMaterialWrappersTest.kt`

**Interfaces:**
- Consumes: all Task 1 tokens and Task 2 buttons/icons/state panel.
- Produces the following exact public API; implementation-only helpers stay `private`:

```kotlin
@Composable
fun MediaTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    actions: @Composable RowScope.() -> Unit = {},
)

@Composable
fun MediaAppScaffold(
    snackbarHostState: SnackbarHostState,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
)

@Composable
fun MediaScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
)

@Composable
fun MediaConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)

@Immutable
data class MediaOption<T>(
    val key: T,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
)

@Composable
fun <T> MediaOptionMenu(
    expanded: Boolean,
    options: List<MediaOption<T>>,
    selectedKey: T?,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun MediaBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    contentWindowInsets: @Composable () -> WindowInsets = {
        BottomSheetDefaults.windowInsets
    },
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
)

enum class MediaSnackbarKind { INFO, SUCCESS, ERROR }

data class MediaSnackbarVisuals(
    override val message: String,
    val kind: MediaSnackbarKind,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

@Composable
fun MediaSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
)

@Composable
fun MediaTimelineSlider(
    durationMs: Long,
    positionMs: Long,
    enabled: Boolean = true,
    onDragStart: () -> Unit,
    onPositionPreview: (Long) -> Unit,
    onPositionCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun MediaVerticalLevelControl(
    value: Float,
    label: String,
    enabled: Boolean = true,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
)

enum class MediaUrlFieldState { IDLE, TESTING, SUCCESS, ERROR }

@Composable
fun MediaUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    state: MediaUrlFieldState,
    selectedIpv4: String? = null,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
)

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
)

@Composable
fun SettingsChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
```

- `MediaUrlField` and the two settings layout primitives are state-independent: they do not import `SettingsUiState` or `SettingsViewModel`. Flow Task 4 derives `MediaUrlFieldState` from its real state and wires these APIs before foundation Task 6 rearranges the finished screen.
- Does not modify `MediaViewerApp.kt`; the flow plan Task 7 is the sole root integration owner.

- [ ] **Step 1: Write failing scaffold and wrapper tests**

Create `MediaScaffoldTest.kt`:

```kotlin
@Test
fun bottomBarParticipatesInLayoutInsteadOfCoveringTheLastItem() {
    rule.setContent {
        MediaViewerTheme {
            MediaAppScaffold(
                snackbarHostState = remember { SnackbarHostState() },
                bottomBar = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .testTag("dock"),
                    )
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Text(
                        "最后一项",
                        Modifier
                            .align(Alignment.BottomStart)
                            .testTag("last_item"),
                    )
                }
            }
        }
    }

    val dockTop = rule.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot.top
    val itemBottom = rule.onNodeWithTag("last_item").fetchSemanticsNode().boundsInRoot.bottom
    assertTrue(itemBottom <= dockTop)
}
```

Create `MediaMaterialWrappersTest.kt`. Use real `MediaOption` values and exercise semantics, pointer direction, rendered direction and callback cardinality:

```kotlin
@Test
fun verticalLevelUsesRangeSemanticsPointerAndBottomAlignedFill() {
    var value by mutableFloatStateOf(0.25f)
    rule.setContent {
        MediaViewerTheme {
            MediaVerticalLevelControl(
                value = value,
                label = "音量 25%",
                onValueChanged = { value = it },
                modifier = Modifier.testTag("vertical_level"),
            )
        }
    }

    rule.onNodeWithTag("vertical_level")
        .assertRangeInfoEquals(
            ProgressBarRangeInfo(0.25f, 0f..1f),
        )
        .performSemanticsAction(SemanticsActions.SetProgress) {
            it(0.75f)
        }
    rule.runOnIdle { assertEquals(0.75f, value, 0.001f) }

    rule.onNodeWithTag("vertical_level").performTouchInput {
        click(Offset(center.x, 1f))
    }
    rule.runOnIdle { assertTrue(value > 0.95f) }
    rule.onNodeWithTag("vertical_level").performTouchInput {
        click(Offset(center.x, size.height - 1f))
    }
    rule.runOnIdle { assertTrue(value < 0.05f) }

    rule.runOnIdle { value = 0.25f }
    val pixels = rule.onNodeWithTag("vertical_level")
        .captureToImage()
        .toPixelMap()
    val x = pixels.width / 2
    assertNotEquals(
        "top remains unfilled while bottom is filled",
        pixels[x, 2],
        pixels[x, pixels.height - 3],
    )
}

@Test
fun disabledVerticalLevelIgnoresPointerInput() {
    var calls = 0
    rule.setContent {
        MediaViewerTheme {
            MediaVerticalLevelControl(
                value = 0.5f,
                label = "亮度 50%",
                enabled = false,
                onValueChanged = { calls++ },
                modifier = Modifier.testTag("disabled_level"),
            )
        }
    }
    rule.onNodeWithTag("disabled_level")
        .assertIsNotEnabled()
        .performTouchInput { click(center) }
    rule.runOnIdle { assertEquals(0, calls) }
}

@Test
fun timelineBeginsOncePreviewsCommitsOnceAndClamps() {
    var position by mutableLongStateOf(5_000L)
    var starts = 0
    val previews = mutableListOf<Long>()
    val commits = mutableListOf<Long>()
    rule.setContent {
        MediaViewerTheme {
            MediaTimelineSlider(
                durationMs = 10_000L,
                positionMs = position,
                onDragStart = { starts++ },
                onPositionPreview = {
                    previews += it
                    position = it
                },
                onPositionCommit = {
                    commits += it
                    position = it
                },
                modifier = Modifier
                    .width(240.dp)
                    .testTag("timeline"),
            )
        }
    }

    rule.onNodeWithTag("timeline").performTouchInput {
        down(Offset(8f, center.y))
        moveTo(Offset(size.width - 8f, center.y))
        up()
    }
    rule.runOnIdle {
        assertEquals(1, starts)
        assertTrue(previews.isNotEmpty())
        assertTrue(previews.all { it in 0L..10_000L })
        assertEquals(1, commits.size)
        assertTrue(commits.single() in 0L..10_000L)
    }

    rule.runOnIdle { position = 50_000L }
    rule.onNodeWithTag("timeline").assertRangeInfoEquals(
        ProgressBarRangeInfo(10_000f, 0f..10_000f),
    )
}

@Test
fun disabledTimelineDoesNotBeginPreviewOrCommit() {
    var calls = 0
    rule.setContent {
        MediaViewerTheme {
            MediaTimelineSlider(
                durationMs = 10_000L,
                positionMs = 2_000L,
                enabled = false,
                onDragStart = { calls++ },
                onPositionPreview = { calls++ },
                onPositionCommit = { calls++ },
                modifier = Modifier
                    .width(240.dp)
                    .testTag("disabled_timeline"),
            )
        }
    }
    rule.onNodeWithTag("disabled_timeline")
        .assertIsNotEnabled()
        .performTouchInput {
            swipe(centerLeft, centerRight, 300L)
        }
    rule.runOnIdle { assertEquals(0, calls) }
}

@Test
fun dialogExposesOnlyRealActions() {
    rule.setContent {
        MediaViewerTheme {
            MediaConfirmDialog(
                title = "删除队列项",
                message = "此操作不会删除文件",
                confirmLabel = "删除",
                dismissLabel = "取消",
                destructive = true,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
    rule.onNodeWithText("删除").assertHasClickAction()
    rule.onNodeWithText("取消").assertHasClickAction()
    rule.onAllNodes(hasClickAction()).assertCountEquals(2)

}

@Test
fun optionMenuExposesRealSelection() {
    rule.setContent {
        MediaViewerTheme {
            MediaOptionMenu(
                expanded = true,
                options = listOf(
                    MediaOption("name", "按名称"),
                    MediaOption("date", "按日期"),
                ),
                selectedKey = "date",
                onSelect = {},
                onDismissRequest = {},
            )
        }
    }
    rule.onNodeWithText("按日期").assertIsSelected()
    rule.onNodeWithText("按名称").assertIsNotSelected()
}

@Test
fun urlFieldAndChoiceRowExposeResultAndSelectionSemantics() {
    rule.setContent {
        MediaViewerTheme {
            Column {
                MediaUrlField(
                    value = "http://media.example:8080",
                    onValueChange = {},
                    state = MediaUrlFieldState.TESTING,
                    modifier = Modifier.testTag("testing_url"),
                )
                MediaUrlField(
                    value = "http://media.example:8080",
                    onValueChange = {},
                    state = MediaUrlFieldState.SUCCESS,
                    selectedIpv4 = "192.0.2.8",
                )
                MediaUrlField(
                    value = "bad-url",
                    onValueChange = {},
                    state = MediaUrlFieldState.ERROR,
                    errorMessage = "URL 无效",
                    modifier = Modifier.testTag("error_url"),
                )
                SettingsChoiceRow(
                    title = "条漫",
                    description = "纵向连续阅读图片",
                    selected = true,
                    onClick = {},
                )
            }
        }
    }
    rule.onNodeWithTag("testing_url").assertIsNotEnabled()
    rule.onNodeWithText("正在测试连接").assertIsDisplayed()
    rule.onNodeWithText("将连接到 192.0.2.8").assertIsDisplayed()
    rule.onNodeWithText("URL 无效")
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            ),
        )
    rule.onNodeWithText("条漫").assertIsSelected()
}
```

The test file imports the real Compose test APIs used above (`captureToImage`, `toPixelMap`, `performTouchInput`, `assertRangeInfoEquals`, `SemanticsActions.SetProgress`, `hasClickAction`) and declares its own `createComposeRule`. Do not replace these tests with pseudo helpers.

- [ ] **Step 2: Compile tests and verify RED**

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: FAIL because the scaffold and wrapper types do not exist.

- [ ] **Step 3: Implement the shared scaffolds**

`MediaAppScaffold` must use a real Material `bottomBar`:

```kotlin
@Composable
fun MediaAppScaffold(
    snackbarHostState: SnackbarHostState,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = {
            MediaSnackbarHost(
                hostState = snackbarHostState,
            )
        },
        bottomBar = bottomBar,
        contentWindowInsets = contentWindowInsets,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}
```

`MediaScreenScaffold` uses `MediaTopAppBar`, Material background and the content padding returned by its inner Scaffold. It must not create a second global Snackbar host or bottom bar. New and migrated callers use only this implementation; leave `MediaRouteShell` untouched until Task 8 proves it has no callers and deletes it.

- [ ] **Step 4: Implement dialogs, menus, sheets, timeline and true vertical level**

`MediaTimelineSlider` wraps Material `Slider`, clamps `positionMs` to `0..durationMs`, calls `onDragStart` once, previews during drag and commits once in `onValueChangeFinished`. It renders one track only and exposes standard adjustable semantics.

`MediaVerticalLevelControl` must not rotate a horizontal Slider. Use a 48×160dp pointer/semantics region and draw a bottom-aligned fill:

```kotlin
Modifier
    .widthIn(min = 48.dp)
    .heightIn(min = 160.dp)
    .semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = value.coerceIn(0f, 1f),
            range = 0f..1f,
        )
        setProgress { requested ->
            if (!enabled) return@setProgress false
            onValueChanged(requested.coerceIn(0f, 1f))
            true
        }
        if (!enabled) disabled()
    }
    .pointerInput(enabled, onValueChanged) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            fun publish(y: Float) {
                onValueChanged((1f - y / size.height).coerceIn(0f, 1f))
            }
            publish(down.position.y)
            do {
                val event = awaitPointerEvent()
                event.changes.firstOrNull()?.let {
                    publish(it.position.y)
                    it.consume()
                }
            } while (event.changes.any { it.pressed })
        }
    }
```

Apply that modifier to a `Canvas` and draw both layers explicitly; do not substitute a rotated horizontal Material slider:

```kotlin
val trackColor = MaterialTheme.colorScheme.surfaceVariant
val fillColor = MediaTheme.playerColors.volume
Canvas(modifier = controlModifier) {
    val fraction = value.coerceIn(0f, 1f)
    val barWidth = 8.dp.toPx()
    val left = (size.width - barWidth) / 2f
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(left, 0f),
        size = Size(barWidth, size.height),
        cornerRadius = CornerRadius(barWidth / 2f),
    )
    val filledHeight = size.height * fraction
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(left, size.height - filledHeight),
        size = Size(barWidth, filledHeight),
        cornerRadius = CornerRadius(barWidth / 2f),
    )
}
```

Read the two colors before the `Canvas` lambda because composable reads are not allowed inside `DrawScope`; the snippet names show the required visual mapping. The test samples the rendered top and bottom pixels at 25%, while pointer tests prove top means 1 and bottom means 0.

`MediaConfirmDialog` sets `dismissOnClickOutside = false` for normal/destructive confirmations and returns focus to the trigger through normal Compose focus behavior. `MediaBottomSheet` consumes navigation bar insets and limits content to 90% of available height.

Implement the state-independent Settings primitives in this task, before flow Task 4:

```text
MediaUrlFieldState.IDLE    -> example supporting text
MediaUrlFieldState.TESTING -> “正在测试连接”; field disabled
MediaUrlFieldState.SUCCESS -> success glyph and “将连接到 <selectedIpv4>”
MediaUrlFieldState.ERROR   -> error glyph/message and polite live-region semantics
```

`MediaUrlField` uses URI keyboard options and `ImeAction.Done`; its `modifier` is applied to the semantic text-field root so enabled/disabled assertions are executable. It never derives state from `SettingsUiState`. `SettingsSection` renders title, optional description and a `ColumnScope` content slot. `SettingsChoiceRow` uses `Modifier.selectable`, exposes selected/disabled semantics once on the row, supports wrapped title/description text, and clears decorative child-icon semantics.

Define typed Snackbar visuals:

```kotlin
enum class MediaSnackbarKind {
    INFO,
    SUCCESS,
    ERROR,
}

data class MediaSnackbarVisuals(
    override val message: String,
    val kind: MediaSnackbarKind,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals
```

`MediaSnackbarHost` reads `MediaSnackbarVisuals.kind` and adds the corresponding info/success/error glyph. It never interprets message text and never executes retry itself.

- [ ] **Step 5: Run focused wrapper tests**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaScaffoldTest,com.local.mediaviewer.MediaMaterialWrappersTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: compilation and both connected classes PASS. Without an emulator, the connected command is `NOT RUN`, not PASS.

- [ ] **Step 6: Commit shared wrappers and scaffolds**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/components `
  app/src/main/java/com/local/mediaviewer/ui/settings/MediaUrlField.kt `
  app/src/main/java/com/local/mediaviewer/ui/settings/SettingsSection.kt `
  app/src/androidTest/java/com/local/mediaviewer/MediaScaffoldTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/MediaMaterialWrappersTest.kt
git commit -m "feat: add media scaffolds and material wrappers"
```

### Task 4: Home Connection and Share Layout

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/home/ConnectionStatusCard.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/home/ShareCard.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/home/HomeScreen.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`

**Interfaces:**
- Consumes: `HomeUiState`, `ServerShare`, Task 2 glyph/buttons and Task 3 screen scaffold.
- Keeps: `HomeScreen(state, onRetry, onOpenSettings, onOpenShare)` callback contract.
- Produces: `ConnectionStatusCard` and `ShareCard` that have no ViewModel or repository dependency.

- [ ] **Step 1: Add failing Home state and lazy-list tests**

Add to `HomeSettingsScreenTest.kt`:

```kotlin
@Test
fun connectedHomeShowsStatusAndDistinguishesDisabledShares() {
    var opened: ServerShare? = null
    val enabled = ServerShare(
        id = "movies",
        displayName = "电影",
        urlPrefix = "movies",
        directoryBrowsing = true,
        authenticationMode = ShareAuthenticationMode.ANONYMOUS,
    )
    val disabled = ServerShare(
        id = "private",
        displayName = "私有",
        urlPrefix = "private",
        directoryBrowsing = true,
        authenticationMode = ShareAuthenticationMode.BASIC,
    )
    rule.setContent {
        MediaViewerTheme {
            HomeScreen(
                state = HomeUiState.Connected(
                    ipv4 = "192.0.2.10",
                    shares = listOf(enabled, disabled),
                ),
                onRetry = {},
                onOpenSettings = {},
                onOpenShare = { opened = it },
            )
        }
    }

    rule.onNodeWithText("已连接").assertIsDisplayed()
    rule.onNodeWithText("192.0.2.10").assertIsDisplayed()
    rule.onNodeWithText("不支持当前认证方式").assertIsDisplayed()
    rule.onNodeWithTag("share:私有").assertIsNotEnabled()
    rule.onNodeWithText("电影").performClick()
    rule.runOnIdle { assertEquals(enabled, opened) }
}

@Test
fun thirtySharesRemainReachableAt320DpAndTwoXFont() {
    val shares = (1..30).map { index ->
        ServerShare(
            id = "share-$index",
            displayName = "共享 $index",
            urlPrefix = "share-$index",
            directoryBrowsing = true,
            authenticationMode = ShareAuthenticationMode.ANONYMOUS,
        )
    }
    rule.setContent {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = 1f,
                fontScale = 2f,
            ),
        ) {
            Box(
                Modifier
                    .size(width = 320.dp, height = 568.dp)
                    .testTag("home_window"),
            ) {
                MediaViewerTheme {
                    HomeScreen(
                        state = HomeUiState.Connected(
                            ipv4 = "192.0.2.10",
                            shares = shares,
                        ),
                        onRetry = {},
                        onOpenSettings = {},
                        onOpenShare = {},
                    )
                }
            }
        }
    }

    rule.onNodeWithTag("home_list")
        .performScrollToNode(hasText("共享 30"))
    rule.onNodeWithText("共享 30").assertIsDisplayed()
    val window = rule.onNodeWithTag("home_window")
        .fetchSemanticsNode().boundsInRoot
    val tail = rule.onNodeWithTag("share:共享 30")
        .fetchSemanticsNode().boundsInRoot
    assertTrue(tail.left >= window.left)
    assertTrue(tail.right <= window.right)
    assertTrue(tail.bottom <= window.bottom)
}
```

Use the existing real imports (`ServerShare`, `ShareAuthenticationMode`) plus Compose `Density`, `LocalDensity`, `hasText`, `performScrollToNode`, and `320.dp/568.dp`. No `share(...)` helper is introduced.

- [ ] **Step 2: Run the connected Home class and confirm RED**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.HomeSettingsScreenTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: FAIL because “已连接” and the new disabled-state wording/layout do not exist.

- [ ] **Step 3: Implement connection and share semantic components**

`ConnectionStatusCard` maps states:

```text
Connecting -> “正在连接服务器” + compact progress
Connected  -> success glyph + “已连接” + secondary IPv4
Error      -> error glyph + message + primary “重试” + secondary “服务器设置”
```

`ShareCard` takes `share`, `onClick`, and `modifier`. Its outer card uses the stable semantic tag
`"share:${share.displayName}"`; a disabled card adds `Modifier.semantics { disabled() }`,
has no enter arrow and no clickable modifier. It maps:

```kotlin
val unavailableReason = when {
    !share.directoryBrowsing -> "目录浏览未开放"
    share.authenticationMode == ShareAuthenticationMode.BASIC ->
        "不支持当前认证方式"
    else -> null
}
```

Update `HomeScreen` to `MediaScreenScaffold(title = "MediaViewer")` with a settings `MediaIconButton`. Inside, use one `LazyColumn` tagged `home_list`, with content padding from `MediaTheme.spacing`, connection card first, then a section title and keyed share items. Empty shares use `MediaStatePanel(EMPTY, "没有可浏览的共享", ..., onOpenSettings)`.

- [ ] **Step 4: Run Home tests and compile adjacent navigation**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.HomeSettingsScreenTest,com.local.mediaviewer.MediaViewerNavigationTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: Home states, disabled share semantics and 30-item scrolling PASS; navigation still opens the same share.

- [ ] **Step 5: Commit Home visual unit**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/home `
  app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt
git commit -m "feat: redesign home connection and shares"
```

### Task 5: Compact Browser, Breadcrumbs, and Playback Menu

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/browser/MediaBreadcrumbs.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/browser/MediaFileRow.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt`

**Interfaces:**
- Depends on flow plan Task 3 having produced `BrowserUiState.Loading(previous)` and `Error(previous, failedLogicalUrl)`.
- Keeps: all existing `BrowserScreen` callbacks and `SnackbarHostState?`.
- Consumes: Task 2 icons and Task 3 `MediaScreenScaffold`, `MediaStatePanel`, `MediaOptionMenu`.

- [ ] **Step 1: Add failing retained-content, breadcrumb, row, and menu tests**

Add to `BrowserScreenTest.kt`:

```kotlin
@Test
fun retainedPageRemainsVisibleWhileChildLoads() {
    val page = browserPage(
        breadcrumbs = listOf(
            Breadcrumb(
                label = "根",
                logicalUrl = "http://media.example/middle/",
            ),
            Breadcrumb(
                label = "视频",
                logicalUrl = "http://media.example/middle/video/",
            ),
        ),
        entries = listOf(
            browserEntry(
                name = "long movie name.mp4",
                kind = MediaKind.VIDEO,
            ),
        ),
    )
    rule.setContent {
        MediaViewerTheme {
            BrowserScreen(
                state = BrowserUiState.Loading(previous = page),
                onEntryClick = {},
                onBreadcrumbClick = {},
                onPlaybackAction = { _, _ -> },
                onRetry = {},
                onBack = {},
            )
        }
    }

    rule.onNodeWithText("long movie name.mp4").assertIsDisplayed()
    rule.onNodeWithTag("browser_refreshing").assertIsDisplayed()
    rule.onNodeWithTag("breadcrumb_1").assertIsSelected()
}

@Test
fun playbackMenuNamesItsTargetAndKeepsApprovedOrder() {
    val entry = browserEntry("movie.mp4", MediaKind.VIDEO)
    rule.setContent {
        MediaViewerTheme {
            BrowserScreen(
                state = BrowserUiState.Content(
                    browserPage(entries = listOf(entry)),
                ),
                onEntryClick = {},
                onBreadcrumbClick = {},
                onPlaybackAction = { _, _ -> },
                onRetry = {},
                onBack = {},
            )
        }
    }
    rule.onNodeWithContentDescription("更多播放操作：movie.mp4")
        .performClick()
    val now = rule.onNodeWithText("立即播放")
        .assertIsDisplayed()
        .fetchSemanticsNode().boundsInRoot
    val next = rule.onNodeWithText("下一项播放")
        .assertIsDisplayed()
        .fetchSemanticsNode().boundsInRoot
    val enqueue = rule.onNodeWithText("添加到队列")
        .assertIsDisplayed()
        .fetchSemanticsNode().boundsInRoot
    assertTrue(now.top < next.top)
    assertTrue(next.top < enqueue.top)
}
```

Change the existing file-local helper, rather than inventing `crumb`, `video` or `showContent`:

```kotlin
private fun browserPage(
    entries: List<DirectoryEntry>,
    breadcrumbs: List<Breadcrumb> = listOf(
        Breadcrumb(
            "MiddleDir",
            "http://media.example/middle/",
        ),
    ),
) = BrowserPage(
    root = BROWSER_SHARE,
    logicalDirectoryUrl = breadcrumbs.last().logicalUrl,
    requestDirectoryUrl = "http://192.0.2.1/middle/",
    breadcrumbs = breadcrumbs,
    entries = entries,
)
```

Retain existing real `browserEntry(name, kind)` and the tests for entry click, empty state, retry and breadcrumb callbacks.

- [ ] **Step 2: Run Browser tests and verify RED**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: new tests fail because refresh replaces content, the current breadcrumb has no selected semantic, and the menu description omits the filename.

- [ ] **Step 3: Implement breadcrumbs and compact file rows**

`MediaBreadcrumbs` uses a horizontal `LazyRow`. Each item has `testTag("breadcrumb_$index")`; the final item uses selected semantics and ellipsis. Insert a chevron as a decorative, semantics-cleared separator.

`MediaFileRow`:

- minimum height 64dp;
- 40dp `MediaGlyph` chosen from `MediaKind`;
- filename maximum two lines with ellipsis;
- metadata maximum one line;
- whole primary area opens the entry;
- the menu action is a separate 48dp `MediaIconButton`;
- menu description is `"更多播放操作：${entry.name}"`.

The Browser content `LazyColumn` must always expose `Modifier.testTag("browser_list")`. This tag is produced here before Task 8 consumes it; loading/error with retained content keep the same tagged list.

- [ ] **Step 4: Render all Browser states without dropping context**

Use `MediaScreenScaffold`. The page resolver is exact:

```kotlin
val visiblePage = when (state) {
    is BrowserUiState.Content -> state.page
    is BrowserUiState.Empty -> state.page
    is BrowserUiState.Loading -> state.previous
    is BrowserUiState.Error -> state.previous
}
```

When `visiblePage == null`, show the full `MediaStatePanel`. When it is non-null, always render breadcrumbs/list; overlay only a compact `browser_refreshing` indicator or an offline/error banner. Empty content keeps breadcrumbs and shows an empty panel in the list body. Keep menu order equal to `BrowserPlaybackAction.entries` and provide the existing Snackbar host to the screen scaffold.

- [ ] **Step 5: Run Browser and formatting tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.browser.BrowserFormattersTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: all tests PASS, including previous-content loading/error and filename-specific menu semantics.

- [ ] **Step 6: Commit Browser visual unit**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/browser `
  app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt
git commit -m "feat: redesign compact media browser"
```

### Task 6: Scrollable and IME-Safe Settings

**Files:**
- Consume without redefining: `app/src/main/java/com/local/mediaviewer/ui/settings/MediaUrlField.kt`
- Consume without redefining: `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsSection.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`

**Interfaces:**
- Depends on flow plan Task 4 state and callback contract: `isSaving`, `saveError`, `hasUnsavedServerChange`, `onBackRequest`, and `onDiscardConfirmed`.
- Consumes: Task 2 buttons and Task 3 screen scaffold/confirm dialog plus the already-tested `MediaUrlField`, `MediaUrlFieldState`, `SettingsSection`, and `SettingsChoiceRow`.
- Keeps the exact flow Task 4 transitional `SettingsScreen(state, onInputChanged, onTest, onSave, onDefaultImageModeChanged, onBack, onBackRequest, onDiscardConfirmed)` signature; this task changes layout only.

- [ ] **Step 1: Add failing IME, role, and result tests**

Add to `HomeSettingsScreenTest.kt`:

```kotlin
@Test
fun settingsKeepsSaveReachableAtLargeFontAndShowsActionHierarchy() {
    var saves = 0
    rule.setContent {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = 1f,
                fontScale = 2f,
            ),
        ) {
            Box(
                Modifier
                    .size(width = 320.dp, height = 568.dp)
                    .testTag("settings_window"),
            ) {
                MediaViewerTheme {
                    SettingsScreen(
                        state = SettingsUiState(
                            input = "http://media.example:8080",
                            resolvedIpv4s = listOf("192.0.2.8"),
                            selectedIpv4 = "192.0.2.8",
                            canSave = true,
                            hasUnsavedServerChange = true,
                        ),
                        onInputChanged = {},
                        onTest = {},
                        onSave = { saves++ },
                        onDefaultImageModeChanged = {},
                        onBack = {},
                        onBackRequest = {
                            SettingsBackDecision.LEAVE
                        },
                        onDiscardConfirmed = {},
                    )
                }
            }
        }
    }

    rule.onNodeWithTag("settings_list")
        .performScrollToNode(hasTestTag("save_server"))
    rule.onNodeWithText("将连接到 192.0.2.8").assertIsDisplayed()
    rule.onNodeWithTag("save_server").performClick()
    rule.runOnIdle {
        assertEquals(1, saves)
        val window = rule.onNodeWithTag("settings_window")
            .fetchSemanticsNode().boundsInRoot
        val save = rule.onNodeWithTag("save_server")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(save.left >= window.left)
        assertTrue(save.right <= window.right)
        assertTrue(save.bottom <= window.bottom)
    }
}

@Test
fun settingsExposesSecondaryProgressPrimarySaveErrorAndReaderSelection() {
    rule.setContent {
        MediaViewerTheme {
            SettingsScreen(
                state = SettingsUiState(
                    input = "http://media.example:8080",
                    isTesting = true,
                    defaultImageMode = ImageReaderMode.COMIC,
                    saveError = "保存失败，请重试",
                ),
                onInputChanged = {},
                onTest = {},
                onSave = {},
                onDefaultImageModeChanged = {},
                onBack = {},
                onBackRequest = {
                    SettingsBackDecision.LEAVE
                },
                onDiscardConfirmed = {},
            )
        }
    }

    rule.onNodeWithTag("settings_secondary_action")
        .assertIsNotEnabled()
    rule.onNodeWithText("正在测试连接").assertIsDisplayed()
    rule.onAllNodesWithTag("settings_primary_action")
        .assertCountEquals(1)
    rule.onNodeWithTag("settings_save_error")
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            ),
        )
    rule.onNodeWithTag("default_reader_comic")
        .assertIsSelected()
}
```

These snippets use the real flow-owned `SettingsUiState` constructor and exact `SettingsScreen` signature. They add no `settingsState(...)` or other hidden helper.

- [ ] **Step 2: Compile and run the Settings class to verify RED**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.HomeSettingsScreenTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: the large-font scroll test fails because the current Column cannot scroll to save, and the connection result still looks like debug output.

- [ ] **Step 3: Verify the flow handoff and consume the existing primitives**

Confirm the branch contains foundation Task 3 and flow Task 4 commits. Inspect `SettingsScreen` and retain flow Task 4's `MediaUrlField` state derivation, save/discard behavior, `BackHandler`, and exact callback names. Do not recreate or change `MediaUrlField.kt` / `SettingsSection.kt` here; this task only moves their already-wired calls into the final scrollable layout.

- [ ] **Step 4: Rebuild Settings with LazyColumn and IME insets**

Use `MediaScreenScaffold`, then:

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .testTag("settings_list"),
    contentPadding = PaddingValues(
        start = MediaTheme.spacing.md,
        end = MediaTheme.spacing.md,
        top = MediaTheme.spacing.md,
        bottom = MediaTheme.spacing.xxl,
    ),
    verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.lg),
) {
    item { serverSection() }
    item { readerSection() }
}
```

Apply `Modifier.imePadding()` to the scrolling content. Wrap the save button in `Box(Modifier.testTag("settings_primary_action"))` and keep `MediaPrimaryButton(..., modifier = Modifier.testTag("save_server"), loading = state.isSaving)` on its child. Do the same for test connection with wrapper tag `settings_secondary_action` and child tag `test_connection`. This preserves existing selectors while making the one-primary/one-secondary hierarchy executable without stacking two replacing `testTag` modifiers on one node. Tag save errors `settings_save_error` and apply `liveRegion = Polite`. Keep the flow plan's discard dialog and `BackHandler` unchanged.

- [ ] **Step 5: Run Settings unit/connected regressions**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.settings.SettingsViewModelTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.HomeSettingsScreenTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: save retry/discard behavior, large-font scrolling, action hierarchy and selected reader mode PASS.

- [ ] **Step 6: Commit Settings visual unit**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt `
  app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt
git commit -m "feat: redesign accessible server settings"
```

### Task 7: Immersive Image Reader Visual Integration

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderToolbar.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageItemErrorPanel.kt`
- Modify only for loading/error slots: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt`
- Modify only for loading/error slots: `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`

**Interfaces:**
- Depends on flow plan Task 5 explicit user retry behavior.
- Keeps all `ImageReaderScreen` callbacks, image request generations, zoom/comic transform, sort and anchor behavior.
- Consumes: `MediaTheme.playerColors`, `MediaTopAppBar`, `MediaStatePanel`, `MediaOptionMenu`.

- [ ] **Step 1: Add failing immersive-state and retry-label tests**

Add to `ImageReaderScreenTest.kt`:

```kotlin
@Test
fun readerUsesImmersiveToolbarAndKeepsNetworkRetryExplicit() {
    val base = contentState()
    val failedLogicalUrl = base.anchorLogicalUrl
    var retriedLogicalUrl: String? = null
    setScreen(
        state = base.copy(
            itemFailures = mapOf(
                failedLogicalUrl to ImageItemFailure(
                    message = "连接已失效",
                    kind = ImageLoadFailureKind.NETWORK,
                ),
            ),
        ),
        onRetryImage = { retriedLogicalUrl = it },
    )

    rule.onNodeWithTag("image_reader_scrim").assertIsDisplayed()
    rule.onNodeWithText("2 / 3").assertIsDisplayed()
    rule.onNodeWithText("重新连接并重试").performClick()
    rule.runOnIdle {
        assertEquals(failedLogicalUrl, retriedLogicalUrl)
    }
}
```

This uses the existing real `contentState()` and `setScreen(...)` helpers, the real `ImageReaderUiState.Content.itemFailures` map, and the failed item's logical URL. Retain all existing tests for mode switching, sorting, single-image failure, comic anchor and endpoint-refresh generation.

- [ ] **Step 2: Run ImageReader tests and verify RED**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: the new scrim/count assertions fail against the opaque default TopAppBar.

- [ ] **Step 3: Apply the immersive shell without changing image identity**

Keep the reader canvas `MediaTheme.playerColors.canvas`. `ImageReaderToolbar` receives `currentIndex` and `totalCount`, renders filename at one line with ellipsis and `"${currentIndex + 1} / $totalCount"` as metadata. Its background is a top gradient from `0xB3000000` to transparent and has tag `image_reader_scrim`; all action buttons consume `safeDrawing` top inset.

Use `MediaIconButton` for Back/Mode/Sort. Mode exposes state description `"条漫"` or `"单图"`; sort menu uses `MediaOptionMenu` with selected check. Decorative icons are cleared from semantics when the parent action already names them.

- [ ] **Step 4: Unify reader states and local image errors**

Map directory Loading/Empty/Error to `MediaStatePanel` on the black canvas, using player-readable colors. When `state.isRefreshingEndpoint`, show a non-blocking top chip `"正在重新连接"` instead of a second centered spinner.

`ImageItemErrorPanel` maps:

```text
NETWORK -> “重新连接并重试”
DECODE  -> “重试此图”
HTTP    -> “重试此图”
```

Do not change `requestGeneration`, `itemRequestGenerations`, `logicalUrl`, `ComicTransform`, zoom state or image-loader calls.

- [ ] **Step 5: Run complete image regressions**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.*' `
  --tests 'com.local.mediaviewer.ui.image.*' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.ComicReaderDynamicLoadingTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: all image logic and connected classes PASS with unchanged anchor/zoom/loading behavior.

- [ ] **Step 6: Commit Image visual unit**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/image `
  app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt
git commit -m "feat: unify immersive image reader visuals"
```

### Task 8: Foundation Integration and Verification Record

**Files:**
- Integration modify only by flow plan Task 7 owner: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Modify after flow Task 7 handoff: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Delete after all callers migrate: `app/src/main/java/com/local/mediaviewer/ui/components/AppErrorPanel.kt`
- Create: `docs/verification/2026-07-31-media-ui-foundation-pages.md`

**Interfaces:**
- Consumes: Tasks 1–7 and the flow plan's final `MediaViewerApp.kt` root integration.
- Produces: one global `MediaAppScaffold`, one global Snackbar host and a real bottom-bar slot for `NowPlayingBar`.

- [ ] **Step 1: Add the root bottom-bar integration regression after flow Task 7**

Extend `MediaViewerNavigationTest.kt` after flow Task 7 has stabilized root routing:

```kotlin
@Test
fun nowPlayingDockLeavesTheBrowserTailReachable() {
    container.playbackController.replaceQueue(
        items = listOf(
            QueueMediaItem(
                mediaKey = "playing",
                name = "正在播放.mp3",
                logicalUrl = "http://media.test/playing.mp3",
                kind = MediaKind.AUDIO,
            ),
        ),
        startMediaKey = "playing",
    )
    openNestedDirectory()
    rule.onNodeWithTag("browser_list")
        .performScrollToNode(hasText("样例.wav"))

    val dockTop = rule.onNodeWithTag("now_playing_bar")
        .fetchSemanticsNode().boundsInRoot.top
    val tailBottom = rule.onNodeWithText("样例.wav")
        .fetchSemanticsNode().boundsInRoot.bottom
    assertTrue(tailBottom <= dockTop)
}
```

This extends the class's existing single `setUp`, real `FakeAppContainer`, `QueueMediaItem` constructor, and `openNestedDirectory()` helper. It does not call a second `setContent`, does not invent `launchAppWith`, `queueItem` or `video`, and consumes the `browser_list` tag produced by Task 5. The independent 30-item lazy-list proof already lives in Task 4 with real `ServerShare` values.

- [ ] **Step 2: Verify the final root integration**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaViewerNavigationTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected after the flow Task 7 root commit: PASS. If it fails, return the reproducible test to the same flow Task 7 integration owner; Task 8 must not edit `MediaViewerApp.kt`. This is a post-integration regression gate, not an artificial RED against an earlier root revision.

- [ ] **Step 3: Let the single root owner install the app scaffold**

In the flow plan Task 7 integration commit, replace the root `Box` overlay with:

```kotlin
MediaAppScaffold(
    snackbarHostState = globalSnackbarHostState,
    bottomBar = {
        if (sessionState.currentItem != null) {
            NowPlayingBar(
                // pass the existing narrow state and callbacks
                modifier = Modifier.testTag("now_playing_bar"),
            )
        }
    },
) { appPadding ->
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = Modifier.padding(appPadding),
    ) {
        // retain the existing destinations
    }
}
```

This is an explicit instruction to the Task 7 single owner, not permission for another parallel task to edit `MediaViewerApp.kt`. The same owner collects playback persistence notices into `globalSnackbarHostState`. Do not place another SnackbarHost inside page routes.

- [ ] **Step 4: Run foundation and navigation gates**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.theme.ThemeTokensTest' `
  --tests 'com.local.mediaviewer.ui.icons.MediaIconsTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaComponentsTest,com.local.mediaviewer.MediaScaffoldTest,com.local.mediaviewer.HomeSettingsScreenTest,com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.MediaViewerNavigationTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: all JVM tests and available connected classes PASS. Verify 320×568, 360×800, width 600dp, video landscape, font scales 1.0/1.3/2.0, light/dark and IME cases represented by the owning page tests.

- [ ] **Step 5: Remove obsolete error/shell components and rerun compilation**

After `rg -n "AppErrorPanel|MediaRouteShell" app/src` shows only their definitions, delete those two obsolete files. Run:

```powershell
.\gradlew.bat :app:assembleDebug `
  :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: PASS and the old components have no references.

- [ ] **Step 6: Write and commit the foundation evidence**

Use `apply_patch` to create `docs/verification/2026-07-31-media-ui-foundation-pages.md` with exact commit, JVM/compile/connected commands, PASS/FAIL/NOT RUN results, theme/size/font/IME coverage and any unrun screenshots. Then:

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/components/AppErrorPanel.kt `
  app/src/main/java/com/local/mediaviewer/ui/components/MediaRouteShell.kt `
  app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt `
  docs/verification/2026-07-31-media-ui-foundation-pages.md
git commit -m "docs: record media ui foundation verification"
```

Deleted paths may be supplied to `git add`; do not add screenshots, APKs or unrelated untracked files.

---

## Requirement Traceability

| Requirement | Owner |
|---|---|
| light/dark/video tokens and contrast | Task 1 |
| shared icons, buttons and state panels | Task 2 |
| Material wrappers, adjustable controls and app/page scaffolds | Task 3 |
| Home states and shares | Task 4 |
| Browser retained content, breadcrumbs and rows | Task 5 |
| scrollable/IME-safe Settings | Task 6 |
| immersive Image Reader visual integration | Task 7 |
| root bottom bar, global Snackbar and obsolete component removal | Task 8 + flow plan Task 7 single owner |

Before execution, verify the flow plan Tasks 3/4/5 land before this plan Tasks 5/6/7 respectively. This plan's Tasks 1–4 can run earlier because they do not touch those flow files.
