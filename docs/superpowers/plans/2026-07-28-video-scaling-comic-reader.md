# Video Scaling and Comic Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 LibVLC 视频输出布局，提供四种当前视频画面模式，并增加支持设置默认方式、六种排序、统一缩放和动态加载的当前目录条漫阅读器。

**Architecture:** 抽取不含 UI 状态的共享目录内容仓库，供浏览器与图片阅读器共同使用；视频端用项目自有模式和输出容器接口隔离 `VLCVideoLayout`，图片端用稳定逻辑 URL、专用 ViewModel 和分离的单图/条漫组件组织状态。所有新增功能保持 HTTP、DNS/IPv4、播放位置及服务器设置契约不变。

**Tech Stack:** Kotlin 2.3.21、Jetpack Compose BOM 2026.06.00、AndroidX Lifecycle/Navigation/DataStore、OkHttp 5.3.0、Coil 3.5.0、LibVLC 4.0.0-eap29、JUnit 4、Robolectric 4.16.1、Android Instrumentation API 36 x86_64。

## Global Constraints

- 最低 Android 版本保持 API 29，compileSdk/targetSdk 保持 36。
- 继续使用明文 HTTP、DNS A 记录和 IPv4；不新增 HTTPS 或认证。
- 固定入口仍为 `/middle/` 与 `/pik/`，目录格式仍为 Caddy JSON。
- 视频模式固定为等比适应、裁剪铺满、强制拉伸、原始尺寸。
- 新视频默认等比适应，临时选择不跨视频持久化。
- 新安装默认条漫；设置页可独立保存条漫或单图默认方式。
- 条漫只读取当前文件夹图片，不递归子目录。
- 初始排序为现有文件名升序；另提供文件名、修改时间、大小的升降序。
- 条漫初始定位点击图片，排序和模式切换保持当前图片锚点。
- 条漫统一等比缩放范围为 `1×–5×`，`1×` 铺满屏宽。
- 图片只动态加载可见及必要邻近项，不整目录预加载。
- Coil 磁盘缓存保持禁用，内存缓存上限保持应用可用内存的 20%。
- 单张图片解码目标不超过 `4_194_304` 像素。
- Bitmap、Drawable 和 Painter 不得进入 ViewModel。
- 不修改 Room 播放位置表和现有稳定媒体键。
- 不读取、复制、输出或记录真实媒体文件名；媒体行为使用生成夹具测试。
- 每个任务遵循测试先行：失败测试、最小实现、目标测试、相关回归、提交。
- 不引入新的第三方运行时依赖。

## Design Source

[批准的设计规格](../specs/2026-07-28-video-scaling-comic-reader-design.md)

## TODO

- [ ] [TODO 01：抽取共享目录内容仓库](video-scaling-comic-reader/01-directory-content-repository.md)
- [ ] [TODO 02：独立阅读偏好与设置页](video-scaling-comic-reader/02-reader-preferences-settings.md)
- [ ] [TODO 03：LibVLC 官方布局与画面模式引擎](video-scaling-comic-reader/03-libvlc-video-output.md)
- [ ] [TODO 04：视频画面模式状态与界面](video-scaling-comic-reader/04-video-scale-ui.md)
- [ ] [TODO 05：图片序列、排序、缩放与解码策略](video-scaling-comic-reader/05-image-reader-core.md)
- [ ] [TODO 06：图片阅读状态、目录上下文与导航](video-scaling-comic-reader/06-image-reader-state-navigation.md)
- [ ] [TODO 07：单图/条漫界面与统一缩放](video-scaling-comic-reader/07-comic-reader-ui.md)
- [ ] [TODO 08：图片错误恢复与动态加载验证](video-scaling-comic-reader/08-image-errors-dynamic-loading.md)
- [ ] [TODO 09：端到端回归与原生几何验收](video-scaling-comic-reader/09-end-to-end-verification.md)
- [ ] [TODO 10：中文文档、APK 与最终验收](video-scaling-comic-reader/10-delivery.md)

## Dependency Order

```text
TODO 01 ───────────────┐
                       ├── TODO 06 ── TODO 07 ── TODO 08 ──┐
TODO 02 ───────────────┘                                    │
                                                            ├── TODO 09 ── TODO 10
TODO 03 ── TODO 04 ─────────────────────────────────────────┘

TODO 05 ──────────────── TODO 06
```

TODO 01–05 都要在各自提交后保持项目可编译、相关测试通过。TODO 06 开始替换
旧图片查看导航；TODO 09 汇总两个功能流；TODO 10 只在实现提交干净后生成验收
记录和交付 APK。

## Planned File Structure

```text
app/src/main/java/com/local/mediaviewer/
├── browser/
│   ├── DirectoryContentRepository.kt
│   └── BrowserRepository.kt
├── image/
│   ├── ImageReaderMode.kt
│   ├── ReaderPreferencesRepository.kt
│   ├── DataStoreReaderPreferencesRepository.kt
│   ├── ImageReaderModels.kt
│   ├── ImageSequence.kt
│   ├── ComicTransform.kt
│   ├── ImageDecodePolicy.kt
│   ├── ImageLoadFailure.kt
│   └── ImageReaderViewModel.kt
├── playback/
│   ├── VideoScaleMode.kt
│   ├── PlaybackEngine.kt
│   └── AndroidVlcPlaybackEngine.kt
├── ui/image/
│   ├── ImageReaderScreen.kt
│   ├── ComicReader.kt
│   ├── SingleImageViewer.kt
│   └── ImageReaderToolbar.kt
└── ui/player/
    ├── VlcSurface.kt
    ├── VideoScaleMenu.kt
    └── VideoPlayerScreen.kt
```

测试文件按相同包结构放入 `app/src/test` 与 `app/src/androidTest`。完成替换后删除
不再使用的 `ImageViewerViewModel.kt` 与 `ImageViewerScreen.kt`，不保留两套并行
实现。

## Commit Sequence

```text
refactor: share directory content loading
feat: persist default image reader mode
fix: use LibVLC video layout and scale modes
feat: add video scale controls
feat: add image reader policies
feat: load image reader from directory context
feat: add zoomable comic reader
feat: recover image items and bound lazy loading
test: cover media viewing enhancements end to end
docs: document video scaling and comic reader
docs: record media viewing enhancement acceptance
```

## Final Verification

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat `
  testDebugUnitTest `
  lintDebug `
  assembleDebug `
  connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.realServerBaseUrl=http://192.168.1.17:8080' `
  --stacktrace
```

预期：

- JVM/Robolectric 全部通过；
- Android 设备测试全部通过且真实服务器烟测不跳过；
- Lint 0 error；
- Debug APK 构建成功；
- 正式运行依赖不含测试库；
- APK 包名 `com.local.mediaviewer`；
- minSdk 29、targetSdk 36；
- LibVLC 四种 ABI 完整；
- 工作树在提交验收记录后干净；
- `dist/mediaviewer-debug.apk` 与 SHA-256 存在且继续被 Git 忽略。
