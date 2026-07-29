# arm64 压缩 Release 设计

日期：2026-07-29
状态：已由用户批准

## 背景

当前 `mediaviewer` 1.0.0 Release 是一个通用 APK，同时包含
`arm64-v8a`、`armeabi-v7a`、`x86` 和 `x86_64` 四套 LibVLC Native
库，并以未压缩方式存储 Native 与 DEX。已生成 APK 为 287.60 MiB，其中：

- Native 库为 238.67 MiB，占 83.0%；
- DEX 为 48.07 MiB，占 16.7%；
- `libvlc.so` 四个 ABI 合计 206.62 MiB。

本应用只供用户本人使用，目标设备采用 `arm64-v8a`。用户接受以更长的安装
时间和安装后解压占用换取显著更小的侧载 APK。

## 目标

1. 只交付一个 `arm64-v8a` Release APK。
2. 像 VLC 官方侧载 APK 一样压缩 Native `.so` 与 DEX。
3. 将版本升级为 `1.0.1`，`versionCode` 升级为 `2`。
4. 保持现有 LibVLC 4、播放、图片、HTTP 和 IPv4 行为不变。
5. 最终 APK 不超过 70 MiB。
6. 提供可重复构建、签名、检查和生成 SHA-256 的脚本。

## 非目标

- 不切换到 LibVLC 3。
- 不启用 R8、代码混淆或资源裁剪。
- 不修改业务代码或播放器适配层。
- 不创建、复制或提交新的正式签名密钥。
- 不生成通用 Release APK，也不生成 armv7、x86 或 x86_64 Release APK。
- 不声称在当前不存在的 arm64 真机上完成运行时验收。

## 构建架构

### Release ABI

在 `release` 构建类型的 `ndk.abiFilters` 中只保留
`arm64-v8a`。该配置只影响 Release：

- Release 只打包 arm64 Native 库；
- Debug 继续打包现有 ABI，因此 API 36 x86_64 模拟器测试保持可用；
- 不使用通用 Release 作为回退产物。

### ZIP 压缩

通过 Android Gradle Plugin 的 Packaging DSL 启用旧式压缩存储：

- `jniLibs.useLegacyPackaging = true`；
- `dex.useLegacyPackaging = true`。

这两个设置位于模块级 Packaging 配置，因此 Debug APK 也可能采用压缩存储，
但不会改变 Debug ABI 范围或应用逻辑。安装器会在安装时解压相关内容，用户
已经接受由此增加的安装时间和安装后磁盘占用。

### 版本和优化

- `versionName = "1.0.1"`；
- `versionCode = 2`；
- LibVLC 固定为 `4.0.0-eap29`；
- Release 显式保持 `isMinifyEnabled = false`；
- 不启用 `isShrinkResources`。

这样可以把变化限制在打包层，避免因更换播放引擎或 R8 保留规则造成运行时
回归。

## 构建与交付组件

### Gradle 配置

`app/build.gradle.kts` 负责：

- 应用版本；
- Release 专用 arm64 ABI 过滤；
- Native 和 DEX 压缩；
- 明确关闭本次范围外的 R8 与资源裁剪。

### 个人 Release 脚本

新增 `scripts/Build-PersonalRelease.ps1`，作为唯一的 Release 交付入口。脚本
接收 Android SDK 路径和签名文件参数，并依次完成：

1. 检查工作树、SDK、Gradle Wrapper、Build Tools 和签名文件；
2. 执行单元测试、Release Lint 和 Release 构建；
3. 检查未签名 APK 的版本、ABI、ZIP 压缩方式和体积；
4. 执行 ZIP 对齐；
5. 使用现有 Android 调试证书签名；
6. 独立验证签名和最终 APK 元数据；
7. 复制到 `dist` 并生成 SHA-256；
8. 生成中文 Release 验收记录。

签名文件只通过参数或本机默认路径读取，不复制到仓库，不把密码写入日志或
验收记录。个人 Release 继续使用此前 Debug APK 相同的 Android 调试证书，
从而允许匹配证书的旧版本覆盖安装。

