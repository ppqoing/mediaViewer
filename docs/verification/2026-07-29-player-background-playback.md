# 播放器后台播放验收记录

- 本地执行日期：2026-07-30
- 本地环境：Windows、JDK 21、Android SDK 36
- 设备/真实服务器边界：本轮未启动 AVD、未运行 connected tests、未访问真实服务器

## 已执行的本地自动门禁

执行：

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

结果：PASS。

```text
BUILD SUCCESSFUL
本地自动门禁通过：JVM、Lint、Debug/Release、androidTest 编译、Manifest、Media3、APK ABI
设备测试：NOT RUN（使用 -RunDeviceTests 明确启用）
真实服务器测试：NOT RUN（使用 -RunDeviceTests -RunRealServerTest 明确启用）
```

脚本实际完成：

- `testDebugUnitTest`：PASS
- `lintDebug`：PASS，0 error；现有非阻断 warning 保留
- `assembleDebug`：PASS
- `assembleRelease`：PASS
- `compileDebugAndroidTestKotlin`：PASS
- Manifest 前台服务权限与 `mediaPlayback` service type：PASS
- Media3 common/session 版本 `1.10.1`：PASS
- Debug APK LibVLC ABI：
  `arm64-v8a, armeabi-v7a, x86, x86_64`
- Release APK LibVLC ABI：仅 `arm64-v8a`
- Debug APK：140,393,958 bytes
- Release unsigned APK：43,574,311 bytes

## 已编译、待设备执行的定向测试

以下测试源码已编译为 GREEN，但本记录不把“编译通过”写成“设备运行通过”：

- `BackgroundPlaybackTest`
  - 渲染真实 `VlcSurface`，由 Activity `ON_STOP/ON_START` 自动触发
    `Detached -> Attached`，测试体不直接调用视频输出绑定 API
  - Activity 停止期间 position 继续增长且仍 playing，恢复后 position 连续
- `MediaSessionControlsTest`
  - 独立 MediaController 的 pause/play/previous/next/seek 与应用控制器同步
  - active MediaStyle notification 包含当前标题和 session token
  - 20 秒视频在约 12 秒位置 STOP_AND_RELEASE，仅关闭一个 engine
  - 真实 Room repository 与 `PlaybackPositionPolicy` 保留队列和有效恢复位置
  - 冷连接先保持 `playWhenReady=false` 且不自动播放；首次用户 play 后从保存
    位置附近继续，再由用户 pause
- `LibVlcVideoOutputTest`
  - VLCVideoLayout 可从旧 host 解绑并绑定到新 host
  - 回绑不改变播放器状态，旧/新 host 不残留 View

API 36 x86_64 定向执行命令：

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -RunDeviceTests `
  -AvdName 'Pixel_3a_API_36_extension_level_17_x86_64'
```

状态：**NOT RUN**。

全量 `connectedDebugAndroidTest` 状态：**NOT RUN**。

真实服务器 `RealServerSmokeTest` 状态：**NOT RUN**。

## 人工设备验收清单

以下项目需要 API 33–36 模拟器或真实设备，当前均为 **NOT RUN**：

- [ ] 视频退到后台后仅继续声音，返回后画面和进度连续
- [ ] 通知与锁屏显示当前标题、进度和有效控制按钮
- [ ] 正在播放时划掉最近任务仍继续播放
- [ ] 通知停止释放播放器但不清空持久队列
- [ ] 拔出有线耳机后暂停，重新插入不自动播放
- [ ] 蓝牙/耳机 play、pause、previous、next
- [ ] 网络失败保留当前项，不自动跳到下一项
- [ ] 进程结束、冷启动和设备重启后恢复为暂停且不自动出声

## 完成声明边界

本记录只证明本地 JVM、Lint、构建、仪器测试编译和静态交付契约通过。通知、
锁屏、划掉任务、耳机、真实音频后台播放及设备重启行为，需要上述设备步骤
实际执行后才能记为 PASS。
