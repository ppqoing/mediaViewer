# VLC 续播时钟与前后台生命周期复验记录

- 日期：2026-08-02
- 复验范围：手动暂停后续播不刷新视频输出；视频页前后台状态机；ARM64 个人签名 Release 静态门禁。
- 历史边界：[暂停恢复后精确播放进度验证记录](2026-08-02-player-resume-exact-progress.md)。该历史记录中的人工 ARM64 场景不是本记录的 PASS 证据。

## 1. 修订与边界

- 实施基线：`d24bc4313b9de15b089e71ad7bf7ae864f800a10`。
- 本轮 Release 源 HEAD：`0fc38710bbc110222ac904c28e46e856e4bc1345`（`fix: preserve video session across app background`）。
- 基线至 HEAD 的定向 diff 覆盖 `PlayerViewModel.kt`、`VideoBackgroundPlaybackPolicy.kt`、`MediaViewerApp.kt` 与两份对应测试，共 300 行新增、50 行删除；未使用 `HEAD~3` 作为错误边界。
- 本任务没有运行 JVM 或 Android 测试，也没有调用会全量测试的 `scripts/Build-PersonalRelease.ps1`。

## 2. 前序定向 RED/GREEN 证据（仅引用，未重跑）

| 来源 | RED | GREEN | 结果 |
|---|---|---|---|
| Task 1，提交 `21e5212f226ed8959795766532c39f838a86e93c` | `PlayerViewModelTest.paused video resumes without refreshing output` 与 `paused scrub defers play until engine confirms target`：2/2 断言失败，旧实现调用 `refresh`。 | 同一筛选命令 `testDebugUnitTest`：`BUILD SUCCESSFUL in 16s`，退出码 0。 | PASS（前序定向证据） |
| Task 2，提交 `bcf33c3`、`f3fd49f`、`e2f7512` | `VideoBackgroundPlaybackPolicyTest` 在状态机 API 未实现时编译失败，含 `onAppStopped`、`VideoBackgroundLifecycleState` 等 unresolved reference。 | 同一筛选命令：`BUILD SUCCESSFUL in 20s`，退出码 0；覆盖暂停记忆、手动暂停、后台播放开关、配置重建、等待会话项单次恢复、重复 stop、媒体变更/关闭、离开视频页共 8 项。 | PASS（前序定向证据） |
| Task 3，提交 `0fc38710bbc110222ac904c28e46e856e4bc1345` | 不适用。 | `compileDebugKotlin --no-daemon`：`BUILD SUCCESSFUL in 19s`，8 个任务中 2 个执行、6 个最新。 | PASS（前序编译证据） |

静态代码复核与上述前序报告一致：`PlayerViewModel.playNow()` 只调用 `play()`；`ON_STOP` 不清空视频队列；离开视频页仍停止并清空队列；音频页不接入视频状态机。

## 3. 本轮 Release 构建与静态门禁

