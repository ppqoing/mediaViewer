# mediaviewer Android 验收记录

- 完成时间：2026-07-29 00:57:03 +08:00
- Git 修订：c00de60249aa94f9ab736a175670b873c4bcdab3
- AVD：Pixel_3a_API_36_extension_level_17_x86_64
- Android API：36
- ABI：x86_64
- 真实服务器：http://192.168.1.17:8080
- 应用进程 PID：11244
- APK：dist/mediaviewer-debug.apk
- SHA-256：abd47bd7434a8f9499338244ad9f68a5335a7494986f8f54e7b27f0c197438f2

## 自动门禁

- JVM 单元测试：通过
- Robolectric API 29：通过
- Android Lint：0 error
- Debug APK 构建：通过
- Compose 全导航：通过
- PNG/WAV/MP4 自生成夹具：通过
- HTTP Range 206：通过
- LibVLC 视频、音频与 seek：通过
- LibVLC VLCVideoLayout 输出几何：通过
- 四种视频画面模式：通过
- 条漫/单图默认设置：通过
- 六种图片排序与锚点：通过
- 50 图片动态加载：通过
- 统一缩放与解码上限：通过
- 横屏旋转：通过
- API 36 x86_64 安装与启动：通过

## 真实服务器

- /middle/：HTTP 200，Caddy JSON 可解析，应用内解析通过
- /pik/：HTTP 200，Caddy JSON 可解析，应用内解析通过

验收过程未读取媒体正文，未在日志或本记录中写入真实目录条目名称。