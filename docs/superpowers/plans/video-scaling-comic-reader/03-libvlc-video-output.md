# TODO 03 LibVLC Video Output and Scale Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 `VLCVideoLayout` 替换裸 `SurfaceView` 绑定，修复画面尺寸/位置，并在播放引擎层支持四种模式。

**Architecture:** `PlaybackEngine` 只接收项目控制的 Android `ViewGroup` 输出宿主和 `VideoScaleMode`。`AndroidVlcPlaybackEngine` 私有创建 `VLCVideoLayout`、调用 `MediaPlayer.attachViews()` 并映射 LibVLC ScaleType。

**Tech Stack:** Android View interop、LibVLC 4.0.0-eap29、Compose `AndroidView`、Robolectric、Android Instrumentation。

## Global Constraints

- Compose 和 ViewModel 不得导入 LibVLC 类型。
- 默认模式为 `BEST_FIT`。
- 输出绑定、解绑和模式应用在主线程执行。
- Surface 重建不重新 `prepare()` 媒体。
- 音频页面不创建输出宿主。
- 离开播放器仍释放所有 LibVLC 资源。

## Files

- Create: `app/src/main/java/com/local/mediaviewer/playback/VideoScaleMode.kt`
- Create: `app/src/test/java/com/local/mediaviewer/playback/VideoScaleModeTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/playback/PlaybackEngine.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VlcSurface.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/LibVlcEngineCreationTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaPlaybackInstrumentedTest.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/LibVlcVideoOutputTest.kt`

## Interfaces

- Produces:

```kotlin
enum class VideoScaleMode {
    BEST_FIT,
    FILL_CROP,
    STRETCH,
    ORIGINAL,
}
```

- Updated engine contract:

```kotlin
interface PlaybackEngine : AutoCloseable {
    val state: StateFlow<PlaybackState>
    fun prepare(url: String)
    fun attachVideoOutput(host: ViewGroup)
    fun detachVideoOutput()
    fun setVideoScaleMode(mode: VideoScaleMode)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    override fun close()
}
```

- Internal-only mapping:

```kotlin
internal fun VideoScaleMode.toLibVlcScaleType():
    MediaPlayer.ScaleType
```

## Steps

- [ ] **Step 1: Write failing mode mapping tests**

Create a Robolectric test:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class VideoScaleModeTest {
    @Test
    fun `四种项目模式精确映射 LibVLC`() {
        assertEquals(
            MediaPlayer.ScaleType.SURFACE_BEST_FIT,
            VideoScaleMode.BEST_FIT.toLibVlcScaleType(),
        )
        assertEquals(
            MediaPlayer.ScaleType.SURFACE_FIT_SCREEN,
            VideoScaleMode.FILL_CROP.toLibVlcScaleType(),
        )
        assertEquals(
            MediaPlayer.ScaleType.SURFACE_FILL,
            VideoScaleMode.STRETCH.toLibVlcScaleType(),
        )
        assertEquals(
            MediaPlayer.ScaleType.SURFACE_ORIGINAL,
            VideoScaleMode.ORIGINAL.toLibVlcScaleType(),
        )
    }
}
```

- [ ] **Step 2: Run the mapping test and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.playback.VideoScaleModeTest'
```

Expected: compilation fails because the enum and mapping do not exist.

- [ ] **Step 3: Add mode and change the engine contract**

Create the enum and mapping exactly as defined above. Change
`PlaybackEngine` from `SurfaceView` to `ViewGroup`.

Before editing implementations, enumerate all fakes:

```powershell
rg -n "attachVideoSurface|detachVideoSurface|PlaybackEngine" app/src
```

Update each fake to implement:

```kotlin
override fun attachVideoOutput(host: ViewGroup) = Unit
override fun detachVideoOutput() = Unit
override fun setVideoScaleMode(mode: VideoScaleMode) = Unit
```

This step is mechanical only; do not add UI state yet.

- [ ] **Step 4: Run compilation and confirm the production engine fails**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin `
  :app:compileDebugUnitTestKotlin
```

Expected before implementing the engine: failure in
`AndroidVlcPlaybackEngine` and `VlcSurface` due to removed Surface methods.

- [ ] **Step 5: Implement `VLCVideoLayout` ownership**

Add fields:

```kotlin
private var videoHost: ViewGroup? = null
private var videoLayout: VLCVideoLayout? = null
private var videoScaleMode = VideoScaleMode.BEST_FIT
```

Implement:

