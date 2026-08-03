# 分片 MP4 播放进度兼容验证

- 日期：2026-08-03
- 设计：`docs/superpowers/specs/2026-08-03-fragmented-mp4-progress-design.md`
- 实施计划：`docs/superpowers/plans/2026-08-03-fragmented-mp4-progress.md`
- 验收分支：`feature/android-mediaviewer`
- 实现提交：`8d5d9c9`、`de37ff2`、`5de4cda`、`a78f668`
- 总结：自动门禁和 LibVLC 设备冒烟通过；目标 fMP4 与两份对照样本均未复现“数秒跳至接近结尾”的进度异常。前后 seek 的时间变化可以触发，但受无声、无窗口模拟器和队列自动续播影响，未形成足以同时确认画面、声音、时间三者一致的稳定证据，按 `BLOCKED/PARTIAL` 记录。

## 1. 自动测试

所有 Gradle 命令均只在对应进程中设置：

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT='C:\Users\Administrator\AppData\Local\Android\Sdk'
```

| 项目 | 命令 | 退出码 | 结果 |
| --- | --- | ---: | --- |
| 缺陷聚焦 JVM 测试 | `.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.playback.IsoBmffFragmentDetectorTest" --tests "com.local.mediaviewer.playback.PlaybackSourceResolverTest" --tests "com.local.mediaviewer.playback.VlcMediaOptionsTest" --tests "com.local.mediaviewer.queue.PlaybackCoordinatorTest" --tests "com.local.mediaviewer.service.VlcSessionPlayerTest" --tests "com.local.mediaviewer.service.PlaybackSessionCallbackTest"` | 0 | `BUILD SUCCESSFUL in 7s` |
| 完整 JVM 测试 | `.\gradlew.bat :app:testDebugUnitTest` | 0 | `BUILD SUCCESSFUL in 12s`；JUnit XML 合计 436 个测试，0 失败、0 错误、0 跳过 |
| AndroidTest 编译 | `.\gradlew.bat :app:compileDebugAndroidTestKotlin` | 0 | `BUILD SUCCESSFUL in 1s` |
| lint | `.\gradlew.bat :app:lintDebug` | 0 | `BUILD SUCCESSFUL in 51s`；HTML/SARIF 报告生成成功 |
| Debug APK | `.\gradlew.bat :app:assembleDebug` | 0 | `BUILD SUCCESSFUL in 12s` |

首次在受限沙箱中运行聚焦测试和完整 JVM 测试时，Gradle Wrapper 下载 `gradle-9.5.0-bin.zip` 被网络策略拒绝，两个首次尝试均退出 1，错误为 `java.net.SocketException: Permission denied: getsockopt`。在获准使用项目 Gradle 网络/缓存权限后，以完全相同的 Gradle 参数重跑并取得上表退出码 0；这两次环境前置失败不属于代码或测试失败。

Debug APK：

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：135,447,541 字节
- 生成时间：2026-08-03 11:35:20 +08:00
- SHA-256：`F06DA690E4D5B6B2AFCD300EF62A0DF066BCF8234C36D1816992E7BCCFF7ED3A`

## 2. 设备与 LibVLC 冒烟

设备信息：

- 设备：`emulator-5554`
- AVD：本机已运行的 `Pixel_3a_API_36_extension_level_17_x86_64`；该进程在本次验收开始前已存在，因此本次没有启动或停止模拟器进程
- 制造商/型号：Google / `sdk_gphone64_x86_64`
- Android：16，API 36
- 指纹：`google/sdk_gphone64_x86_64/emu64xa:16/BP22.250325.006/13344233:userdebug/dev-keys`
- 分辨率：1080×2220

主 APK 安装：

```powershell
C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

退出码 0，输出 `Success`。

按计划第一次直接运行：

```powershell
C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe -s emulator-5554 shell am instrument -w -e class com.local.mediaviewer.LibVlcEngineCreationTest com.local.mediaviewer.test/androidx.test.runner.AndroidJUnitRunner
```

第一次退出码 1，输出 `INSTRUMENTATION_FAILED`。根因检查显示 `pm list instrumentation` 为空、`pm path com.local.mediaviewer.test` 退出 1：计划只安装了主 APK，设备上没有测试包和 runner。补齐前置条件：

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

两条命令均退出 0，测试 APK 安装输出 `Success`，`pm list instrumentation` 随后显示 `com.local.mediaviewer.test/androidx.test.runner.AndroidJUnitRunner`。重跑原 instrumentation 命令退出 0：

```text
com.local.mediaviewer.LibVlcEngineCreationTest:..

Time: 0.189

OK (2 tests)
```

两项测试实际覆盖默认 Media 创建/关闭、显式 `PlaybackDemuxStrategy.AVFORMAT` Media 创建/关闭，以及应用级唯一播放控制器。

## 3. 用户 LAN 样本

### 3.1 连接与样本

通过应用“设置 → 服务器连接”把 URL 从原有 `http://192.168.1.17:8080` 编辑为 `http://192.168.1.17:8081`，点击“测试连接”后“保存”按钮变为可用并保存。返回媒体源页后真实显示：

```text
已连接
192.168.1.17
webdev / UC / pik / pik-2 / tmp
```

随后从应用 UI 进入 `pik/32223323/姝姬娘娘/3/姝姬娘娘 50v/`。目录真实列出目标和两份对照文件。未调用服务器管理接口、未写入服务器，也未改动媒体文件。

目标 URL：

```text
http://192.168.1.17:8081/pik/32223323/姝姬娘娘/3/姝姬娘娘 50v/5_6192647223633779422.mp4
```

### 3.2 目标 fMP4 进度

首次从 0 打开后，UI 显示总时长 `01:51`。以 MediaSession 首次进入 `PLAYING`、位置 0 为运动开始基准，ADB 日志得到：

