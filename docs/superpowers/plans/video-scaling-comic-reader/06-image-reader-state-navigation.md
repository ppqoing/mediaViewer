# TODO 06 Image Reader State and Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让图片阅读状态从稳定的当前目录上下文加载全部图片，并管理默认模式、排序和逻辑 URL 锚点。

**Architecture:** 目录页媒体启动事件附带根 ID 和当前逻辑目录 URL；导航参数保持轻量。`ImageReaderViewModel` 消费共享目录仓库、阅读偏好和纯图片策略，UI 接线留到 TODO 07。

**Tech Stack:** Kotlin Serialization Navigation、ViewModel/StateFlow、Coroutines、JUnit。

## Global Constraints

- 导航参数不得包含整份图片列表或 Bitmap。
- 图片序列只能从当前逻辑目录重新加载。
- ViewModel 不持有 Compose、Coil 或 Android View 类型。
- 默认模式从 `ReaderPreferencesRepository` 读取。
- 初始排序固定 `NAME_ASC`。
- 模式和排序变化必须保持逻辑 URL 锚点。

## Files

- Create: `app/src/main/java/com/local/mediaviewer/image/ImageReaderViewModel.kt`
- Create: `app/src/test/java/com/local/mediaviewer/image/ImageReaderViewModelTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/browser/BrowserModels.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/browser/BrowserViewModel.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/navigation/Destinations.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt`

## Interfaces

- Expanded launch context:

```kotlin
data class MediaLaunchRequest(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
    val rootId: String,
    val directoryLogicalUrl: String,
)
```

- New route, initially added alongside the old route until TODO 07 wires UI:

```kotlin
@Serializable
data class ImageReaderRoute(
    val rootId: String,
    val directoryLogicalUrl: String,
    val selectedLogicalUrl: String,
    val selectedName: String,
)
```

- Reader state:

```kotlin
sealed interface ImageReaderUiState {
    data object Loading : ImageReaderUiState

    data class Content(
        val images: List<ImageReaderItem>,
        val mode: ImageReaderMode,
        val sortOrder: ImageSortOrder,
        val anchorLogicalUrl: String,
        val requestGeneration: Int = 0,
        val isRefreshingEndpoint: Boolean = false,
    ) : ImageReaderUiState

    data object Empty : ImageReaderUiState

    data class Error(
        val message: String,
    ) : ImageReaderUiState
}
```

- ViewModel:

```kotlin
class ImageReaderViewModel(
    private val directoryLogicalUrl: String,
    private val selectedLogicalUrl: String,
    private val contentRepository:
        DirectoryContentRepository,
    private val preferences:
        ReaderPreferencesRepository,
) : ViewModel {
    val uiState: StateFlow<ImageReaderUiState>
    fun retryDirectory()
    fun setMode(mode: ImageReaderMode)
    fun setSortOrder(order: ImageSortOrder)
    fun updateAnchor(logicalUrl: String)
}
```

## Steps

- [ ] **Step 1: Write failing browser launch-context test**

Extend the existing browser test:

```kotlin
assertEquals(RootShare.MIDDLE.id, launch.rootId)
assertEquals(subUrl, launch.directoryLogicalUrl)
assertEquals(video.logicalUrl, launch.mediaKey)
```

Add an image entry and assert its launch contains the same parent directory,
not the image URL.

- [ ] **Step 2: Run browser tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.browser.BrowserViewModelTest'
```

Expected: compilation fails because launch context fields do not exist.

- [ ] **Step 3: Add launch context**

In `BrowserViewModel.open()` obtain:

```kotlin
val current = pages.lastOrNull() ?: return
```

before branching on directory/file. For media emit:

```kotlin
MediaLaunchRequest(
    name = entry.name,
    logicalUrl = entry.logicalUrl,
    requestUrl = entry.requestUrl,
    mediaKey = entry.logicalUrl,
    kind = entry.kind,
    rootId = root.id,
    directoryLogicalUrl =
        current.logicalDirectoryUrl,
)
```

Do not change video/audio route behavior yet.

- [ ] **Step 4: Run browser tests**

Run the Step 2 command.

Expected: all pass.

- [ ] **Step 5: Write failing ImageReaderViewModel loading tests**

Create fakes for `DirectoryContentRepository` and
`ReaderPreferencesRepository`. Add:

```kotlin
@Test
fun `加载当前目录图片并定位点击项`() =
    runTest(dispatcher) {
        val repository = FakeDirectoryContentRepository(
            DirectoryContent(
                logicalDirectoryUrl = directoryUrl,
                requestDirectoryUrl = requestDirectoryUrl,
                entries = listOf(
                    entry("a.jpg", MediaKind.IMAGE),
                    entry("movie.mp4", MediaKind.VIDEO),
                    entry("b.png", MediaKind.IMAGE),
                ),
            ),
        )
        val viewModel = ImageReaderViewModel(
            directoryLogicalUrl = directoryUrl,
            selectedLogicalUrl = "${directoryUrl}b.png",
            contentRepository = repository,
            preferences = FakeReaderPreferences(
                ImageReaderMode.COMIC,
            ),
        )
        advanceUntilIdle()

        val content =
            viewModel.uiState.value as ImageReaderUiState.Content
        assertEquals(
            listOf("a.jpg", "b.png"),
            content.images.map(ImageReaderItem::name),
        )
        assertEquals(
            "${directoryUrl}b.png",
            content.anchorLogicalUrl,
        )
        assertEquals(ImageReaderMode.COMIC, content.mode)
    }
