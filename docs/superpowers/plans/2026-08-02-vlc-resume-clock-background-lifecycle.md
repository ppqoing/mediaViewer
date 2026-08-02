# VLC 暂停恢复时钟与前后台生命周期修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除视频在播放器内“暂停 → 播放”后时间数字与进度滑块异常加速的问题，并把应用切后台从“退出并清空”改为按后台播放开关暂停或继续、回前台条件续播。

**Architecture:** 保留现有 MediaSessionService、Media3 控制层、LibVLC 4.0.0-eap29 和精确位置快照链路。手动续播只发送 `play()`，视频 Surface 刷新继续由既有 LibVLC `Vout` 事件负责；应用前后台行为由无 Android 依赖的纯 Kotlin 状态机决定，Compose 只转发 `PAUSE`、`PLAY` 动作。

**Tech Stack:** Kotlin 2.3.21、Jetpack Compose、AndroidX Lifecycle、Media3 1.10.1、LibVLC 4.0.0-eap29、kotlinx.coroutines、JUnit 4、Gradle、Android SDK Build Tools。

**Design:** `docs/superpowers/specs/2026-08-02-vlc-resume-clock-background-lifecycle-design.md`

---

## 0. 执行边界

- [ ] 不升级或替换 LibVLC、Media3、Kotlin、Gradle 插件。
- [ ] 不修改 `--network-caching=1500`、位置轮询周期、时钟外推、位置节流或总时长计算。
- [ ] 不删除 `refreshVideoOutput()` 公共接口，不修改 `AndroidVlcPlaybackEngine.refreshVideoOutput()` 和 `VideoOutputRefreshScheduler`。
- [ ] 只移除 `PlayerViewModel.playNow()` 在暂停视频续播时的无条件 Surface 刷新。
- [ ] 未勾选后台播放：切后台前正在播放才暂停并登记一次恢复请求；回前台仅恢复同一个视频。
- [ ] 已勾选后台播放：切后台继续播放，回前台不额外发送 `play()`。
- [ ] 切后台前已手动暂停：回前台保持暂停。
- [ ] 配置重建不暂停、不续播、不清队列。
- [ ] 从视频播放页返回目录或关闭播放页始终停止并清空队列，与后台播放开关无关。
- [ ] 音频不进入视频前后台状态机，现有音频行为不变。
- [ ] 只重跑本计划中刚刚失败的定向测试；不重跑此前已经通过的完整测试集。
- [ ] 只做基础功能性审查：定向单测、编译、Release Lint、APK 静态门禁和相关真机手测。
- [ ] 保留现有未跟踪内容，不暂存、不移动、不删除：`.superpowers/brainstorm/`、`dist/` 中非本次目标文件、`docs/analysis/`、`docs/verification/2026-07-30-arm64-compressed-release.md`。

## 1. 文件与职责

| 文件 | 操作 | 职责 |
|---|---|---|
| `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt` | 修改 | 固定暂停续播只调用 `play()` 的契约 |
| `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt` | 修改 | 移除手动续播后的无条件 Surface 刷新 |
| `app/src/test/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicyTest.kt` | 修改 | 覆盖后台暂停、条件续播、配置重建、重复事件与退出清队列 |
| `app/src/main/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicy.kt` | 修改 | 实现纯 Kotlin 视频前后台状态机 |
| `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt` | 修改 | 将 Lifecycle、播放器页和状态机动作接到现有控制器 |
| `docs/verification/2026-08-02-player-resume-exact-progress.md` | 修改 | 记录旧修复未解决用户场景并链接本次复验 |
| `docs/verification/2026-08-02-vlc-resume-clock-background-lifecycle.md` | 新建 | 记录测试、静态门禁、APK 和真机结果 |

---

## Task 1：解除手动续播与 Surface 强制刷新耦合

**Files:**

