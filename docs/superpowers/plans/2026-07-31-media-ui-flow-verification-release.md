# Media UI and Flow Verification Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对整体界面重设计和 P0/P1 流程加固执行分层回归、设备视觉与媒体验收，最终交付只包含 `arm64-v8a` 的个人签名 Release APK 和可追溯证据。

**Architecture:** 本计划不再增加产品功能，而是依次消费界面地基、播放器/队列和流程加固三个计划的已审查提交。验证结果分为 JVM/静态门禁、API 36 connected tests、真实服务器/媒体、人工设备场景和 Release 产物五层，任何未运行层都明确标记 `NOT RUN`。

**Tech Stack:** Kotlin 2.3.21、Jetpack Compose BOM 2026.06.00、Material 3、Media3 1.10.1、LibVLC 4.0.0-eap29、Room 2.8.4、JUnit 4、Robolectric、Compose UI Test、Gradle、PowerShell 7、ADB、Android SDK 36

## Global Constraints

- 本计划只在 `docs/superpowers/plans/2026-07-31-media-ui-foundation-pages.md`、`docs/superpowers/plans/2026-07-31-media-player-queue-ui.md` 和 `docs/superpowers/plans/2026-07-31-app-flow-hardening.md` 完成后执行。
- 项目用于个人使用，不增加账号体系、复杂鉴权、遥测、审批或重量级验证平台。
- 视频进入后台后继续播放声音，回前台恢复当前实际位置的画面。
- 暂停拖动松手后保持暂停并显示目标帧；点击播放后从目标位置恢复。
- 播放页只保留一条主时间轴，不恢复其下方的独立加载进度条。
- 队列保留手动加入、插入下一项、跨多项排序、删除和跨重启恢复。
- 不增加字幕、多音轨、投屏、画中画、搜索、收藏或文件删除。
- Release 保持 `versionName = "1.1.0"`、`versionCode = 3`、`minSdk = 29`、`targetSdk = 36`，且只包含 `arm64-v8a`。
- APK 使用现有个人调试证书签名；密码、keystore 和真实密码值不得进入 Git 或验收文档。
- 所有 Gradle 调用由根执行者串行运行；定向命令添加 `-Pkotlin.incremental=false`。
- 自动化、模拟器、ARM64 设备、真实服务器和人工视觉结果分别陈述，不互相替代。
- 保留用户已有未跟踪 `.superpowers/brainstorm/` 和 `docs/verification/2026-07-30-arm64-compressed-release.md`；不得提交、覆盖或丢失。

## File Structure

| File | Responsibility |
|---|---|
| `scripts/Invoke-AndroidVerification.ps1` | 现有 JVM、Lint、Debug/Release、AndroidTest 编译、Manifest、ABI 和定向设备门禁 |
| `scripts/Build-PersonalRelease.ps1` | 从干净工作树执行测试、arm64 Release、对齐、个人签名、元数据和 checksum |
| `scripts/ReleaseApkTools.psm1` | APK ABI、压缩、badging、证书和 checksum 断言 |
| `app/src/androidTest/java/com/local/mediaviewer/*Test.kt` | 页面、导航、队列、播放器、后台和真实服务器设备验证 |
| `.superpowers/sdd/2026-07-31-ui-redesign/` | 忽略的审查、命令日志、截图和临时验收证据 |
| `docs/verification/2026-07-31-media-ui-flow-redesign.md` | 最终提交的分层验收记录 |
| `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk` | 最终个人签名 APK，不提交 |
| `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256` | 最终 checksum，不提交 |

## Overall Execution Order

1. 并行完成界面地基 Tasks 1–3 与流程 Tasks 1–5；任务只接触各自列出的非共享文件，所有 Gradle RED/GREEN 命令仍由根执行者串行运行。
2. 流程 Task 2 审查通过后执行流程 Task 6；页面视觉任务分别在对应流程状态任务后执行：Home 在流程 Task 1 后，Browser 在流程 Task 3 后，Settings 在流程 Task 4 后，Image 在流程 Task 5 后。
3. 界面地基 Tasks 1–3 审查通过后执行播放器 Tasks 1–5；播放器 Task 6 同时依赖流程 Task 6。
4. `docs/superpowers/plans/2026-07-31-app-flow-hardening.md` Task 7 是 `MediaViewerApp.kt` 唯一集成提交，等待流程 Tasks 1–6、界面地基 Tasks 1–7 和播放器 Tasks 1–6 全部完成。
5. 根集成后依次执行界面地基 Task 8、播放器 Task 7、流程 Task 8；最后才执行本验收计划 Tasks 1–8。

---

### Task 1: Program Completion and Scope Gate

**Files:**
- Inspect: `docs/superpowers/specs/2026-07-31-media-ui-system-flow-hardening-design.md`
- Inspect: `docs/superpowers/plans/2026-07-31-media-ui-foundation-pages.md`
- Inspect: `docs/superpowers/plans/2026-07-31-media-player-queue-ui.md`
- Inspect: `docs/superpowers/plans/2026-07-31-app-flow-hardening.md`
- Create, ignored: `.superpowers/sdd/2026-07-31-ui-redesign/preflight-result.md`

**Interfaces:**
- Consumes: reviewed commits from all three implementation plans
- Produces: one immutable code commit ID to which all verification results refer
- Produces: explicit list of preserved unrelated untracked paths

- [ ] **Step 1: Inspect branch, scope, and unresolved markers**

Run:

```powershell
git status --short --branch
git log --oneline --decorate -20
rg -n "TODO|TBD|NotImplementedError|即将支持" `
  app/src/main app/src/test app/src/androidTest
