# 空目录兼容与 GIF 动图浏览设计

日期：2026-08-02  
状态：已通过对话确认，待用户审阅文档

## 1. 背景

Android 应用当前存在两个图片与目录浏览问题：

1. 进入真实空目录时，页面显示“加载子目录失败 / 目录响应格式无效”，没有进入该目录。
2. `.gif` 文件虽然已被分类为图片，但图片查看器只能显示静态内容或加载失败，不能播放 GIF 动画。

本次采用方案 A：在目录协议解析边界兼容 Caddy 的合法空目录响应，并通过现有 Coil 图片加载器增加官方 GIF 解码能力。保留现有目录仓库、浏览状态机、图片阅读 ViewModel、单图分页、条漫缩放和图片缓存架构。

## 2. 已确认选择

- Caddy 返回 JSON `null` 或空数组 `[]` 时，都表示成功加载的空目录。
- 空目录必须真正进入目标路径，并更新当前页面及面包屑。
- 空目录页面中央文案使用“空文件夹”。
- 缺字段、非法时间、非法 URL、对象结构或非 JSON 正文仍属于“目录响应格式无效”，不能静默转换为空目录。
- GIF 支持复用现有 Coil 3.5.0 图片加载链路，引入同版本 `coil-gif` 扩展。
- GIF 在单图与条漫两种图片浏览模式中都按动图显示。
- 保留现有单图左右滑、放大后平移、条漫缩放不重新加载、失败重试和内存缓存行为。
- 只进行本次范围内的基础功能性审查和定向重验。

## 3. 根因

### 3.1 空目录

Caddy 的目录浏览实现先构造一个未初始化 `Items` 切片，再逐项追加目录内容。目录完全为空时，`Items` 保持 Go 的 `nil` 切片；Caddy 使用 JSON 编码器直接输出 `listing.Items`，因此响应正文是 `null`，不是 `[]`。

当前 `DefaultDirectoryJsonParser` 直接把正文反序列化为 `List<CaddyEntryDto>`。Kotlin 非空列表不能接收顶层 JSON `null`，解析因此返回 `AppError.InvalidDirectoryResponse`。下游仓库和 `BrowserViewModel` 实际已经能把成功的空列表映射为 `BrowserUiState.Empty`，错误发生在协议解析边界。

参考：

