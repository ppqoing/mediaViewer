# TODO 05 Image Reader Core Policies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用纯 Kotlin、可单元测试的类型实现图片筛选、六种排序、稳定锚点、统一条漫缩放和解码尺寸上限。

**Architecture:** 所有策略都是不依赖 Compose、Coil、网络和 ViewModel 的纯函数。UI 只提交输入并消费结果，后续可独立更换手势或图片加载实现。

**Tech Stack:** Kotlin、`java.time.Instant`、JUnit 4。

## Global Constraints

- 只接受 `MediaKind.IMAGE`。
- 初始排序为 `NAME_ASC`。
- 六种排序必须确定且稳定。
- 锚点使用逻辑 URL。
- 条漫缩放范围固定 `1f..5f`。
- `1f` 时水平偏移必须为零。
- 解码目标宽度最多视口两倍，高度最多视口四倍，总像素最多 `4_194_304`。

## Files

- Create: `app/src/main/java/com/local/mediaviewer/image/ImageReaderModels.kt`
- Create: `app/src/main/java/com/local/mediaviewer/image/ImageSequence.kt`
- Create: `app/src/main/java/com/local/mediaviewer/image/ComicTransform.kt`
- Create: `app/src/main/java/com/local/mediaviewer/image/ImageDecodePolicy.kt`
- Create: `app/src/test/java/com/local/mediaviewer/image/ImageSequenceTest.kt`
- Create: `app/src/test/java/com/local/mediaviewer/image/ComicTransformTest.kt`
- Create: `app/src/test/java/com/local/mediaviewer/image/ImageDecodePolicyTest.kt`

## Interfaces

- Produces:

```kotlin
enum class ImageSortOrder {
    NAME_ASC,
    NAME_DESC,
    MODIFIED_ASC,
    MODIFIED_DESC,
    SIZE_ASC,
    SIZE_DESC,
}

data class ImageReaderItem(
    val name: String,
    val size: Long,
    val modifiedAt: Instant,
    val logicalUrl: String,
    val requestUrl: String,
)

object ImageSequence {
    fun fromEntries(
        entries: List<DirectoryEntry>,
        order: ImageSortOrder,
    ): List<ImageReaderItem>

    fun sort(
        items: List<ImageReaderItem>,
        order: ImageSortOrder,
    ): List<ImageReaderItem>

    fun anchorOrFirst(
        items: List<ImageReaderItem>,
        requestedLogicalUrl: String,
    ): String?
}
```

```kotlin
data class ComicTransform(
    val scale: Float = 1f,
    val horizontalOffsetPx: Float = 0f,
)

object ComicTransformReducer {
    fun gesture(
        current: ComicTransform,
        zoomChange: Float,
        panXPx: Float,
        viewportWidthPx: Float,
    ): ComicTransform

    fun clamp(
        current: ComicTransform,
        viewportWidthPx: Float,
    ): ComicTransform

    fun reset(): ComicTransform
}
```

```kotlin
data class ImageDecodeSize(
    val widthPx: Int,
    val heightPx: Int,
)

object ImageDecodePolicy {
    const val MAX_PIXELS = 4_194_304L

    fun target(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        scale: Float,
    ): ImageDecodeSize
}
```

## Steps

- [ ] **Step 1: Write failing sequence tests**

Create entries with case differences, equal timestamps and equal sizes.

```kotlin
@Test
fun `只保留图片并默认名称升序`() {
    val entries = listOf(
        entry("10.jpg", MediaKind.IMAGE),
        entry("folder", MediaKind.DIRECTORY),
        entry("2.png", MediaKind.IMAGE),
        entry("movie.mp4", MediaKind.VIDEO),
    )

    assertEquals(
        listOf("10.jpg", "2.png"),
        ImageSequence.fromEntries(
            entries,
            ImageSortOrder.NAME_ASC,
        ).map(ImageReaderItem::name),
    )
}
```

Add one assertion for every enum:

```kotlin
assertEquals(
    listOf("c.jpg", "b.jpg", "a.jpg"),
    ImageSequence.sort(items, ImageSortOrder.NAME_DESC)
        .map(ImageReaderItem::name),
)
```

For modified time and size ties, assert name then exact name then logical URL
produces deterministic results.

Add anchor tests:

```kotlin
assertEquals(
    "logical-b",
    ImageSequence.anchorOrFirst(items, "logical-b"),
)
assertEquals(
    "logical-a",
    ImageSequence.anchorOrFirst(items, "missing"),
)
assertNull(
    ImageSequence.anchorOrFirst(emptyList(), "missing"),
)
```

- [ ] **Step 2: Run sequence tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ImageSequenceTest'
```

Expected: compilation fails for missing reader models and policy.

- [ ] **Step 3: Implement image item conversion and comparators**

Use one deterministic tie-breaker:

Use explicit comparators so descending name order does not depend on an
unavailable `compareByDescending` comparator overload:

```kotlin
private fun compareNames(
    left: ImageReaderItem,
    right: ImageReaderItem,
    descending: Boolean,
): Int {
    val first = if (descending) right else left
    val second = if (descending) left else right
    return String.CASE_INSENSITIVE_ORDER
        .compare(first.name, second.name)
        .takeIf { it != 0 }
        ?: first.name.compareTo(second.name)
            .takeIf { it != 0 }
        ?: left.logicalUrl.compareTo(right.logicalUrl)
}

