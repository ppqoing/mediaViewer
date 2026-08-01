# MediaViewer 整体界面与流程加固验收记录

验收计划：`docs/superpowers/plans/2026-07-31-media-ui-flow-verification-release.md`（Tasks 1–8）
执行日期：2026-07-31 至 2026-08-01

## 1. Scope and tested commit

- 分支：`feature/android-mediaviewer`；工作树：`D:\code\mediaviewer\.worktrees\android-mediaviewer`
- 被测试最终代码 commit：`de618ea`（`test(android): align navigation contracts with review fixes`）
- 提交链（验收基线 `bb07b42` 之后）：`0e92f2e`（Release 脚本/测试合约对齐）、`e966bd1`（CQ-F1）、`d1006c4`（SC-I4）、`7977dda`（SC-I1）、`53569cf`（SC-I2）、`3e8ce02`（SC-I3）、`ab1027f`（SC-I5）、`1c97300`（SC-I6）、`de618ea`（复审契约对齐）
- 范围：三份实施计划（media-ui-foundation-pages、media-player-queue-ui、app-flow-hardening）的全部交付 + 本计划 Tasks 1–8 验收
- 设备：`emulator-5554`，AVD `Pixel_3a_API_36_extension_level_17_x86_64`，API=36，ABI=x86_64；无 ARM64 真机

## 2. Local JVM/Lint/build gate

在最终 commit `de618ea` 上重跑（不复用旧日志）：

- 聚焦 JVM 8 套件（AppSessionViewModel/ControllerConnectionMachine/CurrentPlayerNavigation/BrowserViewModel/SettingsViewModel/ImageReaderViewModel/PlaybackCoordinator/PlaybackPrimaryAction）：PASS，退出码 0。
- 仓库验证脚本 `scripts/Invoke-AndroidVerification.ps1`：退出码 0，输出 `本地自动门禁通过：JVM、Lint、Debug/Release、androidTest 编译、Manifest、Media3、APK ABI`。
  - 全量 `testDebugUnitTest`：**318 tests，0 failures / 0 errors / 0 skipped**（316 基线 + 2 条新增播放器入口断言）。
  - `lintDebug`、`assembleDebug`、`assembleRelease`、`compileDebugAndroidTestKotlin`：全部 BUILD SUCCESSFUL。
  - Manifest 前台媒体服务、Media3 1.10.1、Debug ABI（arm64-v8a+x86_64）、Release ABI（严格 arm64-v8a）静态断言：PASS。
- 脚本内三处 Gradle 调用与 Release 脚本均已带 `-Pkotlin.incremental=false --no-daemon`（`0e92f2e`）。
- 设备测试（本命令内）：NOT RUN（未带 `-RunDeviceTests`，符合预期）。
- 日志：`.superpowers/sdd/2026-07-31-ui-redesign/logs/task6-focused-jvm.log`、`task6-local-gate.log`。

## 3. API 36 connected class results

在 `de618ea` 上逐类（一类一命令）重跑，设备身份每阶段核验（emulator-5554 / API 36 / x86_64 / 唯一在线）：**18 类 162 tests，全部 PASS（退出码 0）**。

| 类 | 用例数 | 结果 | 类 | 用例数 | 结果 |
|---|---|---|---|---|---|
| AppLaunchTest | 1 | PASS | PlayerBootstrapContentTest | 1 | PASS |
| AppActivityRecreationTest | 2 | PASS | PlayerScreenTest | 14 | PASS |
| MediaComponentsTest | 3 | PASS | PlaybackControlsTest | 15 | PASS |
| MediaScaffoldTest | 1 | PASS | VideoControlsOverlayTest | 16 | PASS |
| MediaMaterialWrappersTest | 10 | PASS | VideoGestureLayerTest | 4 | PASS |
| MediaViewerNavigationTest | 14 | PASS | PlaybackQueueUiTest | 29 | PASS |
| HomeSettingsScreenTest | 15 | PASS | BackgroundPlaybackTest | 1 | PASS |
| BrowserScreenTest | 10 | PASS | MediaSessionControlsTest | 6 | PASS |
| ImageReaderScreenTest | 18 | PASS | LibVlcVideoOutputTest | 2 | PASS |

