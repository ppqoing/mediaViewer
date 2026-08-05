# 视频返回时后台播放修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复视频会话勾选“后台播放”后返回目录仍被停止并清空的问题，同时保持默认未勾选、切换应用和音频播放的既有行为。

**Architecture:** 由纯 Kotlin 的 `PlayerRouteLifecyclePolicy` 统一根据最近确认的媒体类型和当前路由会话开关决定 `LEAVE_ONLY` 或 `STOP_AND_CLEAR`。Compose 路由只传递状态并执行决策；播放服务、LibVLC、MediaSession 和前后台恢复状态机保持不变。

**Tech Stack:** Kotlin、Jetpack Compose、Navigation Compose、Media3 MediaSessionService、LibVLC、JUnit4、AndroidX Compose UI Test、Gradle。

## Global Constraints

- 后台播放开关只属于当前视频路由会话，每次新建播放器路由默认不选中。
- 勾选后返回只保留音频、队列和系统媒体控制，不实现画中画、悬浮窗或目录页视频画面。
- 未勾选视频返回仍保存进度、停止并清空；音频返回行为不变。
- 切换到其他应用时继续沿用当前 `VideoBackgroundPlaybackPolicy` 的暂停/恢复规则。
- 不修改速度、进度时钟、画面比例、自动隐藏、队列编辑、LibVLC、Media3 或依赖版本。
- 保留工作区中现有图标尺寸调整和产物文件，不覆盖、不回滚任何无关改动。
- 只做相关基础功能验证；仅重验失败项及受本次改动直接影响的门禁。

---

## 文件职责

- `app/src/main/java/com/local/mediaviewer/navigation/PlayerRouteLifecyclePolicy.kt`：唯一的视频路由退出动作决策。
- `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`：把当前路由开关传给 Ready 与 bootstrap 退出路径并执行动作。
- `app/src/main/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicy.kt`：只保留应用前后台暂停/恢复状态机，删除已迁移到路由策略的旧导航判断。
- `app/src/test/java/com/local/mediaviewer/navigation/PlayerRouteLifecyclePolicyTest.kt`：覆盖媒体身份、开关与重连身份保留的退出矩阵。
- `app/src/test/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicyTest.kt`：只验证切换应用及配置重建，不再锁定错误的导航退出规则。
- `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`：覆盖顶部返回键、系统返回键、队列保留、迷你播放器和新路由默认关闭。

### Task 1: 用失败测试定义会话开关控制的退出矩阵

**Files:**
- Modify: `app/src/test/java/com/local/mediaviewer/navigation/PlayerRouteLifecyclePolicyTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`

**Interfaces:**
- Consumes: `PlayerRouteLifecycleState(lastPresentedKind: MediaKind?)`、`PlayerRouteExitAction`。
- Produces: 期望接口 `PlayerRouteLifecyclePolicy.exitAction(state: PlayerRouteLifecycleState, backgroundPlaybackEnabled: Boolean): PlayerRouteExitAction`。

- [x] **Step 1: 将策略测试改为显式传入后台播放开关**

把已确认视频测试拆成以下断言，并给所有音频/未确认调用传入 `backgroundPlaybackEnabled = false`：

```kotlin
@Test
fun `confirmed video stops and clears only when background playback is disabled`() {
    val confirmedVideo = PlayerRouteLifecyclePolicy.observeCurrentItem(
        state = PlayerRouteLifecycleState(),
        currentKind = MediaKind.VIDEO,
    )

    assertEquals(
        PlayerRouteExitAction.STOP_AND_CLEAR,
        PlayerRouteLifecyclePolicy.exitAction(
            state = confirmedVideo,
            backgroundPlaybackEnabled = false,
        ),
    )
    assertEquals(
        PlayerRouteExitAction.LEAVE_ONLY,
        PlayerRouteLifecyclePolicy.exitAction(
            state = confirmedVideo,
            backgroundPlaybackEnabled = true,
        ),
    )
}
```

- [x] **Step 2: 强化导航测试的可见结果并加入系统返回键场景**