- Modify: `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt:124-142`
- Modify: `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt:772-805`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt:446-454`

### Step 1：先写失败测试

- [ ] 将测试重命名为 `paused video resumes without refreshing output`，并把核心断言改为：

```kotlin
viewModel.play()

assertEquals(listOf("play"), controller.playCommands)
assertEquals(0, controller.refreshVideoOutputCalls)
```

- [ ] 在 `paused scrub defers play until engine confirms target` 收到目标位置后的最终断言改为：

```kotlin
assertEquals(1, controller.playCalls)
assertEquals(listOf("play"), controller.playCommands)
assertEquals(0, controller.refreshVideoOutputCalls)
assertNull(viewModel.uiState.value.seekSync.pending)
```

### Step 2：只运行这两个新契约，确认 RED

- [ ] 执行：

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.player.PlayerViewModelTest.paused video resumes without refreshing output' `
  --tests 'com.local.mediaviewer.player.PlayerViewModelTest.paused scrub defers play until engine confirms target' `
  --no-daemon
```

预期：两个定向测试均因当前实现仍收到 `refresh` 而失败。编译、环境或依赖失败不算 RED 证据。

### Step 3：做最小生产修复

- [ ] 将 `PlayerViewModel.playNow()` 改为：

```kotlin
private fun playNow() {
    controller.play()
}
```

- [ ] 不改 `PlaybackController.refreshVideoOutput()`、`Media3PlaybackController.refreshVideoOutput()`、`LocalVideoOutputBinder` 或 `AndroidVlcPlaybackEngine`。

### Step 4：只重跑 Step 2 中失败的测试，确认 GREEN

- [ ] 原样重跑 Step 2 命令，预期两个测试通过。
- [ ] 检查边界：

```powershell
rg -n -C 4 'private fun playNow|refreshVideoOutput' `
  app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt
```

预期：`playNow()` 只有 `controller.play()`，该文件不再调用 `refreshVideoOutput()`。

### Step 5：提交 Task 1

- [ ] 只暂存本任务文件：

```powershell
git add -- `
  app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt `
  app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt
git commit -m "fix: decouple video resume from surface refresh"
```

---

## Task 2：实现可等待 MediaSession 重连的前后台状态机

**Files:**

- Modify: `app/src/test/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicyTest.kt:1-43`
- Modify: `app/src/main/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicy.kt:1-15`

### Step 1：用纯状态转换测试固定行为

- [ ] 替换现有策略测试，至少保留下列独立测试及断言：

1. `disabled background playback pauses playing video and remembers it`：`APP_BACKGROUND`、开关关闭、`playWhenReady=true`、当前 key 有效，结果为 `PAUSE`、`isForeground=false`、pending 等于当前 key。
2. `disabled background playback keeps manually paused video paused`：`playWhenReady=false` 时为 `NONE` 且无 pending。
3. `enabled background playback neither pauses nor schedules resume`：开关开启时为 `NONE` 且无 pending。
4. `configuration change does not pause or schedule resume`：配置重建为 `NONE` 且无 pending。
5. `foreground waits for session item then resumes matching video once`：`onAppStarted()` 后 current key 为 `null` 时等待；同一 key 到达后仅一次 `PLAY` 并清 pending；再次 reconcile 为 `NONE`。
6. `repeated stop does not lose or duplicate pending resume`：首次后台得到 pending 后，重复 `ON_STOP` 即使 `playWhenReady=false` 也保留 pending 但不再发 `PAUSE`。
7. `different item or closed player cancels pending resume`：key 变化或视频页消失均清 pending 且不播放。
8. `leaving video always stops and clears regardless of background setting`：只有 `NAVIGATE_AWAY` 的 `shouldStopAndClear()` 为 true。

关键测试代码：

```kotlin
val stopped = VideoBackgroundPlaybackPolicy.onAppStopped(
    state = VideoBackgroundLifecycleState(),
    backgroundPlaybackEnabled = false,
    reason = VideoSessionExitReason.APP_BACKGROUND,
    currentMediaKey = "video-1",
    playWhenReady = true,
)
assertEquals(VideoBackgroundLifecycleAction.PAUSE, stopped.action)
assertEquals("video-1", stopped.state.pendingResumeMediaKey)