- [Caddy browse.go](https://github.com/caddyserver/caddy/blob/master/modules/caddyhttp/fileserver/browse.go)
- [Caddy browsetplcontext.go](https://github.com/caddyserver/caddy/blob/master/modules/caddyhttp/fileserver/browsetplcontext.go)

### 3.2 GIF 动图

`MediaClassifier` 已把 `gif` 扩展名分类为 `MediaKind.IMAGE`，单图和条漫页面也统一使用 `MediaImageLoaderFactory` 创建的 Coil `ImageLoader`。项目目前只依赖 `coil-compose` 和 `coil-network-okhttp`，没有引入 Coil 的 GIF 扩展，所以加载器不具备 GIF 动画解码器。

Coil 官方将 GIF 支持放在独立的 `coil-gif` Android 扩展中；项目最低系统版本为 API 29，可使用基于 Android `ImageDecoder` 的动画解码路径。

参考：

- [Coil GIF 官方文档](https://coil-kt.github.io/coil/gifs/)

## 4. 目标与非目标

### 4.1 目标

- 正确接受 Caddy 的 `null` 空目录响应，同时继续接受 `[]`。
- 成功进入空子目录，保留目标路径、标题、返回行为和面包屑。
- 在可用内容区域中央显示“空文件夹”，不显示错误条或重试按钮。
- 让网络 GIF 在单图和条漫模式中播放动画。
- GIF 继续使用现有请求 URL、尺寸约束、内存缓存、错误分类与手动重试机制。
- 用定向测试覆盖本次真实失败边界。

### 4.2 非目标

- 不把空字符串、HTML、JSON 对象或任意解析失败转换为空目录。
- 不修改 Caddy 服务端输出格式。
- 不重写目录仓库、浏览页面状态机或图片阅读导航。
- 不新增 GIF 播放控制按钮、逐帧控制、速度调整或导出功能。
- 不改变 GIF 文件原有循环元数据或强制统一循环次数。
- 不引入第二套图片网络、磁盘缓存或手势实现。
- 不为 GIF 使用 VLC 或视频播放器路由。

## 5. 目录响应设计

### 5.1 解析边界

`DefaultDirectoryJsonParser` 负责把顶层目录响应规范化：

```text
JSON []
  -> Success(emptyList())

JSON null
  -> Success(emptyList())

JSON [合法目录项...]
  -> Success(sortedEntries)

其他正文或数组内非法目录项
  -> Failure(InvalidDirectoryResponse)
```

兼容逻辑只作用于顶层 JSON `null`，不放宽数组项字段、时间、模式、URL 或类型校验。解析器返回统一的非空 Kotlin `List<DirectoryEntry>`，下游无需感知 Caddy 的 `null` 表示法。

### 5.2 页面状态与导航

成功的空列表继续按现有链路传递：

```text
Caddy 200 application/json + null
  -> DirectoryJsonParser: Success(emptyList())
  -> DirectoryContentRepository: Success(DirectoryContent)
  -> BrowserRepository: Success(BrowserPage)
  -> BrowserViewModel: BrowserUiState.Empty(page)
  -> BrowserScreen: 当前路径 + 居中“空文件夹”
```

目标 `BrowserPage` 必须加入页面栈。因此：

- 面包屑显示已经进入空目录；
- 系统返回或页面返回回到父目录；
- 不保留父目录作为错误页的 `previous` 内容；
- 不显示“加载子目录失败”“目录响应格式无效”或“重试”。

## 6. GIF 动图设计

### 6.1 依赖与解码器

在版本目录中增加与现有 Coil 版本一致的 `coil-gif` 库别名，并由 app 模块引用。`MediaImageLoaderFactory` 继续作为应用唯一图片加载器工厂。

加载器使用 Coil 官方动画解码组件。项目最低 API 为 29，因此首选 `AnimatedImageDecoder`；实现可以使用 Coil 3.5.0 的自动组件发现，也可以在工厂中显式注册，最终以“加载器中确实存在动画解码能力且测试可验证”为准。

### 6.2 现有 UI 复用

`SingleImageViewer` 和 `ComicReader` 已使用 `SubcomposeAsyncImage`。解码结果变为可动画 Drawable 后，继续通过 `SubcomposeAsyncImageContent()` 绘制，不新建 GIF 专用页面。

行为约束：

- 单图模式仍可左右滑切换 GIF 与相邻静态图片。
- 单图放大后仍只平移当前 GIF，不触发翻页。
- 条漫模式只为 Compose 当前保留的项目维护绘制与动画生命周期；离开组合的项目由现有 Compose/Coil 生命周期释放。
- 条漫双指缩放仍只改变显示变换，不让已显示 GIF 因缩放重新创建请求。
- 图片请求继续遵守 `ImageDecodePolicy.MAX_PIXELS` 和设备位图上限。
- 用户明确重试时继续通过请求代次生成新缓存键；普通重组不改变请求代次。
- GIF 加载失败继续复用现有图片错误面板和重试入口。

## 7. 测试设计

遵循 TDD，先增加失败测试，再实现最小改动。只重验本次新增或未通过的测试。

### 7.1 空目录测试

1. `DirectoryJsonParserTest`
   - `null` 返回 `Success(emptyList())`；
   - `[]` 仍返回 `Success(emptyList())`；
   - `{}`、空正文和非法数组项仍返回 `Failure(InvalidDirectoryResponse)`。
2. `CaddyDirectoryClientTest`
   - `200 application/json`、正文 `null` 经过完整客户端链路返回成功空列表。
3. `BrowserViewModelTest`
   - 从父目录点击空子目录后得到 `BrowserUiState.Empty`；
   - 当前页 URL 和面包屑属于子目录；
   - 返回能回到父目录。
4. `BrowserScreenTest`
   - 空状态中央显示“空文件夹”；
   - 不存在“加载子目录失败”和“目录响应格式无效”。

### 7.2 GIF 测试

1. 依赖/工厂测试确认 GIF 动画解码组件对应用加载器可用。
2. 使用最小真实 GIF fixture 执行图片加载测试，确认成功结果是可动画图片而不是静态首帧或错误结果。
3. 保留现有单图分页和条漫请求稳定性测试，确认加入解码器没有改变左右滑、缩放和缓存键策略。

若本地 JVM/Robolectric 无法可靠推进动画时钟，自动化门槛限定为“真实 GIF 被动画解码器成功解码为可动画结果”；逐帧变化作为 ARM64 设备基础功能验收，不把未运行的设备验收写成通过。

## 8. 基础功能验收

### 8.1 空目录

- 从有内容的父目录点击真实空子目录。
- 页面进入目标路径，中央显示“空文件夹”。
- 页面没有错误条和重试按钮。
- 返回后恢复父目录。
- 非空目录、只有子目录的目录和非法 JSON 仍保持原有正确行为。

### 8.2 GIF

- 打开网络 GIF 后能看到连续动画，不只显示第一帧。
- 左右滑能在 GIF 与相邻图片之间切换。
- 返回 GIF 时能重新正常显示和播放，不出现永久加载状态。
- 条漫中的可见 GIF 能播放；双指缩放不闪回占位图、不重复发起已显示项请求。
- 静态 JPG、PNG、WebP 等格式继续正常显示。

## 9. 影响范围

预计只涉及：

- `DefaultDirectoryJsonParser` 及其定向测试；
- 浏览空状态文案及其 UI 测试；
- Coil 版本目录和 app 依赖；
- `MediaImageLoaderFactory` 及 GIF 解码测试/fixture。

目录仓库、浏览 ViewModel、单图分页、条漫手势与播放器代码原则上不改；只有新增回归测试证明现有链路存在额外缺口时，才做相应最小修复。
