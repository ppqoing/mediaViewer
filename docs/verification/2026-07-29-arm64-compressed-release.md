# mediaviewer arm64 压缩 Release 验收记录

- 完成时间：2026-07-29 12:06:42 +08:00
- Git 修订：9b38b34a38a37162f81666a4d16f464537351b6f
- 包名：com.local.mediaviewer
- 版本：1.0.1 (2)
- ABI：arm64-v8a
- APK：dist/mediaviewer-v1.0.1-arm64-v8a-release.apk
- APK 大小：39.8 MiB
- SHA-256：065f72309095e000607eb5dc1b47db83568d9e1bde5eac82aadf5d6a3215afcf
- 签名证书 SHA-256：b432a64032601b66f275d0c4b3308d95cbb40b58be9269c1494783e82fa5415d
- 此前证书对比：通过

## 自动门禁

- JVM 与 Robolectric 测试：通过
- Release Lint：0 error
- arm64-v8a 为唯一 Native ABI：通过
- LibVLC Native 条目压缩：通过
- 所有 DEX 条目压缩：通过
- APK 小于或等于 70 MiB：通过
- ZIP 对齐与 APK 签名：通过
- SHA-256 二次验证：通过

## 运行时验收边界

- API 36 x86_64 Debug 全功能验收由现有 Android 验收流程完成。
- 当前没有 arm64 真机，本记录不声称完成 arm64 Release 安装或冷启动。