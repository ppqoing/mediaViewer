# 视频画面模式与条漫阅读设计

**日期：** 2026-07-28

**状态：** 已批准

**适用项目：** `mediaviewer` Android 应用

## 目标

在不破坏现有目录浏览、HTTP Range 播放、断点续播、单图查看、DNS/IPv4
刷新和服务器设置流程的前提下：

1. 修复视频画面缩在右下角、未正确使用可用显示区域的问题；
2. 为视频提供等比适应、裁剪铺满、强制拉伸和原始尺寸四种画面模式；
3. 为图片提供默认进入的条漫连续阅读方式；
4. 支持当前文件夹图片的多种排序、整流统一缩放和动态加载；
5. 在设置页配置默认看图方式；
6. 通过职责拆分为后续图片翻页、预取策略或视频显示扩展保留清晰边界。

## 已确认的产品决策

### 视频

- 每个新视频默认使用“等比适应”。
- 画面模式只在当前视频页面内有效。
- 旋转、全屏和视频 Surface 重建时保留当前选择。
- 离开视频后不持久化选择。
- 固定提供以下四种模式：
  - 等比适应；
  - 裁剪铺满；
  - 强制拉伸；
  - 原始尺寸。

### 图片

- 新安装默认进入条漫模式。
- 设置页可将默认方式改为单图模式。
- 阅读页可在条漫与单图之间临时切换；临时切换不修改默认设置。
- 条漫包含当前文件夹内的全部图片，不递归进入子文件夹。
- 初始定位到用户在目录页点击的图片。
- 点击图片之前的条目可向上浏览，之后的条目可向下浏览。
- 初始排序沿用现有文件名升序。
- 页面内可切换：
  - 文件名升序；
  - 文件名降序；
  - 修改时间升序；
  - 修改时间降序；
  - 文件大小升序；
  - 文件大小降序。
- 临时排序不持久化。
- 排序变化后保持当前主要可见图片为阅读锚点。
- 条漫默认按屏幕宽度等比显示。
- 整条图片流共享同一缩放倍率和水平偏移。
- 缩放范围沿用现有单图逻辑的 `1×–5×`。
- 图片必须动态加载，不能一次性下载或解码整个目录。

## 现状与问题

### 视频输出

当前 `VlcSurface` 创建裸 `SurfaceView`，再调用
`PlaybackEngine.attachVideoSurface(surfaceView)`。`AndroidVlcPlaybackEngine`
直接使用 `IVLCVout.setVideoView()` 和无监听器的 `attachViews()`。

项目实际固定使用的 LibVLC `4.0.0-eap29` 已包含：

- `VLCVideoLayout`；
- `MediaPlayer.attachViews(VLCVideoLayout, ...)`；
- `MediaPlayer.setVideoScale(ScaleType)`；
- `SURFACE_BEST_FIT`；
- `SURFACE_FIT_SCREEN`；
- `SURFACE_FILL`；
- `SURFACE_ORIGINAL`。

LibVLC 的 `MediaPlayer.updateVideoSurfaces()` 只有在内部 `VideoHelper` 已由
`MediaPlayer.attachViews(VLCVideoLayout, ...)` 建立时才会执行完整布局更新。
当前裸 Surface 路径没有建立这个帮助器，因此画面尺寸和位置不能由 LibVLC
布局层正确维护。

### 图片导航与状态

当前 `ImageRoute` 只携带一张图片的名称、逻辑 URL 和请求 URL；
`ImageViewerViewModel` 只管理单张图片的一次端点刷新。它不知道父目录，
无法可靠取得同目录图片序列。

直接把整份图片 URL 列表写进导航参数会带来以下问题：

- 大目录可能超过 Android 导航状态或 Bundle 的合理大小；
- 实际请求 URL 绑定本次解析出的 IPv4，刷新端点后整批参数会过期；
- 目录排序、图片错误和导航状态耦合；
- 进程重建时恢复成本过高。

因此导航只传稳定身份和目录上下文，阅读器进入后通过共享目录领域组件重新
加载轻量元数据。

## 总体架构

采用定向重构，不建立通用媒体播放列表。

```text
BrowserViewModel
      │
      ├── DirectoryContentRepository ── CaddyDirectoryClient
      │             │
      │             └── ServerSessionManager
      │
      └── ImageReaderRoute
                    │
                    ▼
             ImageReaderViewModel
               │            │
               │            └── ReaderPreferencesRepository
               ▼
        ImageReaderScreen
          ├── ComicReader
          └── SingleImageViewer

VideoPlayerScreen ── PlayerViewModel ── PlaybackEngine
                                            │
                                            ▼
                              AndroidVlcPlaybackEngine
                                            │
                                 VLCVideoLayout + LibVLC
```