```

Add tests:

```text
默认设置为单图时初始单图
点击项消失时回退第一张
没有图片时进入 Empty
目录错误显示已有中文错误
重试目录替换错误状态
```

- [ ] **Step 6: Run ViewModel tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ImageReaderViewModelTest'
```

Expected: compilation fails for missing ViewModel and state.

- [ ] **Step 7: Implement initial loading**

Initialize with `Loading`. One private `loadJob` executes:

```kotlin
private fun load() {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
        mutableUiState.value = ImageReaderUiState.Loading
        val mode = preferences.currentDefaultMode()
        when (
            val result =
                contentRepository.load(directoryLogicalUrl)
        ) {
            is AppResult.Success -> {
                val images = ImageSequence.fromEntries(
                    result.value.entries,
                    ImageSortOrder.NAME_ASC,
                )
                val anchor = ImageSequence.anchorOrFirst(
                    images,
                    selectedLogicalUrl,
                )
                mutableUiState.value = if (anchor == null) {
                    ImageReaderUiState.Empty
                } else {
                    ImageReaderUiState.Content(
                        images = images,
                        mode = mode,
                        sortOrder =
                            ImageSortOrder.NAME_ASC,
                        anchorLogicalUrl = anchor,
                    )
                }
            }

            is AppResult.Failure -> {
                mutableUiState.value =
                    ImageReaderUiState.Error(
                        result.error.userMessage,
                    )
            }
        }
    }
}
```

`retryDirectory()` calls `load()`.

- [ ] **Step 8: Write failing state transition tests**

Add:

```kotlin
@Test
fun `切换排序和模式保持当前锚点`() =
    runTest(dispatcher) {
        val viewModel = populatedViewModel()
        advanceUntilIdle()
        viewModel.updateAnchor(logicalB)
        viewModel.setSortOrder(ImageSortOrder.SIZE_DESC)
        viewModel.setMode(ImageReaderMode.SINGLE)

        val content =
            viewModel.uiState.value as ImageReaderUiState.Content
        assertEquals(logicalB, content.anchorLogicalUrl)
        assertEquals(
            ImageReaderMode.SINGLE,
            content.mode,
        )
        assertEquals(
            ImageSortOrder.SIZE_DESC,
            content.sortOrder,
        )
    }
```

Also assert invalid anchor updates are ignored.

- [ ] **Step 9: Implement state transitions**

`setSortOrder()` sorts the existing image list only:

```kotlin
val content =
    mutableUiState.value as? ImageReaderUiState.Content
        ?: return
if (content.sortOrder == order) return
mutableUiState.value = content.copy(
    images = ImageSequence.sort(content.images, order),
    sortOrder = order,
)
```

`setMode()` and `updateAnchor()` are idempotent. `updateAnchor()` accepts only
logical URLs present in the current list.

- [ ] **Step 10: Add the serializable route**

Add `ImageReaderRoute` to `Destinations.kt` but keep the old `ImageRoute`
temporarily so the current single-image screen still compiles. TODO 07 will
replace the old route atomically with the new UI.

Add a serialization round-trip test to `BrowserScreenTest` or a new
`ImageReaderRouteTest` using special characters in logical URLs. Assert exact
string preservation.

- [ ] **Step 11: Run focused and browser regressions**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ImageReaderViewModelTest' `
  --tests 'com.local.mediaviewer.image.ImageSequenceTest' `
  --tests 'com.local.mediaviewer.browser.BrowserViewModelTest'
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest'
```

Expected: all pass; current image screen behavior is unchanged until TODO 07.

- [ ] **Step 12: Review and commit**

Run:

```powershell
git diff --check
git status --short
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: load image reader from directory context"
```

Confirm the commit contains no Bitmap/Painter fields and no large route list.
