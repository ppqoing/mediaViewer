# mediaviewer

`mediaviewer` 是一个 Android 10 及以上的私有媒体浏览器。它通过 HTTP
读取 Caddy 目录 JSON，并使用 HTTP Range 随机读取原始视频、音频和图片。

## 已实现能力

- 固定入口：`MiddleDir`（`/middle/`）和 `pik`（`/pik/`）
- 支持嵌套目录、IPv4 字面地址、DNS A 记录、私网 IPv4 和公网 IPv4
- 视频、音频和未知文件由内嵌 LibVLC 尝试播放
- 图片支持双指缩放、拖动和双击复位
- 播放位置每 5 秒及暂停、退出、后台时保存
- 不足 10 秒不恢复，完成或达到 95% 时清除进度
- 图片只使用内存缓存，不写 Coil 磁盘缓存

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

也可以把 APK 复制到 Android 设备后手工安装。系统询问时允许从当前文件
管理器安装未知应用。

## 使用

1. 确认 Android 设备能够访问服务器的 HTTP 端口。
2. 启动 `mediaviewer`，等待首页显示当前 IPv4。
3. 点击 `MiddleDir` 或 `pik` 浏览目录。
4. 点击文件播放或查看；视频页可拖动进度条并切换全屏。
5. 服务器地址变化时进入设置，输入 HTTP 根地址，点击“测试连接”，成功后保存。

## 常见问题

- “域名没有可用的 IPv4”：确认 DNS 存在 A 记录；AAAA 记录不会被使用。
- “两个媒体目录未同时通过”：确认 `/middle/` 与 `/pik/` 都开启 Caddy
  文件浏览，并且带 `Accept: application/json` 时返回 JSON。
- “无法连接服务器”：确认 Windows 防火墙允许服务端口，手机与服务器路由可达，
  公网使用时确认端口转发和 DDNS 指向当前公网 IPv4。
- “媒体无法播放”：LibVLC 会尝试识别未知文件，但损坏文件或不支持的编码仍会失败。
- “没有断点续播”：少于 10 秒不恢复；播放达到 95% 或自然结束后会清除记录。

## 明确不支持

HTTPS、身份认证、后台播放、画中画、投屏、Android TV、下载、离线媒体缓存、
播放列表、缩略图和服务端写操作不在本应用范围内。

第三方组件与许可见 `THIRD_PARTY_NOTICES.md`。