- 脚本设备门禁（`Invoke-AndroidVerification.ps1 -RunDeviceTests`，含 BackgroundPlayback/MediaSessionControls/LibVlcVideoOutput 三类）：PASS，输出 `API 36 后台播放定向设备测试通过`。
- 过程事件（不影响最终结论）：模拟器执行中冻结两次（队列用例挂起 500s——已定位为修复期中间态引发的无限重组并修复；`MediaMaterialWrappersTest`  teardown 期 `Can't find service: activity`——环境性），AVD 按纪律重启三次，受影响类全部重跑至绿。汇总：`logs/connected-api36-task6-summary.txt`；逐类日志 `logs/connected-task6{,b,c}-*.log`。
- 本层不能推出：ARM64 解码、锁屏/划任务、耳机/蓝牙、真实服务器与问题媒体（见第 5/6/9 节）。

## 4. Visual/adaptation screenshot checklist

Tasks 4 阶段（基线 `bb07b42`）执行，本阶段未重跑；记录摘自 `.superpowers/sdd/2026-07-31-ui-redesign/preflight-result.md`：

- 10 类适配/语义自动化：修复后重跑 **124/124 PASS**（含 2 条 D1 新断言）；应用层 nav/recreation/session/background 23/23。
- 12 张规定截图逐张人工核查：home-connected-light、home-error-dark、browser-content-dark、browser-empty-light、image-reader-dark、audio-player-dark、video-player-fullscreen-landscape、mini-player-browser-tail、queue-current-next、dialog-destructive 共 10 张 PASS；video-player-normal 初版 FAIL（真实缺陷 D1：浅色主题普通播放器传输控件不可见）→ 修复（`8ff3ee1`，两屏幕包 `MediaViewerTheme(darkTheme = true)`）后重截 PASS（另补 audio-player-light-fixed）；settings-ime-320-font2 记环境性 FAIL（`wm size` 覆盖与 AVD IME 组合的工具层假象，自动化 320dp 用例与 392dp 对照均正常，交 ARM64 真机复核——见第 9 节）。
- 自动化缺口如实记录：360×800 布局、视频横屏用例、fontScale 1.3 三项 NOT RUN（以截图人工核查补充，不补凑数用例）。
- 本阶段修复新增像素断言并全绿：浅色主题迷你播放器/队列浮层控件可见性（SC-I5，暗像素占比断言）。

## 5. Real server and problem-media results

真实服务器探测（Task 5 Step 1，2026-08-01 两次独立执行）：

- `http://127.0.0.1:9955/tmp/wallpa/`：连接失败（curl exit 7，HTTP 000；`netstat` 无 9955 监听）。
- `http://127.0.0.1:8080/`：HTTP 404（无关服务，无 `/tmp/wallpa/` 目录/媒体响应）。
- 判定：用户真实逻辑服务器与 `/tmp/wallpa/` 已知问题媒体**当前不可验证**；按计划不构造 fixture 冒充。

| 项 | 结果 | 原因 |
|---|---|---|
| adb reverse tcp:9955 | NOT RUN | 主机 9955 无服务可桥接 |
| RealServerSmokeTest | NOT RUN | 需真实服务器 baseUrl |
| PLAY-01 pause→play | NOT RUN | 真实服务器/问题媒体不可达 |
| PLAY-02 暂停拖动显示目标帧 | NOT RUN | 真实媒体不可达；且目标帧核对属 ARM64 真机项 |
| PLAY-03 seek 后播放 | NOT RUN | 真实服务器/问题媒体不可达 |
| PLAY-04 连续 seek 容差 | NOT RUN | 真实媒体不可达；长时稳定性属真机项 |
| PLAY-05 短/长缓冲 | NOT RUN | 需真实网络路径真实服务器 |
| PLAY-06 曾有声无图媒体 | NOT RUN | 需用户实际问题媒体；arm64 解码属真机项 |
| PLAY-07 后台 15s 回前台 | NOT RUN | 真实媒体不可达；真机渲染恢复属真机项 |
| PLAY-08 端点刷新保持 | NOT RUN | 真实服务器/问题媒体不可达 |

