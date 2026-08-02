# PDF 阅读器基础功能验证记录（2026-08-03）

## 总体状态：BLOCKED

原因：指定的模拟器门禁 49 项中有 12 项失败；此外，要求的
`app/build/outputs/apk/release/app-release.apk` 未生成。以下结果仅覆盖 PDF
基础功能门禁；未进行真机运行声明。

| 门禁 | 实际命令 | 结果 |
| --- | --- | --- |
| JVM 定向测试 | `./gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.pdf.*' --tests 'com.local.mediaviewer.ui.pdf.*' --tests 'com.local.mediaviewer.network.MediaClassifierTest' --tests 'com.local.mediaviewer.browser.BrowserViewModelTest' --tests 'com.local.mediaviewer.ui.browser.BrowserFormattersTest' '-Pkotlin.incremental=false' --no-daemon` | exit 0，`BUILD SUCCESSFUL`；XML 汇总 46 项，失败 0、错误 0。 |
| 指定模拟器类 | `./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PdfRendererInstrumentedTest,com.local.mediaviewer.PdfReaderScreenTest,com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.MediaViewerNavigationTest,com.local.mediaviewer.AppActivityRecreationTest' '-Pkotlin.incremental=false' --no-daemon` | 设备 `emulator-5554` 在线（`device`、`boot_completed=1`）；仪器测试 exit 1，49 项中 12 项失败，**BLOCKED**。其中 `PdfReaderScreenTest` 4 项失败。 |
| Debug/Release 构建 | `./gradlew.bat :app:assembleDebug :app:assembleRelease '-Pkotlin.incremental=false' --no-daemon` | exit 0，`BUILD SUCCESSFUL`。 |

## Release APK 检查

- 要求路径 `app/build/outputs/apk/release/app-release.apk`：不存在，故该项
  **BLOCKED**。
- 实际生成的未签名产物：
  `app/build/outputs/apk/release/app-release-unsigned.apk`，40,547,986 字节
  （38.67 MiB）。只读检查其中 `lib/` 目录，ABI 仅为 `arm64-v8a`（5 个 native
  library 条目）。

原始失败和命令通道超时的过程摘要见
`.superpowers/sdd/2026-08-03-pdf-reader/task-7-report.md`（已忽略，不纳入提交）。