第三方类型只允许存在于 Android/LibVLC 适配实现中。Compose 页面和
ViewModel 只消费项目定义的枚举、状态和接口。

## 目录内容边界

抽取目录读取与端点重试职责：

```kotlin
data class DirectoryContent(
    val logicalDirectoryUrl: String,
    val requestDirectoryUrl: String,
    val entries: List<DirectoryEntry>,
)

interface DirectoryContentRepository {
    suspend fun load(
        logicalDirectoryUrl: String,
    ): AppResult<DirectoryContent>
}
```

`DefaultDirectoryContentRepository`：

- 从 `ServerSessionManager` 取得当前 `SessionEndpoint`；
- 用逻辑目录 URL 生成本次请求 URL；
- 调用现有 `CaddyDirectoryClient`；
- 网络失败时调用一次 `refreshAfterRequestFailure()`；
- 使用刷新后的端点再试一次；
- 返回逻辑 URL、实际请求 URL 和已解析、已排序的 `DirectoryEntry`；
- 不持有页面面包屑或 Compose 状态。

`DefaultBrowserRepository` 改为消费 `DirectoryContentRepository`，继续负责
`RootShare`、面包屑和 `BrowserPage`。现有浏览行为和排序规则不变。

图片阅读器也消费同一仓库，仅过滤 `MediaKind.IMAGE`，不复制 Caddy、DNS 或
IPv4 重试逻辑。

## 视频画面模式

### 领域模型

新增项目枚举：

```kotlin
enum class VideoScaleMode {
    BEST_FIT,
    FILL_CROP,
    STRETCH,
    ORIGINAL,
}
```

中文显示文本由独立格式函数提供，领域枚举不存放 Android 资源或 LibVLC 类型。

### 播放引擎接口

`PlaybackEngine` 增加：

```kotlin
fun attachVideoOutput(host: ViewGroup)
fun detachVideoOutput()
fun setVideoScaleMode(mode: VideoScaleMode)
```

删除以裸 `SurfaceView` 为契约的 `attachVideoSurface()`。接口可以依赖 Android
基础视图容器，但不能暴露 `VLCVideoLayout` 或 `MediaPlayer.ScaleType`。

测试假实现只记录绑定状态和最后一次模式，不创建原生播放器。

### LibVLC 适配

`AndroidVlcPlaybackEngine` 在输出容器内创建并持有一个填满父容器的
`VLCVideoLayout`，通过：

```kotlin
mediaPlayer.attachViews(
    videoLayout,
    null,
    false,
    false,
)
```

完成视频输出绑定。模式映射固定为：

| 项目模式 | LibVLC 模式 | 用户语义 |
| --- | --- | --- |
| `BEST_FIT` | `SURFACE_BEST_FIT` | 等比完整显示，允许黑边 |
| `FILL_CROP` | `SURFACE_FIT_SCREEN` | 等比铺满，裁剪超出区域 |
| `STRETCH` | `SURFACE_FILL` | 忽略原比例，宽高铺满 |
| `ORIGINAL` | `SURFACE_ORIGINAL` | 原始输出尺寸 |

模式可能先于视图绑定设置。引擎必须保存最后一次模式，并在新布局绑定后再次
应用。`detachVideoOutput()` 只释放视图绑定，不停止媒体；`close()` 仍负责
停止和释放播放器。

### 播放器状态与界面

`PlayerUiState` 增加 `videoScaleMode`，默认 `BEST_FIT`。
`PlayerViewModel.setVideoScaleMode()` 同步更新 UI 状态并调用引擎。

普通播放器控制栏增加画面模式菜单；全屏状态保留精简模式按钮。模式切换：

- 不调用 `prepare()`；
- 不重新请求媒体；
- 不修改播放位置；
- 不影响音频页面；
- Activity 重建后由保留的 ViewModel 状态重新应用；
- 新的 `PlayerViewModel` 始终重新从 `BEST_FIT` 开始。

## 图片阅读导航

用新的路由替代单图专用参数：

```kotlin
@Serializable
data class ImageReaderRoute(
    val rootId: String,
    val directoryLogicalUrl: String,
    val selectedLogicalUrl: String,
    val selectedName: String,
)
```

`BrowserViewModel` 发出图片启动请求时附带当前 `BrowserPage` 的根 ID 和逻辑
目录 URL。路由不携带：

