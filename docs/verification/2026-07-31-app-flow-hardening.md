# App Flow Hardening Verification

日期：2026-07-31（UTC）/ 2026-08-01（本机）

基准 Commit（`git rev-parse HEAD` 执行时输出）：`ad34e09c159bc5e18e393e85428a073f203060e6`

验证对象：上述基准工作树 + Flow 计划 Task 8 的测试改动（`AppActivityRecreationTest.kt` 新建、`MediaViewerNavigationTest.kt`/`MediaSessionControlsTest.kt` 强化）。本提交除测试与本文档外无生产代码改动。

执行设备：Pixel_3a_API_36_extension_level_17_x86_64（emulator-5554，API 36，x86_64）。执行期间该 AVD 崩溃一次，按纪律以同一 AVD 重启后继续，受影响单项全部重跑；另将系统设置 `show_ime_with_hard_keyboard` 置 1 以排除软键盘抑制变量（对下表 IME 行无影响，已复位留档）。所有 Gradle 命令串行、单执行者，统一 `'-Pkotlin.incremental=false' --no-daemon`，`ANDROID_HOME` 已设置；connected 一类一命令。

## 门禁结果

| 门禁 | 命令 | 结果 |
|---|---|---|
| JVM 单测 | `.\gradlew.bat :app:testDebugUnitTest '-Pkotlin.incremental=false' --no-daemon` | PASS 316/316（0 failures / 0 errors / 0 skipped，72 个结果文件） |
| Lint | `.\gradlew.bat :app:lintDebug '-Pkotlin.incremental=false' --no-daemon` | PASS（BUILD SUCCESSFUL；36 条 findings 全部 note 级，0 warning/error/fatal） |
| AndroidTest 编译 | `.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false' --no-daemon` | PASS（BUILD SUCCESSFUL） |
| API 36 runtime | 见下表（一类一命令串行） | 88/89；1 项环境性 FAIL（SET-IME 行） |

## API 36 connected runtime（一类一命令）

| 套件 | 用例数 | 结果 |
|---|---|---|
| `AppActivityRecreationTest` | 2 | PASS 2/2 |
| `MediaViewerNavigationTest` | 13 | PASS 13/13 |
| `BrowserScreenTest` | 9 | PASS 9/9 |
| `HomeSettingsScreenTest` | 15 | 14 PASS / 1 FAIL：`settingsKeepsSaveAboveVisibleImeAfterUrlFocus`（详见 SET-IME 行） |
| `ImageReaderScreenTest` | 16 | PASS 16/16 |
| `PlaybackQueueUiTest` | 27 | PASS 27/27 |
| `MediaSessionControlsTest` | 6 | PASS 6/6 |
| `BackgroundPlaybackTest` | 1 | PASS 1/1 |

`HomeSettingsScreenTest` 的失败用例与本任务改动零交集（该文件与全部生产代码在本提交中未变，进程独立），判定为预存的环境性失败，如实记录不遮掩。

## 场景矩阵（BOOT/NAV/SET/NET/IMG/PLAY/QUEUE/BG/FOCUS）

