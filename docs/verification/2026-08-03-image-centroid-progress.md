# 图片中点缩放、连续阅读进度与功能区验证记录（2026-08-03）

## 总体状态：PARTIAL

图片相关 JVM 定向门禁和 Debug/Release 构建通过。模拟器三个指定类共执行
65 项，56 项通过、9 项失败；只定向重验这 9 个失败方法后仍为 9 项失败，
这是原始集成门禁和首次定向重验的历史事实。后续提交 `d121bcf` 仅修正
`holdingProgressPausesDeadlineAndReleaseRestartsIt` 的测试时钟边界，该方法定向
1/1 通过，生产代码未改，因此本计划新增图片功能门禁当前通过。其余 8 项已在
此前任务或 PDF 共享回归中记录为既有失败且未再重跑，完整三个类门禁仍非全绿，
总体状态保持 PARTIAL。

所有 Gradle 命令只在命令作用域设置：

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT='C:\Users\Administrator\AppData\Local\Android\Sdk'
```

## JVM 定向门禁

实际命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ZoomStateTest' `
  --tests 'com.local.mediaviewer.image.ComicTransformTest' `
  --tests 'com.local.mediaviewer.image.ReaderControlsStateTest' `
  --tests 'com.local.mediaviewer.ui.image.ComicProgressTest' `
  --tests 'com.local.mediaviewer.ui.image.ComicReaderPolicyTest' `
  --tests 'com.local.mediaviewer.image.ImageReaderViewModelTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

- 首次受限运行：退出码 1；Gradle 9.5.0 分发下载被沙箱网络权限拒绝，未进入测试，
  不计为测试失败。
- 在允许访问 Gradle 缓存/必要网络的环境中以相同命令重跑：退出码 0，
  `BUILD SUCCESSFUL`。
- XML 汇总：40/40 通过，0 失败，0 跳过。其中 `ZoomStateTest` 7 项、
  `ComicTransformTest` 10 项、`ReaderControlsStateTest` 3 项、
  `ComicProgressTest` 1 项、`ComicReaderPolicyTest` 3 项、
  `ImageReaderViewModelTest` 16 项。

## 模拟器设备门禁

执行前 `adb devices -l` 显示 `emulator-5554` 状态为 `device`；connected 测试
识别为 `Pixel_3a_API_36_extension_level_17_x86_64(AVD) - 16`。

一次性集成门禁命令：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.ComicReaderDynamicLoadingTest,com.local.mediaviewer.MediaViewerNavigationTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

结果：退出码 1，65 项中 56 项通过、9 项失败、0 跳过：

- `ImageReaderScreenTest`：40 项中 36 项通过、4 项失败；
- `ComicReaderDynamicLoadingTest`：4/4 通过；
- `MediaViewerNavigationTest`：21 项中 16 项通过、5 项失败。

遵循“只重验未通过测验”，之后仅以同一 connected 任务和
`android.testInstrumentationRunnerArguments.class` 定向列出下述 9 个失败方法，
没有重跑其余 56 个已通过方法。定向重验退出码 1，0/9 通过、9 项仍失败、
0 跳过。以下先保留该原始结果，再记录唯一新增门禁的后续定向修正；其余 8 项
没有再次运行。

### 本计划新增门禁的后续修正

- `ImageReaderScreenTest#holdingProgressPausesDeadlineAndReleaseRestartsIt`：首次完整
  门禁和定向重验均失败。测试在按住进度条 3.5 秒后松手，推进 2999 ms 再推进
  2 ms，并在 `ImageReaderScreenTest.kt:776` 要求顶部进度节点不存在。XML 精确错误为
  `Failed: assertDoesNotExist. Reason: Did not expect any node but found '1' node that satisfies: (TestTag = 'image_reader_toolbar_progress')`；找到的节点文本为
  `2 / 3`。这是本计划 Task 4 自动隐藏路径的原始新增门禁失败，不能归入既有失败。

后续按 Task 4 的 TDD 排查只修正测试时序：