### 交付物

- `dist/mediaviewer-v1.0.1-arm64-v8a-release.apk`
- `dist/mediaviewer-v1.0.1-arm64-v8a-release.apk.sha256`
- `docs/verification/2026-07-29-arm64-compressed-release.md`

旧的 1.0.0 APK 不被覆盖。

## 验证数据流

```text
干净提交
  -> JVM 测试与 Release Lint
  -> assembleRelease
  -> 未签名 APK 结构检查
  -> ZIP 对齐
  -> APK 签名
  -> 签名与元数据复核
  -> 体积门禁
  -> dist APK
  -> SHA-256 二次核对
  -> 中文验收记录
```

结构检查必须直接读取 APK ZIP 中央目录，而不是只检查 Gradle 文本配置。

## 失败处理

脚本遇到以下任一情况立即退出并使用中文报告原因：

- 工作树不干净；
- 缺少 SDK、Build Tools、Gradle Wrapper 或签名文件；
- Gradle 测试、Lint 或构建失败；
- 包名、版本名或版本号不正确；
- APK 缺少 `arm64-v8a/libvlc.so`；
- APK 包含 armv7、x86 或 x86_64 Native 文件；
- 任意 DEX 或 Native `.so` 仍以 ZIP Store 方式保存；
- APK 超过 70 MiB；
- ZIP 对齐、APK 签名或 SHA-256 验证失败。

脚本失败时不得用旧 APK 冒充新产物，也不得写出“验收通过”记录。

## 测试策略

### 业务功能回归

现有 Debug 测试链路保持不变，并在 API 36 x86_64 模拟器执行：

- JVM 和 Robolectric 测试；
- Android Lint；
- Compose 仪器测试；
- LibVLC 播放、seek 和四种视频画面模式；
- 单图、条漫、排序、动态加载和缩放；
- HTTP Range 与真实服务器双根目录冒烟；
- Debug APK 安装和冷启动。

### arm64 Release 静态验收

arm64 Release 必须验证：

- 包名 `com.local.mediaviewer`；
- `versionName=1.0.1`、`versionCode=2`；
- `minSdk=29`、`targetSdk=36`；
- Native ABI 集合严格等于 `{arm64-v8a}`；
- Native `.so` 与所有 DEX 均为压缩条目；
- APK 不超过 70 MiB；
- APK 签名有效并与此前个人 APK 证书一致；
- SHA-256 文件与 APK 实际哈希一致。

### 运行时验收边界

当前只有 x86_64 模拟器，arm64 Release 无法在该模拟器安装。最终验收记录
必须明确区分：

- x86_64 Debug：完成全功能运行时验收；
- arm64 Release：完成构建、结构、压缩、签名和哈希静态验收。

未来连接 arm64 设备后，可以给脚本增加或启用可选安装、冷启动和进程存活
检查，但本次不把它作为交付前置条件。

## 文档与维护

README 更新以下内容：

- Release APK 只适用于 arm64 Android 设备；
- APK 体积减小依赖 ABI 过滤和 ZIP 压缩；
- 安装时间与安装后占用不会同比减少；
- 提供 Release 构建、校验和安装命令；
- x86_64 模拟器继续使用 Debug APK。

ABI 名称、70 MiB 上限和交付文件名由 Release 脚本集中定义，避免在多个脚本
中复制。以后需要其他 ABI 时，应生成新的 ABI 专用包，而不是恢复通用 Release。

## 验收标准

1. 所有现有自动化测试继续通过。
2. Debug APK 仍可在 API 36 x86_64 模拟器安装并启动。
3. Release APK 只包含 arm64 Native 库。
4. Release 中 Native 和 DEX 均已压缩。
5. Release APK 不超过 70 MiB。
6. Release APK 签名、包元数据和 SHA-256 全部验证通过。
7. 中文 README 与验收记录准确描述运行时验收边界。
8. 功能分支无未提交源代码改动，签名材料未进入 Git。