- 整份图片列表；
- Bitmap；
- 本次解析出的 IPv4；
- 整批实际请求 URL。

进程或 Activity 重建后，路由仍能用稳定逻辑 URL 重新加载目录。

## 图片阅读领域模型

### 阅读方式

```kotlin
enum class ImageReaderMode {
    COMIC,
    SINGLE,
}
```

### 排序

```kotlin
enum class ImageSortOrder {
    NAME_ASC,
    NAME_DESC,
    MODIFIED_ASC,
    MODIFIED_DESC,
    SIZE_ASC,
    SIZE_DESC,
}
```

所有排序都必须确定且稳定：

1. 先比较所选主字段；
2. 主字段相同时按不区分大小写的名称升序；
3. 名称仍相同时按原始名称升序；
4. 最后按逻辑 URL 升序。

文件名升序继续符合当前目录解析器的不区分大小写排序语义。

### UI 状态

```kotlin
sealed interface ImageReaderUiState {
    data object Loading : ImageReaderUiState

    data class Content(
        val images: List<ImageReaderItem>,
        val mode: ImageReaderMode,
        val sortOrder: ImageSortOrder,
        val anchorLogicalUrl: String,
        val requestGeneration: Int,
        val refreshingEndpoint: Boolean,
        val itemFailures: Map<String, ImageItemFailure>,
    ) : ImageReaderUiState

    data class Error(
        val message: String,
    ) : ImageReaderUiState
}
```

状态只保存元数据和轻量错误，不保存 Painter、Drawable 或 Bitmap。

`ImageReaderViewModel` 负责：

- 加载当前目录；
- 过滤图片；
- 读取默认阅读方式；
- 初始选择点击图片；
- 排序；
- 更新阅读锚点；
- 模式切换；
- 去重端点刷新；
- 重建全部实际请求 URL；
- 对目录错误和单项错误建模。

如果点击图片在刷新后的目录中已不存在，内容仍可显示，并以排序后的第一张为
锚点；若目录没有图片，则显示明确空状态。

## 默认看图方式

新增独立接口：

```kotlin
interface ReaderPreferencesRepository {
    val defaultMode: Flow<ImageReaderMode>
    suspend fun currentDefaultMode(): ImageReaderMode
    suspend fun setDefaultMode(mode: ImageReaderMode)
}
```

使用独立 Preferences DataStore 文件或独立封装，不向 `ServerConfig` 添加 UI
偏好字段。没有已保存键时返回 `COMIC`。

设置页新增“图片阅读”区域，允许选择“条漫”或“单图”。选择后立即保存。
服务器 URL 仍必须测试成功后才能保存；图片偏好不改变服务器按钮的启用条件，
也不触发服务器连接测试。

## 条漫布局与统一缩放

### 列表

`ComicReader` 使用 `LazyColumn`：

- `key` 使用逻辑 URL；
- 只创建可见项和 Compose 所需的邻近项；
- 不主动遍历并提交全目录图片请求；
- 图片之间不插入强制分页；
- 每张图片使用原始宽高比；
- `1×` 时图片宽度等于可用屏幕宽度；
- 黑色背景，无磁盘缓存。

初次内容可用后调用一次 `scrollToItem(selectedIndex)`。之后不通过
`LaunchedEffect` 反复抢夺用户滚动位置。

“当前图片”取可见区域占比最大的图片。以下操作以其逻辑 URL 为锚点：

- 条漫切换为单图；
- 排序变化；
- 目录刷新；
- Activity 重建后的页面恢复。

### 整流变换

新增纯状态组件：

```kotlin
data class ComicTransform(
    val scale: Float = 1f,
    val horizontalOffsetPx: Float = 0f,
)
```

所有条漫项读取同一个 `ComicTransform`。不能只在 `LazyColumn` 外层使用
`graphicsLayer` 放大，因为那不会扩大列表的实际测量高度，会导致放大后无法
滚动到真实底部。

正确方式是：

- 根据共享倍率改变每个图片项的实际布局宽度；
- 高度由图片固有宽高比等比计算；
- 列表因此获得正确的放大后总滚动范围；
- 共享水平偏移应用于每个图片项；
- 水平偏移按放大后的内容宽度与视口宽度钳制；
- `scale == 1f` 时水平偏移强制归零；
- 两指手势更新统一倍率和水平偏移；
- 单指纵向手势交给 `LazyColumn`；
- 放大后单指横向拖动只更新统一水平偏移；
- 手势方向在越过 touch slop 后锁定，减少斜向拖动冲突。

