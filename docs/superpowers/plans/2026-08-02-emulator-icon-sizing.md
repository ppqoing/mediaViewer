# Android 模拟器图标尺寸调整 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收紧 Android 应用中无显式尺寸的生成式 PNG 图标，同时保留 48dp 触摸目标和现有主次操作层级。

**Architecture:** 在 `MediaIconButton` 与 `PlayerIconButton` 中增加视觉图标尺寸参数，将触摸容器与图形尺寸解耦；对没有经过按钮封装的 `MediaIconImage` 调用按场景显式设置 20–28dp。保留主播放按钮、文件类型徽标、空状态、音频封面和分段控件现有尺寸。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Android Compose UI Test、Gradle、API 36 模拟器。

## Global Constraints

- 普通操作按钮图标画布默认 28dp。
- 播放器次级控制图标画布默认 32dp。
- 所有可点击图标的触摸目标继续不小于 48dp。
- 面包屑和行内图标使用 20–24dp。
- 不裁切、不缩放、不重新生成 `drawable-nodpi` PNG 文件。
- 不改变主播放按钮、文件类型徽标、空状态、音频封面和分段控件尺寸。
- 只做本次图标尺寸相关的基础功能性回归。

---

### Task 1: 按钮图标尺寸接口与回归测试

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaIconButton.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaComponentsTest.kt`

**Interfaces:**
- Consumes: `MediaTheme.sizing.minimumTouchTarget`、`MediaIconImage`。
- Produces: `MediaIconButton(..., iconSize: Dp = 28.dp)` 与 `PlayerIconButton(..., iconSize: Dp = 32.dp)`。

- [x] **Step 1: 编写失败的视觉占用测试**

在 `generatedIconButtonKeepsTouchTargetAndDescription` 中保留 48dp 触摸区域断言，并通过 `captureToImage()` 计算与角落背景色存在明显色差的像素包围盒，断言普通按钮图形宽高不超过 24px（测试密度为 1）。新增播放器按钮用例，断言其图形宽高不超过 28px。

```kotlin
val pixels = rule.onNodeWithContentDescription("搜索")
    .captureToImage()
    .toPixelMap()
val inkBounds = pixels.inkBoundsComparedWith(pixels[0, 0])
assertTrue(inkBounds.width <= 24)
assertTrue(inkBounds.height <= 24)
```

- [x] **Step 2: 运行测试并确认先失败**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaComponentsTest `
  -Pkotlin.incremental=false --no-daemon
```

Expected: `generatedIconButtonKeepsTouchTargetAndDescription` 的图形包围盒超过 24px，测试失败。

- [x] **Step 3: 实现按钮内部图标尺寸**

将接口改为：

```kotlin
fun MediaIconButton(
    icon: MediaIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    loading: Boolean = false,
    stateDescription: String? = null,
    iconSize: Dp = 28.dp,
)

fun PlayerIconButton(
    icon: MediaIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    loading: Boolean = false,
    stateDescription: String? = null,
    iconSize: Dp = 32.dp,
)
```

`SemanticIconButton` 接收 `iconSize`，并给非加载状态的 `MediaIconImage` 设置 `Modifier.requiredSize(iconSize)`；加载指示器继续使用 20dp。

- [x] **Step 4: 重新运行组件测试并确认通过**

Run: 与 Step 2 相同。

Expected: `MediaComponentsTest` 全部通过，触摸区域仍至少 48dp，普通与播放器图形包围盒符合上限。

实际：两项新增图标尺寸用例定向通过；整类运行仍会触发既有不稳定用例 `segmentedControlAndBottomNavigationUseStableIds`，与本次尺寸修改无关。

### Task 2: 收紧直接使用的页面图标

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaTopAppBar.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/MediaBreadcrumbs.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/home/ShareCard.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/settings/MediaUrlField.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaComponentsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`

**Interfaces:**
- Consumes: Task 1 的按钮默认尺寸。
- Produces: 顶栏 28dp、面包屑 20dp、行尾/输入框 24dp、队列标题栏 28dp 的显式尺寸。

- [x] **Step 1: 编写直接图标尺寸失败测试**

给面包屑分隔图标添加稳定测试标签 `breadcrumb_separator_<index>`，在 `BrowserScreenTest` 的多层路径用例中按测试密度断言其宽高均为 20dp。队列测试通过未合并语义树中的图标测试标签断言“更多”和“关闭”的视觉边界不大于 28dp，避免把外层 48dp 触控边界误判为图标边界。

