# Foundation 计划 Task 8 验证记录（Foundation Integration and Verification Record）

- 验证时间：2026-08-01 12:19 – 12:35 +08:00（verifier 执行窗口）
- HEAD commit：ccf244800e13f35c7b7d8df607cb67f3e894ad90（`test(android): lock app flow recovery regressions`，Flow Task 8）
- 分支：feature/android-mediaviewer（工作树干净，仅用户未跟踪路径 `.superpowers/brainstorm/` 与 `docs/verification/2026-07-30-arm64-compressed-release.md`，未触碰）
- 设备：emulator-5554（Pixel_3a_API_36_extension_level_17_x86_64，API 36），全程在线，无重启
- 环境：`ANDROID_HOME=C:\Users\Administrator\AppData\Local\Android\Sdk`；Git Bash；每条命令带 `'-Pkotlin.incremental=false' --no-daemon`，串行执行
- 根集成（计划 Step 3）已由 Flow Task 7 提交 60f5954 完成；本任务未改动 `MediaViewerApp.kt`

## Step 1：根 bottom-bar 集成回归

`MediaViewerNavigationTest.nowPlayingDockLeavesTheBrowserTailReachable` 按计划原文追加：
复用类既有单 `setUp`、`openNestedDirectory()` 与真实 `QueueMediaItem` 构造，无第二次
`setContent`。根 `NowPlayingBar` 已带 `Modifier.testTag("now_playing_bar")`
（`MediaViewerApp.kt:224`，Flow Task 7 接线），**无 dock tag 偏差**，断言 `now_playing_bar`
顶边而非迷你条替代方案。connected 实测 PASS（3.2s）。

## 门禁命令与结果

1. `./gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.ui.theme.ThemeTokensTest' --tests 'com.local.mediaviewer.ui.icons.MediaIconsTest' '-Pkotlin.incremental=false' --no-daemon`
   — **PASS**，BUILD SUCCESSFUL，共 4 tests，0 failures / 0 errors / 0 skipped
   （ThemeTokensTest 3；MediaIconsTest 1）。

2. `./gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false' --no-daemon`
   — **PASS**，BUILD SUCCESSFUL。

3. `./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaComponentsTest,com.local.mediaviewer.MediaScaffoldTest,com.local.mediaviewer.HomeSettingsScreenTest,com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.MediaViewerNavigationTest' '-Pkotlin.incremental=false' --no-daemon`
   — **57 PASS / 1 FAIL / 0 NOT RUN**（共 58 tests，BUILD FAILED 仅因下述已知环境失败）：
   - MediaComponentsTest 3/3 PASS；
   - MediaScaffoldTest 1/1 PASS；
   - HomeSettingsScreenTest 14 PASS / 1 FAIL：
     `settingsKeepsSaveAboveVisibleImeAfterUrlFocus` 为已知预存环境性失败——模拟器
     IME inset 恒 0，`waitUntil { imeBottomPx > 0 }` 5000ms 超时
     （ComposeTimeoutException，HomeSettingsScreenTest.kt:477），Flow Task 8 已定性
     为环境性，如实记录、不修；
   - BrowserScreenTest 9/9 PASS；
   - ImageReaderScreenTest 16/16 PASS；
   - MediaViewerNavigationTest 14/14 PASS，含新增
     `nowPlayingDockLeavesTheBrowserTailReachable`。

4. Step 5 重编译：`./gradlew.bat :app:assembleDebug :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false' --no-daemon`
   — **PASS**，BUILD SUCCESSFUL。

## 主题 / 尺寸 / 字体 / IME 覆盖矩阵（对照计划 Step 4）

| 覆盖项 | 结果 | 代表用例（所在测试类） |
|---|---|---|
| 320×568 | PASS（connected） | `HomeSettingsScreenTest.thirtySharesRemainReachableAt320DpAndTwoXFont`、`settingsKeepsSaveReachableAtLargeFontAndShowsActionHierarchy`；`ImageReaderScreenTest.immersiveToolbarKeepsControlsInsideA320DpTwoXFontWindow` |
| 360×800 | NOT RUN | 六个门禁类中无 360×800 代表；计划各 owning 任务亦未产出该用例（计划仅规定 320/600dp 适配），如实记缺失 |
| 600dp 宽 | PASS（connected） | `HomeSettingsScreenTest.wideHomeUses24DpPageGutterAt600Dp`（600×400）、`settingsUses24DpPageGutterAt600Dp`（600×500）；`ImageReaderScreenTest.immersiveCanvasRemainsFullWidthAt600Dp`（600×568） |
| 视频横屏 | NOT RUN | 六个门禁类中无横屏/landscape 用例 |
| 字体 1.0 | PASS（connected，默认） | 未覆盖 Density 的全部用例均以 fontScale 1.0 运行 |
| 字体 1.3 | NOT RUN | 六个门禁类中无 fontScale 1.3 代表（现存仅 2f 与默认） |
| 字体 2.0 | PASS（connected） | 上述三条 320dp 用例均 `fontScale = 2f` |
| 明 / 暗 | PASS（connected + JVM） | 暗色：`ImageReaderScreenTest` 五处 `MediaViewerTheme(darkTheme = true)` 用例（含 320dp 与 600dp 两条适配用例）；明暗 token 与对比度：`ThemeTokensTest` 3/3（JVM）；浅色为各页默认主题 |
| IME | FAIL（环境性，已定性） | `HomeSettingsScreenTest.settingsKeepsSaveAboveVisibleImeAfterUrlFocus`，见门禁 3 |

## Step 5：废弃组件删除验证

`grep -rn "AppErrorPanel|MediaRouteShell" app/src` 确认只剩定义后，删除
`app/src/main/java/com/local/mediaviewer/ui/components/AppErrorPanel.kt` 与
`app/src/main/java/com/local/mediaviewer/ui/components/MediaRouteShell.kt`
（两文件均在上述目录，无其它路径变体）。删除后 `assembleDebug` 与
`compileDebugAndroidTestKotlin` PASS，无任何残留引用。

## 未运行项（NOT RUN）

- 截图 / 真机人工视觉复核：NOT RUN——本记录全部证据来自 JVM 单测与 emulator-5554
  connected 测试的语义/边界断言，未做截图归档与真机人工检查。
- 360×800、字体 1.3、视频横屏：NOT RUN——门禁类集合中无代表用例，见覆盖矩阵。
- 全量 `:app:testDebugUnitTest` 与其余 connected 类：NOT RUN——不在本计划 Task 8
  门禁范围。