条漫变换在本次阅读页面内保留。切换到单图再返回条漫时恢复原倍率；离开阅读
页面后不持久化。

单图继续使用独立 `ZoomTransform`，保留双指缩放、拖动和双击复位。单图变换
不覆盖条漫变换。

## 动态加载与内存策略

### 请求生命周期

- 完整目录只保存图片元数据。
- 每个 `LazyColumn` 项在进入组合时创建 Coil 请求。
- 项离开组合后请求由 Coil/Compose 取消或释放。
- 不把 Bitmap、Drawable 或 Painter 保存到 ViewModel。
- 不进行整目录预加载。

### 解码尺寸

新增可单元测试的 `ImageDecodePolicy`：

- `1×` 以视口像素宽度作为目标解码宽度；
- 放大时提高目标宽度；
- 目标解码宽度最高为视口宽度的两倍；
- 目标解码高度最高为视口高度的四倍；
- 单张解码目标最多为 `4_194_304` 像素；
- 如果宽高目标超过像素上限，按相同比例同时缩小；
- 最终尺寸同时受设备安全最大位图尺寸约束；
- `2×–5×` 的额外视觉放大不继续增加解码目标；
- 保持原图宽高比；
- 不创建第二份磁盘副本。

这一策略优先保证滚动稳定和内存可控，而不是在 `5×` 时保持像素级原图清晰。

### 缓存

继续使用共享 `MediaImageLoaderFactory`：

- 磁盘缓存为 `null`；
- `diskCachePolicy` 为 `DISABLED`；
- 内存缓存最多使用应用可用内存的 20%；
- 网络缓存策略不创建 OkHttp 磁盘缓存；
- 逻辑 URL 和请求代数参与请求身份，端点刷新后不会错误复用失败请求。

20% 是缓存部分的明确上限，不代表整个进程的 Bitmap 硬上限；可见项仍会持有
活动图片。因此还必须同时依赖 LazyColumn 的惰性组合和单张
`4_194_304` 像素解码上限控制峰值。极端超长图片仍可能因设备解码能力失败；
失败必须局部显示，不允许引发整个阅读页面崩溃。

## 排序与锚点

排序是对已过滤的图片元数据进行的纯函数转换，不重新请求目录。

排序流程：

1. 记录当前主要可见图片的逻辑 URL；
2. 生成新排序列表；
3. 查找该逻辑 URL 的新索引；
4. 更新状态；
5. 列表在新数据提交后滚动到新索引；
6. 如果锚点已删除，则使用第一张图片。

空目录不会尝试滚动。

## 图片错误与端点刷新

### 目录错误

目录加载失败显示整页中文错误和重试按钮。重试重新调用
`DirectoryContentRepository.load()`。

### 单项错误

图片解码或请求失败时：

- 该项显示高度受控的行内错误块；
- 显示文件名和“重试此图”；
- 其他图片继续加载和滚动；
- 不移除失败项，避免列表位置跳变。

### 刷新去重

`ImageReaderViewModel` 在同一页面内维护：

- `refreshJob`；
- `automaticEndpointRefreshUsed`；
- `requestGeneration`。

首次可重试的网络失败：

1. 如果刷新任务已运行，不再创建第二个；
2. 调用 `ServerSessionManager.refreshAfterRequestFailure()`；
3. 成功后用新的 `SessionEndpoint` 为所有逻辑 URL 重建请求 URL；
4. `requestGeneration` 加一；
5. 清除可重试网络错误；
6. 每个可见项按新请求身份重试。

自动刷新最多一次。之后失败只提供人工单项重试，防止无限循环和多图片刷新
风暴。明确的图片解码错误不触发 DNS 刷新。

## 生命周期与并发

- 目录加载、默认设置读取和端点刷新运行在 ViewModel scope。
- 同一时刻只有一个目录加载任务和一个端点刷新任务。
- 新排序不启动网络请求。
- 离开页面自动取消 ViewModel 中未完成任务。
- Coil 项请求跟随组合生命周期。
- 视频输出绑定和 LibVLC 布局操作在主线程执行。
- 播放引擎仍保持单实例约束。
- 音频页面不创建视频输出容器，也不显示画面模式入口。

## 兼容与迁移

- 不修改 Room 播放位置表，不需要数据库迁移。
- 不修改 `ServerConfig` 的已保存结构。
- 新图片偏好键缺失时使用 `COMIC`，旧安装可直接升级。
- 现有播放位置的逻辑媒体键不变。
- Caddy JSON、HTTP Range、DNS A 记录和 IPv4 选择契约不变。
- 默认服务器地址和两个固定根目录不变。
- 仍支持 Android 10 / API 29 及以上。