val started = VideoBackgroundPlaybackPolicy.onAppStarted(stopped.state)
val waiting = VideoBackgroundPlaybackPolicy.reconcileForeground(
    state = started,
    currentMediaKey = null,
    hasActiveVideo = true,
)
assertEquals(VideoBackgroundLifecycleAction.NONE, waiting.action)
assertEquals("video-1", waiting.state.pendingResumeMediaKey)

val resumed = VideoBackgroundPlaybackPolicy.reconcileForeground(
    state = waiting.state,
    currentMediaKey = "video-1",
    hasActiveVideo = true,
)
assertEquals(VideoBackgroundLifecycleAction.PLAY, resumed.action)
assertNull(resumed.state.pendingResumeMediaKey)
```

### Step 2：只运行该策略测试，确认 RED

- [ ] 执行：

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.player.VideoBackgroundPlaybackPolicyTest' `
  --no-daemon
```

预期：因为新状态、动作和转换函数尚不存在而失败。环境失败不计为 RED。

### Step 3：实现最小纯 Kotlin 状态机

- [ ] 将策略文件实现为以下 API 和逻辑：

```kotlin
package com.local.mediaviewer.player

enum class VideoSessionExitReason {
    NAVIGATE_AWAY,
    APP_BACKGROUND,
    CONFIGURATION_CHANGE,
}

data class VideoBackgroundLifecycleState(
    val isForeground: Boolean = true,
    val pendingResumeMediaKey: String? = null,
)

enum class VideoBackgroundLifecycleAction {
    NONE,
    PAUSE,
    PLAY,
}

data class VideoBackgroundLifecycleTransition(
    val state: VideoBackgroundLifecycleState,
    val action: VideoBackgroundLifecycleAction,
)

