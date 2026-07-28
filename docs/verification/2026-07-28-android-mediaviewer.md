# mediaviewer Android 验收记录

- 完成时间：2026-07-28 20:43:32 +08:00
- Git 修订：368ffbe08a63edab8f1f73c8e51a20267ab54f59
- AVD：Pixel_3a_API_36_extension_level_17_x86_64
- Android API：36
- ABI：x86_64
- 真实服务器：http://192.168.1.17:8080
- 应用进程 PID：28608
- APK：dist/mediaviewer-debug.apk
- SHA-256：457a2690c47cf5eda27ac2c274e069d17d49274e2bf00b52598dddf3397ab46f

## 自动门禁

- JVM 单元测试：通过
- Robolectric API 29：通过
- Android Lint：0 error
- Debug APK 构建：通过
- Compose 全导航：通过
- PNG/WAV/MP4 自生成夹具：通过
- HTTP Range 206：通过
- LibVLC 视频、音频与 seek：通过
- 横屏旋转：通过
- API 36 x86_64 安装与启动：通过

## 真实服务器

- /middle/：HTTP 200，Caddy JSON 可解析，应用内解析通过
- /pik/：HTTP 200，Caddy JSON 可解析，应用内解析通过

验收过程未读取媒体正文，未在日志或本记录中写入真实目录条目名称。