## 测试策略

### JVM 与 Robolectric

- 四种 `VideoScaleMode` 的顺序、显示文本和默认值；
- `PlayerViewModel` 模式切换调用引擎但不调用 `prepare()`；
- 新播放器恢复 `BEST_FIT`；
- `DirectoryContentRepository` 首次请求、网络刷新和单次重试；
- 浏览器重构后的页面、面包屑和返回行为；
- 六种图片排序和确定性平局规则；
- 初始锚点、排序后锚点和缺失锚点回退；
- `ComicTransform` 的 `1×–5×` 钳制和水平边界；
- `ImageDecodePolicy` 的屏宽、宽高倍率上限、`4_194_304` 像素上限和极值；
- 默认 `COMIC`、保存 `SINGLE` 和服务器设置互不影响；
- 多个并发图片错误只触发一次端点刷新；
- 解码错误不触发端点刷新。

### Compose 设备测试

- 视频模式菜单显示四个中文选项；
- 普通和全屏均可切换模式；
- 默认进入条漫；
- 设置默认单图后，新图片页面进入单图；
- 条漫初始定位点击图片；
- 可滚动到点击图片之前和之后的条目；
- 六种排序切换后保持锚点；
- 条漫统一缩放影响所有可见项；
- 单图与条漫切换保留当前图片；
- 单图原有双指缩放、拖动和双击复位继续通过；
- 单项失败不遮挡其他图片。

### 原生与集成测试

- 使用项目内生成的 4:3 H.264 MP4；
- `VLCVideoLayout` 填满 Compose 提供的输出容器；
- 视频内容在等比模式居中，不再停留右下角；
- 四种 LibVLC 模式可以切换；
- Surface 重建后重新应用模式；
- 切换模式不重启媒体且不丢失 seek 位置；
- 构造至少 50 张图片的 MockWebServer 目录；
- 首屏请求数量显著小于目录图片总数；
- 滚动后才请求后续图片；
- 端点刷新更新全部请求 URL；
- Coil 磁盘缓存仍为空，缓存上限仍为 20%，单张解码目标不超过
  `4_194_304` 像素。

### 最终门禁

- `testDebugUnitTest`；
- `lintDebug`，0 error；
- `assembleDebug`；
- API 36 x86_64 上的全部 `connectedDebugAndroidTest`；
- 真实 `/middle/` 与 `/pik/` 仅做 HTTP 200 和 Caddy JSON 解析冒烟；
- APK 安装、冷启动和进程存活；
- 包名、minSdk、targetSdk 和四种 LibVLC ABI；
- 重新生成 Debug APK 与 SHA-256；
- 更新中文 README、第三方说明（仅依赖变化时）和验收记录。

真实服务器验收不读取媒体正文，不记录真实目录条目名称。视频与图片行为使用
项目内生成夹具验证。

## 验收标准

1. 视频输出容器占满播放器可用区域，画面不再缩在右下角。
2. 四种视频模式的视觉语义符合定义。
3. 当前视频模式在旋转、全屏和 Surface 重建后保持。
4. 新视频重新使用等比适应。
5. 新安装点击图片默认进入条漫。
6. 设置为单图后，后续新页面默认进入单图。
7. 条漫包含当前文件夹全部图片且不包含子目录内容。
8. 初始位置是点击图片，前后图片均可访问。
9. 六种排序正确，切换后当前图片不丢失。
10. 整条图片流统一 `1×–5×` 等比缩放，默认铺满屏宽。
11. 大目录不会在首屏请求全部图片。
12. Bitmap 不进入 ViewModel，磁盘缓存保持禁用，缓存上限不超过 20%，单张
    解码目标不超过 `4_194_304` 像素。
13. 单图、浏览器、音频、播放位置、服务器设置和 IPv4 刷新回归测试通过。
14. 所有错误以简体中文呈现，单张图片失败不使页面崩溃。
15. 完整自动门禁、真实目录 JSON 冒烟和 APK 验收全部通过。

## 非目标

- 递归合并子文件夹图片；
- 条漫阅读进度持久化；
- 临时排序方式持久化；
- 图片下载、离线缓存或磁盘缓存；
- 超大图片分块/瓦片解码；
- 自定义视频宽高比；
- 字幕缩放或字幕位置控制；
- 视频缩放手势；
- 视频连播、图片播放列表或跨媒体队列；
- 后台播放、画中画、投屏或 Android TV；
- 修改、上传、删除或重命名服务端文件。