git diff --check HEAD
```

Expected:

- only the two pre-existing untracked paths are present;
- no unstaged product or test file remains;
- no newly introduced `TODO`, `TBD`, `NotImplementedError`, or false “即将支持” queue label exists;
- `git diff --check HEAD` emits no whitespace errors.

- [ ] **Step 2: Trace every specification completion requirement**

Read section 16 of the design specification and verify these concrete owners:

```text
design system/pages       -> media-ui-foundation-pages plan commits
audio/video/mini/queue    -> media-player-queue-ui plan commits
P0/P1 flow fixes          -> app-flow-hardening plan commits
full matrix/release       -> this plan
```

Use `apply_patch` to create `preflight-result.md` with:

- the exact current `git rev-parse HEAD`;
- the three plan paths;
- each specification completion item marked `COVERED` or `BLOCKED`;
- the exact two preserved untracked paths;
- no `PASS` claim for tests not yet run.

- [ ] **Step 3: Stop on a real scope blocker**

If any completion item is `BLOCKED`, return to the owning plan and add a failing test plus the minimal fix before continuing. If every item is `COVERED`, record `Scope gate: PASS`.

Expected: the task ends with a reviewable preflight document and no product changes.

### Task 2: Complete Local Android Gate

**Files:**
- Consume: `scripts/Invoke-AndroidVerification.ps1`
- Create, ignored: `.superpowers/sdd/2026-07-31-ui-redesign/logs/local-gate.log`

**Interfaces:**
- Consumes: final code commit from Task 1
- Produces: JVM, Lint, Debug/Release, AndroidTest compile, Manifest, Media3 and APK ABI evidence

- [ ] **Step 1: Run focused unit suites first**

Run serially:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*AppSessionViewModelTest' `
  --tests '*ControllerConnectionMachineTest' `
  --tests '*CurrentPlayerNavigationTest' `
  --tests '*BrowserViewModelTest' `
  --tests '*SettingsViewModelTest' `
  --tests '*ImageReaderViewModelTest' `
  --tests '*PlaybackCoordinatorTest' `
  --tests '*PlaybackPrimaryActionTest' `
  -Pkotlin.incremental=false `
  --no-daemon `
  --stacktrace
```

Expected: all focused suites PASS. A missing class means the corresponding owning plan is incomplete; do not silently remove it from the command.

- [ ] **Step 2: Run the repository verification script**

Because a clean Android build can exceed one tool yield, launch it as one background PowerShell process with output redirected to the ignored log, then poll the process and log without declaring timeout failure:

```powershell
$localGateLogDirectory = (
  '.\.superpowers\sdd\2026-07-31-ui-redesign\logs'
)
New-Item -ItemType Directory -Force `
  -Path $localGateLogDirectory | Out-Null
$localGateOutput = Join-Path $localGateLogDirectory 'local-gate.log'
$localGateError = Join-Path `
  $localGateLogDirectory `
  'local-gate.error.log'
$verificationProcess = Start-Process `
  -FilePath 'pwsh.exe' `
  -ArgumentList @(
    '-NoProfile',
    '-File', '.\scripts\Invoke-AndroidVerification.ps1',
    '-SdkRoot', 'C:\Users\Administrator\AppData\Local\Android\Sdk'
  ) `
  -RedirectStandardOutput $localGateOutput `
  -RedirectStandardError $localGateError `
  -WindowStyle Hidden `
  -PassThru
$verificationProcess.Id
```

Poll with:

```powershell
Get-Process -Id $verificationProcess.Id -ErrorAction SilentlyContinue
Get-Content -Tail 80 -LiteralPath $localGateOutput

# Polling may yield control between calls; perform this final gate only
# after the process has disappeared from Get-Process.
$verificationProcess.WaitForExit()
$verificationExitCode = $verificationProcess.ExitCode
$localGateText = Get-Content -Raw -LiteralPath $localGateOutput
$localGateErrorText = Get-Content -Raw -LiteralPath $localGateError
if ($verificationExitCode -ne 0) {
  throw (
    "本地自动门禁失败，退出码 $verificationExitCode`n" +
    $localGateErrorText
  )
}
if ($localGateText -notmatch '本地自动门禁通过') {
  throw '本地门禁退出为 0，但缺少成功标记'
}
```

Expected final log contains `本地自动门禁通过` and the process exit code is 0. Without `-RunDeviceTests`, connected tests remain `NOT RUN`.

- [ ] **Step 3: Record exact local result**

Append to the ignored preflight document using `apply_patch`:

```text
Local JVM/Lint/build gate: PASS
Device tests in this command: NOT RUN
```

If the process exits non-zero, record `FAIL` with the exact failing task and return to the owning TDD task before rerunning this entire local gate.

### Task 3: API 36 UI, Navigation, Player, Queue, and Background Tests

**Files:**
- Test: `app/src/androidTest/java/com/local/mediaviewer/AppLaunchTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/AppActivityRecreationTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaComponentsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaScaffoldTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaMaterialWrappersTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlayerBootstrapContentTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/VideoGestureLayerTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/BackgroundPlaybackTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaSessionControlsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/LibVlcVideoOutputTest.kt`
- Create, ignored: `.superpowers/sdd/2026-07-31-ui-redesign/logs/connected-api36.log`

**Interfaces:**
- Consumes: API 36 x86_64 AVD named `MediaViewerApi36`
- Produces: connected evidence for BOOT/NAV/UI/PLAY/QUEUE/BG paths that can be automated
- Tasks 3–5 run in the same PowerShell session; every direct ADB call still
  passes `-s $serial` and rechecks the named AVD before changing device state.

- [ ] **Step 1: Start or reuse the verified API 36 AVD**

Run:

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -RunDeviceTests `
  -AvdName 'MediaViewerApi36'
