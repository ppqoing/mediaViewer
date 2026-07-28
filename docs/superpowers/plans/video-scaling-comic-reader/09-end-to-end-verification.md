# TODO 09 End-to-End and Native Geometry Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用生成夹具覆盖视频几何、四种模式、设置默认值、图片目录导航、条漫滚动、排序、缩放和动态加载的完整回归。

**Architecture:** 保留纯策略单元测试，Compose 测试验证交互，真实 `AndroidVlcPlaybackEngine` 与 MockWebServer 验证原生输出和 HTTP Range。真实用户服务器只做目录 JSON 冒烟。

**Tech Stack:** AndroidJUnit4、Compose UI Test、ActivityScenario、MockWebServer、MediaCodec 生成 MP4、Coil、Room。

## Global Constraints

- 测试媒体只生成在应用测试缓存目录。
- 不读取 `I:\MiddleDir` 或 `G:\pik` 的文件名或正文。
- 不把真实目录条目写入日志、断言或报告。
- 视频必须继续验证 HTTP 206/Range、播放、seek 和 Surface 重建。
- 图片必须验证 50 项不会首屏全请求。
- 全量设备测试使用 API 36 x86_64。

## Files

- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaFixtureServerTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaPlaybackInstrumentedTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/MediaFixtureServer.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/MediaEnhancementsEndToEndTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/RealServerSmokeTest.kt` only if constructor wiring changed; do not broaden its data access.

## Interfaces

No new production interfaces. This task verifies the contracts produced by
TODO 01–08.

The test fake should expose deterministic controls:

```kotlin
class FakeAppContainer(
    context: Context,
    initialReaderMode:
        ImageReaderMode = ImageReaderMode.COMIC,
    directoryContent:
        DirectoryContent = defaultDirectoryContent(),
) : AppContainer, AutoCloseable
```

Fake engine records:

```kotlin
val attachedHosts: MutableList<ViewGroup>
val scaleModes: MutableList<VideoScaleMode>
```

## Steps

- [ ] **Step 1: Audit all existing device tests after refactor**

Run:

```powershell
rg -n "ImageViewer|ImageRoute|attachVideoSurface|SurfaceView" `
  app/src/androidTest app/src/test
```

Classify every match:

- obsolete reference: update or remove;
- deliberate generated video implementation: keep;
- LibVLC internal test import: keep.

Do not suppress compilation failures with unused compatibility shims.

- [ ] **Step 2: Strengthen Range fixture tests**

Keep existing exact 206 assertions. Add a generated page request:

```kotlin
MediaFixtureServer(
    fixtures = fixtures,
    imageCount = 50,
).use { server ->
    server.start()
    client.newCall(
        Request.Builder()
            .url(server.url("/pik/page-050.png"))
            .build(),
    ).execute().use { response ->
        assertEquals(200, response.code)
        assertEquals("image/png", response.header("Content-Type"))
    }
}
```

Do not print directory JSON.

- [ ] **Step 3: Verify native video host geometry after playback starts**

In `MediaPlaybackInstrumentedTest`, host the generated 4:3 MP4 in an
800×450 FrameLayout. After `durationMs > 0` and seekable:

```kotlin
scenario.onActivity { activity ->
    val host = requireNotNull(
        activity.findViewById<FrameLayout>(hostId),
    )
    val layout = host.getChildAt(0)
    assertEquals(host.width, layout.width)
    assertEquals(host.height, layout.height)
    assertEquals(0f, layout.translationX)
    assertEquals(0f, layout.translationY)
}
```

Then switch through all `VideoScaleMode.entries`, wait one UI frame per mode,
assert engine state is not `ERROR`, seek position stays within 2 seconds, and
Range request count does not reset to a new full media session.

This test verifies the original “右下角、小画面” regression through container
geometry and stable playback.

- [ ] **Step 4: Verify video recreation and current-only mode**

Set `STRETCH`, recreate the Activity, attach a replacement host, and assert:

- playback remains non-error;
- duration unchanged;
- position is not reset;
- current ViewModel UI still reports `STRETCH`.