| 墙钟时间 | 播放位置 | 结论 |
| ---: | ---: | --- |
| 0.000 秒 | 0.000 秒 | 开始播放 |
| 3.004 秒 | 2.812 秒 | 正常递增 |
| 6.007 秒 | 5.761 秒 | 约 5 秒检查正常 |
| 9.011 秒 | 8.827 秒 | 约 10 秒检查正常，远小于 `01:40` |

为取得可读的 UI 定时证据，第二轮从已保存位置 34.883 秒恢复播放：墙钟 +5.054 秒时 UI 为 `00:40 / 01:51`，+10.060 秒时 UI 为 `00:45 / 01:51`。两轮证据均显示进度与墙钟近似 1:1，没有复现旧版本约 4 秒跳到约 108 秒的问题。

### 3.3 暂停与恢复

ADB 媒体按键用于避免控件 3 秒自动隐藏造成误触；UI 与 MediaSession 使用同一播放会话：

| 操作 | 位置/状态 | 结果 |
| --- | --- | --- |
| 暂停起点 | 78.093 秒，`PAUSED`，speed 0.0 | 记录暂停点 |
| 暂停 3 秒后 | 78.093 秒，`PAUSED`，speed 0.0 | 位置完全不增长，PASS |
| 恢复后 | 78.350 秒，`PLAYING`，speed 1.0 | 从暂停点继续 |
| 恢复约 5 秒后的最近一次 MediaSession 更新 | 81.179 秒，`PLAYING`；随后 UI 抓取为 `01:27 / 01:51` | 位置继续增长，PASS；UI 抓取包含约 3 秒层级导出开销 |

### 3.4 向前/向后 seek

状态：`BLOCKED/PARTIAL`。

原计划是在目标 `5_6192647223633779422.mp4` 上分别点击“快进 10 秒”和“快退 10 秒”。实际执行到这一步时，目标已经播放到结尾并自动续播；可审计的操作前状态为 `active item id=1`、`PAUSED`、位置 4.411 秒，后续 UI 标题/总长确认此时媒体已经是相邻 GPAC `5_6239897357152951964.mp4`（总长 `00:37`），不再是目标文件。两个控件的尝试分别记录如下：

| 尝试 | 计划操作 | 实际操作前后读数 | 可观察结果 | 验收结论 |
| --- | --- | --- | --- | --- |
| 向前 seek | 计划点击目标文件的“快进 10 秒”；ADB 尝试点击控件所在位置 `(742, 1846)` | 前：相邻 GPAC，`PAUSED`、4.411 秒、`active item id=1`；约 0.6 秒后：`PLAYING`、仍为 4.411 秒、`active item id=1` | 没有出现可归因的 `+10 秒` 读数，反而从暂停切到播放；说明控件自动隐藏/队列切换后该坐标没有形成可审计的前向 seek。没有目标文件的前向 seek 数字可用。 | `BLOCKED`，不能记 PASS |
| 向后 seek | 紧接着计划点击“快退 10 秒”；ADB 尝试点击控件所在位置 `(338, 1846)` | 前一可审计状态为相邻 GPAC 4.411 秒；约 0.6 秒后为 `BUFFERING`、5.350 秒、`active item id=1`；随后 UI 层级为 `00:00 / 00:37` | 位置由 4.411 秒增到 5.350 秒而不是减少 10 秒，UI 又在缓冲时显示 0；没有形成可归因的 `-10 秒` 读数，也没有目标文件的后向 seek 数字可用。 | `BLOCKED`，不能记 PASS |

因此，已有证据只能证明两次 seek 自动化都没有得到稳定、可归因的按钮结果，不能证明任一控件正确或错误。无窗口模拟器上的播放控件会在 3 秒后自动隐藏，样本到结尾还会自动续播下一项；模拟器也没有可供验收者直接听取的音频输出，无法把同一次操作的目标视频画面、声音和时间三项都可靠归因。本项保持 `BLOCKED/PARTIAL`；没有用静态测试替代，也没有修改服务器或样本规避。

## 4. 对照样本

### 4.1 另一份 GPAC fMP4

文件：`5_6239897357152951964.mp4`，UI 总时长 `00:37`。

- 开始时 MediaSession 为 0.000 秒。
- UI 可读抓取为 `00:11 / 00:37`。
- 后续日志连续为 15.104、18.185、21.250、24.058、27.125、30.260 秒，约每 3 秒增加 3 秒。
- 没有在数秒内跳至 `00:35 / 00:37`，结果 PASS。

### 4.2 普通平坦 MP4

文件：`5_6239902382264688558.mp4`，UI 总时长 `00:19`。

- 点击后 0.505 秒检测到 `PLAYING`、位置 0。
- 从该检测点计时 7.052 秒时唤出控件，UI 抓取为 `00:08 / 00:19`。
- MediaSession 日志连续为 0.000、2.673、5.672、8.653 秒，约每 3 秒增加 3 秒。
- 结果 PASS，符合墙钟约 7 秒时 UI 仍约 `00:07 / 00:19` 的预期（抓取时多约 1 秒）。

## 5. 范围核对

- 服务器修改：无。
- 媒体重封装或改写：无。
- 依赖升级：无。
- 全局 `avformat`：无；仅解析结果为分片 MP4 时选择 `AVFORMAT`。
- MediaSession 位置链路改动：无。
- UI 进度计算改动：无。
- Tasks 1–4 的实现文件仅涉及 BMFF 探测、HTTP 播放源解析、播放源/队列传递、LibVLC Media 选项及对应测试；本 Task 5 只新增本验证记录。
- 用户原有未提交改动与未跟踪文件保持原样，未重置、清理或纳入本次提交。