```

Expected: the script verifies API `36`, ABI `x86_64`, and the existing BackgroundPlayback, MediaSessionControls and LibVlcVideoOutput classes PASS. If the named AVD does not exist, record the exact reason as `NOT RUN` and do not claim device acceptance.

- [ ] **Step 2: Run the complete UI/flow class set**

Resolve the named AVD again after the script restores its environment, bind
all later Gradle/ADB calls to that exact serial, and derive the class argument
from the completed plan inventory:

```powershell
$adb = (
  'C:\Users\Administrator\AppData\Local\Android\Sdk' +
  '\platform-tools\adb.exe'
)
$matchingSerials = @(
  foreach ($line in (& $adb devices)) {
    if ($line -notmatch '^(emulator-\d+)\s+device$') {
      continue
    }
    $candidate = $Matches[1]
    $reportedName = (
      & $adb -s $candidate emu avd name 2>$null |
      Select-Object -First 1
    )
    if (
      $null -ne $reportedName -and
      $reportedName.Trim() -eq 'MediaViewerApi36'
    ) {
      $candidate
    }
  }
)
if ($matchingSerials.Count -ne 1) {
  throw (
    '必须且只能找到一个 MediaViewerApi36，实际数量：' +
    $matchingSerials.Count
  )
}
$serial = $matchingSerials[0]
$apiLevel = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
$abi = (& $adb -s $serial shell getprop ro.product.cpu.abi).Trim()
if ($apiLevel -ne '36' -or $abi -ne 'x86_64') {
  throw "设备身份错误：$serial API $apiLevel $abi"
}
$env:ANDROID_SERIAL = $serial
$deviceIdentity = "Serial=$serial API=$apiLevel ABI=$abi AVD=MediaViewerApi36"
$deviceIdentity | Set-Content -LiteralPath `
  '.\.superpowers\sdd\2026-07-31-ui-redesign\logs\connected-api36.log'

$requiredConnectedClasses = @(
  'AppLaunchTest',
  'AppActivityRecreationTest',
  'MediaComponentsTest',
  'MediaScaffoldTest',
  'MediaMaterialWrappersTest',
  'MediaViewerNavigationTest',
  'HomeSettingsScreenTest',
  'BrowserScreenTest',
  'ImageReaderScreenTest',
  'PlayerBootstrapContentTest',
  'PlayerScreenTest',
  'PlaybackControlsTest',
  'VideoControlsOverlayTest',
  'VideoGestureLayerTest',
  'PlaybackQueueUiTest',
  'BackgroundPlaybackTest',
  'MediaSessionControlsTest',
  'LibVlcVideoOutputTest'
)
$missingConnectedClasses = @(
  $requiredConnectedClasses | Where-Object {
    -not (Test-Path -LiteralPath (
      ".\app\src\androidTest\java\com\local\mediaviewer\$_.kt"
    ) -PathType Leaf)
  }
)
if ($missingConnectedClasses.Count -ne 0) {
  throw (
    '实施计划尚未交付这些 connected tests：' +
    ($missingConnectedClasses -join ', ')
  )
}
$connectedClassArgument = (
  $requiredConnectedClasses |
  ForEach-Object { "com.local.mediaviewer.$_" }
) -join ','
$connectedLogDirectory = (
  '.\.superpowers\sdd\2026-07-31-ui-redesign\logs'
)
New-Item -ItemType Directory -Force `
  -Path $connectedLogDirectory | Out-Null
$connectedStdout = Join-Path $connectedLogDirectory 'connected-api36.stdout.log'
$connectedStderr = Join-Path $connectedLogDirectory 'connected-api36.stderr.log'
$connectedEvidence = Join-Path $connectedLogDirectory 'connected-api36.log'
$connectedProcess = Start-Process `
  -FilePath '.\gradlew.bat' `
  -ArgumentList @(
    'connectedDebugAndroidTest',
    "-Pandroid.testInstrumentationRunnerArguments.class=$connectedClassArgument",
    '-Pkotlin.incremental=false',
    '--no-daemon',
    '--stacktrace'
  ) `
  -RedirectStandardOutput $connectedStdout `
  -RedirectStandardError $connectedStderr `
  -WindowStyle Hidden `
  -PassThru
$connectedProcess.WaitForExit()
$connectedExitCode = $connectedProcess.ExitCode
$connectedStdoutText = Get-Content -Raw -LiteralPath $connectedStdout
$connectedStderrText = Get-Content -Raw -LiteralPath $connectedStderr
@(
  $deviceIdentity
  "ConnectedClassCount=$($requiredConnectedClasses.Count)"
  "ExitCode=$connectedExitCode"
  '--- STDOUT ---'
  $connectedStdoutText
  '--- STDERR ---'
  $connectedStderrText
) | Set-Content -LiteralPath $connectedEvidence -Encoding utf8NoBOM
if ($connectedExitCode -ne 0) {
  throw (
    "API 36 connected gate 失败，退出码 $connectedExitCode`n" +
    $connectedStderrText
  )
}
```

Expected: all listed classes PASS with zero failed instrumentation tests.

- [ ] **Step 3: Triage failures by layer**

For each failure:

1. rerun only the failing class;
2. reproduce with a failing test that does not depend on test order;
3. use `superpowers:systematic-debugging`;
4. make the minimal owning-plan fix;
5. rerun the focused class;
6. rerun the full class set from Step 2.

Do not loosen semantics assertions, timeouts, or layout requirements merely to turn a real failure green.

- [ ] **Step 4: Record device automation boundary**

Record exact class counts, Gradle result and emulator identity in the ignored preflight document. Mark unautomated lock-screen, task-swipe, ARM64 decoder and real-server checks as `NOT RUN`.

### Task 4: Visual Adaptation and Accessibility Acceptance

**Files:**
- Consume: all page and player Compose tests
- Create, ignored: `.superpowers/sdd/2026-07-31-ui-redesign/screenshots/`
- Modify only on reproduced failure: owning UI/test files from the first two implementation plans

**Interfaces:**
- Consumes: light/dark theme, 320×568, 360×800, 600dp and landscape test cases from the UI plans
- Produces: device screenshots and explicit visual checklist results

- [ ] **Step 1: Verify automated adaptation and semantics assertions**

Run the focused connected classes that own layout and semantics:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaComponentsTest,com.local.mediaviewer.MediaScaffoldTest,com.local.mediaviewer.MediaMaterialWrappersTest,com.local.mediaviewer.HomeSettingsScreenTest,com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.PlaybackControlsTest,com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.PlaybackQueueUiTest' `
  -Pkotlin.incremental=false `
  --no-daemon
```

