# PDF 阅读器基础功能验证记录（2026-08-03）

## 总体状态：DONE_WITH_CONCERNS

PDF 功能门禁已通过。原设备失败的根因是测试观察方式不匹配，生产代码无需
修改；测试修复提交为 `3f7d827 test: align PDF reader device assertions`，并已由
scoped reviewer 审查通过。共享全类回归仍有 8 项非 PDF 既有失败，未在本 PDF
计划扩修或重验。

| 门禁 | 实际命令 | 结果 |
| --- | --- | --- |
| JVM 定向测试 | `./gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.pdf.*' --tests 'com.local.mediaviewer.ui.pdf.*' --tests 'com.local.mediaviewer.network.MediaClassifierTest' --tests 'com.local.mediaviewer.browser.BrowserViewModelTest' --tests 'com.local.mediaviewer.ui.browser.BrowserFormattersTest' '-Pkotlin.incremental=false' --no-daemon` | exit 0，`BUILD SUCCESSFUL`；原定向 XML 汇总 46/46 通过，新增 PDF UI 定向重验 10/10 通过。 |
| PDF 设备功能 | 原指定 connected 命令覆盖 5 个类；测试修复后仅重跑原 4 个 `PdfReaderScreenTest` 失败方法 | 设备 `emulator-5554`；重跑 4/4 通过。结合原结果：`PdfRendererInstrumentedTest` 1/1，`PdfReaderScreenTest` 原 5 项通过加定向重验 4/4，`BrowserScreenTest` 13/13（含 PDF 筛选），PDF 打开/返回释放及 PDF 重建复用均通过。**PDF 功能 PASS**。 |
| 共享全类回归 | `./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PdfRendererInstrumentedTest,com.local.mediaviewer.PdfReaderScreenTest,com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.MediaViewerNavigationTest,com.local.mediaviewer.AppActivityRecreationTest' '-Pkotlin.incremental=false' --no-daemon` | **PARTIAL**：原 49 项中的剩余 8 项失败均为非 PDF；包括 `MediaViewerNavigationTest` 5 项旧 `MediaViewer` 文案/玩家路径，以及 `AppActivityRecreationTest` 3 项后台播放/旧文案。按本计划范围未扩修、未重验，不写 PASS。 |
| Debug/Release 构建 | `./gradlew.bat :app:assembleDebug :app:assembleRelease '-Pkotlin.incremental=false' --no-daemon` | exit 0，`BUILD SUCCESSFUL`。 |

## Release APK 检查

- 项目的标准 Gradle Release 产物是
  `app/build/outputs/apk/release/app-release-unsigned.apk`；仓库
  `scripts/Build-PersonalRelease.ps1` 和既有计划均以该文件为输入再签名，因此原
  Task 7 写死的 `app-release.apk` 文件名不适用。
- 未签名产物为 40,547,986 字节（38.67 MiB）；只读检查其中 `lib/` 目录，ABI
  仅为 `arm64-v8a`（5 个 native library 条目）。本记录不声称已签名或真机通过。

原始失败和命令通道超时的过程摘要见
`.superpowers/sdd/2026-08-03-pdf-reader/task-7-report.md`（已忽略，不纳入提交）。
