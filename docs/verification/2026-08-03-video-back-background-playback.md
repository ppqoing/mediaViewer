# 视频返回时后台播放修复验收记录

- 日期：2026-08-03
- 工作树：`D:\code\mediaviewer\.worktrees\android-mediaviewer`
- 分支：`feature/android-mediaviewer`
- 验收范围：视频后台播放复选框对顶部返回键和系统返回键的退出决策

## 修复结果

- 未勾选后台播放时，视频返回仍保存进度、停止并清空播放队列。
- 勾选后台播放时，返回只保存进度并离开页面，不停止或清空；目录页保留当前媒体、队列和迷你播放器。
- 顶部返回键和系统返回键使用同一退出决策。
- 新播放器路由的后台播放复选框仍默认关闭。
- 切换到其他应用的既有暂停/恢复状态机和音频退出策略未修改。

## TDD 证据

### RED

命令：

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.local.mediaviewer.navigation.PlayerRouteLifecyclePolicyTest' --no-daemon
```

结果：按预期失败。新增测试的 5 个调用均报告 `No parameter with name 'backgroundPlaybackEnabled' found`，证明当前策略无法表达勾选后的保留动作。

### GREEN

命令：

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.local.mediaviewer.navigation.PlayerRouteLifecyclePolicyTest' --tests 'com.local.mediaviewer.player.VideoBackgroundPlaybackPolicyTest' --no-daemon
```

结果：`BUILD SUCCESSFUL in 25s`。

## 模拟器基础功能验证

- 设备：`Pixel_3a_API_36_extension_level_17_x86_64`，API 36，ABI `x86_64`。
- `videoBackgroundDefaultsOffAndBackClearsQueue`：PASS。
- `videoBackgroundOptInPreservesQueueAndNewSessionResetsOff`：PASS。
- `videoBackgroundOptInSystemBackPreservesQueue`：PASS。

首轮测试暴露了既有导航测试在控制层自动隐藏后直接查找按钮、且仍使用旧无障碍名称“更多播放设置”的问题。定向测试改为必要时单击视频区域唤出控制层，并匹配当前生产名称“更多播放选项”；随后仅重验失败项并通过。

## 编译、Lint 与 Release

- `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`：PASS，`BUILD SUCCESSFUL in 9s`。
- `:app:lintRelease`：PASS，`BUILD SUCCESSFUL in 9s`。
- `:app:assembleRelease`：PASS，`BUILD SUCCESSFUL in 58s`。
- 现有 `Build-PersonalRelease.ps1` 因工作树同时包含用户尚未提交的图标调整和本次修复而会主动拒绝运行；未暂存、回滚或覆盖这些改动。发布阶段复用同一 `ReleaseApkTools.psm1` 校验函数、Android build-tools 和默认个人签名完成等价检查。

## APK 结果

- 路径：`dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`
- 大小：`40,535,333` 字节（`38.66 MiB`）
- SHA-256：`dfa550a7f8f9c18c23b77075a3b6b554b98c16fde68a89bf0c41366e2e7b0430`
- 唯一 Native ABI：`arm64-v8a`
- Native 条目：5；DEX 条目：3；压缩检查通过
- 包名：`com.local.mediaviewer`
- 版本：`1.1.0 (3)`
- minSdk / targetSdk：`29 / 36`
- 签名：APK Signature Scheme v3，通过
- 签名证书 SHA-256：`b432a64032601b66f275d0c4b3308d95cbb40b58be9269c1494783e82fa5415d`
- 16 KiB ZIP 对齐：通过
- `.sha256` 文件与独立计算结果：一致

## 未运行边界

- 当前只有 x86_64 模拟器，arm64-v8a Release APK 未安装运行。
- 真实 ARM64 设备上的“勾选后返回目录，声音持续播放并可由系统通知控制”动态观察：`NOT RUN`。
- 本记录不以 Compose 假控制器测试替代上述真实播放设备验收。