object VideoBackgroundPlaybackPolicy {
    fun onAppStopped(
        state: VideoBackgroundLifecycleState,
        backgroundPlaybackEnabled: Boolean,
        reason: VideoSessionExitReason,
        currentMediaKey: String?,
        playWhenReady: Boolean,
    ): VideoBackgroundLifecycleTransition {
        val backgroundState = state.copy(isForeground = false)
        if (
            reason != VideoSessionExitReason.APP_BACKGROUND ||
            backgroundPlaybackEnabled
        ) {
            return VideoBackgroundLifecycleTransition(
                backgroundState.copy(pendingResumeMediaKey = null),
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        if (state.pendingResumeMediaKey != null) {
            return VideoBackgroundLifecycleTransition(
                backgroundState,
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        val mediaKey = currentMediaKey?.takeIf(String::isNotBlank)
        if (!playWhenReady || mediaKey == null) {
            return VideoBackgroundLifecycleTransition(
                backgroundState.copy(pendingResumeMediaKey = null),
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        return VideoBackgroundLifecycleTransition(
            backgroundState.copy(pendingResumeMediaKey = mediaKey),
            VideoBackgroundLifecycleAction.PAUSE,
        )
    }

    fun onAppStarted(
        state: VideoBackgroundLifecycleState,
    ): VideoBackgroundLifecycleState = state.copy(isForeground = true)

    fun reconcileForeground(
        state: VideoBackgroundLifecycleState,
        currentMediaKey: String?,
        hasActiveVideo: Boolean,
    ): VideoBackgroundLifecycleTransition {
        val pending = state.pendingResumeMediaKey
        if (!state.isForeground || pending == null) {
            return VideoBackgroundLifecycleTransition(
                state,
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        if (!hasActiveVideo) {
            return VideoBackgroundLifecycleTransition(
                clearPending(state),
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        if (currentMediaKey == null) {
            return VideoBackgroundLifecycleTransition(
                state,
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        return VideoBackgroundLifecycleTransition(
            clearPending(state),
            if (currentMediaKey == pending) {
                VideoBackgroundLifecycleAction.PLAY
            } else {
                VideoBackgroundLifecycleAction.NONE
            },
        )
    }

    fun clearPending(
        state: VideoBackgroundLifecycleState,
    ): VideoBackgroundLifecycleState =
        state.copy(pendingResumeMediaKey = null)

    fun shouldStopAndClear(
        reason: VideoSessionExitReason,
    ): Boolean = reason == VideoSessionExitReason.NAVIGATE_AWAY
}
```

### Step 4：只重跑 Step 2 的失败测试，确认 GREEN

- [ ] 原样重跑 Step 2 命令，预期策略测试全部通过。

### Step 5：提交 Task 2

- [ ] 只暂存策略和测试：

```powershell
git add -- `
  app/src/test/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicyTest.kt `
  app/src/main/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicy.kt
git commit -m "feat: model video background lifecycle"
```

---

## Task 3：把状态机接入 Compose 应用生命周期

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt:57-65`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt:129-165`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt:524-580`

### Step 1：建立状态和唯一动作出口

- [ ] 引入 `VideoBackgroundLifecycleAction`、`VideoBackgroundLifecycleState`、`VideoBackgroundLifecycleTransition`。
- [ ] 在 `activeVideoBackgroundPlaybackEnabled` 后加入：

```kotlin
var videoBackgroundLifecycleState by remember {
    mutableStateOf(VideoBackgroundLifecycleState())
}
val applyVideoBackgroundTransition =
    { transition: VideoBackgroundLifecycleTransition ->
        videoBackgroundLifecycleState = transition.state
        when (transition.action) {
            VideoBackgroundLifecycleAction.NONE -> Unit
            VideoBackgroundLifecycleAction.PAUSE -> playbackController.pause()
            VideoBackgroundLifecycleAction.PLAY -> playbackController.play()
        }
    }
```

该状态不使用 `rememberSaveable`：配置重建不制造恢复请求；播放和队列真值仍由 MediaSessionService 持有。

### Step 2：改写 ON_START / ON_STOP

- [ ] 将当前清队列逻辑替换为：

```kotlin
LifecycleEventEffect(Lifecycle.Event.ON_START) {
    playbackController.onAppStarted()
    videoBackgroundLifecycleState =
        VideoBackgroundPlaybackPolicy.onAppStarted(
            videoBackgroundLifecycleState,
        )
}
LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
    if (activeVideoEntryId != null) {
        applyVideoBackgroundTransition(
            VideoBackgroundPlaybackPolicy.onAppStopped(
                state = videoBackgroundLifecycleState,
                backgroundPlaybackEnabled =
                    activeVideoBackgroundPlaybackEnabled,
                reason = if (activity.isChangingConfigurations) {
                    VideoSessionExitReason.CONFIGURATION_CHANGE
                } else {
                    VideoSessionExitReason.APP_BACKGROUND
                },
                currentMediaKey = playbackSession.currentItem?.mediaKey,
                playWhenReady = playbackSession.playWhenReady,
            ),
        )
    } else {
        videoBackgroundLifecycleState =
            VideoBackgroundPlaybackPolicy.clearPending(
                videoBackgroundLifecycleState.copy(isForeground = false),
            )
    }
    playbackController.onAppStopped()
}
```

`ON_STOP` 不再调用 `clearAll()`；关闭后台播放时最多调用一次 `pause()`，队列和当前位置保留。

### Step 3：等待同一 MediaSession 项目后只续播一次

- [ ] 紧随生命周期块加入：

```kotlin
LaunchedEffect(
    videoBackgroundLifecycleState.isForeground,
    videoBackgroundLifecycleState.pendingResumeMediaKey,
    activeVideoEntryId,
    playbackSession.currentItem?.mediaKey,
) {
    applyVideoBackgroundTransition(
        VideoBackgroundPlaybackPolicy.reconcileForeground(
            state = videoBackgroundLifecycleState,
            currentMediaKey = playbackSession.currentItem?.mediaKey,
            hasActiveVideo = activeVideoEntryId != null,
        ),
    )
}
```

若控制器尚未重连，`currentItem == null` 时继续等待；同一 `mediaKey` 到达后才发 `play()`。`PLAY` 转换同时清除 pending，后续重组不会重复续播。

### Step 4：退出视频页时取消 pending 并始终停止清队列

- [ ] 在视频 `DisposableEffect(entry.id).onDispose` 清理活动项时，同时调用 `VideoBackgroundPlaybackPolicy.clearPending()`。
- [ ] 将 `leaveVideo` 改为：

```kotlin
val leaveVideo = {
    videoBackgroundLifecycleState =
        VideoBackgroundPlaybackPolicy.clearPending(
            videoBackgroundLifecycleState,
        )
    if (
        VideoBackgroundPlaybackPolicy.shouldStopAndClear(
            VideoSessionExitReason.NAVIGATE_AWAY,
        )
    ) {
        player.stopAndClear {
            navController.leavePlayerSafely()
        }
    }
}
```

不得再用 `videoBackgroundPlaybackEnabled` 决定是否退出；该开关只控制应用切后台。

### Step 5：只做编译和基础静态审查

- [ ] 执行 Kotlin 编译，不重跑测试：

```powershell
.\gradlew.bat compileDebugKotlin --no-daemon
```

- [ ] 检查关键上下文：

```powershell
rg -n -C 10 'LifecycleEventEffect\(Lifecycle.Event.ON_(START|STOP)\)' `
  app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt
rg -n -C 10 'val leaveVideo' `
  app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt
rg -n 'clearAll\(\)' `
  app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt
```

预期：编译通过；`ON_STOP` 上下文没有 `clearAll()`；`leaveVideo` 仍经 `stopAndClear` 退出。其他明确的队列清理入口不因本次修改删除。

### Step 6：提交 Task 3

- [ ] 只暂存应用接线文件：

```powershell
git add -- app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt
git commit -m "fix: preserve video session across app background"
```

---

## Task 4：定向复验、ARM64 Release 与证据记录

**Files:**

- Modify: `docs/verification/2026-08-02-player-resume-exact-progress.md`
- Create: `docs/verification/2026-08-02-vlc-resume-clock-background-lifecycle.md`
- Produce: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`
- Produce: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256`

### Step 1：确认边界，不重复完整测试集

- [ ] 检查提交和工作树：

```powershell
git status --short
git log --oneline -5
git diff HEAD~3 -- `
  app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt `
  app/src/main/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicy.kt `
  app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt `
  app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt `
  app/src/test/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicyTest.kt
```

- [ ] 只核对 Task 1、Task 2 的 GREEN 日志，不再运行已通过测试。

### Step 2：运行 Release Lint 与 ARM64 Release 构建

现有 `scripts/Build-PersonalRelease.ps1` 会无条件重跑完整 `testDebugUnitTest`，本计划不调用它。使用同一 APK 工具模块完成签名和静态门禁，但 Gradle 只执行 `clean lintRelease assembleRelease`。

- [ ] 执行：

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat clean lintRelease assembleRelease `
  -Pkotlin.incremental=false `
  --no-daemon `
  --stacktrace
```

预期：`BUILD SUCCESSFUL`，生成 `app/build/outputs/apk/release/app-release-unsigned.apk`。

### Step 3：对齐、个人签名并执行现有 APK 静态门禁

- [ ] 在同一 PowerShell 会话执行。命令只覆盖明确的目标 APK 和对应 SHA-256，不删除 `dist/` 其他内容：

```powershell
$ErrorActionPreference = 'Stop'
Import-Module .\scripts\ReleaseApkTools.psm1 -Force

$sdkRoot = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$buildTools = Find-CompleteAndroidBuildTools -SdkRoot $sdkRoot
$aapt = Join-Path $buildTools 'aapt.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$unsignedApk = (Resolve-Path `
  '.\app\build\outputs\apk\release\app-release-unsigned.apk').Path
$stage = Join-Path (Resolve-Path '.\app\build').Path 'manual-release'
$alignedApk = Join-Path $stage 'aligned.apk'
$signedApk = Join-Path `
  $stage `
  'mediaviewer-v1.1.0-arm64-v8a-release.apk'
$keystore = Join-Path $env:USERPROFILE '.android\debug.keystore'

New-Item -ItemType Directory -Path $stage -Force | Out-Null
$null = Assert-Arm64CompressedArchive `
  -ApkPath $unsignedApk `
  -MaximumBytes 70MB

& $zipalign -P 16 -f -v 4 $unsignedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'zipalign 对齐失败' }

$passwordVariable = 'MEDIAVIEWER_APKSIGNER_PASSWORD'
$previousPassword = [Environment]::GetEnvironmentVariable($passwordVariable)
$hadPassword = Test-Path "Env:$passwordVariable"
try {
  Set-Item -Path "Env:$passwordVariable" -Value 'android'
  & $apksigner sign `
    --ks $keystore `
    --ks-key-alias androiddebugkey `
    --ks-pass "env:$passwordVariable" `
    --key-pass "env:$passwordVariable" `
    --v4-signing-enabled false `
    --out $signedApk `
    $alignedApk
  if ($LASTEXITCODE -ne 0) { throw 'APK 个人签名失败' }
} finally {
  if ($hadPassword) {
    Set-Item -Path "Env:$passwordVariable" -Value $previousPassword
  } else {
    Remove-Item "Env:$passwordVariable" -ErrorAction SilentlyContinue
  }
}

$releaseContract = Assert-Arm64CompressedArchive `
  -ApkPath $signedApk `
  -MaximumBytes 70MB
$badging = @(& $aapt dump badging $signedApk)
if ($LASTEXITCODE -ne 0) { throw 'aapt 无法读取已签名 APK' }
$null = Assert-ApkBadgingMetadata `
  -Badging $badging `
  -ExpectedApplicationId 'com.local.mediaviewer' `
  -ExpectedVersionCode 3 `
  -ExpectedVersionName '1.1.0' `
  -ExpectedMinSdk 29 `
  -ExpectedTargetSdk 36 `
  -ExpectedAbi 'arm64-v8a'
$signatureOutput = @(
  & $apksigner verify --verbose --print-certs $signedApk
)
if ($LASTEXITCODE -ne 0) { throw 'APK 签名验证失败' }
$certificateSha256 = Get-ApkSignerCertificateSha256 `
  -ApkSignerOutput $signatureOutput
& $zipalign -c -P 16 -v 4 $signedApk
if ($LASTEXITCODE -ne 0) { throw '已签名 APK 对齐校验失败' }

$dist = Join-Path (Resolve-Path '.').Path 'dist'
$finalApk = Join-Path `
  $dist `
  'mediaviewer-v1.1.0-arm64-v8a-release.apk'
New-Item -ItemType Directory -Path $dist -Force | Out-Null
Copy-Item -LiteralPath $signedApk -Destination $finalApk -Force
$delivery = Write-VerifiedSha256 `
  -ApkPath $finalApk `
  -ChecksumPath "$finalApk.sha256"

[PSCustomObject]@{
  Apk = $finalApk
  SizeMiB = $releaseContract.SizeMiB
  Abi = $releaseContract.Abi
  Sha256 = $delivery.Sha256
  CertificateSha256 = $certificateSha256
}
```

预期：唯一 Native ABI 为 `arm64-v8a`，LibVLC Native 和 DEX 均压缩，APK 不超过 70 MiB，包名和版本正确，签名、16 KiB page 对齐和 SHA-256 二次验证通过。该包使用个人 Debug 证书，仅用于个人安装测试。

### Step 4：在 ARM64 真机只复验相关失败路径

- [ ] 安装本次 APK。若同包名旧应用签名不同，报告签名冲突，不擅自卸载或清除用户数据。
- [ ] 用用户原始失败视频，在 `1.0×` 下执行：播放约 10 秒 → 手动暂停 3 秒 → 再播放至少 60 秒。
- [ ] 确认右侧总时长不变；左侧时间约每真实 1 秒增加 1 秒；滑块与画面、声音一致；不得快速到末尾；不得画面卡住而声音继续。
- [ ] 未勾选后台播放且正在播放：切到其他应用至少 5 秒；MediaViewer 应暂停；切回后恢复同一视频、原位置附近并继续。
- [ ] 未勾选后台播放且已手动暂停：切后台再返回，仍保持暂停。
- [ ] 勾选后台播放：切后台继续播放；返回不跳转、不重置位置。
- [ ] 从视频页返回目录：停止播放并清空播放列表。
- [ ] 旋转或触发配置重建：不因本状态机额外暂停或续播。

没有 ARM64 真机时，以上项目均记录为 `NOT RUN`，不得以 APK 静态门禁代替真机通过。

### Step 5：记录证据并修正旧记录边界

- [ ] 在 `docs/verification/2026-08-02-player-resume-exact-progress.md` 末尾增加“后续用户复验”：明确旧实现仍在手动暂停续播场景失败，并链接本次记录；不改写旧命令和历史输出。
- [ ] 新建 `docs/verification/2026-08-02-vlc-resume-clock-background-lifecycle.md`，写入：

  - Git 修订；
  - Task 1、Task 2 定向 RED/GREEN 测试名与结果；
  - `compileDebugKotlin`、`lintRelease`、`assembleRelease` 结果；
  - APK 绝对路径、字节数、ABI、SHA-256、证书 SHA-256；
  - Step 4 每个真机场景的 `PASS`、`FAIL` 或 `NOT RUN`；
  - 失败时保留原始症状与复现步骤，不能把“部分改善”记为通过。

### Step 6：基础功能性复核与提交

- [ ] 检查未执行项目没有被写为 PASS：

```powershell
rg -n 'PASS|FAIL|NOT RUN|BUILD SUCCESSFUL|SHA-256|arm64-v8a' `
  docs/verification/2026-08-02-vlc-resume-clock-background-lifecycle.md
git status --short
```

- [ ] 只暂存本次两份验收文档；`dist/` 作为交付物不纳入 Git：

```powershell
git add -- `
  docs/verification/2026-08-02-player-resume-exact-progress.md `
  docs/verification/2026-08-02-vlc-resume-clock-background-lifecycle.md
git commit -m "docs: verify VLC resume and background lifecycle fix"
```

---

## 完成判定

只有以下条件全部满足，才可以宣称“代码修复并生成 ARM64 Release”：

- [ ] `PlayerViewModel.playNow()` 只调用 `play()`。
- [ ] Task 1 两个定向失败测试在最小修复后通过。
- [ ] Task 2 状态机定向失败测试在实现后通过。
- [ ] `MediaViewerApp` 的 `ON_STOP` 不再清空视频队列。
- [ ] 退出视频页仍停止并清空队列。
- [ ] `compileDebugKotlin`、`lintRelease`、`assembleRelease` 通过。
- [ ] 签名 APK 的 ABI、压缩、包元数据、签名、对齐、体积、SHA-256 静态门禁通过。
- [ ] 验收记录准确区分自动门禁与 ARM64 真机结果。

“用户原始问题已解决”只能在 ARM64 真机的暂停续播和前后台相关场景全部 PASS 后宣称；否则写“代码与静态门禁完成，真机复验 NOT RUN”或明确记录仍失败的场景。