| 编号 | 场景 | 结果 |
|---|---|---|
| BOOT-02 | 应用范围只连接一次，导航不再重复连接 | PASS（`app_scope_connects_once_and_navigation_does_not_connect_again`，API 36） |
| BOOT-03 | 全局重连时 Browser 内容与面包屑保留 | PASS（`browser_remains_visible_during_global_reconnect`、`browser_deep_reconnect_retains_current_breadcrumbs`，API 36） |
| BOOT-04 | Player failed 提供显式重连 | PASS（`failed_player_has_reconnect_and_back_without_an_infinite_spinner` 重连腿，`reconnectCalls == 1`） |
| BOOT-05 | Player failed/Connecting 可安全返回 | PASS（同上用例返回腿；`player_connecting_back_does_not_reopen_after_timeout` 返回腿） |
| BOOT-06 | Player Connecting 返回后越过 5s timeout 不再导航 | PASS（`player_connecting_back_does_not_reopen_after_timeout`，冻结时钟构造 Connecting 窗口） |
| NAV-01 | Browser → Player 返回同一目录与面包屑 | PASS（`browser_player_back_returns_to_the_same_directory`） |
| NAV-04 | 通知来源 Player 返回 Home 且只导航一次 | PASS（`notification_request_from_browser_returns_home_and_empty_queue_exits_once` 前半；`MediaSessionControlsTest.notificationRequestOpensCurrentPlayerOnceAcrossRepeatedIntents` 真实 intent 两次投递 + 一次返回即 Home） |
| NAV-05 | 空队列自动退出一次，越过 timeout 不重入 | PASS（`notification_request_from_browser_returns_home_and_empty_queue_exits_once` 后半） |
| SET-01 | 脏设置返回弹放弃确认 | PASS（`dirty_settings_back_uses_discard_confirmation`） |
| SET-02 | 设置页保存/输入/错误展示套件 | PASS（`HomeSettingsScreenTest` 15 例中 14 例） |
| SET-IME | IME 可见时保存按钮不被遮挡 | FAIL（预存环境性失败：模拟器 `mIsInputViewShown=true` 但 `WindowInsets.ime` bottom 恒 0，`show_ime_with_hard_keyboard=1` 后仍复现；与本提交代码无关，交设置/地基负责人在标准 AVD 配置复核） |
| NET-01 | 初始服务器离线：有限错误 + 重试 + 设置入口 | NOT RUN：真机/真实服务器场景（发布计划范围），本任务无真实服务器 |
| NET-02 | 端点失效后刷新恢复播放 | NOT RUN：需要可失效的真实端点（真机/发布计划） |
| IMG-01 | 图片阅读器组件行为套件 | PASS（`ImageReaderScreenTest` 16/16，API 36） |
| IMG-02 | 图片自动重试预算耗尽后仅人工重连失败项 | NOT RUN：真机人工观察行（计划 Step 7） |
| PLAY-01 | 暂停后播放：音画推进 | PASS（模拟器证据：`systemCommandsStayInSyncWithAppControllerAndNotification` 暂停/播放同步；`pausedSeekStaysPausedAndExplicitPlayResumesAtTarget` 播放腿位置推进） |
| PLAY-02 | 暂停 → 拖动 → 松手仍暂停且显示目标帧 | 部分 PASS：暂停保持与目标位置 API 36 自动 PASS（同上用例）；“目标帧画面”NOT RUN：harness 无 presented-frame 时间戳/像素回调，按计划不以 position 冒充画面，留 ARM64 人工 |
| PLAY-03 | 拖动后播放从目标同步恢复 | PASS（`pausedSeekStaysPausedAndExplicitPlayResumesAtTarget` 恢复腿：显式 play 后从目标推进且不早于目标） |
| PLAY-04 | 反复 seek/进度容差 | NOT RUN：发布计划真机矩阵项，本任务未执行专项 |
| PLAY-05 | 短/长缓冲状态展示 | NOT RUN：需可控慢网（真机/发布计划） |
| PLAY-06 | 原仅音频媒体出画面或明确不支持错误 | NOT RUN：真实问题媒体属 ARM64/真实服务器发布计划 |
| PLAY-07 | 后台 15 秒回前台：声音继续、画面回到当前位置 | 部分 PASS：后台 2 秒无 Surface 推进 ≥500ms 且回前台重新 Attached、position 不倒退（`BackgroundPlaybackTest`）；15 秒与画面帧 NOT RUN：真机人工行 |
| PLAY-08 | 端点刷新后 mediaKey/queue/position 保留 | NOT RUN：需真实失效端点（真机/发布计划） |
| QUEUE-01 | 冷控制器恢复队列快照：current mediaKey、顺序、mode、speed、position、暂停意图 | PASS（`stopReleasesOnceAndColdControllerRestoresQueuePaused`：2 项顺序、REPEAT_ONE、1.25x、持久化位置 12000、playWhenReady=false、显式 play 后从持久位置恢复） |
| QUEUE-02 | 持久化失败 notice 到达一次，重试不改变 playWhenReady/currentItem | PASS（`persistenceNoticeReachesControllerOnceAndRetryKeepsPlaybackState`） |
| QUEUE-03 | controller 断开期间旧 notice 不在重连后 replay | PASS（`stalePersistenceNoticeIsNotReplayedAfterColdReconnect`） |
| QUEUE-04 | 队列 UI：排序、删除、撤销、多入口同一 Sheet | PASS（`PlaybackQueueUiTest` 27/27） |
| QUEUE-05 | 持久化失败 Snackbar 非阻塞且重试可用（真机） | NOT RUN：真机人工行（计划 Step 7） |
| BG-01 | 退后台无 Surface 播放继续（2 秒推进 ≥500ms 且 isPlaying） | PASS（`BackgroundPlaybackTest.videoKeepsPlayingWithoutSurfaceAndReattachesContinuously`） |
| BG-02 | 回前台 Surface 重新 Attached 且 position 不倒退 | PASS（同上用例） |
| BG-03 | 锁屏/解锁 | NOT RUN：需真机系统交互（发布计划） |
| BG-04 | 播放中/暂停划掉任务 | NOT RUN：需真机系统交互（发布计划） |
| BG-05 | 进程/服务恢复 | NOT RUN：系统杀进程人工项；`ActivityScenario.recreate()` 仅覆盖 Activity 重建（见 PLAY-RECREATE 行），按计划不冒充进程死亡 |
| PLAY-RECREATE | Activity 重建恢复 Player 路由与 service-owned item、不重放旧 notice | PASS（`AppActivityRecreationTest` 2/2：position 12_345 不变、路由保留；旧 persistence notice 重建后不出现） |
| FOCUS-01 | 短暂音频焦点丢失后按条件恢复 | NOT RUN：需可注入真实焦点事件的设备场景（发布计划） |
| FOCUS-02 | 永久丢失/耳机或蓝牙断开：暂停且不自动恢复 | NOT RUN：需耳机/蓝牙硬件 |
| FOCUS-03 | 系统/通知命令与通知元数据 | PASS（`systemCommandsStayInSyncWithAppControllerAndNotification`：play/pause/next/previous/seek 与 MediaStyle 通知标题、session token） |