Navigate away and construct a new player ViewModel; assert `BEST_FIT`. Use a
fake engine for the last assertion so no second native player is required.

- [ ] **Step 5: Add full image navigation test**

`MediaEnhancementsEndToEndTest` uses `FakeAppContainer` with at least:

```text
001.jpg
002.jpg
003.jpg
clip.mp4
subfolder/
```

Test:

```kotlin
rule.onNodeWithText("MiddleDir").performClick()
rule.onNodeWithText("示例目录").performClick()
rule.onNodeWithText("002.jpg").performClick()
rule.onNodeWithTag("comic_reader").assertIsDisplayed()
rule.onNodeWithText("002.jpg").assertIsDisplayed()
rule.onNodeWithTag("image_sort_menu").performClick()
rule.onNodeWithText("文件名降序").performClick()
rule.onNodeWithText("002.jpg").assertIsDisplayed()
```

Scroll to both `001.jpg` and `003.jpg` to prove the clicked image is an anchor,
not a truncated list start.

- [ ] **Step 6: Add default-mode settings end-to-end test**

Start with `COMIC`, open Settings, select `SINGLE`, return without testing the
server, open `002.jpg`, and assert:

```kotlin
rule.onNodeWithTag("media_image").assertIsDisplayed()
rule.onNodeWithTag("comic_reader").assertDoesNotExist()
```

Also assert the logical server URL remains unchanged and the reader preference
fake recorded exactly one `SINGLE` save.

- [ ] **Step 7: Verify uniform comic transform**

Use three fixed-size generated PNG items. Perform a two-finger zoom gesture on
`comic_reader` or drive an injectable transform state in the focused component
test. Assert semantics exported for tests report the same scale for every
visible item:

```text
comic_scale:001.jpg = 2.0
comic_scale:002.jpg = 2.0
```

Then perform horizontal drag and assert both items share the same horizontal
offset. Reset to `1×` and assert offset is zero.

Do not make production semantics expose request URLs; use a stable hash or
display name only in generated tests.

- [ ] **Step 8: Verify sorting matrices**

Use names, times and sizes whose six orders differ. For each
`ImageSortOrder.entries`, invoke the UI menu and assert visible order via
semantics collection. Keep `002.jpg` as anchor after every change.

Pure comparator correctness is already covered in TODO 05; this test verifies
menu-to-ViewModel wiring, not every tie rule again.

- [ ] **Step 9: Run targeted device suite repeatedly**

Run twice to catch lifecycle and lazy-list flakiness:

```powershell
1..2 | ForEach-Object {
  .\gradlew.bat :app:connectedDebugAndroidTest `
    '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.LibVlcVideoOutputTest,com.local.mediaviewer.MediaPlaybackInstrumentedTest,com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.ComicReaderDynamicLoadingTest,com.local.mediaviewer.MediaEnhancementsEndToEndTest'
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
```

Expected: both complete with zero failure and zero unexpected skip.

- [ ] **Step 10: Run all pre-commit gates**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --stacktrace
.\gradlew.bat connectedDebugAndroidTest --stacktrace
```

The no-argument full device run may skip only `RealServerSmokeTest`; it will be
run with real URL in TODO 10.

- [ ] **Step 11: Inspect reports**

Parse:

```powershell
app\build\test-results\testDebugUnitTest\TEST-*.xml
app\build\outputs\androidTest-results\connected\debug\TEST-*.xml
app\build\reports\lint-results-debug.xml
```

Assert total failures and errors are zero and Lint Error count is zero. Record
the current test totals for the delivery record; do not hardcode old totals.

- [ ] **Step 12: Review and commit**

Run:

```powershell
git diff --check
git status --short
rg -n "I:\\\\MiddleDir|G:\\\\pik" app/src
git add app/src/test app/src/androidTest
git commit -m "test: cover media viewing enhancements end to end"
```

Expected privacy scan: no output. If production fixes were required while
writing these tests, commit them separately with the smallest relevant TODO
instead of hiding them in the test commit.
