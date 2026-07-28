# mediaviewer Android 客户端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个 Android 10 及以上可安装的 `mediaviewer`，通过明文 HTTP 浏览 Caddy 的 `/middle/` 与 `/pik/`，并随机读取、播放或查看原始媒体文件。

**Architecture:** 工程采用单应用模块与手工依赖装配，设置、DNS/连接会话、目录浏览、播放进度、LibVLC 播放和 Coil 图片查看分别通过小接口隔离。逻辑服务器 URL 与本次解析出的 IPv4 始终分离，前者生成稳定媒体键，后者只负责当前会话请求；Compose 页面只消费 ViewModel 暴露的不可变状态。

**Tech Stack:** Kotlin 2.3.21、Android Gradle Plugin 9.3.0、Gradle 9.5.0、Jetpack Compose Material 3、Navigation Compose、OkHttp 5.3.0、Kotlin Serialization 1.11.0、DataStore 1.2.1、Room 2.8.4、Coil 3.5.0、LibVLC `4.0.0-eap29`、JUnit 4、Robolectric 4.16.1、AndroidX Test。

## Global Constraints

- 应用显示名固定为 `mediaviewer`，应用 ID 固定为 `com.local.mediaviewer`。
- 支持 Android 10（API 29）及以上手机和平板；`minSdk = 29`、`compileSdk = 36`、`targetSdk = 36`。
- 默认服务器固定为 `http://192.168.1.17:8080`，只接受 `http` 根地址。
- 固定入口为 `MiddleDir -> /middle/` 与 `pik -> /pik/`，并支持所有嵌套目录。
- 支持 IPv4 字面地址与 DNS A 记录；忽略 IPv6；公网和私网 IPv4 均允许，不增加安全过滤或警告。
- Manifest 必须显式允许 cleartext HTTP；不实现 HTTPS、证书或身份认证。
- 目录请求发送 `Accept: application/json`，并使用服务端 `url` 相对当前 URL 做标准 URI 解析。
- 媒体请求直接使用原始 HTTP URL 与 HTTP Range，不先下载完整文件。
- 视频、音频和未知非图片文件由内嵌 `org.videolan.android:libvlc-all:4.0.0-eap29` 尝试播放；视频输出使用 `SurfaceView`。
- 图片只使用进程内内存缓存，Coil 磁盘缓存必须禁用。
- Room 播放键使用逻辑服务器 URL 与逻辑媒体 URL，不得包含当前解析出的 IPv4。
- 播放位置每 5 秒、暂停、退出和进入后台时保存；不足 10 秒不恢复；完成或达到 95% 时删除。
- 不实现后台播放、画中画、投屏、Android TV、下载、离线缓存、播放列表、缩略图或服务端写操作。
- 所有用户可见错误使用简体中文；日志不得记录媒体响应体或目录响应体。
- 交付 Debug APK、SHA-256、中文 README、第三方许可说明和模拟器验收记录，不创建正式签名密钥。

---

## 1. 固定构建与依赖基线

所有版本在首次执行时按下表固定，不使用 `+`、`latest.release` 或其他动态版本。

