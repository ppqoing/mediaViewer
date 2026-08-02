# 暖纸界面与 Image2 图标系统基础验收记录

- 验收日期：2026-08-02
- 初始整合基线：`3bb621edea8a2788940ec88f3e96d8a286585ef3`
- 最终基础审查修复后 HEAD：`f2bf1a4d4447c1a4d928831c5c6d73761d16e395`
- 验收范围：Task 8 静态门禁、Android 编译与 JVM 定向测试、设备可用性判断、一次基础静态视觉检查
- 验收边界：只做基础功能性验收，不做多轮审美审查，不访问真实服务器，不创建或启动模拟器，不构建 APK

## 结论

静态图标门、业务源码 Material Icons 门、Android 编译和 JVM 定向套件最终通过。图标处理器最初不支持计划规定的 `--verify-only` 参数，首轮退出码为 `2`；完成局部修复并提交 `c0ea2354d49885deee6f9b454829617eec761169` 后，只重验该失败目标，最终退出码为 `0`，共验证 61 个图标且没有改写现有资源或报告。

`adb devices -l` 成功执行但设备列表为空。因此，13 个定向 Compose 测试类、全部基础人工功能检查以及真机视觉与适配检查均为 `NOT RUN`。这些项目没有被编译或 JVM 测试结果替代，也没有记为 `PASS`。

APK 状态：**NOT BUILT**。本任务没有执行 assemble、bundle 或发布打包命令。

## 最终基础审查后修复

- Finding：漫画模式仍显示只属于单图模式的“上一张/下一张”控件；这些控件在漫画模式下失效，不应暴露给用户。
- 最小修复：只修改 `ImageReaderToolbar.kt` 与 `ImageReaderScreenTest.kt`，在漫画模式隐藏单图上一张/下一张控件；提交为 `f2bf1a4d4447c1a4d928831c5c6d73761d16e395`（`fix: hide single image navigation in comic mode`）。
- 编译门：`:app:compileDebugKotlin` 退出码 0；`:app:compileDebugAndroidTestKotlin` 退出码 0。这里仅记录编译成功，不把 AndroidTest 编译等同于 connected 运行时通过。
- Connected runtime：`NOT RUN`，设备仍不可用；没有补跑真机或模拟器测试。
- Scoped review：`CLEAN`，独立限定范围复核未发现问题。
- 本次文档增量没有重跑既有测试；原有 JVM 17/17、图标 61 个、APK `NOT BUILT` 与所有真机 `NOT RUN` 结论保持不变。

## 自动化与静态门禁

| 门禁 | 实际命令 | 退出码 | 结果 | 证据 |
|---|---|---:|---|---|
| 图标 Python 既有测试 | `python -m unittest discover -s tools/icon_pipeline/tests -p 'test_*.py' -v` | 0 | PASS | 3 个测试运行，3 通过、0 失败、0 错误、0 跳过 |
| 图标只读验证（首轮） | `python tools/icon_pipeline/process_icon_sheet.py --manifest tools/icon_pipeline/icon_manifest.json --source artifacts/warm-paper-icons/transparent --output app/src/main/res/drawable-nodpi --report artifacts/warm-paper-icons/processed --verify-only` | 2 | FAIL，已修复 | `process_icon_sheet.py` 最初不识别 `--verify-only`；没有进入资源处理 |
| `--verify-only` 新增回归测试 RED | `python -m unittest discover -s tools/icon_pipeline/tests -p 'test_process_icon_sheet.py' -k verify_only -v` | 1 | 预期 RED | 1 个测试运行，因 CLI 参数缺失而失败 |
| `--verify-only` 新增回归测试 GREEN | 同上 | 0 | PASS | 1 个测试运行，1 通过；覆盖只读成功、报告不一致拒绝和非整除母版尺寸 |
| 图标只读验证（失败项最终重验） | 与首轮图标只读验证相同 | 0 | PASS | `Verified 61 icons`；清单、透明输入、输出 PNG、质量报告和联系表契约通过；监测 63 个输出/报告文件，0 个发生长度或时间戳变化 |
| Material Icons 业务源码门 | `rg -n 'androidx\.compose\.material\.icons|Icons\.(Default|Filled|Outlined|AutoMirrored)' app/src/main/java` | 1 | PASS | `rg` 无匹配时退出码为 1；业务源码无命中 |
| Android 编译与 JVM 定向套件（沙箱内首次尝试） | 见下方 Gradle 命令 | 1 | 环境失败，未进入测试 | Gradle Wrapper 下载 `gradle-9.5.0-bin.zip` 时因沙箱网络权限报 `Permission denied: getsockopt` |
| Android 编译与 JVM 定向套件（同命令获准重试） | 见下方 Gradle 命令 | 0 | PASS | `BUILD SUCCESSFUL in 4s`；32 个任务中 3 个执行、29 个 up-to-date；编译目标通过，7 个测试类共 17 个测试全部通过 |
| 设备枚举 | `adb devices -l` | 0 | PASS（仅设备探测） | 输出只有 `List of devices attached`，没有设备序列号 |

