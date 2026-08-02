# 暂停恢复后精确播放进度验证记录

- 日期：2026-08-02
- 根因分析：`docs/analysis/2026-08-02-player-resume-progress-root-cause.md`
- 设计规格：`docs/superpowers/specs/2026-08-02-player-resume-exact-progress-design.md`（`a30c8f8`）
- 实施计划：`docs/superpowers/plans/2026-08-02-player-resume-exact-progress.md`（`b306e4c`）
- 本阶段源提交：`ac8a604`、`070ba0e`、`3986459`、`44c31fd`、`b886b0b`、`842f2fe`

## 1. 结论与边界

- UI 的共享视频/音频位置路径改为读取 LibVLC 引擎状态形成的权威快照，经 MediaSession 自定义命令送到应用侧控制器；不再读取 `MediaController.currentPosition`。
- 自定义命令只同步读取当前会话状态并返回 `Bundle`，不改变播放、队列或持久化状态。
- x86_64 API 36 模拟器上的真实 `PlaybackCoordinator -> MediaSession -> MediaController -> Media3PlaybackController` 冻结位置回归为 **PASS（1/1）**。
- arm64 真机上的 1.0x/2.0x 真实视频人工场景为 **NOT RUN**；没有已知 arm64 真机，不使用 x86_64 模拟器替代。
- VLC4 EAP 的 `TimeChanged` 报告时间是否与实际显示帧存在独立偏移仍未在真机证实。本阶段只消除了已证实的 MediaController 客户端外推，不把 VLC4/vout 风险写成已修复。

## 2. TDD RED / GREEN 证据

### 2.1 权威快照与 Bundle 编解码

- RED：`testDebugUnitTest --tests '*PlaybackPositionSnapshotCodecTest'` 在生产契约尚不存在时因 `PlaybackPositionSnapshot`、`toPlaybackPositionSnapshot` 和 `PlaybackPositionSnapshotCodec` 均为 unresolved reference 而编译失败。
- GREEN：同一命令 **PASS（3 tests，13 秒）**。覆盖真实会话状态转快照、Bundle 往返及损坏输入边界。
- 提交：`ac8a604 feat(android): define exact playback position snapshots`。

### 2.2 MediaSession 只读自定义命令

- RED：`testDebugUnitTest --tests '*PlaybackSessionCallbackTest'` 的新增测试在“可用命令包含精确位置动作”断言处失败；会话唯一 ID 错误是断言提前退出、fixture 未释放后的级联结果。
- GREEN：同一命令 **PASS（7 tests，8 秒）**。无媒体返回 invalid state；有媒体返回 `video-a / 12500 / 60000` 快照。
- 下游 Lint RED：`lintDebug` **FAIL（1 error，36 warnings）**，`PlaybackSessionCallback.kt:108 [WrongConstant]`；`SessionResult(...)` 错误构造参数误用了 `SessionResult.RESULT_ERROR_INVALID_STATE`。
- 最小修复后定向 GREEN：`testDebugUnitTest --tests '*PlaybackSessionCallbackTest'` **PASS（7 tests，7 秒）**；`lintDebug` **PASS（8 秒，0 error，36 warnings）**。
- 提交：`070ba0e feat(android): expose exact playback position command`；契约修复 `842f2fe fix(android): use session error for exact position`。

### 2.3 带媒体身份的客户端位置缓存

- 首轮 RED：`testDebugUnitTest --tests '*ExactPlaybackPositionStoreTest'` 因生产类不存在而出现 4 处 unresolved reference。
- 首轮 GREEN：同一命令 **PASS（3 tests，3 秒）**。
- 复审 RED：扩展到 5 tests 后有 2 项失败，分别证明媒体切换后和当前媒体为空后拒绝候选时仍遗留旧快照。
- 最终 GREEN：同一命令 **PASS（5 tests，2 秒）**。覆盖错媒体拒绝、媒体切换/清空、向后 seek 以较小位置覆盖及显式清空。
- 提交：`3986459 feat(android): track exact playback positions by media`；复审修复 `44c31fd fix(android): clear stale exact playback positions`。

### 2.4 真实 MediaController 链路冻结位置回归

- 设备：`emulator-5554`，`Pixel_3a_API_36_extension_level_17_x86_64`，API 36，ABI `x86_64`。
- 命令：`connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaSessionControlsTest#appProgressUsesFrozenEnginePositionAfterPauseResumeAndAtDoubleSpeed'`。
- RED：**FAIL（1 test，1 failure，24 秒）**，冻结引擎位置预期 `8000`，应用侧实际 `8660`；证明旧路径仍按墙钟外推。
- GREEN：同一真实 MediaSession/MediaController 测试 **PASS（1/1，17 秒）**；1.0x、2.0x、暂停后恢复三个冻结阶段均保持引擎位置。
- 静态唯一时钟门禁：`rg -n "currentPosition" app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt` 无匹配，PASS。
- 提交：`b886b0b fix(android): use exact engine positions in player UI`。

## 3. 基础自动门禁

