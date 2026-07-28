# mediaviewer Android 应用设计

## 目标

创建一个供个人使用的 Android 媒体浏览与播放应用 `mediaviewer`。应用连接
现有 Caddy HTTP 文件服务，浏览 `/middle/` 与 `/pik/`，并直接随机读取原始
媒体文件。

首版必须：

- 支持 Android 10（API 29）及以上手机和平板；
- 默认连接 `http://192.168.1.17:8080`，并允许修改服务器地址；
- 支持 IPv4 字面地址和可解析 A 记录的 DNS 域名；
- 浏览嵌套目录；
- 在应用内播放视频和音频，并查看图片；
- 支持视频和音频拖动进度与断点续播；
- 支持视频横屏全屏；
- 生成可直接安装的 Debug APK、SHA-256 和中文使用文档。

## 非目标

首版不包含：

- 自动扫描、mDNS 或 NSD 服务发现；
- HTTPS、身份认证或公网/私网地址安全过滤；
- 后台播放、画中画、投屏或 Android TV 遥控器界面；
- 下载、离线缓存、播放列表或媒体库索引；
- 图片或视频缩略图；
- 正式发布签名或应用商店发布；
- 服务端写入、上传、删除、移动或重命名。

## 现有服务契约

服务器默认地址为：

```text
http://192.168.1.17:8080
```

根目录映射为：

| 应用入口 | HTTP 路径 | Windows 目录 |
| --- | --- | --- |
| MiddleDir | `/middle/` | `I:\MiddleDir` |
| pik | `/pik/` | `G:\pik` |

目录请求发送：

```http
Accept: application/json
```

Caddy 返回 JSON 数组，每个条目包含：

```text
name, size, url, mod_time, mode, is_dir, is_symlink
```

客户端使用 `url` 字段相对当前目录 URL 做标准 URI 解析。客户端不得再次编码
整个 URL，也不得使用文件名进行字符串拼接，以避免中文、日文、空格、括号和
emoji 被重复编码。

媒体请求直接使用原始文件 URL。Caddy 已支持 HTTP Range；播放器不得先下载
完整文件。

## 技术路线

应用采用：

- Kotlin；
- 单一 Android 应用模块；
- Jetpack Compose 与 Material 3；
- Compose Navigation；
- 内嵌 `org.videolan.android:libvlc-all:4.0.0-eap29`；
- OkHttp 负责目录与连接测试请求；
- Kotlin Serialization 解析 Caddy JSON；
- Coil Compose 查看图片；
- Room 保存视频和音频播放位置；
- 手工构造依赖，不引入依赖注入框架。

LibVLC 负责所有视频、音频以及未知非图片文件的播放尝试，以尽可能接近 VLC
的格式兼容范围。视频输出使用 `SurfaceView`，不使用 `TextureView`。图片由
独立查看器处理，避免占用 LibVLC 播放资源。

LibVLC 当前选用 Maven Central 的 4.0.0 EAP 构建。该选择适合本项目的私用
Debug APK，但必须通过 x86_64 模拟器测试和最终 APK 构建验证降低预览版本
风险。应用随附第三方许可说明。

## 模块边界

### ServerSettings

职责：

- 保存用户输入的服务器 URL；
- 首次启动返回默认地址；
- 校验 URL 仅使用 `http`；
- 拒绝用户名、密码、查询串、片段和非根路径；
- 保存原始主机名，而不是某次解析出的 IP；
- 保存最近一次成功连接的 IPv4，仅作为状态展示。

用户输入必须是完整形式：

```text
http://主机名或IPv4:端口
```

端口可以省略；省略时使用 HTTP 默认端口 80。应用提供的默认值明确包含
端口 8080。

### Ipv4Resolver

职责：

- IPv4 字面地址直接返回；
- DNS 主机名通过 A 记录解析；
- 忽略 IPv6 结果；
- 没有 IPv4 时返回可展示错误；
- 保留系统解析顺序；
- 不区分公网与私网 IPv4，也不显示安全警告。

应用启动、保存设置和首次连接失败时执行解析。解析得到多个 IPv4 时，
ConnectionProbe 依次测试。

### ConnectionProbe

对每个候选 IPv4 构造会话基础 URL，并依次请求：

```text
/middle/
/pik/
```

两个请求都必须返回 HTTP 200 且响应可解析为 Caddy JSON，该 IPv4 才可作为
当前会话端点。首个成功候选结束探测。所有候选都失败时返回包含域名解析结果
和最后一个连接错误的失败状态。

单次探测的连接超时为 3 秒，读取超时为 5 秒。

设置中保存用户填写的域名或 IPv4；目录与媒体请求使用本次探测成功的 IPv4。
应用下次启动重新解析域名，以适配 DNS 或 DDNS 变化。