Gradle 整合门在同一 PowerShell 进程中显式设置：

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT='C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:compileDebugKotlin testDebugUnitTest '-Pkotlin.incremental=false' --tests '*ThemeTokensTest' --tests '*MediaIconsTest' --tests '*PlayerIconsTest' --tests '*BrowserFormattersTest' --tests '*ZoomStateTest' --tests '*SingleImageDecodePolicyTest' --tests '*ComicReaderPolicyTest'
```

JVM 测试 XML 汇总如下：

| 测试类 | 测试数 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `ThemeTokensTest` | 3 | 0 | 0 | 0 |
| `MediaIconsTest` | 1 | 0 | 0 | 0 |
| `PlayerIconsTest` | 3 | 0 | 0 | 0 |
| `BrowserFormattersTest` | 3 | 0 | 0 | 0 |
| `ZoomStateTest` | 1 | 0 | 0 | 0 |
| `SingleImageDecodePolicyTest` | 3 | 0 | 0 | 0 |
| `ComicReaderPolicyTest` | 3 | 0 | 0 | 0 |
| **合计** | **17** | **0** | **0** | **0** |

## 图标联系表与一次基础静态视觉检查

- 联系表仓库路径：`artifacts/warm-paper-icons/processed/contact-sheet.png`
- 联系表绝对路径：`D:\code\mediaviewer\.worktrees\android-mediaviewer\artifacts\warm-paper-icons\processed\contact-sheet.png`
- 图标静态结果：`PASS`。一次性查看联系表，可见 61 个具名图标；自动验证同时确认每个输出为 `192 × 192 px` 透明 PNG、可见最长边占画布 70%–74%、四角 alpha 为 0、中心偏移不超过 4 px。
- 暖米白画布与纸张层级：静态 `PASS`。主题定义包含 `#F5EAD3` 背景、`#FFF7E8` 表面；`WarmPaperCard` 与固定低 alpha 的 `drawWithCache` 纸张表面实现存在。
- 陶土主色与鼠尾草媒体色：静态 `PASS`。主题定义包含 `#C96B2C` 主色和 `#77835F` 次要/文件夹/图片色。
- 暖棕半透明播放器：静态 `PASS`。播放器色值包含 `#2E2118` 暖棕基色以及 `0xA82E2118`、`0xB82E2118` 半透明渐变端点。
- 刘海/挖孔安全区：静态覆盖 `PASS`，真机动态结果 `NOT RUN`。共享 Scaffold、视频控制层和图片阅读控件使用 `WindowInsets.safeDrawing`，并存在注入 cutout inset 的 Compose 测试；无设备，未执行这些测试。
- 320dp + 2× 字体：静态覆盖 `PASS`，真机动态结果 `NOT RUN`。首页/设置、播放器、队列和图片阅读存在 320dp、`fontScale = 2f` 的 Compose 测试；无设备，未执行。
- 横屏可达性：静态覆盖 `PASS`，真机动态结果 `NOT RUN`。视频控制层存在紧凑横屏和常见手机横屏布局测试；无设备，未执行。