## ARM64 manual

NOT RUN：本任务无 ARM64 设备；真机人工矩阵（后台画面停帧、暂停 seek 目标帧、锁屏/任务划除、通知冷启动按钮、ARM64 LibVLC 解码与 `/tmp/wallpa/` 问题媒体）属发布计划（`2026-07-31-media-ui-flow-verification-release.md`），不以模拟器/JVM 结果冒充。

## Real server

NOT RUN：本任务无用户真实服务器；connected 套件使用仓库内本地 fixture server（`MediaFixtureServer`）与 fake harness，真实服务器验收属发布计划。

## 预存失败定性记录（`MediaSessionControlsTest.stopReleasesOnceAndColdControllerRestoresQueuePaused`）

系统化诊断后定性为**两层测试缺陷，无生产缺陷**（`PlaybackService.kt` 曾临时改动验证后已还原，最终生产零改动）：

1. **位置门槛不可达（复现失败点 `MediaSessionControlsTest.kt:114`）**：无视频输出时模拟器 VLC 持续报 vout 失败并提前发出 `EndReached`，`REPEAT_ONE` 随之从头重播（logcat 系统媒体镜像实证 position 12001→0 循环），position 无法在 20s 内维持 ≥11.5s。修复：按 `BackgroundPlaybackTest` 既有模式挂真实 `VlcSurface`（ActivityScenario + MainActivity），引擎位置稳定后原断言不变通过。
2. **冷连接 5s 超时**：服务端 `session.release()` 后，Media3 1.10.1 客户端 `MediaController.release()` 将 `unbindService` 延后至已释放会话应答或 `RELEASE_TIMEOUT_MS=30_000`（库源码实证），期间旧实例被绑定、`onGetSession` 拒绝（`Session rejected`，dumpsys 实证 `startRequested=false` 且存在持续应用侧绑定，`onDestroy` 探针确认销毁未发生）。修复：`connectControllerAfterRelease(timeoutMs = 45_000L)` 覆盖该窗口，冷连接约 30s 后成功。副作用说明：生产冷启动在停止后最长约 30s 内重连可能被拒，属 Media3 1.10.1 释放时序的已知上游行为，根导航已有显式重连路径；如需消除该窗口属 F2/F6 服务链设计变更，移交对应负责人与发布计划。

另记录一处测试基建禁忌：本类不得加类级 Compose rule——rule 的 `runTest` 环境下 `Thread.sleep` 真实等待会饿死 Activity 重组帧（实测 attach 20s 超时、`videoOutputState=Detached`）；需要 Compose 断言的用例（通知导航）改为在 UI 段内手动 `rule.apply(Statement, Description)` 包裹。