- 原断言保持稳定 RED；临时状态诊断显示按下后
  `interactionActive=true, epoch=0`，抬手后为
  `interactionActive=false, epoch=1`，说明生产状态已经收到抬手并重启计时。
- 根因是 `autoAdvance=false` 时，2999 ms 后尚需显式帧启动/提交 Compose 状态；
  仅推进 2 ms 不能可靠提交到期后的界面变化。
- 提交 `d121bcf test: stabilize reader interaction auto-hide timing` 只改 AndroidTest：
  2999 ms 时上下功能区仍显示，第一个显式帧后仍显示，第二个显式帧后上下同步隐藏，
  容差约 32 ms。
- 仅定向运行该方法，1/1 通过，`BUILD SUCCESSFUL`；没有重跑其他已通过或既有失败
  方法。定向复审结论为 CLEAN，生产代码没有改动。

据此，本计划新增的按住暂停计时、抬手重计和上下同步隐藏门禁当前通过；但这不把
原始 65 项集成结果改写为全绿。

### 既有失败隔离

以下 8 项均在本计划实现前的任务报告或 2026-08-03 PDF 共享全类回归中已有记录，
本次没有扩展修复：

1. `ImageReaderScreenTest#singleImageSwipesLeftToNextAndRightToPrevious`：Task 2–4
   报告中的既有 pager 失败；预期切到 `c.webp`，实际仍为 `b.png`。
2. `ImageReaderScreenTest#zoomedSingleImagePansWithoutPagingUntilDoubleTapReset`：Task 2–4
   报告中的既有 pager 失败；预期切到 `c.webp`，实际仍为 `b.png`。
3. `ImageReaderScreenTest#loadingStateUsesChineseText`：Task 2–4 报告中的既有主题
   颜色断言失败；预期纯黑，实际为 `Color(0.08627451, 0.05882353, 0.043137256, 1.0)`。
4. `MediaViewerNavigationTest#unverified_settings_edit_back_leaves_without_confirmation`：
   2026-08-03 PDF 共享回归已记录的旧 `MediaViewer` 文案/共享导航失败。
5. `MediaViewerNavigationTest#browser_deep_reconnect_retains_current_breadcrumbs`：同一
   PDF 共享回归既有失败。
6. `MediaViewerNavigationTest#player_connecting_back_does_not_reopen_after_timeout`：同一
   PDF 共享回归既有失败。
7. `MediaViewerNavigationTest#notification_request_from_browser_returns_home_and_empty_queue_exits_once`：
   同一 PDF 共享回归既有失败。
8. `MediaViewerNavigationTest#failed_player_has_reconnect_and_back_without_an_infinite_spinner`：
   同一 PDF 共享回归既有失败。

上述 5 个共享导航方法本次都在各自末尾的 `MediaViewer` 文本
`assertIsDisplayed()` 处失败，错误为该文本节点未显示。

## Debug/Release 构建

实际命令：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease `
  '-Pkotlin.incremental=false' --no-daemon
```

结果：退出码 0，`BUILD SUCCESSFUL in 56s`。

## Release APK 核对

- Gradle `output-metadata.json` 记录的实际 Release 文件名为
  `app-release-unsigned.apk`，不是计划示例中的 `app-release.apk`。
- 绝对路径：
  `D:\code\mediaviewer\.worktrees\android-mediaviewer\app\build\outputs\apk\release\app-release-unsigned.apk`。
- 文件大小：40,572,246 字节。
- 使用 Android SDK 36.0.0 `apksigner verify --verbose --print-certs` 核对：退出码 1，
  `DOES NOT VERIFY`，错误为 `Missing META-INF/MANIFEST.MF`；该产物确为未签名 APK。
- 只读检查 APK ZIP 中的 native library：共 5 项，全部位于 `lib/arm64-v8a/`：
  `libandroidx.graphics.path.so`、`libc++_shared.so`、
  `libdatastore_shared_counter.so`、`libvlc.so`、`libvlcjni.so`；没有其他 ABI。
- 本次设备门禁使用 x86_64 模拟器，未执行 ARM64 真机安装或运行验收，因此不声称
  ARM64 真机通过。