### CaddyDirectoryClient

职责：

- 请求 Caddy JSON 目录；
- 将条目转换为 `DirectoryEntry`；
- 使用当前目录 URL 与服务端 `url` 字段解析下一 URL；
- 将文件夹排在文件前，再按名称进行不区分大小写的稳定排序；
- 将网络、HTTP 和解析错误转换为明确的领域错误。

网络请求不得在主线程运行。目录状态分为：

```text
Loading
Content
Empty
Error
```

普通目录请求的连接超时为 5 秒，读取超时为 15 秒。

### MediaClassifier

条目类型为：

```text
Directory
Video
Audio
Image
Unknown
```

识别不区分扩展名大小写。已知视频、音频和图片显示各自图标；未知文件显示
通用文件图标，点击后交给 LibVLC 尝试播放。目录列表不因未知扩展名而隐藏
文件。

### VlcPlaybackEngine

职责：

- 创建和释放 LibVLC 与 MediaPlayer；
- 通过 HTTP URL 播放视频、音频和未知文件；
- 暴露播放、暂停、时长、当前位置、缓冲和错误状态；
- 支持精确到播放器能力范围内的 seek；
- 管理音频焦点；
- 耳机断开时暂停；
- 应用进入后台时暂停，不提供后台播放；
- 视频播放时保持屏幕常亮；
- 离开播放页时释放原生播放器和视频 Surface。

同一时刻只存在一个 LibVLC 播放实例。视频使用 `SurfaceView`。音频页不创建
视频 Surface。

### ImageViewer

职责：

- 通过 HTTP URL 加载图片；
- 显示加载进度和失败重试；
- 支持双指缩放、拖动与双击复位；
- 离开页面后释放大图引用。

图片查看只使用进程内内存缓存，禁用 Coil 磁盘缓存，避免形成未受管理的
离线副本。

### PlaybackPositionStore

Room 表使用稳定的逻辑媒体键作为主键。该键由用户保存的服务器 URL 与 Caddy
相对路径生成，不包含本次 DNS 解析出的 IPv4，因此 DDNS 地址变化后仍能恢复
进度。表保存：

```text
media_key, position_ms, duration_ms, updated_at
```

规则：

- 播放位置每 5 秒保存一次；
- 暂停、页面退出和进程进入后台时立即保存；
- 位置不足 10 秒时不恢复；
- 播放完成或已达到总时长 95% 时删除记录；
- 图片不保存查看位置。

## 界面与导航

### 首页

顶部栏显示：

- 应用名 `mediaviewer`；
- 当前服务器状态；
- 设置按钮。

内容区显示两个固定入口：

- MiddleDir；
- pik。

连接未建立时显示连接错误与重试按钮，不显示失效的目录内容。

### 目录页

目录页使用紧凑列表，不加载缩略图。每行显示：

- 类型图标；
- 完整文件名；
- 文件大小；
- 修改时间。

文件夹优先，其余按名称排序。顶部显示可点击面包屑。返回键返回上级目录；
根目录再返回时回到首页。

### 视频页

提供：

- 播放和暂停；
- 当前时间与总时长；
- 可拖动进度条；
- 缓冲状态；
- 全屏按钮；
- 返回按钮。

全屏按钮进入横屏沉浸模式。返回键先退出全屏，再关闭播放页。旋转时播放器
实例和当前位置不重建。

### 音频页

显示文件名、音频类型图标、播放控制、当前位置、总时长和进度条。不提供
后台播放或通知栏媒体控制。

### 图片页

使用深色背景，显示文件名和返回按钮。图片支持缩放、拖动和双击复位。

### 设置页

设置页包含：

- 服务器 URL 输入框；
- 测试连接按钮；
- 解析出的 IPv4 列表；
- 当前选择的 IPv4；
- 保存按钮。

只有 ConnectionProbe 找到同时支持两个根目录的 IPv4 后才允许保存。

## 状态与数据流

1. 应用读取 ServerSettings。
2. Ipv4Resolver 解析主机。
3. ConnectionProbe 选择可用 IPv4。
4. 首页进入 Connected 状态。
5. 用户打开一个根目录。
6. CaddyDirectoryClient 请求 JSON 并生成 DirectoryEntry 列表。
7. 用户进入子目录时沿服务端 URL 继续解析。
8. 用户点击文件时，同时生成稳定的 `media_key` 和本次会话的实际请求 URL；
   MediaClassifier 再选择 VlcPlaybackEngine 或 ImageViewer。
9. 视频和音频播放时，PlaybackPositionStore 保存进度。
10. 连接失败时仅重新解析并重试一次，之后显示人工重试。

Compose 页面只消费 ViewModel 暴露的不可变 UI 状态，不直接执行网络、
数据库或 LibVLC 操作。进程内会话保存当前解析 IP、浏览路径和播放状态。