```kotlin
override fun attachVideoOutput(host: ViewGroup) {
    check(!closed.get()) {
        "PlaybackEngine is closed"
    }
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "视频输出必须在主线程绑定"
    }
    detachVideoOutputInternal()

    val layout = VLCVideoLayout(host.context)
    host.removeAllViews()
    host.addView(
        layout,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
    mediaPlayer.attachViews(
        layout,
        null,
        false,
        false,
    )
    videoHost = host
    videoLayout = layout
    mediaPlayer.setVideoScale(
        videoScaleMode.toLibVlcScaleType(),
    )
}
```

`detachVideoOutputInternal()`:

```kotlin
private fun detachVideoOutputInternal() {
    if (videoLayout == null) return
    mediaPlayer.detachViews()
    videoHost?.removeAllViews()
    videoLayout = null
    videoHost = null
}
```

Public detach enforces main thread and returns safely when already detached.
`close()` calls the internal method before `mediaPlayer.stop()` and release,
even after the `closed` flag has changed.

- [ ] **Step 6: Implement mode application**

```kotlin
override fun setVideoScaleMode(mode: VideoScaleMode) {
    check(!closed.get()) {
        "PlaybackEngine is closed"
    }
    videoScaleMode = mode
    if (videoLayout != null) {
        mediaPlayer.setVideoScale(
            mode.toLibVlcScaleType(),
        )
    }
}
```

Keep `MediaPlayer.Event.Vout -> mediaPlayer.updateVideoSurfaces()`. It now
reaches LibVLC's `VideoHelper` because `attachViews(VLCVideoLayout, ...)`
created it.

- [ ] **Step 7: Change Compose host from SurfaceView to FrameLayout**

In `VlcSurface.kt`:

```kotlin
AndroidView(
    modifier = modifier.testTag("vlc_surface"),
    factory = { context ->
        FrameLayout(context).also { host ->
            host.keepScreenOn = keepScreenOn
            engine.attachVideoOutput(host)
        }
    },
    update = { host ->
        host.keepScreenOn = keepScreenOn
    },
    onRelease = { host ->
        host.keepScreenOn = false
        engine.detachVideoOutput()
    },
)
```

Do not import `VLCVideoLayout` in this file.

- [ ] **Step 8: Run unit and existing engine tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.playback.VideoScaleModeTest' `
  --tests 'com.local.mediaviewer.player.PlayerViewModelTest' `
  --tests 'com.local.mediaviewer.playback.PlaybackInterruptionsTest'
```

Expected: all pass.

- [ ] **Step 9: Write failing Android output geometry test**

Create `LibVlcVideoOutputTest` using `ActivityScenario`:

```kotlin
@Test
fun videoLayoutFillsHostAndAcceptsEveryScaleMode() {
    val context =
        ApplicationProvider.getApplicationContext<Context>()
    val engine = AndroidVlcPlaybackEngine(context)
    try {
        ActivityScenario.launch(MainActivity::class.java).use {
            scenario ->
            scenario.onActivity { activity ->
                val host = FrameLayout(activity).apply {
                    id = View.generateViewId()
                }
                activity.setContentView(
                    host,
                    ViewGroup.LayoutParams(800, 450),
                )
                engine.attachVideoOutput(host)
                assertEquals(1, host.childCount)
                assertTrue(
                    host.getChildAt(0) is VLCVideoLayout,
                )
                assertEquals(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    host.getChildAt(0).layoutParams.width,
                )
                assertEquals(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    host.getChildAt(0).layoutParams.height,
                )
                VideoScaleMode.entries.forEach(
                    engine::setVideoScaleMode,
                )
            }
        }
    } finally {
        engine.close()
    }
}
```

The test source may import `VLCVideoLayout`; production UI may not.

- [ ] **Step 10: Update native playback recreation test**

Replace `SurfaceView` with `FrameLayout` in
`MediaPlaybackInstrumentedTest`. Preserve all existing Range, play, seek and
recreation assertions. After recreation, call `attachVideoOutput()` on a new
host and set `FILL_CROP`; assert playback does not enter `ERROR` and the
position remains within the existing tolerance.

- [ ] **Step 11: Run Android engine tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.LibVlcVideoOutputTest,com.local.mediaviewer.LibVlcEngineCreationTest,com.local.mediaviewer.MediaPlaybackInstrumentedTest'
```

Expected: all tests pass on API 36 x86_64; generated MP4 still produces HTTP
Range requests.

- [ ] **Step 12: Run Lint and commit**

Run:

```powershell
.\gradlew.bat lintDebug assembleDebug
git diff --check
git status --short
git add app/src/main app/src/test app/src/androidTest
git commit -m "fix: use LibVLC video layout and scale modes"
```

Confirm `VLCVideoLayout` appears only in the playback implementation and
instrumentation test.