证据：`.superpowers/sdd/2026-07-31-ui-redesign/device-acceptance.md`。

## 6. Background/focus/device results

- 模拟器自动化已覆盖并 PASS：BackgroundPlaybackTest（1）、MediaSessionControlsTest（6）、AppActivityRecreationTest（2）、LibVlcVideoOutputTest（2）及播放器/队列/UI 全部在范围用例（第 3 节）。
- 需真机/外设的人工项，全部 NOT RUN（不从模拟器升级）：
  - BG-03 锁屏/解锁：NOT RUN（无 ARM64 真机）。
  - BG-04 播放中/暂停中划掉最近任务：NOT RUN（无真机）。
  - BG-05 进程/MediaSessionService 恢复：NOT RUN（无真机）。
  - FOCUS-01 瞬时 audio focus 丢失与条件恢复：NOT RUN（无电话/真实焦点竞争源）。
  - FOCUS-02 永久丢失/有线耳机拔出/Bluetooth 断开：NOT RUN（无耳机、无 Bluetooth 外设）。
  - 通知 play/pause/previous/next/stop 与通知冷启动人工核对：NOT RUN（会话控制面已由 MediaSessionControlsTest 自动化覆盖；真机通知栏逐项人工核对需 ARM64 设备）。
  - 320dp+font2+IME 设置内容可达性（Task 4 工具层假象定性复核）：NOT RUN（无 ARM64 真机；自动化 320dp 用例 PASS）。
  - LibVLC arm64 native 解码：NOT RUN（无 ARM64 设备）。
  - Release APK 真机安装/冷启动/覆盖升级：NOT RUN（无 ARM64 设备）。

## 7. Independent review

范围 `2949a99..bb07b42`，两名独立复审员（规格符合性 / 代码质量），结论均经同一复审员 re-review：

- 首轮：`Ready: No` × 2（规格 6 Important + 13 Minor；质量 1 Important + 7 Minor）。
- 7 个 Important 全部按「独立核实 → RED 失败测试 → 最小修复 → GREEN → 逐条提交」关闭：
  CQ-F1 播放器入口分支顺序（`e966bd1`）；SC-I1 锁定返回退出全屏（`7977dda`）；SC-I2 浏览页刷新入口（`53569cf`）；SC-I3 图片轻触切换工具栏（`3e8ce02`）；SC-I4 放弃确认范围（`d1006c4`）；SC-I5 迷你播放器/队列浅色对比度（`ab1027f`）；SC-I6 菜单选中勾选（`1c97300`）。复审固化契约对齐：`de618ea`。
- re-review：**`Ready: Yes` × 2**，无未关闭 Critical/Important。
- 24 条 Minor 全部书面留存（含 re-review 新增 4 条），理由记录于 `.superpowers/sdd/2026-07-31-ui-redesign/final-review-result.md`。
- 复审后最终回归：本记录第 2/3 节全部内容均在最后代码变更 `de618ea` 上重跑取得。

## 8. Release APK metadata, signature limitation and SHA-256

干净工作树守护流程（临时 stash 两个用户路径 + 逐文件 SHA-256 inventory + try/finally 恢复验证）内由 `scripts/Build-PersonalRelease.ps1` 于 `de618ea` 全新生成（旧同名产物已先删除）：