| 项目 | 固定版本 | 依据 |
| --- | --- | --- |
| Android Gradle Plugin | `9.3.0` | [AGP 9.3 兼容表](https://developer.android.com/build/releases/agp-9-3-0-release-notes) |
| Gradle Wrapper | `9.5.0` | AGP 9.3 最低及默认版本 |
| Kotlin 编译器插件 | `2.3.21` | 与 AGP 9.3 官方示例一致；使用 AGP 内建 Kotlin，不应用 `org.jetbrains.kotlin.android` |
| KSP | `2.3.10` | [KSP 迁移文档](https://kotlinlang.org/docs/ksp-kapt-migration.html) |
| Compose BOM | `2026.06.00` | [Compose BOM](https://developer.android.com/develop/ui/compose/bom) |
| Activity Compose | `1.13.0` | AndroidX 稳定通道 |
| Lifecycle | `2.11.0` | AndroidX 稳定通道 |
| Navigation Compose | `2.9.8` | AndroidX 稳定通道 |
| DataStore Preferences | `1.2.1` | [DataStore 版本说明](https://developer.android.com/jetpack/androidx/releases/datastore) |
| Room | `2.8.4` | [Room 版本说明](https://developer.android.com/jetpack/androidx/releases/room) |
| OkHttp / MockWebServer | `5.3.0` | [OkHttp 官方仓库](https://github.com/square/okhttp) |
| Kotlin Serialization JSON | `1.11.0` | [Kotlin Serialization](https://kotlinlang.org/docs/serialization-get-started.html) |
| Kotlin Coroutines | `1.11.0` | [kotlinx.coroutines 官方仓库](https://github.com/Kotlin/kotlinx.coroutines) |
| Coil Compose / OkHttp | `3.5.0` | [Coil Getting Started](https://coil-kt.github.io/coil/getting_started/) |
| LibVLC Android | `4.0.0-eap29` | [Maven Central artifact](https://central.sonatype.com/artifact/org.videolan.android/libvlc-all) |
| Robolectric | `4.16.1` | Robolectric 当前稳定版，支持 API 36；API 36 测试使用 JDK 21 |
| AndroidX Test | Core/Runner `1.7.0`、Espresso `3.7.0`、Ext JUnit `1.3.0`、Orchestrator `1.6.1` | [AndroidX Test 版本说明](https://developer.android.com/jetpack/androidx/releases/test) |

本机执行基线：

```text
JDK: 21.0.7
Android SDK: C:\Users\Administrator\AppData\Local\Android\Sdk
Build Tools: 36.0.0
AVD: Pixel_3a_API_36_extension_level_17_x86_64
真实 Caddy: http://192.168.1.17:8080
```

## 2. 文件地图

第 2.2 至 2.4 节中的短路径均相对
`app/src/main/java/com/local/mediaviewer/`；其他条目使用仓库根目录相对路径。

### 2.1 工程与应用外壳

| 文件 | 责任 |
| --- | --- |
| `.gitignore` | 忽略构建目录、IDE 状态、Room Schema 和 `dist/` 交付副本 |
| `settings.gradle.kts` | 仓库、插件仓库和 `:app` 模块声明 |
| `build.gradle.kts` | 根插件版本入口 |
| `gradle/libs.versions.toml` | 所有依赖和插件的固定版本 |
| `gradle/wrapper/*`、`gradlew*` | 固定 Gradle 9.5.0 |
| `gradle.properties` | AndroidX、Kotlin、JVM 与构建参数 |
| `app/build.gradle.kts` | Android 36、minSdk 29、Compose、Room/KSP、测试与 LibVLC 打包配置 |
| `app/proguard-rules.pro` | 保留空的项目级混淆规则入口；Debug 构建不启用压缩 |
| `app/src/main/AndroidManifest.xml` | 应用、网络权限、cleartext、主 Activity 与播放器旋转 `configChanges` |
| `app/src/main/res/xml/network_security_config.xml` | 允许明文 HTTP |
| `app/src/main/res/values/strings.xml` | 简体中文 UI 文案 |
| `app/src/main/java/com/local/mediaviewer/MediaViewerApplication.kt` | 持有进程级 `AppContainer` |
| `app/src/main/java/com/local/mediaviewer/MainActivity.kt` | Compose 宿主与系统 UI 恢复点 |
| `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt` | 手工构造单例依赖 |
| `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt` | 根主题与 Navigation Compose 图 |

### 2.2 共享模型、设置和网络

| 文件 | 责任 |
| --- | --- |
| `core/AppResult.kt` | 成功或领域错误结果 |
| `core/AppError.kt` | 简体中文错误模型 |
| `core/DispatcherProvider.kt` | 可替换协程调度器 |
| `model/ServerConfig.kt` | 持久化的逻辑服务器地址 |
| `model/ValidatedServerUrl.kt` | 已验证 URL 的主机、端口和规范地址 |
| `model/SessionEndpoint.kt` | 逻辑地址与当前 IPv4 请求地址 |
| `model/RootShare.kt` | 两个固定根入口 |
| `model/DirectoryEntry.kt` | Caddy 条目与逻辑/请求 URL |
| `model/MediaKind.kt` | Directory、Video、Audio、Image、Unknown |
| `settings/ServerUrlValidator.kt` | URL 拒绝和规范化规则 |
| `settings/ServerSettingsRepository.kt` | 设置存储接口 |
| `settings/DataStoreServerSettingsRepository.kt` | Preferences DataStore 实现 |
| `network/CaddyEntryDto.kt` | Caddy JSON 传输模型 |
| `network/MediaClassifier.kt` | 按目录标志与扩展名分类媒体 |
| `network/DirectoryJsonParser.kt` | 严格解析、URI 解析、分类与排序 |
| `network/CaddyDirectoryClient.kt` | 5 秒连接、15 秒读取的目录请求 |
| `network/Ipv4Resolver.kt` | IPv4 解析接口 |
| `network/SystemIpv4Resolver.kt` | `InetAddress` A 记录实现 |
| `network/ConnectionProbe.kt` | 探测接口和测试结果 |
| `network/OkHttpConnectionProbe.kt` | 3 秒连接、5 秒读取并验证两个根目录 |

### 2.3 会话、浏览与 Compose 页面

| 文件 | 责任 |
| --- | --- |
| `session/ServerSessionState.kt` | Connecting、Connected、Failed |
| `session/ServerSessionManager.kt` | 启动解析、设置测试、保存、失败后单次重解析 |
| `browser/BrowserModels.kt` | 位置、面包屑、页面和媒体启动请求 |
| `browser/BrowserRepository.kt` | 目录加载、端点重映射和一次重试 |
| `browser/BrowserViewModel.kt` | 导航栈与 Loading/Content/Empty/Error |
| `home/HomeViewModel.kt` | 首页连接状态与重试 |
| `settings/SettingsViewModel.kt` | 输入、探测结果和“仅探测成功才保存” |
| `navigation/Destinations.kt` | 可序列化类型安全路由 |
| `ui/home/HomeScreen.kt` | 两个根入口、服务器状态与设置按钮 |
| `ui/settings/SettingsScreen.kt` | URL、IPv4 列表、当前选择、测试与保存 |
| `ui/browser/BrowserScreen.kt` | 文件夹优先列表、面包屑、空态与重试 |
| `ui/browser/BrowserFormatters.kt` | 文件大小和本地时间格式化 |
| `ui/components/AppErrorPanel.kt` | 统一错误与重试控件 |
| `ui/components/MediaRouteShell.kt` | 任务 8 的可返回媒体路由边界 |
| `ui/theme/*` | Material 3 主题 |

### 2.4 进度、播放器与图片

| 文件 | 责任 |
| --- | --- |
| `playback/PlaybackPositionEntity.kt` | Room 表 |
| `playback/PlaybackPositionDao.kt` | 查询、upsert、删除 |
| `playback/MediaViewerDatabase.kt` | Room 数据库 |
| `playback/PlaybackPositionPolicy.kt` | 10 秒与 95% 阈值 |
| `playback/PlaybackMediaKey.kt` | 从逻辑媒体 URL 生成稳定 Room 键 |
| `playback/PlaybackPositionStore.kt` | 稳定媒体键的保存、恢复、清除 |
| `playback/PlaybackState.kt` | LibVLC 映射后的不可变状态 |
| `playback/PlaybackEngine.kt` | 可替换播放接口 |
| `playback/EngineEventReducer.kt` | 把 LibVLC 原生事件归约为 `PlaybackState` |
| `playback/PlaybackInterruptions.kt` | 音频焦点、耳机断开与进程后台暂停 |
| `playback/AndroidVlcPlaybackEngine.kt` | LibVLC、Surface、音频焦点、耳机断开和资源释放 |
| `player/PlayerModels.kt` | 请求、UI 状态和用户动作 |
| `player/PlayerViewModel.kt` | 恢复、5 秒保存、失败后重解析一次 |
| `ui/player/PlayerFormatters.kt` | 播放时间格式化 |
| `ui/player/FullscreenController.kt` | 横屏沉浸、方向和系统栏恢复 |
| `ui/player/VlcSurface.kt` | `SurfaceView` 的 Compose 适配 |
| `ui/player/PlayerControls.kt` | 播放、暂停、seek 与进度控件 |
| `ui/player/VideoPlayerScreen.kt` | seek、缓冲、横屏沉浸与常亮 |
| `ui/player/AudioPlayerScreen.kt` | 前台音频控制 |
| `image/MediaImageLoaderFactory.kt` | Coil 内存缓存与禁用磁盘缓存 |
| `image/ImageViewerViewModel.kt` | 图片请求、人工重试与单次端点刷新 |
| `image/ZoomState.kt` | 缩放、拖动和双击复位 |
| `ui/image/ImageViewerScreen.kt` | 深色图片页、加载、失败和重试 |

### 2.5 测试、验收与交付

| 文件 | 责任 |
| --- | --- |
| `app/src/test/java/com/local/mediaviewer/**` | URL、DNS、Caddy、会话、浏览、Room 策略和 ViewModel JVM 测试 |
| `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt` | Compose 测试替身 |
| `app/src/androidTest/java/com/local/mediaviewer/testing/MediaFixtureFactory.kt` | 在模拟器上生成 PNG、WAV、MP4，不读取用户媒体 |
| `app/src/androidTest/java/com/local/mediaviewer/testing/MediaFixtureServer.kt` | MockWebServer 的 Range 目录与媒体响应 |
| `app/src/androidTest/java/com/local/mediaviewer/MediaFixtureServerTest.kt` | Caddy JSON 与 `206 Content-Range` 合同测试 |
| `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt` | 首页到目录和三类媒体页面 |
| `app/src/androidTest/java/com/local/mediaviewer/MediaPlaybackInstrumentedTest.kt` | LibVLC 播放、seek、旋转和恢复 |
| `app/src/androidTest/java/com/local/mediaviewer/RealServerSmokeTest.kt` | 真实服务器双根的应用内解析烟测 |
| `app/src/androidTest/java/com/local/mediaviewer/AppLaunchTest.kt` | API 36 安装启动与应用标签基线 |
| `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt` | 首页与设置纯 UI 行为 |
| `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt` | 目录列表行、面包屑与点击行为 |
| `app/src/androidTest/java/com/local/mediaviewer/LibVlcEngineCreationTest.kt` | x86_64 LibVLC 原生创建和释放 |
| `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt` | 视频与音频控件语义 |
| `app/src/androidTest/java/com/local/mediaviewer/ImageViewerScreenTest.kt` | 图片错误态与重试语义 |
| `scripts/Invoke-AndroidVerification.ps1` | 单测、Lint、仪器测试、构建、安装和启动 |
| `scripts/Write-ApkChecksum.ps1` | 复制 Debug APK 并生成 SHA-256 |
| `README.md` | 中文构建、使用和故障排查 |
| `THIRD_PARTY_NOTICES.md` | 第三方依赖及许可链接 |
| `docs/verification/2026-07-28-android-mediaviewer.md` | 实际命令、结果、设备和真实 Caddy 验收记录 |
| `dist/mediaviewer-debug.apk` | 最终 Debug APK，不提交 Git |
| `dist/mediaviewer-debug.apk.sha256` | 最终校验文件，不提交 Git |

## 3. 跨任务接口目录

后续文档必须保持以下签名一致：

```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    val userMessage: String
}

data class ServerConfig(
    val logicalBaseUrl: String = DEFAULT_SERVER_URL,
    val lastSuccessfulIpv4: String? = null,
) {
    companion object {
        const val DEFAULT_SERVER_URL = "http://192.168.1.17:8080"
    }
}

data class ValidatedServerUrl(
    val logicalBaseUrl: String,
    val host: String,
    val port: Int,
    val isIpv4Literal: Boolean,
)

data class SessionEndpoint(
    val logicalBaseUrl: String,
    val requestBaseUrl: String,
    val ipv4: String,
) {
    fun requestUrlFor(logicalUrl: String): String
}

enum class RootShare(
    val id: String,
    val displayName: String,
    val path: String,
) {
    MIDDLE("middle", "MiddleDir", "/middle/"),
    PIK("pik", "pik", "/pik/");

    companion object {
        fun fromId(id: String): RootShare
    }
}

enum class MediaKind {
    DIRECTORY,
    VIDEO,
    AUDIO,
    IMAGE,
    UNKNOWN,
}

data class DirectoryEntry(
    val name: String,
    val size: Long,
    val modifiedAt: Instant,
    val mode: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val logicalUrl: String,
    val requestUrl: String,
    val kind: MediaKind,
)

data class ConnectionTestResult(
    val server: ValidatedServerUrl,
    val resolvedIpv4s: List<String>,
    val endpoint: SessionEndpoint,
)

sealed interface ServerSessionState {
    data object Connecting : ServerSessionState
    data class Connected(
        val endpoint: SessionEndpoint,
        val resolvedIpv4s: List<String>,
    ) : ServerSessionState
    data class Failed(
        val error: AppError,
        val resolvedIpv4s: List<String>,
    ) : ServerSessionState
}

interface ServerSettingsRepository {
    val config: Flow<ServerConfig>
    suspend fun current(): ServerConfig
    suspend fun save(config: ServerConfig)
}

interface CaddyDirectoryClient {
    suspend fun listDirectory(
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>>
}

interface Ipv4Resolver {
    suspend fun resolve(host: String): AppResult<List<String>>
}

interface ConnectionProbe {
    suspend fun probe(
        server: ValidatedServerUrl,
        ipv4Candidates: List<String>,
    ): AppResult<ConnectionTestResult>
}

interface ServerSessionManager {
    val state: StateFlow<ServerSessionState>
    suspend fun connectSaved()
    suspend fun testCandidate(input: String): AppResult<ConnectionTestResult>
    suspend fun saveCandidate(result: ConnectionTestResult)
    suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint>
}

data class Breadcrumb(
    val label: String,
    val logicalUrl: String,
)

data class BrowserPage(
    val root: RootShare,
    val logicalDirectoryUrl: String,
    val requestDirectoryUrl: String,
    val breadcrumbs: List<Breadcrumb>,
    val entries: List<DirectoryEntry>,
)

interface BrowserRepository {
    suspend fun openRoot(root: RootShare): AppResult<BrowserPage>
    suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage>
}

interface PlaybackPositionStore {
    suspend fun resumePosition(mediaKey: String): Long?
    suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean = false,
    )
    suspend fun clear(mediaKey: String)
}

enum class PlaybackStatus {
    IDLE,
    OPENING,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Float = 0f,
    val isSeekable: Boolean = false,
    val errorMessage: String? = null,
)

interface PlaybackEngine : AutoCloseable {
    val state: StateFlow<PlaybackState>
    fun prepare(url: String)
    fun attachVideoSurface(surfaceView: SurfaceView)
    fun detachVideoSurface()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    override fun close()
}

fun interface PlaybackEngineFactory {
    fun create(): PlaybackEngine
}

interface AppContainer {
    val settingsRepository: ServerSettingsRepository
    val sessionManager: ServerSessionManager
    val browserRepository: BrowserRepository
    val playbackEngineFactory: PlaybackEngineFactory
    val playbackPositionStore: PlaybackPositionStore
    val imageLoader: ImageLoader
}
```

## 4. 任务清单

严格按顺序执行；每项完成后运行该文档的门禁并按详细文档提交。任务 01 至 12
各有一个提交，任务 13 先提交验收实现，再在干净修订上验收并提交实际记录。

- [ ] [任务 01：建立可构建的 Android 36 工程与测试基线](android-mediaviewer/01-project-baseline.md)
- [ ] [任务 02：实现服务器 URL 校验与 DataStore 设置存储](android-mediaviewer/02-server-settings.md)
- [ ] [任务 03：实现 Caddy JSON、URL 解析、媒体分类与目录客户端](android-mediaviewer/03-caddy-directory.md)
- [ ] [任务 04：实现 DNS IPv4 解析与双根目录连接探测](android-mediaviewer/04-ipv4-probe.md)
- [ ] [任务 05：实现服务器会话状态与失败后单次重解析](android-mediaviewer/05-server-session.md)
- [ ] [任务 06：实现目录浏览仓库与导航 ViewModel](android-mediaviewer/06-browser-domain.md)
- [ ] [任务 07：实现应用导航、首页与设置页](android-mediaviewer/07-app-home-settings.md)
- [ ] [任务 08：实现目录列表、面包屑与媒体路由](android-mediaviewer/08-browser-ui.md)
- [ ] [任务 09：实现 Room 播放位置存储与阈值策略](android-mediaviewer/09-playback-position.md)
- [ ] [任务 10：实现 LibVLC 播放引擎与 Android 生命周期集成](android-mediaviewer/10-libvlc-engine.md)
- [ ] [任务 11：实现视频、音频页面和断点续播协调](android-mediaviewer/11-player-ui.md)
- [ ] [任务 12：实现无磁盘缓存的图片查看器](android-mediaviewer/12-image-viewer.md)
- [ ] [任务 13：完成端到端测试、模拟器验收和 APK 交付](android-mediaviewer/13-verification-delivery.md)

## 5. 需求覆盖矩阵

| 已批准需求 | 实施任务 | 验证门 |
| --- | --- | --- |
| Android 10+、`mediaviewer`、`com.local.mediaviewer` | 01 | `MinimumApiContractTest`、`aapt dump badging` |
| 明文 HTTP、默认地址、固定双根 | 01、02、04 | Manifest/网络配置测试、URL 测试、双根探测测试 |
| DNS A、IPv4 字面量、多 A 首个可用、公私网均允许 | 02、04、05 | resolver/probe/session JVM 测试与真实烟测 |
| Caddy JSON、标准 URI、Unicode、嵌套目录和目录列表 | 03、06、08 | parser/client/browser/Compose 测试 |
| 原始 URL 与 HTTP Range，不整文件预下载 | 10、13 | MockWebServer `206` 与真实 LibVLC Range 计数 |
| 视频、音频、未知文件使用 LibVLC 与 `SurfaceView` | 03、10、11 | 分类测试、引擎测试、播放器仪器测试 |
| seek、全屏横屏、前台常亮、后台暂停 | 10、11、13 | reducer/UI/旋转/生命周期测试 |
| Room 断点续播与 5 秒、10 秒、95% 规则 | 09、11 | DAO、policy、store 与 PlayerViewModel 测试 |
| 图片缩放、拖动、双击复位、仅内存缓存 | 12 | ImageLoader、Zoom reducer 与 Compose 测试 |
| 中文错误、不记录目录或媒体正文 | 01、03、04、13 | 错误模型测试、代码审查与验收脚本输出审查 |
| 无后台播放、PiP、下载、投屏、TV、缩略图和写操作 | 全任务 | 依赖/Manifest/路由审查与最终范围检查 |
| Debug APK、SHA-256、中文 README、许可和验收记录 | 13 | 统一 PowerShell 验收脚本 |

## 6. 依赖顺序与审查门

```text
01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10 → 11 → 12 → 13
```

每项审查必须确认：

1. 该项列出的失败测试先观察到预期失败；
2. 仅实现让该项测试通过所需的最小行为；
3. 运行该项的精确 Gradle 任务；
4. 没有将逻辑 URL 替换为会话 IPv4；
5. 没有引入下载、磁盘媒体缓存、认证或地址过滤；
6. 工作树只包含当前任务列出的文件；
7. 使用该任务文档指定的提交信息提交。

最终全量门禁：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -AvdName 'Pixel_3a_API_36_extension_level_17_x86_64' `
  -RealServerBaseUrl 'http://192.168.1.17:8080'
```

预期结果：

```text
所有 JVM 测试通过
Android Lint 0 error
所有仪器测试通过
API 36 x86_64 模拟器安装并启动 com.local.mediaviewer
真实 /middle/ 与 /pik/ 返回可浏览内容或合法空目录
dist/mediaviewer-debug.apk 存在
dist/mediaviewer-debug.apk.sha256 与 Get-FileHash 结果一致
```