## 明文 HTTP

应用目标版本高于 Android 9，因此 Manifest 必须显式允许 cleartext HTTP。
应用层不对公网与私网 IPv4 做区别，不显示安全警告，也不限制用户连接的
IPv4。HTTPS、证书、认证及网络安全策略不属于首版范围。

## 错误处理

| 场景 | 用户行为 |
| --- | --- |
| DNS 无 A 记录 | 显示“未解析到 IPv4”，允许修改或重试 |
| 所有 IPv4 均连接失败 | 显示逐个探测后的最后错误与重试 |
| 连接或读取超时 | 保留当前页面，显示重试 |
| HTTP 403/404 | 显示状态码；目录文件消失时允许返回 |
| HTTP 其他错误 | 显示服务器状态码，不尝试解析正文 |
| JSON 字段错误 | 显示“目录响应格式无效” |
| 空目录 | 显示空状态，不视为错误 |
| VLC 无法解码 | 显示播放错误并允许返回 |
| 图片加载失败 | 显示图片错误与重试 |
| 应用旋转 | 保留目录、媒体 URL 和播放位置 |

所有错误都转换为简体中文用户消息；日志保留底层异常类型和 HTTP 状态，但
不记录媒体响应体。

## 测试策略

### JVM 单元测试

覆盖：

- ServerSettings URL 规范化与拒绝规则；
- IPv4 字面地址；
- DNS 单个、多个、仅 IPv6和解析失败；
- 公网与私网 IPv4 均被接受；
- 候选 IP 按顺序探测及失败切换；
- Caddy JSON 成功、空数组和字段错误；
- 相对 URL 与特殊字符；
- 媒体分类；
- 排序；
- 播放位置保存、恢复和清除阈值。

### HTTP 集成测试

使用 MockWebServer 覆盖：

- 两个根目录均成功；
- 一个根目录失败；
- 200、403、404、500；
- 超时；
- 非 JSON；
- Unicode 和已编码 URL；
- 服务器地址切换。

### Compose UI 测试

使用可替换的假 Repository 和播放器状态覆盖：

- 首页连接状态；
- 两个根入口；
- 目录进入与返回；
- 面包屑；
- 空目录；
- 重试；
- 设置测试和保存；
- 视频、音频和图片三类页面；
- 播放进度恢复提示。

### 模拟器验收

使用现有 API 36、x86_64 AVD
`Pixel_3a_API_36_extension_level_17_x86_64`：

- 安装 Debug APK；
- 启动应用；
- 使用默认地址访问真实 Caddy；
- 浏览 `/middle/` 与 `/pik/`；
- 使用项目内生成的短视频、短音频和图片固定样本，通过 MockWebServer 验证
  播放、seek 和缩放，不依赖或复制用户媒体；
- 验证横竖屏切换；
- 验证应用重启后的播放位置。

最低 API 29 行为由 Robolectric API 29 测试、Android Lint 和 minSdk 构建
约束覆盖；本机当前没有 API 29 模拟器镜像。

### 构建门禁

交付前必须全部通过：

```text
Gradle JVM tests
Android Lint
Compose/仪器测试
assembleDebug
APK 安装与启动
```

## 交付物

- Android Studio/Gradle 工程；
- `mediaviewer-debug.apk`；
- APK SHA-256；
- 中文 README；
- 第三方许可说明；
- 自动化测试；
- 构建与模拟器验收记录。

应用 ID 固定为：

```text
com.local.mediaviewer
```

APK 使用 Android Debug keystore 签名，不创建或提交正式签名密钥。

## 验收标准

满足以下条件即为首版完成：

1. Android 10+ 工程可从干净检出构建；
2. API 36 模拟器可以安装和启动 APK；
3. 默认服务器连接成功；
4. 两个根目录及嵌套目录可浏览；
5. Unicode 与特殊字符文件 URL 正确；
6. 视频和音频由内嵌 LibVLC 播放并可 seek；
7. 视频可横屏全屏；
8. 图片可加载、缩放和拖动；
9. 播放位置可保存、恢复并在完成后清除；
10. DNS 主机名可解析一个或多个任意 IPv4；
11. 网络与媒体错误不会使应用崩溃；
12. 自动化测试、Lint、Debug 构建和模拟器验收全部通过；
13. 输出 APK、SHA-256 与中文使用文档。

## 参考资料

- [Caddy `file_server` 目录 JSON](https://caddyserver.com/docs/caddyfile/directives/file_server)
- [Android 明文 HTTP 说明](https://developer.android.com/media/media3/exoplayer/troubleshooting)
- [LibVLC Android Maven Artifact](https://central.sonatype.com/artifact/org.videolan.android/libvlc-all)