- APK：`D:\code\mediaviewer\.worktrees\android-mediaviewer\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk`
- 大小：43,776,126 字节（41.75 MiB ≤ 70 MiB）
- 包名/版本/SDK/ABI：`com.local.mediaviewer` / **1.1.0 (3)** / minSdk 29 / targetSdk 36 / 唯一 Native ABI `arm64-v8a`
- 压缩：5 个 native 条目（含 `lib/arm64-v8a/libvlc.so`）与 4 个 DEX 条目全部 DEFLATE（断言通过）
- 对齐：`zipalign -c -P 16 -v 4` 通过
- 签名：`apksigner verify` 通过；**仅 v3 scheme**（v1/v2/v3.1/v4 = false）；1 signer；RSA；证书 DN `C=US, O=Android, CN=Android Debug`
- 证书 SHA-256：`b432a64032601b66f275d0c4b3308d95cbb40b58be9269c1494783e82fa5415d`
- **APK SHA-256：`0dd7ab188e408e9bf39154205b6b2457e687d0d1ed2692d4029e75c809d6eb77`**
- APK SHA-512：`53f82c0c4759f45fa1fc95850ca73e787117c6aa619cacc0e701283f9f2c15e9abdd92415cb5d26d70edc230a510ee771431d95c094f947921bc6407edae021b`
- sidecar：`dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256` 整行（hash + 文件名）与 APK 一致
- **签名限制**：本 APK 使用个人 debug keystore 签名，不是商店/正式生产签名；未来只有持有同一 keystore 的 APK 才能原地升级；keystore 丢失或重建后新 APK 无法覆盖安装，需卸载旧应用并可能丢失应用本地状态。
- 构建内门禁：clean、`testDebugUnitTest`、`lintRelease`、`assembleRelease`、压缩/zipalign/签名/badging/证书 SHA-256/APK SHA-256 全部通过（守护窗退出码 0）。
- 独立验证日志：`logs/release-verification.log`；构建脚本生成的记录外存于 `C:\tmp\mediaviewer-guard-ui-flow-release-build-script-record-20260801T153406285Z-ea0081c1bbf145849648ac663ac9efa1.md`。

## 9. Explicit NOT RUN items

1. 真实服务器烟测（RealServerSmokeTest）与 adb reverse：NOT RUN——`127.0.0.1:9955` 连接拒绝（无监听），8080 为无关 404 服务；不构造 fixture 冒充。
2. PLAY-01..08 问题媒体矩阵：NOT RUN——真实服务器与 `/tmp/wallpa/` 实际问题媒体不可达（PLAY-02/04..07 同时依赖 ARM64 真机）。
3. BG-03 锁屏、BG-04 划掉最近任务、BG-05 进程/服务恢复：NOT RUN——无 ARM64 真机。
4. FOCUS-01 瞬时焦点、FOCUS-02 永久焦点/耳机/蓝牙：NOT RUN——无电话、耳机、Bluetooth 外设。
5. 通知控制与通知冷启动人工核对：NOT RUN——需 ARM64 设备（会话控制面已自动化覆盖）。
6. ARM64 真机全部项：Release APK 安装/冷启动/覆盖升级、LibVLC arm64 解码、真实硬件渲染/后台恢复、长时播放与连续 seek：NOT RUN——无 ARM64 设备；x86_64 模拟器与静态 APK 检查均不替代。
7. 320dp+font2+IME 设置内容可达性真机复核：NOT RUN——无 ARM64 设备（模拟器自动化用例 PASS，手工假象已定性）。
8. 360×800 布局、视频横屏、fontScale 1.3 自动化：NOT RUN——自动化缺口（视觉层以截图人工核查补充）。
9. 个人 debug 签名的商店/生产等效性：NOT RUN（非目标）——签名限制见第 8 节。

---

真机人工验收清单（交付用户）：上述第 1–7 项需在用户真实服务器（9955）与 ARM64 真机环境下执行；安装本 APK 后重点核对暂停拖动目标帧、后台 15 秒声音与回前台画面、端点刷新保持、锁屏/划任务/焦点与通知控制、以及 320dp 大字体 IME 下设置页可达性。