在 `videoBackgroundOptInPreservesQueueAndNewSessionResetsOff` 返回目录后加入：

```kotlin
rule.onNodeWithTag("now_playing_bar").assertIsDisplayed()
```

新增测试，打开视频并勾选后台播放，第一次 `Espresso.pressBack()` 关闭更多菜单，第二次 `Espresso.pressBack()` 退出播放器，然后断言目录、非空队列和 `now_playing_bar` 均存在：

```kotlin
@Test
fun videoBackgroundOptInSystemBackPreservesQueue() {
    openNestedDirectory()
    rule.onNodeWithText("样例.mp4").performClick()
    rule.onNodeWithContentDescription("更多播放设置").performClick()
    rule.onNodeWithTag("video_background_playback").performClick()

    Espresso.pressBack()
    Espresso.pressBack()

    rule.onNodeWithTag("browser_list").assertIsDisplayed()
    rule.onNodeWithTag("now_playing_bar").assertIsDisplayed()
    rule.runOnIdle {
        assertTrue(
            container.fakePlaybackController
                .sessionState.value.queue.items.isNotEmpty(),
        )
    }
}
```

- [x] **Step 3: 运行 JVM 测试并确认 RED**

Run:

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat testDebugUnitTest --tests 'com.local.mediaviewer.navigation.PlayerRouteLifecyclePolicyTest' --no-daemon
```

Expected: 编译失败，明确指出 `exitAction` 尚无 `backgroundPlaybackEnabled` 参数；失败原因必须来自新增契约，而不是环境或其他测试。

### Task 2: 最小实现路由退出决策并接入两条返回路径

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/navigation/PlayerRouteLifecyclePolicy.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt:546-557`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt:637-654`
- Modify: `app/src/main/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicy.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicyTest.kt`

**Interfaces:**
- Consumes: 当前路由的 `videoBackgroundPlaybackEnabled: Boolean` 与 `PlayerRouteLifecycleState`。
- Produces: `exitAction(state: PlayerRouteLifecycleState, backgroundPlaybackEnabled: Boolean): PlayerRouteExitAction`；Ready 与 bootstrap 退出统一调用它。

- [x] **Step 1: 实现最小策略**

将 `PlayerRouteLifecyclePolicy.exitAction` 改为：

```kotlin
fun exitAction(
    state: PlayerRouteLifecycleState,
    backgroundPlaybackEnabled: Boolean,
): PlayerRouteExitAction = if (
    state.lastPresentedKind == MediaKind.VIDEO &&
    !backgroundPlaybackEnabled
) {
    PlayerRouteExitAction.STOP_AND_CLEAR
} else {
    PlayerRouteExitAction.LEAVE_ONLY
}
```

- [x] **Step 2: 让 bootstrap 与 Ready 退出都传入同一路由开关**

在 `MediaViewerApp.kt` 的两个 `exitAction` 调用中加入：

```kotlin
backgroundPlaybackEnabled = videoBackgroundPlaybackEnabled,
```

保留现有动作执行：`STOP_AND_CLEAR` 分支清理 pending 状态并调用 `clearAll()`/`player.stopAndClear()`；`LEAVE_ONLY` 分支只调用 `player.leave()` 或直接安全离开 bootstrap。

- [x] **Step 3: 删除重复且已无生产调用的旧导航判断**

从 `VideoBackgroundPlaybackPolicy` 删除：

```kotlin
fun shouldStopAndClear(
    reason: VideoSessionExitReason,
): Boolean = reason == VideoSessionExitReason.NAVIGATE_AWAY
```

同时从 `VideoBackgroundPlaybackPolicyTest` 删除 `leaving video always stops and clears regardless of background setting`，其行为已由 `PlayerRouteLifecyclePolicyTest` 更准确覆盖。保留 `VideoSessionExitReason.NAVIGATE_AWAY` 枚举值，避免本次扩大 API 清理范围。

- [x] **Step 4: 运行策略与生命周期测试并确认 GREEN**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.local.mediaviewer.navigation.PlayerRouteLifecyclePolicyTest' --tests 'com.local.mediaviewer.player.VideoBackgroundPlaybackPolicyTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`，所有筛选测试通过。

