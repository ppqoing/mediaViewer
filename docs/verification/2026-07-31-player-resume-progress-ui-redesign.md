# 播放恢复、进度同步与播放器界面改造验收记录

## 验收对象

- 测试提交：`4b69051ff6591c9e77df81be7a999956e6e893b4`
- 应用包名：`com.local.mediaviewer`
- 版本：`1.1.0 (3)`
- 最低/目标 SDK：`29 / 36`
- APK：`dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`
- APK 大小：`43,649,150` 字节（`41.63 MiB`）
- Native ABI：仅 `arm64-v8a`

## 自动门禁

- 命令：`pwsh -NoProfile -File .\scripts\Invoke-AndroidVerification.ps1 -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'`
- 结果：**PASS**
- 证据：最终提交上 Gradle `BUILD SUCCESSFUL in 44s`，共 `116 actionable tasks: 20 executed, 96 up-to-date`。
- 覆盖：JVM 测试、Debug Lint、Debug/Release 构建、AndroidTest 编译、Manifest、Media3 与 APK ABI 检查。
- 说明：首次用 Windows PowerShell 5 启动时因 UTF-8 脚本编码产生 ParserError，未进入构建；改用 PowerShell 7 后执行上述完整门禁并通过。

## 设备与人工行为验收

- API 36 定向 Connected 门禁：**PASS**，`BackgroundPlaybackTest`、`MediaSessionControlsTest`、`LibVlcVideoOutputTest` 共 `5/5`，`0 failed`，最终独立复跑 `BUILD SUCCESSFUL in 1m 38s`。
- 设备环境说明：首次整合复跑在 `2/5` 后因模拟器掉线报 `device offline`；设备重启后从最终提交完整重跑上述五项并通过。
- 暂停后播放，画面和声音继续推进：**NOT RUN：本轮没有完成交互式人工验收。**
- 暂停后拖动并释放，保持暂停且显示目标帧：**NOT RUN：本轮没有完成交互式人工验收。**
- 上述拖动后播放，画面和声音从目标位置开始：**NOT RUN：本轮没有完成交互式人工验收。**
- 连续五次暂停、拖动、播放且无冻结帧：**NOT RUN：本轮没有完成交互式人工验收。**
- 视频进入后台至少 15 秒，声音继续，返回后画面更新：**NOT RUN：自动后台播放测试通过，但没有完成该人工观察。**
- 普通模式竖向音量弹层、静音/取消静音及硬件音量键刷新：**NOT RUN：本轮没有完成交互式人工验收。**
- 全屏右侧音量轨、左侧亮度轨及锁定模式拦截：**NOT RUN：本轮没有完成交互式人工验收。**
- 队列手动加入、整行拖动、删除普通/当前项及重启恢复：**NOT RUN：队列 Connected 自动测试已通过，但没有完成完整人工重启流程。**
- 进度条下方无第二加载条，中央缓冲图标在播放推进后消失：**NOT RUN：本轮没有完成交互式人工验收。**
- 真实服务器与 `http://127.0.0.1:9955/tmp/wallpa/` 问题视频路径：**NOT RUN：本次没有连接对应服务器和问题媒体。**
- arm64 Release 安装与冷启动：**NOT RUN：没有 arm64 真机。**

## Release 构建与独立校验

- 构建命令：`pwsh -NoProfile -File .\scripts\Build-PersonalRelease.ps1 -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'`
- 构建结果：**PASS**
- APK 签名校验：**PASS**，APK Signature Scheme v3 为 `true`，v1/v2/v3.1/v4 为 `false`，签名者数量为 `1`。
- 签名证书：`C=US, O=Android, CN=Android Debug`
- 签名证书 SHA-256：`b432a64032601b66f275d0c4b3308d95cbb40b58be9269c1494783e82fa5415d`
- 签名限制：使用个人 Android Debug 证书签名，适合本项目个人安装；不应作为公开商店发布证书。
- ZIP 对齐：**PASS**，Android Build Tools `36.0.0` 的 `zipalign -c -P 16 4` 返回成功。
- 包元数据：**PASS**，包名、版本、SDK 与 ABI 均与本记录一致。
- 压缩与大小检查：**PASS**，Native 与 DEX 压缩规则通过，APK 小于 `70 MiB`。
- SHA-256：`155abf7e93f943f207974674cda8806292fc9faea7eb7ef259970d7554128c00`
- 校验文件比对：**PASS**，APK 实际 SHA-256 与 `.sha256` 文件一致。

## 文件保护

- 构建前已把原有未跟踪记录备份到 `C:\tmp\mediaviewer-2026-07-30-arm64-release.before-player-redesign.md`。
- Release 脚本生成的最终临时 2026-07-30 记录另存为 `C:\tmp\mediaviewer-2026-07-31-final-build-script-record.md`。
- 原有 `.superpowers/brainstorm/` 与 `docs/verification/2026-07-30-arm64-compressed-release.md` 已恢复，未纳入本次提交。