Expected assertions cover:

- 320×568, 360×800, width 600dp and video landscape;
- font scales 1.0, 1.3 and 2.0;
- light and dark themes;
- IME visibility and safe drawing/navigation insets;
- `contentDescription`, `stateDescription`, selected, disabled, toggle, adjustable and queue custom actions;
- mini player does not cover the final Home/Browser item.

- [ ] **Step 2: Capture the required screen set**

Use the test harness or normal app navigation to place each required state on the device, then capture with:

```powershell
$adb = 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe'
New-Item -ItemType Directory -Force -Path `
  '.\.superpowers\sdd\2026-07-31-ui-redesign\screenshots' | Out-Null
& $adb -s $serial exec-out screencap -p > `
  '.\.superpowers\sdd\2026-07-31-ui-redesign\screenshots\current.png'
```

Create separate descriptive files for:

```text
home-connected-light.png
home-error-dark.png
browser-content-dark.png
browser-empty-light.png
settings-ime-320-font2.png
image-reader-dark.png
audio-player-dark.png
video-player-normal.png
video-player-fullscreen-landscape.png
mini-player-browser-tail.png
queue-current-next.png
dialog-destructive.png
```

Do not commit screenshots. Inspect every PNG with the local image viewer rather than relying on file existence.

- [ ] **Step 3: Apply the visual checklist**

For every screenshot verify:

- no clipped primary action, title, breadcrumb, queue row or time label;
- status/navigation bars and gesture/three-button navigation do not cover content;
- current, next, disabled, error and buffering states have non-color cues;
- ordinary text contrast is at least 4.5:1 and key controls at least 3:1;
- only one main timeline is visible;
- normal volume popup is vertical; fullscreen volume and brightness rails use distinct colors and disappear;
- the image, ordinary pages and player share the same token language without placing bright video colors on a light surface.

Record each screenshot as `PASS` or `FAIL` in the ignored preflight document.

- [ ] **Step 4: Fix only reproduced visual defects**

For any `FAIL`, write or extend the smallest Compose test, confirm it fails, modify only the owning component, rerun the focused class, recapture the screenshot, and then rerun Step 1. Use one focused commit per rejected visual unit.

### Task 5: Real Server, Problem Media, and System Interaction Acceptance

**Files:**
- Test: `app/src/androidTest/java/com/local/mediaviewer/RealServerSmokeTest.kt`
- Consume: locally reachable server on port `9955` when available
- Create, ignored: `.superpowers/sdd/2026-07-31-ui-redesign/device-acceptance.md`

**Interfaces:**
- Consumes: real logical server URL and known problem media under `/tmp/wallpa/`
- Produces: separate PASS/FAIL/NOT RUN evidence for decoder, seek, background, lock-screen and focus behavior

- [ ] **Step 1: Verify the real server before touching the device**

Run:

```powershell
try {
  $response = Invoke-WebRequest `
    -Uri 'http://127.0.0.1:9955/tmp/wallpa/' `
    -TimeoutSec 5 `
    -UseBasicParsing
  "HTTP $($response.StatusCode)"
} catch {
  "NOT RUN: $($_.Exception.Message)"
}
```

Expected: HTTP 2xx/3xx and a directory/media response. If unavailable, mark every real-server/problem-media row `NOT RUN`; do not invent a fixture that claims to be the user's problem media.

- [ ] **Step 2: Bridge the host service and run real-server smoke**

When Step 1 succeeds:

```powershell
$adb = 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if ([string]::IsNullOrWhiteSpace($env:ANDROID_SERIAL)) {
  throw 'ANDROID_SERIAL 未绑定；在同一 PowerShell 会话重跑 Task 3 Step 2'
}
$serial = $env:ANDROID_SERIAL
$reportedName = (
  & $adb -s $serial emu avd name 2>$null |
  Select-Object -First 1
)
$apiLevel = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
$abi = (& $adb -s $serial shell getprop ro.product.cpu.abi).Trim()
if (
  $null -eq $reportedName -or
  $reportedName.Trim() -ne 'MediaViewerApi36' -or
  $apiLevel -ne '36' -or
  $abi -ne 'x86_64'
) {
  throw "拒绝在错误设备上设置 reverse：$serial API $apiLevel $abi"
}
$reverseOutput = @(
  & $adb -s $serial reverse tcp:9955 tcp:9955 2>&1
)
if ($LASTEXITCODE -ne 0) {
  throw "adb reverse 失败：$($reverseOutput -join [Environment]::NewLine)"
}
@(
  "AdbReverseSerial=$serial"
  'AdbReverse=tcp:9955->tcp:9955 PASS'
  $reverseOutput
) | Add-Content -LiteralPath `
  '.\.superpowers\sdd\2026-07-31-ui-redesign\logs\connected-api36.log'
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -RunDeviceTests `
  -AvdName 'MediaViewerApi36' `
  -RunRealServerTest `
  -RealServerBaseUrl 'http://127.0.0.1:9955'