| 门禁 | 结果 | 证据 |
|---|---|---|
| 完整 JVM / Robolectric | PASS | `testDebugUnitTest --no-daemon`，17 秒；Release 最终源提交上的 JUnit XML 为 75 suites / 345 tests / 0 failures / 0 errors / 0 skipped |
| AndroidTest Kotlin 编译 | PASS | `compileDebugAndroidTestKotlin` 在合并命令中完成 |
| Debug APK | PASS | `assembleDebug` 在合并命令中完成 |
| Debug Lint 初次 | FAIL | 1 error / 36 warnings，唯一错误为上节 WrongConstant |
| Debug Lint 修复后 | PASS | 只重跑 `lintDebug --no-daemon`，8 秒，0 error / 36 warnings |

首次在未设置 Android SDK 环境时，Gradle 在测试前因 `SDK location not found` 退出；设置任务指定的 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 后重跑同一门禁通过。该环境失败不计作测试失败。Lint 修复后没有重复已经通过的 JVM、AndroidTest 编译或 Debug assemble；个人 Release 脚本仍会在最终源提交上重新执行完整 JVM 与 Release 门禁。

## 4. 真机人工场景

arm64 真机真实视频人工检查：**NOT RUN**。

未运行项包括同一真实视频在 1.0x 和 2.0x 下分别播放至少 10 秒、暂停等待 5 秒、恢复观察时间/滑块/声音/画面、向前和向后 seek、再次暂停恢复。因没有已知 arm64 真机，本记录不声称暂停期间 UI 冻结、恢复后画面同步、2.0x 无二次乘速或真实媒体向后 seek 已完成人工验收。

## 5. Release 证据

- 构建源提交：`81fbc4d7be42c2c0defeb252d5a59ee15d630144`（基础验证文档提交）。
- 命令：`scripts/Build-PersonalRelease.ps1 -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'`。
- 脚本门禁：`clean`、`testDebugUnitTest`、`lintRelease`、`assembleRelease`、LibVLC/Dex 压缩、体积、16 KiB ZIP 对齐、签名、包信息、ABI 和二次 SHA-256 均通过；Gradle `BUILD SUCCESSFUL in 1m 16s`，85 个任务中 84 个执行、1 个最新。
- APK：`D:\code\mediaviewer\.worktrees\android-mediaviewer\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk`
- 大小：43,792,510 字节（41.76 MiB，小于 70 MiB 上限）。
- SHA-256：`6a7f4dd2b6aa4b82e6560b5bffd64ca5b1608fa9402382321691b2d066757c8d`；独立 `Get-FileHash` 与 `.sha256` 文件一致。
- 独立 `aapt dump badging`：包名 `com.local.mediaviewer`，版本 `1.1.0 (3)`，`minSdk 29`，`targetSdk 36`，Native ABI 仅 `arm64-v8a`。
- 独立 `zipalign -c -P 16 -v 4`：exit 0，`Verification successful`。
- 独立 `apksigner verify --verbose --print-certs`：exit 0；仅 APK Signature Scheme v3 为 `true`，1 个签名者；证书 SHA-256 为 `b432a64032601b66f275d0c4b3308d95cbb40b58be9269c1494783e82fa5415d`。
- 证书 DN 为 `C=US, O=Android, CN=Android Debug`，只适合个人安装与测试，不是应用商店正式发布证书。
- arm64 真机安装、冷启动及真实视频 1.0x/2.0x 人工场景：**NOT RUN**。

构建前为 26 个既有文件记录长度与 SHA-256。可见未跟踪内容与被忽略的 `dist` 内容分别以 stash `69397253fefc8b66e6f7dbb2873dcef45e98e2a9` 和 `f4be5826d29dab6a29325286485eba89f3d1f82a` 临时保护；原 `dist` 同时移动到 worktree 外的独立同盘临时目录。构建后先移走新 APK、SHA 和脚本生成的冲突验证文件，再恢复原内容；根因文档因系统 `core.autocrlf=true` 需以单次命令级 `core.autocrlf=false` 从已验证 stash blob 原样恢复。最终除明确覆盖的 APK/SHA 外，24/24 个受保护文件的长度和 SHA-256 与构建前一致；原 `docs/verification/2026-07-30-arm64-compressed-release.md` 未被脚本生成副本覆盖。两份任务 stash 均已删除，0 个任务 stash、0 个遗留 Git 进程。

自动测试、x86_64 设备回归、arm64 真机人工检查和 Release 产物在本记录中保持分开报告。

## 6. 后续用户复验

本记录所述旧实现仍在“手动暂停后续播”场景存在失败风险：暂停后恢复播放时，旧路径会额外刷新视频输出，可能造成画面卡住、声音继续或进度异常。此历史记录不应被解读为该场景已经通过 ARM64 真机验收。

该问题的后续代码变更、定向 RED/GREEN 证据、Release 静态门禁及本轮真机状态，见 [VLC 续播时钟与前后台生命周期复验记录](2026-08-02-vlc-resume-clock-background-lifecycle.md)。截至该记录日期，自动与静态门禁不能替代真机人工结论；只有其中所列 ARM64 场景全部 PASS 后，才能宣称用户原始问题已解决。