```kotlin
val separator = rule.onNodeWithTag("breadcrumb_separator_1")
    .fetchSemanticsNode().boundsInRoot
assertEquals(20f, separator.width, 0.5f)
assertEquals(20f, separator.height, 0.5f)
```

- [x] **Step 2: 运行定向测试并确认先失败**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.PlaybackQueueUiTest `
  -Pkotlin.incremental=false --no-daemon
```

Expected: 新增的面包屑标签或尺寸断言失败。

- [x] **Step 3: 添加页面级显式尺寸**

实施以下调用约束：

```kotlin
// 顶栏返回
modifier = Modifier.requiredSize(28.dp)

// 面包屑分隔
modifier = Modifier
    .requiredSize(20.dp)
    .testTag("breadcrumb_separator_$index")

// 首页行尾和 URL 输入框前后图标
modifier = Modifier.requiredSize(24.dp)

// 队列标题栏更多与关闭
modifier = Modifier.requiredSize(28.dp)
```

播放器、图片查看器和迷你播放条通过 Task 1 的 `PlayerIconButton` 默认 32dp 自动收紧；主播放按钮继续使用其显式 32dp 图标。

- [x] **Step 4: 运行定向测试并确认通过**

Run: 与 Step 2 相同。

Expected: `BrowserScreenTest` 与 `PlaybackQueueUiTest` 通过。

### Task 3: 全量基础验证与模拟器复拍

**Files:**
- No production file changes expected.
- Create as untracked evidence: `artifacts/emulator-icon-audit-after/*.png`

**Interfaces:**
- Consumes: Task 1、Task 2 生成的 Debug APK。
- Produces: 变更后的截图证据与测试结果。

- [x] **Step 1: 运行静态与单元测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug `
  -Pkotlin.incremental=false --no-daemon
```

Expected: `BUILD SUCCESSFUL`。

- [x] **Step 2: 运行本次涉及的 Compose UI 测试**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaComponentsTest,com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.HomeSettingsScreenTest,com.local.mediaviewer.PlaybackQueueUiTest,com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.ImageReaderScreenTest `
  -Pkotlin.incremental=false --no-daemon
```

Expected: 所列测试类全部通过。

实际：116 项中 109 项通过、7 项既有交互/状态用例失败；最终再次定向运行本次 4 项图标尺寸用例，4/4 通过。

- [x] **Step 3: 构建并安装 Debug APK**

```powershell
.\gradlew.bat :app:assembleDebug -Pkotlin.incremental=false --no-daemon
adb -s emulator-5554 install -r -t app\build\outputs\apk\debug\app-debug.apk
```

Expected: 构建成功，安装输出 `Success`。

- [x] **Step 4: 复拍受影响页面**

复拍首页、设置、目录浏览、空目录、视频控制、播放队列、单图、漫画、GIF、音频播放器；逐张确认顶部操作、面包屑、迷你播放条、队列标题栏、图片缩放和播放器次级控制不再抢占文字或容器，且所有按钮仍可点击。

实际：已复拍首页错误态/已连接态、设置、目录根页、多级面包屑、空文件夹与图片共享入口。播放器和单图页的真实媒体目录含可能敏感内容，未导出媒体画面；这些位置通过通用/播放器按钮像素包围盒测试与队列标题栏 28dp 定向测试完成基础复验。多级面包屑在 API 36、440dpi 模拟器中实测 55×55px，即 20dp。

- [x] **Step 5: 检查工作树并提交**

```powershell
git diff --check
git status --short
git add docs/superpowers/specs/2026-08-02-emulator-icon-sizing.md docs/superpowers/plans/2026-08-02-emulator-icon-sizing.md app/src/main app/src/androidTest
git commit -m "fix(android): normalize generated icon sizing"
```

Expected: 只提交本计划列出的文档、生产代码和测试；保留用户已有的未跟踪文件不动。

状态：全量 Compose 回归仍有 7 项既有失败；2026-08-05 用户明确要求不再追加设备测试并直接提交。提交仅包含本计划列出的文档、生产代码和测试，临时 brainstorm、模拟器截图、APK 及其他既有未跟踪文件继续保留在本地。