```

Expected: `RealServerSmokeTest` PASS. Record that device `127.0.0.1:9955` reaches the host only because `adb reverse` is active.

- [ ] **Step 3: Execute the problem-media player matrix**

Use the actual media items under `/tmp/wallpa/` and record independently:

```text
PLAY-01 pause -> play: picture and sound both advance
PLAY-02 pause -> seek -> release: remains paused and shows target frame
PLAY-03 play after seek: starts from target with synchronized picture/sound
PLAY-04 repeated seek/progress: UI and engine remain within existing tolerance
PLAY-05 short/long buffering: spinner clears or actionable slow-network state appears
PLAY-06 formerly audio-only media: picture appears or explicit unsupported error appears
PLAY-07 background 15 seconds -> foreground: sound continues and current frame returns
PLAY-08 endpoint refresh: same mediaKey/queue/position retained
```

An ARM64 decoder claim requires an ARM64 device. x86_64 emulator evidence is recorded separately.

- [ ] **Step 4: Execute system interaction checks when hardware permits**

Record:

```text
BG-03 lock/unlock
BG-04 swipe task while playing and while paused
BG-05 process/service recovery
FOCUS-01 transient audio focus loss and conditional resume
FOCUS-02 permanent loss/headset or Bluetooth disconnect pauses without auto-resume
notification play/pause/previous/next/stop and cold launch
```

Unavailable telephony, headset, Bluetooth or ARM64 hardware is `NOT RUN` with the concrete reason.

- [ ] **Step 5: Record results without collapsing layers**

Use `apply_patch` to create `device-acceptance.md` containing emulator identity, `adb reverse` condition, each row's PASS/FAIL/NOT RUN, and the media filename used. Any FAIL returns to `superpowers:systematic-debugging` before release.

### Task 6: Independent Code Review and Final Regression

**Files:**
- Inspect: every committed product/test change since `2949a99`
- Create, ignored: `.superpowers/sdd/2026-07-31-ui-redesign/final-review-result.md`

**Interfaces:**
- Consumes: completed implementation and verification evidence
- Produces: spec-compliance review and code-quality review with no open Critical/Important findings

- [ ] **Step 1: Request a spec-compliance review**

Use `superpowers:requesting-code-review`. Give the reviewer:

```text
WHAT_WAS_IMPLEMENTED: MediaViewer design system, all scoped page/player/queue UI, and P0/P1 flow hardening
PLAN_OR_REQUIREMENTS: docs/superpowers/specs/2026-07-31-media-ui-system-flow-hardening-design.md
BASE_SHA: 2949a99
HEAD_SHA: output of git rev-parse HEAD
```

Require the reviewer to map every finding to an exact specification section and file/line.

- [ ] **Step 2: Request a code-quality review**

Use a fresh reviewer and provide the same base/head range. Require checks for:

- shared component dependency boundaries;
- `PlaybackStatus` versus `PlayerEntryState` type separation;
- stale coroutine/generation and lifecycle bugs;
- stable `mediaKey`/logical URL behavior;
- Surface identity and background service ownership;
- queue persistence, drag semantics and accessibility;
- unbounded recomposition, pointer input or polling loops;
- test assertions that merely mirror implementation.

- [ ] **Step 3: Resolve findings rigorously**

For each Critical/Important finding:

1. reproduce it with a failing test;
2. use `superpowers:receiving-code-review`;
3. make the minimal fix;
4. rerun focused and adjacent tests;
5. ask the same reviewer to re-review.

Minor findings may remain only when documented with a concrete reason and no conflict with the approved specification.

- [ ] **Step 4: Rerun the full local and connected gates**

Repeat Task 2 and Task 3 after the last code change.

Expected: final review says `Ready: Yes`, no open Critical/Important, local gate PASS, and available connected suite PASS.

### Task 7: Clean arm64 Personal Release

**Files:**
- Consume: `scripts/Build-PersonalRelease.ps1`
- Preserve: `.superpowers/brainstorm/`
- Preserve: `docs/verification/2026-07-30-arm64-compressed-release.md`
- Generated: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`
- Generated: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256`
- Preserve generated script record externally: `C:\tmp\mediaviewer-2026-07-31-ui-flow-build-script-record.md`

**Interfaces:**
- Consumes: clean, reviewed final code commit
- Produces: signed, aligned, arm64-only APK and checksum

- [ ] **Step 1: Fail closed, inventory, and stash the two unrelated untracked paths**

Run Steps 1–3 in the same PowerShell session. Parse Git's NUL-delimited
porcelain output, reject every path outside the exact allowlist, and inventory
every preserved file before creating a uniquely identified stash:

```powershell
function Invoke-GitText {
  param([Parameter(Mandatory)][string[]]$Arguments)
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = 'git.exe'
  $startInfo.UseShellExecute = $false
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $startInfo.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
  foreach ($argument in $Arguments) {
    $startInfo.ArgumentList.Add($argument)
  }
  $gitProcess = [Diagnostics.Process]::new()
  $gitProcess.StartInfo = $startInfo
  if (-not $gitProcess.Start()) {
    throw '无法启动 git'
  }
  $stdout = $gitProcess.StandardOutput.ReadToEnd()
  $stderr = $gitProcess.StandardError.ReadToEnd()
  $gitProcess.WaitForExit()
  if ($gitProcess.ExitCode -ne 0) {
    throw "git $($Arguments -join ' ') 失败：$stderr"
  }
  $stdout
}

function Get-GitStatusRecords {
  $raw = Invoke-GitText @(
    '-c', 'core.quotePath=false',
    'status', '--porcelain=v1', '-z', '--untracked-files=all'
  )
  @($raw -split "`0" | Where-Object { $_.Length -ne 0 })
}