- [x] **Step 5: 检查限定文件格式与重复策略引用**

Run:

```powershell
git diff --check -- app/src/main/java/com/local/mediaviewer/navigation/PlayerRouteLifecyclePolicy.kt app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt app/src/main/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicy.kt app/src/test/java/com/local/mediaviewer/navigation/PlayerRouteLifecyclePolicyTest.kt app/src/test/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicyTest.kt app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt
rg -n 'shouldStopAndClear\(' app/src
```

Expected: `git diff --check` 退出码 0；`rg` 无匹配。

### Task 3: 运行返回导航基础功能验证

**Files:**
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Verify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`

**Interfaces:**
- Consumes: Task 2 的退出动作接线。
- Produces: 顶部返回与系统返回在勾选/未勾选状态下的设备级证据。

- [x] **Step 1: 确认模拟器连接与 ABI**

Run:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell getprop ro.product.cpu.abi
```

Expected: 至少一个 `device` 状态模拟器；允许 x86_64，因为此处运行 Debug 导航测试，不用于 ARM64 Release 动态验收。

- [x] **Step 2: 只运行三个直接相关的导航测试**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaViewerNavigationTest#videoBackgroundDefaultsOffAndBackClearsQueue,com.local.mediaviewer.MediaViewerNavigationTest#videoBackgroundOptInPreservesQueueAndNewSessionResetsOff,com.local.mediaviewer.MediaViewerNavigationTest#videoBackgroundOptInSystemBackPreservesQueue" --no-daemon
```

Expected: 三项均通过。若筛选器不接受逗号分隔方法，则逐项运行同一命令，每次只传一个完整 `类#方法`，不扩大到整套设备测试。

- [x] **Step 3: 运行受影响编译和 Lint**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintRelease --no-daemon
```

Expected: `BUILD SUCCESSFUL`，无新增错误；既有弃用警告不作为本次失败。

### Task 4: 构建并核验 arm64-v8a Release APK

**Files:**
- Use: `scripts/Build-PersonalRelease.ps1`
- Output: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`
- Output: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256`

**Interfaces:**
- Consumes: 已通过 Task 2、3 的最终源代码和现有签名配置。
- Produces: 仅含 `arm64-v8a`、原生库保持压缩、通过签名和 16 KiB 对齐检查的 Release APK。

- [x] **Step 1: 运行与现有个人 Release 脚本等价的当前工作树构建门禁**

Run:

```powershell
.\scripts\Build-PersonalRelease.ps1 -Abi arm64-v8a
```

Expected: 脚本完成构建、Lint、签名、ABI、压缩、对齐与 SHA-256 检查并发布 APK；如脚本参数与当前版本不同，先读取脚本参数块后使用其等价 arm64-v8a 调用，不修改脚本。

- [x] **Step 2: 独立复核产物摘要**

Run:

```powershell
Get-Item -LiteralPath 'dist\mediaviewer-v1.1.0-arm64-v8a-release.apk' | Select-Object FullName,Length,LastWriteTime
Get-FileHash -Algorithm SHA256 -LiteralPath 'dist\mediaviewer-v1.1.0-arm64-v8a-release.apk'
```

Expected: 文件存在且哈希与脚本发布结果一致。报告 APK 绝对路径、字节数、SHA-256、ABI、签名和对齐结果；ARM64 真机未执行时明确标记动态播放验收 `NOT RUN`。

---

## 自检结果

- 规格覆盖：方案 A 的默认关闭、勾选返回保留、顶部/系统返回、重连身份、应用切换、音频隔离、新路由重置及非 PiP 边界均有对应任务或明确保持项。
- 占位符扫描：计划不含 `TBD`、`TODO`、“类似前项”或未定义接口。
- 类型一致性：所有步骤统一使用 `exitAction(state: PlayerRouteLifecycleState, backgroundPlaybackEnabled: Boolean): PlayerRouteExitAction`，调用点和测试参数一致。