以上视觉检查只执行一次，没有进入风格探索或多轮审美修改。静态覆盖仅说明实现与测试入口存在，不证明真实设备上的显示效果。

## Connected Compose 套件

状态：**NOT RUN**。

原因：`adb devices -l` 退出码为 0，但没有任何已连接设备；未创建或启动模拟器，也无法判断 API 36 设备上的动态结果。以下 13 个计划目标类均未运行：

1. `MediaComponentsTest`
2. `MediaScaffoldTest`
3. `HomeSettingsScreenTest`
4. `MediaViewerNavigationTest`
5. `BrowserScreenTest`
6. `PlayerScreenTest`
7. `VideoControlsOverlayTest`
8. `PlaybackControlsTest`
9. `PlaybackQueueUiTest`
10. `VideoGestureLayerTest`
11. `ImageReaderScreenTest`
12. `GifImageLoaderInstrumentedTest`
13. `ComicReaderDynamicLoadingTest`

计划中的 `connectedDebugAndroidTest` 命令没有执行；因此设备测试数量为 0，通过 0、失败 0、错误 0、跳过 0，状态仍是 `NOT RUN` 而不是 `PASS`。

## 基础人工功能检查

因无可用设备且不访问真实服务器，以下项目均未执行：

| 检查项 | 状态 | 说明 |
|---|---|---|
| 首页进入共享 | NOT RUN | 无设备、未连接真实服务器 |
| 普通目录 | NOT RUN | 无设备、未连接真实服务器 |
| 子目录 | NOT RUN | 无设备、未连接真实服务器 |
| 空目录 | NOT RUN | 无设备、未连接真实服务器 |
| 视频单击显隐 | NOT RUN | 无设备、无真实媒体 |
| 视频双击播放或暂停 | NOT RUN | 无设备、无真实媒体 |
| 视频暂停与恢复 | NOT RUN | 无设备、无真实媒体 |
| 视频进度定位 | NOT RUN | 无设备、无真实媒体 |
| 视频全屏进入与退出 | NOT RUN | 无设备、无真实媒体 |
| 视频前后台切换 | NOT RUN | 无设备、无真实媒体 |
| 音频播放 | NOT RUN | 无设备、无真实媒体 |
| 队列加入、排序与删除 | NOT RUN | 无设备、无真实媒体 |
| 静态图显示 | NOT RUN | 无设备、无真实媒体 |
| GIF 动画 | NOT RUN | 无设备、无真实媒体 |
| 漫画连续阅读 | NOT RUN | 无设备、无真实媒体 |
| 单图左右翻页 | NOT RUN | 无设备、无真实媒体 |
| 缩放不重载 | NOT RUN | 无设备、无真实媒体 |
| 自动隐藏设置重启后持久化 | NOT RUN | 无设备，未执行重启验证 |

## 未执行项与构建状态

- API 36 设备定向 Compose 套件：`NOT RUN`，无设备。
- 全部人工真机功能检查：`NOT RUN`，无设备；依赖共享或真实媒体的项目同时没有访问真实服务器。
- 刘海/挖孔、320dp + 2× 字体和横屏的真机动态视觉检查：`NOT RUN`，只有静态实现与测试覆盖证据。
- 真实服务器访问：`NOT RUN`，本任务没有发起任何真实服务器请求。
- APK：`NOT BUILT`，没有执行 APK 构建或发布命令。

## 变更与重验纪律

首轮 `--verify-only` 失败后，只修改了图标处理工具及其测试，并只重跑新增回归测试和该失败门。已通过的 3 个既有 Python 测试、Material Icons `rg` 门和后续通过的 JVM 目标均未被重复运行。局部工具修复提交为 `c0ea2354d49885deee6f9b454829617eec761169`。