function Assert-OnlyPreservedUserPaths {
  $records = @(Get-GitStatusRecords)
  $unexpected = @(
    foreach ($record in $records) {
      if ($record.Length -lt 4) {
        $record
        continue
      }
      $statusCode = $record.Substring(0, 2)
      $path = $record.Substring(3).Replace('\', '/')
      if (
        $statusCode -ne '??' -or
        (
          $path -ne (
            'docs/verification/' +
            '2026-07-30-arm64-compressed-release.md'
          ) -and
          -not $path.StartsWith('.superpowers/brainstorm/')
        )
      ) {
        $record
      }
    }
  )
  if ($unexpected.Count -ne 0) {
    throw (
      'Release clean room 发现未授权工作树路径：' +
      ($unexpected -join ' | ')
    )
  }
  $records
}

function Get-ReleaseUserInventory {
  param([Parameter(Mandatory)][string[]]$Roots)
  $repositoryRoot = (Resolve-Path '.').Path
  @(
    foreach ($root in $Roots) {
      Get-ChildItem -LiteralPath $root -Recurse -Force -File |
        ForEach-Object {
          [PSCustomObject]@{
            Path = [IO.Path]::GetRelativePath(
              $repositoryRoot,
              $_.FullName
            ).Replace('\', '/')
            Length = $_.Length
            Sha256 = (
              Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
            ).Hash.ToLowerInvariant()
          }
        }
    }
  ) | Sort-Object Path
}

$preservedRoots = @(
  '.\.superpowers\brainstorm',
  '.\docs\verification\2026-07-30-arm64-compressed-release.md'
)
foreach ($root in $preservedRoots) {
  if (-not (Test-Path -LiteralPath $root)) {
    throw "待保护的用户路径不存在：$root"
  }
}
$statusBefore = @(Assert-OnlyPreservedUserPaths)
if ($statusBefore.Count -eq 0) {
  throw '待保护的用户路径没有出现在 Git 状态中，停止而不是选择旧 stash'
}
$inventoryPath = (
  'C:\tmp\mediaviewer-ui-flow-user-files-before-release.json'
)
$beforeInventoryJson = (
  Get-ReleaseUserInventory -Roots $preservedRoots |
  ConvertTo-Json -Depth 4 -Compress
)
Set-Content -LiteralPath $inventoryPath `
  -Value $beforeInventoryJson `
  -Encoding utf8NoBOM `
  -NoNewline

$stashBefore = @(
  (
    Invoke-GitText @('stash', 'list', '--format=%H')
  ) -split '\r?\n' |
  Where-Object { $_.Length -ne 0 }
)
function Get-ReleaseStashEntries {
  @(
    (
      Invoke-GitText @(
        'stash', 'list', '--format=%H%x09%gd%x09%gs'
      )
    ) -split '\r?\n' |
    Where-Object { $_.Length -ne 0 } |
    ForEach-Object {
      $parts = $_ -split "`t", 3
      [PSCustomObject]@{
        Hash = $parts[0]
        Ref = $parts[1]
        Subject = $parts[2]
      }
    }
  )
}
$releaseStashMessage = 'temporary ui-flow release clean room'
$stashManifestPath = (
  'C:\tmp\mediaviewer-ui-flow-release-stash.json'
)
```

Expected: exactly the two allowed roots are inventoried and the pre-mutation
stash hash set is captured. No user file has been hidden yet; stash creation
begins only after Step 2 enters its outer restoration guard.

- [ ] **Step 2: Run the normal personal Release build inside a mandatory restore guard**

Launch the build as a background process because a clean Release can exceed
one tool yield. The same command wraps launch, wait, log reads, generated-record
preservation, exact-stash restoration and verification in `try/finally`; no
exception between launch and log parsing may leave user files hidden:

```powershell
$releaseFailure = $null
$restoreFailure = $null
$recordMoveFailure = $null
$releaseExitCode = $null
$releaseStash = $null
try {
  try {
    Invoke-GitText @(
      'stash', 'push', '--include-untracked',
      '--message', $releaseStashMessage
    ) | Out-Null
    $newStashes = @(
      Get-ReleaseStashEntries | Where-Object {
        $stashBefore -notcontains $_.Hash -and
        $_.Subject -like "*$releaseStashMessage"
      }
    )
    if ($newStashes.Count -ne 1) {
      throw '没有创建唯一且匹配消息的 Release 临时 stash'
    }
    $releaseStash = $newStashes[0]
    $releaseStash |
      ConvertTo-Json -Compress |
      Set-Content -LiteralPath $stashManifestPath `
        -Encoding utf8NoBOM `
        -NoNewline
    if ((Get-GitStatusRecords).Count -ne 0) {
      throw '创建临时 stash 后工作树仍不干净'
    }

    $releaseProcess = Start-Process `
      -FilePath 'pwsh.exe' `
      -ArgumentList @(
        '-NoProfile',
        '-File', '.\scripts\Build-PersonalRelease.ps1',
        '-SdkRoot', 'C:\Users\Administrator\AppData\Local\Android\Sdk'
      ) `
      -RedirectStandardOutput `
        'C:\tmp\mediaviewer-ui-flow-release.log' `
      -RedirectStandardError `
        'C:\tmp\mediaviewer-ui-flow-release.error.log' `
      -WindowStyle Hidden `
      -PassThru
    $releaseProcess.Id
    # Poll this process/log in bounded tool yields while it runs.
    $releaseProcess.WaitForExit()
    $releaseExitCode = $releaseProcess.ExitCode
    $releaseStdout = Get-Content -Raw -LiteralPath `
      'C:\tmp\mediaviewer-ui-flow-release.log'
    $releaseStderr = Get-Content -Raw -LiteralPath `
      'C:\tmp\mediaviewer-ui-flow-release.error.log'
    if ($releaseExitCode -ne 0) {
      throw (
        "个人 Release 构建失败，退出码 $releaseExitCode`n" +
        $releaseStderr
      )
    }
  } catch {
    $releaseFailure = $_.Exception
  }
} finally {
  try {
    $beforeInventoryJson = Get-Content -Raw -LiteralPath $inventoryPath
    if ($null -eq $releaseStash) {
      try {
        $recoveredCandidates = @(
          Get-ReleaseStashEntries | Where-Object {
            $stashBefore -notcontains $_.Hash -and
            $_.Subject -like "*$releaseStashMessage"
          }
        )
      } catch {
        throw (
          '无法枚举可能的 Release stash。不要 drop；运行 ' +
          '"git stash list --format=''%H %gd %gs''" 人工恢复。原错误：' +
          $_.Exception.Message
        )
      }
      if ($recoveredCandidates.Count -eq 1) {
        $releaseStash = $recoveredCandidates[0]
      } elseif ($recoveredCandidates.Count -gt 1) {
        throw (
          '无法唯一识别 Release stash。不要 drop；运行 ' +
          '"git stash list --format=''%H %gd %gs''" 人工恢复。'
        )
      }
    }

    if ($null -ne $releaseStash) {
      $scriptRecord = (
        '.\docs\verification\2026-07-30-arm64-compressed-release.md'
      )
      if (Test-Path -LiteralPath $scriptRecord) {
        try {
          $uniqueRecordName = (
            'mediaviewer-2026-07-31-ui-flow-build-script-record-' +
            [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') +
            '-' + [Guid]::NewGuid().ToString('N') + '.md'
          )
          $externalScriptRecord = Join-Path 'C:\tmp' $uniqueRecordName
          Move-Item -LiteralPath $scriptRecord `
            -Destination $externalScriptRecord
        } catch {
          $recordMoveFailure = $_.Exception
        }
      }

      Invoke-GitText @(
        'stash', 'apply', [string]$releaseStash.Hash
      ) | Out-Null
      $restoredInventoryJson = (
        Get-ReleaseUserInventory -Roots $preservedRoots |
        ConvertTo-Json -Depth 4 -Compress
      )
      if ($restoredInventoryJson -cne $beforeInventoryJson) {
        throw '用户文件完整清单或 SHA-256 恢复后不一致'
      }
      Assert-OnlyPreservedUserPaths | Out-Null
      $resolvedRecordedRef = (
        Invoke-GitText @(
          'rev-parse', [string]$releaseStash.Ref
        )
      ).Trim()
      if ($resolvedRecordedRef -ne [string]$releaseStash.Hash) {
        throw '记录的 stash ref 已移动；拒绝删除任何 stash'
      }
      Invoke-GitText @(
        'stash', 'drop', [string]$releaseStash.Ref
      ) | Out-Null
      $remainingStashes = @(
        (
          Invoke-GitText @('stash', 'list', '--format=%H')
        ) -split '\r?\n' |
        Where-Object { $_.Length -ne 0 }
      )
      if ($remainingStashes -contains [string]$releaseStash.Hash) {
        throw '用户文件已恢复，但临时 stash 未被安全删除'
      }
    } else {
      $currentInventoryJson = (
        Get-ReleaseUserInventory -Roots $preservedRoots |
        ConvertTo-Json -Depth 4 -Compress
      )
      if ($currentInventoryJson -cne $beforeInventoryJson) {
        throw (
          '没有唯一 Release stash，且用户文件不在原位。不要 drop；' +
          '运行 "git stash list --format=''%H %gd %gs''" 人工恢复。'
        )
      }
    }
  } catch {
    $restoreFailure = $_.Exception
  }
}

if ($null -ne $restoreFailure) {
  throw $restoreFailure
}
if ($null -ne $recordMoveFailure) {
  throw $recordMoveFailure
}
if ($null -ne $releaseFailure) {
  throw $releaseFailure
}
```

Expected exit code 0 and artifacts:

```text
dist/mediaviewer-v1.1.0-arm64-v8a-release.apk
dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256
```

- [ ] **Step 3: Confirm the guarded restoration outcome**

The `finally` block above is the only restoration owner. Independently confirm
the final inventory and that the exact temporary stash hash is gone:

```powershell
$finalInventoryJson = (
  Get-ReleaseUserInventory -Roots $preservedRoots |
  ConvertTo-Json -Depth 4 -Compress
)
$beforeInventoryJson = Get-Content -Raw -LiteralPath $inventoryPath
if ($finalInventoryJson -cne $beforeInventoryJson) {
  throw 'Release guard 返回后用户文件清单不一致'
}
Assert-OnlyPreservedUserPaths | Out-Null
$remainingStashes = @(
  (
    Invoke-GitText @('stash', 'list', '--format=%H')
  ) -split '\r?\n' |
  Where-Object { $_.Length -ne 0 }
)
if (
  $null -ne $releaseStash -and
  $remainingStashes -contains [string]$releaseStash.Hash
) {
  throw 'Release guard 返回后临时 stash 仍存在'
}
```

Expected: `stash apply` succeeds; every file path, length and SHA-256 under both
preserved roots matches; only then is the exact temporary stash dropped. A
conflict or partial restore stops immediately and retains the stash for recovery.

- [ ] **Step 4: Independently verify Release identity**

Run:

```powershell
$sdkRoot = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$apk = '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk'
Import-Module .\scripts\ReleaseApkTools.psm1 -Force
$buildTools = Find-CompleteAndroidBuildTools -SdkRoot $sdkRoot
$signatureOutput = @(
  & "$buildTools\apksigner.bat" `
    verify --verbose --print-certs $apk 2>&1
)
if ($LASTEXITCODE -ne 0) {
  throw "APK 签名验证失败：$($signatureOutput -join [Environment]::NewLine)"
}
$certificateSha256 = Get-ApkSignerCertificateSha256 `
  -ApkSignerOutput $signatureOutput
$alignmentOutput = @(
  & "$buildTools\zipalign.exe" -c -P 16 -v 4 $apk 2>&1
)
if ($LASTEXITCODE -ne 0) {
  throw "APK zipalign 验证失败：$($alignmentOutput -join [Environment]::NewLine)"
}
$badging = @(& "$buildTools\aapt.exe" dump badging $apk 2>&1)
if ($LASTEXITCODE -ne 0) {
  throw "aapt badging 失败：$($badging -join [Environment]::NewLine)"
}
$verifiedMetadata = Assert-ApkBadgingMetadata `
  -Badging $badging `
  -ExpectedApplicationId 'com.local.mediaviewer' `
  -ExpectedVersionCode 3 `
  -ExpectedVersionName '1.1.0' `
  -ExpectedMinSdk 29 `
  -ExpectedTargetSdk 36 `
  -ExpectedAbi 'arm64-v8a'
$archiveVerification = Assert-Arm64CompressedArchive `
  -ApkPath $apk `
  -MaximumBytes 70MB
$actualSha = (
  Get-FileHash -LiteralPath $apk -Algorithm SHA256
).Hash.ToLowerInvariant()
$recordedSha = (
  (Get-Content -Raw -LiteralPath "$apk.sha256").Trim() -split '\s+'
)[0]
if ($actualSha -ne $recordedSha) {
  throw 'APK SHA-256 不一致'
}
"CertificateSha256=$certificateSha256"
$verifiedMetadata
$releaseVerificationLog = (
  '.\.superpowers\sdd\2026-07-31-ui-redesign' +
  '\logs\release-verification.log'
)
@(
  'SignatureExitCode=0'
  "CertificateSha256=$certificateSha256"
  '--- APKSIGNER ---'
  $signatureOutput
  'AlignmentExitCode=0'
  '--- ZIPALIGN ---'
  $alignmentOutput
  'BadgingExitCode=0'
  '--- AAPT BADGING ---'
  $badging
  '--- ASSERTED METADATA ---'
  ($verifiedMetadata | ConvertTo-Json -Compress)
  '--- ASSERTED ARCHIVE ---'
  ($archiveVerification | ConvertTo-Json -Compress)
  "ApkSha256=$actualSha"
  "RecordedSha256=$recordedSha"
) | Set-Content -LiteralPath $releaseVerificationLog `
  -Encoding utf8NoBOM
```

Expected:

- package `com.local.mediaviewer`;
- version `1.1.0 (3)`;
- min/target SDK `29/36`;
- only `arm64-v8a`;
- APK ≤70 MiB;
- zipalign and signature PASS;
- checksum file exactly matches the APK.

### Task 8: Verification Record and Delivery Commit

**Files:**
- Create: `docs/verification/2026-07-31-media-ui-flow-redesign.md`
- Do not commit: APK, checksum, screenshots, logs, stashes, passwords, `.superpowers/brainstorm/`, or the pre-existing 2026-07-30 record

**Interfaces:**
- Consumes: immutable code commit, Tasks 2–7 results, `connected-api36.log`,
  `release-verification.log`, and final APK metadata
- Produces: committed evidence record and final absolute artifact report

- [ ] **Step 1: Collect immutable values**

Run:

```powershell
$testedCommit = git rev-parse HEAD
$apk = '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk'
$apkItem = Get-Item -LiteralPath $apk
$verifiedSha = (
  Get-FileHash -LiteralPath $apk -Algorithm SHA256
).Hash.ToLowerInvariant()
"Commit=$testedCommit"
"ApkBytes=$($apkItem.Length)"
"SHA256=$verifiedSha"
```

Copy the exact connected exit/class count, certificate SHA-256, signing scheme,
zipalign, package/version/SDK/ABI, archive and checksum results from
`connected-api36.log` and `release-verification.log`; do not rely on terminal
scrollback or session variables.

- [ ] **Step 2: Write the evidence document using actual results**

Use `apply_patch` to create `docs/verification/2026-07-31-media-ui-flow-redesign.md` with these sections:

```text
# MediaViewer 整体界面与流程加固验收记录
1. Scope and tested commit
2. Local JVM/Lint/build gate
3. API 36 connected class results
4. Visual/adaptation screenshot checklist
5. Real server and problem-media results
6. Background/focus/device results
7. Independent review
8. Release APK metadata, signature limitation and SHA-256
9. Explicit NOT RUN items
```

Insert the exact collected values and commands. Every row must be `PASS`, `FAIL`, or `NOT RUN：具体原因`; do not use pending markers or infer a higher layer from a lower one.

- [ ] **Step 3: Validate and commit only the evidence document**

Run:

```powershell
rg -n "TODO|TBD|<[^>]+>|待补|稍后" `
  docs/verification/2026-07-31-media-ui-flow-redesign.md
git diff --check -- `
  docs/verification/2026-07-31-media-ui-flow-redesign.md
git add -- docs/verification/2026-07-31-media-ui-flow-redesign.md
git diff --cached --name-only
git commit -m "docs: record media UI flow verification"
```

Expected: marker scan has no matches, cached list contains only the new evidence document, and commit succeeds.

- [ ] **Step 4: Final report**

Run:

```powershell
git status --short --branch
Get-Item -LiteralPath `
  '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk', `
  '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256' |
  Select-Object FullName,Length,LastWriteTime
```

Report:

- absolute APK path;
- exact lowercase SHA-256;
- version, ABI and signing limitation;
- local, connected, real-server, ARM64 and manual results separately;
- final code/evidence commit IDs;
- the two preserved unrelated untracked paths.
