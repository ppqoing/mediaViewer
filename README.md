# mediaviewer

`mediaviewer` 是一个 Android 10 及以上的私有媒体浏览器。它通过 HTTP
读取 Caddy 目录 JSON，并使用 HTTP Range 随机读取原始视频、音频和图片。

## 已实现能力

- 固定入口：`MiddleDir`（`/middle/`）和 `pik`（`/pik/`）
- 支持嵌套目录、IPv4 字面地址、DNS A 记录、私网 IPv4 和公网 IPv4
- 视频、音频和未知文件由内嵌 LibVLC 尝试播放
- 视频支持等比适应、裁剪铺满、强制拉伸和原始尺寸四种画面模式
- 图片支持单图查看和条漫阅读，默认看图方式可在设置页配置
- 条漫包含当前文件夹的全部图片，并从刚点击的图片开始阅读
- 条漫支持六种排序、整条图片流统一缩放、拖动和双击复位
- 播放位置每 5 秒及暂停、退出、后台时保存
- 不足 10 秒不恢复，完成或达到 95% 时清除进度
- 图片动态加载并限制解码尺寸，只使用内存缓存，不写 Coil 磁盘缓存

## 默认服务器

默认地址是 `http://192.168.1.17:8080`。设置页只接受 HTTP 根地址，并且
只有 `/middle/` 与 `/pik/` 都能返回合法 Caddy JSON 时才允许保存。

应用支持 DNS 主机名。每次启动及首次连接失败后重新解析 IPv4 A 记录，
按系统返回顺序探测，选择第一个两个根目录都可用的 IPv4。保存的仍是原始
逻辑域名，播放进度不会绑定某个临时 IPv4。IPv6 不参与探测。

## 构建环境

- JDK 21
- Android SDK 36
- Android Build Tools 36.0.0
- Gradle 9.5.0 Wrapper

在 PowerShell 中构建：

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## 个人 Release

个人 Release 只支持 `arm64-v8a` Android 设备。它不包含 armv7、x86 或
x86_64，因此不能安装到本项目使用的 x86_64 模拟器。

Release APK 压缩存储 LibVLC Native 库和 DEX，可以显著减少传输文件大小。
Android 安装时需要解压这些内容，所以安装时间可能更长，安装后的磁盘占用
不会按 APK 体积同比下降。

在 PowerShell 中生成、签名和验收：

```powershell
.\scripts\Build-PersonalRelease.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

默认使用当前 Windows 用户的
`%USERPROFILE%\.android\debug.keystore`。自定义签名文件时，通过
`MEDIAVIEWER_KEYSTORE_PASSWORD` 环境变量提供密码，不要把密码写进仓库。

交付物：

- `dist/mediaviewer-v1.0.1-arm64-v8a-release.apk`
- `dist/mediaviewer-v1.0.1-arm64-v8a-release.apk.sha256`

## 模拟器验收

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -AvdName 'Pixel_3a_API_36_extension_level_17_x86_64' `
  -RealServerBaseUrl 'http://192.168.1.17:8080'
```

验收脚本会生成 `dist/mediaviewer-debug.apk`、
`dist/mediaviewer-debug.apk.sha256` 与
`docs/verification/2026-07-28-android-mediaviewer.md`。

## 安装

```powershell
& 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe' `
  install -r .\dist\mediaviewer-debug.apk
```

安装 arm64 Release：

```powershell
& 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe' `
  install -r .\dist\mediaviewer-v1.0.1-arm64-v8a-release.apk
```

`INSTALL_FAILED_NO_MATCHING_ABIS` 表示设备不是 arm64。证书不匹配时，必须先
卸载旧应用再安装；此操作会清除应用数据。

也可以把 APK 复制到 Android 设备后手工安装。系统询问时允许从当前文件
管理器安装未知应用。

## 使用

1. 确认 Android 设备能够访问服务器的 HTTP 端口。
2. 启动 `mediaviewer`，等待首页显示当前 IPv4。
3. 点击 `MiddleDir` 或 `pik` 浏览目录。
4. 点击文件播放或查看；视频页可拖动进度条、切换画面模式和进入全屏。
5. 服务器地址变化时进入设置，输入 HTTP 根地址，点击“测试连接”，成功后保存。

### 视频画面模式

播放器右上角的画面模式菜单提供以下选项：

- **等比适应**：完整显示画面，可能出现黑边。
- **裁剪铺满**：保持比例并铺满窗口，边缘可能被裁剪。
- **强制拉伸**：忽略原始比例并铺满窗口。
- **原始尺寸**：按照视频原始像素尺寸显示。

普通播放和全屏播放都可以切换画面模式。画面模式只对当前视频有效；
每次打开新视频都会恢复为“等比适应”，不会影响播放进度记录。

### 图片阅读

设置页可以选择默认看图方式。新安装默认使用“条漫阅读”，也可以改为
“单图查看”；保存后，下一次打开图片即按该方式进入。

条漫阅读会读取当前文件夹中的全部图片，并把刚点击的图片作为初始位置：
向上查看前图，向下查看后图。排序菜单支持：

- 文件名升序、文件名降序
- 修改时间升序、修改时间降序
- 文件大小升序、文件大小降序

条漫中的所有图片始终使用同一个缩放比例，默认等比铺满手机屏幕宽度。
双指可在 `1×` 到 `5×` 之间缩放；放大后可以水平拖动，双击恢复 `1×`
并回到水平居中。

图片只在接近可见区域时动态加载，并按设备显示需求限制解码像素数，以避免
大文件夹或超大图片一次性占用过多内存。图片内容不会写入磁盘缓存，也不提供
离线阅读；服务器不可达时，需要恢复网络连接后重试。

## 常见问题

- “域名没有可用的 IPv4”：确认 DNS 存在 A 记录；AAAA 记录不会被使用。
- “两个媒体目录未同时通过”：确认 `/middle/` 与 `/pik/` 都开启 Caddy
  文件浏览，并且带 `Accept: application/json` 时返回 JSON。
- “无法连接服务器”：确认 Windows 防火墙允许服务端口，手机与服务器路由可达，
  公网使用时确认端口转发和 DDNS 指向当前公网 IPv4。
- “媒体无法播放”：LibVLC 会尝试识别未知文件，但损坏文件或不支持的编码仍会失败。
- “图片无法显示”：确认原文件可解码且服务器仍可访问；网络恢复后点击重试。
- “条漫顺序不正确”：在条漫页打开排序菜单，确认当前使用的排序字段和方向。
- “没有断点续播”：少于 10 秒不恢复；播放达到 95% 或自然结束后会清除记录。

## 明确不支持

HTTPS、身份认证、后台播放、画中画、投屏、Android TV、下载、离线媒体缓存、
播放列表、缩略图和服务端写操作不在本应用范围内。

第三方组件与许可见 `THIRD_PARTY_NOTICES.md`。