private val nameAscending =
    Comparator<ImageReaderItem> { left, right ->
        compareNames(left, right, descending = false)
    }

private fun comparator(
    order: ImageSortOrder,
): Comparator<ImageReaderItem> =
    when (order) {
        ImageSortOrder.NAME_ASC -> nameAscending
        ImageSortOrder.NAME_DESC ->
            Comparator { left, right ->
                compareNames(left, right, descending = true)
            }
        ImageSortOrder.MODIFIED_ASC ->
            compareBy<ImageReaderItem>(
                ImageReaderItem::modifiedAt,
            ).then(nameAscending)
        ImageSortOrder.MODIFIED_DESC ->
            compareByDescending<ImageReaderItem>(
                ImageReaderItem::modifiedAt,
            ).then(nameAscending)
        ImageSortOrder.SIZE_ASC ->
            compareBy<ImageReaderItem>(
                ImageReaderItem::size,
            ).then(nameAscending)
        ImageSortOrder.SIZE_DESC ->
            compareByDescending<ImageReaderItem>(
                ImageReaderItem::size,
            ).then(nameAscending)
    }
```

Do not use locale-sensitive collation.

- [ ] **Step 4: Run sequence tests**

Run the Step 2 command.

Expected: all filter, sort, tie and anchor tests pass.

- [ ] **Step 5: Write failing transform tests**

```kotlin
@Test
fun `统一缩放限制一到五倍并钳制水平偏移`() {
    val zoomed = ComicTransformReducer.gesture(
        current = ComicTransform(),
        zoomChange = 3f,
        panXPx = 2_000f,
        viewportWidthPx = 1_000f,
    )

    assertEquals(3f, zoomed.scale)
    assertEquals(1_000f, zoomed.horizontalOffsetPx)

    val resetByZoom = ComicTransformReducer.gesture(
        current = zoomed,
        zoomChange = 0.01f,
        panXPx = 500f,
        viewportWidthPx = 1_000f,
    )
    assertEquals(ComicTransform(), resetByZoom)
}
```

Add tests for negative offset, changed viewport and explicit reset.

- [ ] **Step 6: Implement `ComicTransformReducer`**

Use:

```kotlin
private fun maxOffset(
    scale: Float,
    viewportWidthPx: Float,
): Float =
    (viewportWidthPx * (scale - 1f) / 2f)
        .coerceAtLeast(0f)
```

`gesture()` multiplies the current scale, clamps `1f..5f`, adds horizontal
pan, then delegates to `clamp()`. `clamp()` returns `ComicTransform()` at
`scale <= 1f`; otherwise clamps offset to `-maxOffset..maxOffset`.

- [ ] **Step 7: Run transform tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ComicTransformTest'
```

Expected: all pass.

- [ ] **Step 8: Write failing decode policy tests**

```kotlin
@Test
fun `一倍使用视口宽且五倍仍限制两倍宽`() {
    assertEquals(
        ImageDecodeSize(1080, 3600),
        ImageDecodePolicy.target(
            viewportWidthPx = 1080,
            viewportHeightPx = 900,
            scale = 1f,
        ),
    )

    val zoomed = ImageDecodePolicy.target(
        viewportWidthPx = 1080,
        viewportHeightPx = 1000,
        scale = 5f,
    )
    assertTrue(zoomed.widthPx <= 2160)
    assertTrue(zoomed.heightPx <= 4000)
    assertTrue(
        zoomed.widthPx.toLong() * zoomed.heightPx <=
            ImageDecodePolicy.MAX_PIXELS,
    )
}
```

Also test zero/negative viewport inputs are coerced to one pixel and never
overflow `Long`.

- [ ] **Step 9: Implement decode policy**

Algorithm:

```kotlin
val safeWidth = viewportWidthPx.coerceAtLeast(1)
val safeHeight = viewportHeightPx.coerceAtLeast(1)
val boundedScale = scale.coerceIn(1f, 2f)
val rawWidth =
    (safeWidth * boundedScale).roundToInt().coerceAtLeast(1)
val rawHeight =
    safeHeight.toLong().times(4L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
val rawPixels = rawWidth.toLong() * rawHeight.toLong()
val shrink = if (rawPixels > MAX_PIXELS) {
    sqrt(MAX_PIXELS.toDouble() / rawPixels.toDouble())
} else {
    1.0
}
return ImageDecodeSize(
    widthPx = (rawWidth * shrink).roundToInt()
        .coerceAtLeast(1),
    heightPx = (rawHeight * shrink).roundToInt()
        .coerceAtLeast(1),
)
```

Do not reference `Bitmap`, `Context`, or Coil in this file.

- [ ] **Step 10: Run all core tests and regression**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ImageSequenceTest' `
  --tests 'com.local.mediaviewer.image.ComicTransformTest' `
  --tests 'com.local.mediaviewer.image.ImageDecodePolicyTest' `
  --tests 'com.local.mediaviewer.image.ZoomStateTest'
```

Expected: all pass, including the original single-image zoom reducer.

- [ ] **Step 11: Review and commit**

Run:

```powershell
git diff --check
git status --short
git add app/src/main/java/com/local/mediaviewer/image `
  app/src/test/java/com/local/mediaviewer/image
git commit -m "feat: add image reader policies"
```

Confirm this commit has no Compose, network or navigation changes.