### 3.1 实际构建命令

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat clean lintRelease assembleRelease '-Pkotlin.incremental=false' --no-daemon --stacktrace
```

首次尝试因沙箱禁止下载 Gradle 包装器而未进入构建；授权下载后，未加引号的属性被 PowerShell 错传为任务 `.incremental=false`，也未执行 lint/编译。以上带单引号的同一属性重试后实际执行 `clean lintRelease assembleRelease`，输出 `BUILD SUCCESSFUL in 1m 24s`，52 个任务执行、1 个最新。`compileReleaseKotlin` 只有既有弃用警告：`PlaybackSessionCallback.kt` 与 `SingleImageViewer.kt`，未新增 lint error。

### 3.2 对齐、个人签名和交付命令

未调用 `Build-PersonalRelease.ps1`。使用 `scripts/ReleaseApkTools.psm1` 的 `Find-CompleteAndroidBuildTools`、`Assert-Arm64CompressedArchive`、`Assert-ApkBadgingMetadata`、`Get-ApkSignerCertificateSha256` 与 `Write-VerifiedSha256`：先对 `app-release-unsigned.apk` 运行压缩/ABI/体积门禁，再以 `zipalign -P 16 -f -v 4` 对齐，并用 `%USERPROFILE%\.android\debug.keystore` 的 `androiddebugkey` 签名（v4 签名关闭）。签名后再次运行压缩/ABI/体积、`aapt dump badging`、`apksigner verify --verbose --print-certs`、`zipalign -c -P 16 -v 4` 和 SHA-256 二次校验。

### 3.3 静态门禁结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| `lintRelease` 与 `assembleRelease` | PASS | 上述 Gradle 命令退出码 0，`BUILD SUCCESSFUL in 1m 24s`。 |
| Native ABI、LibVLC Native 与 DEX 压缩、体积 | PASS | `Assert-Arm64CompressedArchive` 对未签名和已签名 APK 均通过；唯一 Native ABI 为 `arm64-v8a`；`classes*.dex`、`lib/arm64-v8a/libvlc.so`、`libvlcjni.so` 均为 compressed；43,796,606 字节（41.77 MiB），小于 70 MiB。 |
| 包元数据 | PASS | `aapt dump badging`：`com.local.mediaviewer`，`versionCode=3`，`versionName=1.1.0`，`minSdk=29`，`targetSdk=36`，`native-code: 'arm64-v8a'`。 |
| 签名 | PASS | `apksigner verify --verbose --print-certs` 退出码 0；v3 为 true，1 个签名者。 |
| 16 KiB ZIP 对齐 | PASS | `zipalign -c -P 16 -v 4` 输出 `Verification successful`，退出码 0。 |
| SHA-256 交付校验 | PASS | `Write-VerifiedSha256` 写入后再次读取并匹配。 |

## 4. 交付产物

- APK：`D:\code\mediaviewer\.worktrees\android-mediaviewer\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk`
- SHA 文件：`D:\code\mediaviewer\.worktrees\android-mediaviewer\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256`
- 大小：43,796,606 字节（41.77 MiB）。
- SHA-256：`59b0a18cfee5e6c0096252cc3c4de26d143ca1695f5b20830753e40b91244b79`。
- Build Tools：`C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\36.0.0`。
- 证书：`C=US, O=Android, CN=Android Debug`；证书 SHA-256 为 `b432a64032601b66f275d0c4b3308d95cbb40b58be9269c1494783e82fa5415d`。这是个人 Debug 证书，仅用于个人安装测试，不是应用商店发布证书。

## 5. ARM64 真机检测与人工验收

只读检测命令：

```powershell
C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe devices -l
```

输出仅为 `List of devices attached`，没有在线设备；因此没有可用 ARM64 真机。未安装 APK，未卸载旧应用，未清除任何设备数据。以下自动门禁结果不替代真机验收。

| 场景 | 结果 | 原因/后续复现步骤 |
|---|---|---|
| 安装本次 APK 与签名冲突处理 | NOT RUN | 无在线 ARM64 设备；后续如同包名旧应用签名不同，报告冲突，不卸载、不清数据。 |
| 原始失败视频：1.0x 播放约 10 秒、手动暂停 3 秒、再播放至少 60 秒；时间、滑块、画面、声音同步且不快速到末尾 | NOT RUN | 无在线 ARM64 设备。 |
| 未勾选后台播放、正在播放时切后台至少 5 秒；应暂停，返回同视频原位置附近继续 | NOT RUN | 无在线 ARM64 设备。 |
| 未勾选后台播放、已手动暂停时切后台再返回；应保持暂停 | NOT RUN | 无在线 ARM64 设备。 |
| 勾选后台播放时切后台；应继续播放，返回不跳转、不重置位置 | NOT RUN | 无在线 ARM64 设备。 |
| 从视频页返回目录；应停止播放并清空播放列表 | NOT RUN | 无在线 ARM64 设备。 |
| 旋转或配置重建；不因本状态机额外暂停或续播 | NOT RUN | 无在线 ARM64 设备。 |

## 6. 最终审查修复与重新构建

### 6.1 修复范围与代码提交

- 最终审查修复代码提交：`17e840fb3f0a013f035944efaa88d30b00b7d18e`（`fix: retain video route state through reconnect`）。
- `PlayerRouteLifecycleState` 在 `PlayerRoute` destination 作用域保留最后确认的媒体类型；`currentItem == null` 的 Connecting/Failed 重连窗口不再清除已确认视频身份。
- 视频后台播放复选框移至同一 destination 作用域；pending 恢复请求与活动视频身份只在 destination 真正销毁或新 Ready 项确认是音频时清除。
- Ready 视频退出使用可用的 `PlayerViewModel.stopAndClear()`；Connecting、Failed、Empty 的 bootstrap 退出在最后确认是视频时使用 `QueuePlaybackController.clearAll()` 后导航。确认音频或从未确认的路由保持原非视频退出行为，音频页未接入视频后台播放交互。

### 6.2 本轮聚焦 RED/GREEN

只新增并运行 `PlayerRouteLifecyclePolicyTest`，没有运行完整测试集，也没有重跑 Task 1/Task 2 已通过测试。

- RED 命令：`./gradlew.bat testDebugUnitTest --tests 'com.local.mediaviewer.navigation.PlayerRouteLifecyclePolicyTest' --no-daemon`。API 形状建立后，4 项测试中 2 项按预期失败：`confirmed video identity survives missing current item during reconnect` 与 `bootstrap exit after confirmed video stops and clears`；退出码 1。另两项“音频/未确认路由不做视频清理”和“视频变音频结束视频身份”当时已通过。
- GREEN 命令只重跑上述两个失败方法，输出 `BUILD SUCCESSFUL in 22s`，32 个任务中 6 个执行、26 个最新，退出码 0。
- 接线编译：`./gradlew.bat compileDebugKotlin --no-daemon` 输出 `BUILD SUCCESSFUL in 4s`，8 个任务均为最新，退出码 0；该命令不运行测试。
- 基础静态复核：`ON_STOP` 无 `clearAll()`；destination 级 `DisposableEffect(entry.id)` 负责最终清理；bootstrap `onBack` 与 Empty 自动退出均使用路由级退出决策；Ready 视频经 `stopAndClear()`，bootstrap 视频经 `clearAll()`；`AudioPlayerScreen` 不包含视频后台播放复选框接线。限定文件 `git diff --check` 退出码 0。

### 6.3 从最终修复提交重新构建与交付

在 HEAD 为 `17e840fb3f0a013f035944efaa88d30b00b7d18e` 时重新执行：

```powershell
.\gradlew.bat clean lintRelease assembleRelease '-Pkotlin.incremental=false' --no-daemon --stacktrace
```

结果为 `BUILD SUCCESSFUL in 1m 21s`，53 个可操作任务中 52 个执行、1 个最新；没有运行测试。随后使用第 3.2 节所述既有 `ReleaseApkTools.psm1` 流程重新检查、16 KiB 对齐并以同一个人 Debug 证书签名，只覆盖第 4 节列出的 APK 与同名 SHA 文件。

刷新后的交付信息：

- APK 大小：43,796,606 字节（41.77 MiB）。
- 唯一 ABI：`arm64-v8a`；LibVLC Native 与 DEX 均为 compressed；小于 70 MiB。
- 包元数据：`com.local.mediaviewer`，`versionCode=3`，`versionName=1.1.0`，`minSdk=29`，`targetSdk=36`。
- APK SHA-256：`73a053e3e0ac79f92ff24be2cc36c8a299fd2c6b18acf7a8c2bc01e1054b69c4`；同名 SHA 文件与 `Get-FileHash` 二次读取一致。
- 证书 SHA-256：`b432a64032601b66f275d0c4b3308d95cbb40b58be9269c1494783e82fa5415d`；`apksigner verify` 与 `zipalign -c -P 16 -v 4` 均退出码 0。
- 最终只读 `adb devices -l` 仍无在线设备；第 5 节全部真机场景继续为 NOT RUN，没有安装、卸载或清除设备数据。

第 3、4 节保留的是前一次构建的历史事实；当前结论与交付以本节从 `17e840f` 重新生成的 APK 和新 SHA-256 为准。

## 7. 结论

最终审查的两个 Important finding 已由聚焦 RED/GREEN 覆盖，代码提交 `17e840f` 的编译、Release lint/构建以及刷新 APK 静态门禁均记录为 PASS；ARM64 真机人工验收仍全部为 NOT RUN。故本记录只能结论为“最终修复代码与静态门禁完成并重新生成个人签名 ARM64 Release”，不能宣称“用户原始问题已在真机解决”。
