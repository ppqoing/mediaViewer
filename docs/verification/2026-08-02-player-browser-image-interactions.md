# 播放器、目录与图片交互改造验收记录

- 执行日期：2026-08-02
- 分支：`feature/android-mediaviewer`
- 功能代码基线：`e06d957`
- 定向测试修复：`c95a1aa`
- 审查范围：按确认要求仅做基础功能性审查，不做扩展审查

## 自动门禁

| 项目 | 结果 | 证据与边界 |
| --- | --- | --- |
| JVM 测试、Debug/Release 构建 | **PASS** | `testDebugUnitTest assembleDebug assembleRelease`，`BUILD SUCCESSFUL` |
| Lint 与 AndroidTest 编译 | **PASS** | `lintDebug compileDebugAndroidTestKotlin`，`BUILD SUCCESSFUL` |
| 仓库 Android 验证脚本 | **PASS** | JVM、Lint、Debug/Release、AndroidTest 编译、Manifest、Media3 与 APK ABI 门禁全部通过；脚本本身未启用设备和真实服务器测试 |
| 安全区定向自动测试 | **PASS（113/113）** | 外层命令等待超时，但最终 JUnit XML 为 `tests=113, failures=0, errors=0` |
| 首轮设备全量测试 | **FAIL（192/195 通过）** | 发现 3 个测试基础设施问题：设置项未滚动到可见区域、Room schema 未打包为 AndroidTest asset、`PlaybackServiceTest` 使用了不适用于 `MediaSessionService` 的无 action 启动方式 |
| 原 3 处失败的定向复测 | **PASS（5/5）** | `MediaEnhancementsEndToEndTest`、`MediaViewerDatabaseMigrationTest`、`PlaybackServiceTest` 全部通过 |
| 设备离线时未完成用例的定向复测 | **PASS（1/1）** | `AppActivityRecreationTest#defaultVideoSessionClearsQueueWhenActivityReallyStops`，API 36 x86_64 AVD，`BUILD SUCCESSFUL in 57s` |
| 第二次设备全量测试 | **NOT COMPLETED** | 模拟器在首项执行时离线，0/195 被测试平台接收；按用户要求不重跑已通过测试，只定向补验上述未完成项 |

因此，本轮发现的 3 个失败点和 1 个因设备离线未完成的测试均已定向复测通过；本记录不把未重新完整执行的 195 项全量套件标记为 PASS。

## 需求覆盖

| 编号 | 验收点 | 自动化/代码证据 |
| --- | --- | --- |
| 1 | 非全屏单击视频切换上下功能区；半透明背景；按配置自动隐藏 | 单击手势、控制层状态与 3/5/10/15 秒/不隐藏设置测试通过 |
| 2 | 视频默认不后台播放；暂停或播放时退出均停止并清空列表 | 会话后台开关默认关闭，Activity 真正停止时清理队列的定向设备测试通过；音频后台播放行为保留 |
| 3 | 双击视频切换暂停/播放 | 手势仲裁与播放命令测试通过 |
| 4 | 全应用适配刘海屏、挖孔屏 | 根 Scaffold 只消费一次 inset；视频、图片 edge-to-edge，控件避让安全区；注入式安全区测试通过 |
| 5 | 目录只有子目录时仍能加载 | 目录解析允许 folder-only JSON；目标 URL 实际返回的两个子目录可被识别 |
| 6 | 空目录中央显示“路径下无文件” | 空目录状态测试通过 |
| 7 | 单图查看左右划切换图片 | 单图 pager、首尾边界、放大时禁用翻页测试通过 |
| 8 | 条漫双指缩放不重新加载已显示图片 | 图片请求 key 与显示缩放解耦；连续缩放时服务器请求计数保持 1 的回归测试通过 |
| 补充 | 普通/全屏菜单布局、竖屏画面比例、控制层自动隐藏 | 普通模式的更多菜单包含后台播放、速度、模式和比例；全屏时后三项位于进度条下方，布局与状态测试通过 |
| 补充 | 暂停后再播放画面随声音恢复 | 视频输出恢复路径增加重新 attach/恢复播放处理，相应播放引擎与视频输出测试通过 |

## 真实目录探测

- `http://127.0.0.1:8081/.rangeshelf/shares`：HTTP 200，能够读取包含 `MiddleDir` 的共享配置。
- `http://127.0.0.1:8081/MiddleDir/11111111/Ayame/`：HTTP 200、`application/json`，返回两个子目录项，证明该“只有子目录”的响应格式有效。
- 本轮只做只读 HTTP 探测，未在安装后的应用界面中逐层点击该真实目录，因此真实服务器 UI 导航记为 **NOT RUN**。

## 尚未执行的人工验收

- 刘海屏/挖孔屏真机上逐页观察控件是否被实体异形区域遮挡：**NOT RUN**。
- 真实视频连续多次暂停/播放，肉眼确认画面和声音都持续推进且无冻结帧：**NOT RUN**。
- 普通模式与全屏模式逐项打开菜单并肉眼核对布局、半透明度和自动隐藏计时：**NOT RUN**。
- 真实目录 `Ayame/` 在安装后的应用内进入、再进入其子目录：**NOT RUN**。
- arm64 真机安装与冷启动：**NOT RUN**。

## 结论

本次代码、JVM/构建/Lint 门禁以及本轮失败或未完成测试的定向复测均已通过。全量 195 项设备测试没有在最终代码上完整重跑，原因是第二次执行时模拟器离线，之后遵照用户要求只重验未通过或未完成项目；上述人工设备与真实 UI 项目仍需在对应设备环境中验收。
