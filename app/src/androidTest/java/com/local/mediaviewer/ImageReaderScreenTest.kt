package com.local.mediaviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.local.mediaviewer.image.ImageItemFailure
import com.local.mediaviewer.image.ImageLoadFailureKind
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.image.ImageReaderUiState
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.ui.image.ComicHorizontalOffsetSemanticsKey
import com.local.mediaviewer.ui.image.ComicScaleSemanticsKey
import com.local.mediaviewer.ui.image.ImageItemErrorPanel
import com.local.mediaviewer.ui.image.ImageReaderScreen
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import java.time.Instant
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ImageReaderScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var loader: ImageLoader

    @Before
    fun setUp() {
        loader = MediaImageLoaderFactory.create(
            ApplicationProvider.getApplicationContext(),
        )
    }

    @After
    fun tearDown() {
        loader.shutdown()
    }

    @Test
    fun toolbarSwitchesReaderModeAndSortOrder() {
        var selectedMode: ImageReaderMode? = null
        var selectedSort: ImageSortOrder? = null
        setScreen(
            state = contentState(),
            onModeChanged = { selectedMode = it },
            onSortChanged = { selectedSort = it },
        )

        rule.onNodeWithText("b.png").assertIsDisplayed()
        rule.onNodeWithTag("reader_mode_toggle")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "条漫",
                ),
            )
            .performClick()
        rule.runOnIdle {
            assertEquals(
                ImageReaderMode.SINGLE,
                selectedMode,
            )
        }

        rule.onNodeWithTag("image_sort_menu")
            .performClick()
        rule.onNodeWithText("文件名升序")
            .assertIsSelected()
        rule.onNodeWithText("修改时间降序")
            .assertIsDisplayed()
        rule.onNodeWithText("文件大小降序")
            .performClick()
        rule.runOnIdle {
            assertEquals(
                ImageSortOrder.SIZE_DESC,
                selectedSort,
            )
        }
    }

    @Test
    fun loadingStateUsesChineseText() {
        setScreen(ImageReaderUiState.Loading)
        rule.onNodeWithText("正在加载图片…")
            .assertIsDisplayed()
        rule.onNodeWithTag("image_reader_state_loading")
            .assertIsDisplayed()
        val pixels = rule.onNodeWithTag("image_reader_canvas")
            .captureToImage()
            .toPixelMap()
        assertEquals(
            Color.Black,
            pixels[2, pixels.height - 3],
        )
    }

    @Test
    fun emptyStateUsesChineseText() {
        setScreen(ImageReaderUiState.Empty)
        rule.onNodeWithText("此目录没有图片")
            .assertIsDisplayed()
        rule.onNodeWithTag("image_reader_state_empty")
            .assertIsDisplayed()
    }

    @Test
    fun directoryErrorUsesChineseRetry() {
        var retries = 0
        setScreen(
            state = ImageReaderUiState.Error(
                "服务器返回 HTTP 503",
            ),
            onRetryDirectory = { retries += 1 },
        )
        rule.onNodeWithText("服务器返回 HTTP 503")
            .assertIsDisplayed()
        rule.onNodeWithTag("image_reader_state_error")
            .assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle {
            assertEquals(1, retries)
        }
    }

    @Test
    fun comicModeUsesLazyReader() {
        setScreen(contentState(ImageReaderMode.COMIC))
        rule.onNodeWithTag("comic_reader")
            .assertIsDisplayed()
        rule.onNodeWithTag("media_image")
            .assertIsNotDisplayed()
    }

    @Test
    fun singleModeUsesSingleReader() {
        setScreen(contentState(ImageReaderMode.SINGLE))
        rule.onNodeWithTag("media_image")
            .assertIsDisplayed()
    }

    @Test
    fun singleImageShowsEdgeNavigationZoomToolbarAndModeSegments() {
        setScreen(contentState(ImageReaderMode.SINGLE))

        rule.onNodeWithContentDescription("上一张")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("下一张")
            .assertIsDisplayed()
        rule.onNodeWithTag("image_zoom_toolbar")
            .assertIsDisplayed()
        rule.onNodeWithTag("image_reader_modes")
            .assertIsDisplayed()
        rule.onNodeWithTag("segment_image")
            .assertIsSelected()
        rule.onNodeWithTag("segment_gif")
            .assertDoesNotExist()
    }

    @Test
    fun comicModeDoesNotShowSingleImageEdgeNavigation() {
        setScreen(contentState(ImageReaderMode.COMIC))

        rule.onNodeWithContentDescription("上一张")
            .assertDoesNotExist()
        rule.onNodeWithContentDescription("下一张")
            .assertDoesNotExist()
    }

    @Test
    fun controlsStayInsideDisplayCutoutSafeArea() {
        setScreen(
            state = contentState(ImageReaderMode.SINGLE),
            safeDrawingInsets = WindowInsets(
                left = 14.dp,
                top = 48.dp,
                right = 18.dp,
                bottom = 28.dp,
            ),
        )

        val root = rule.onNodeWithTag("image_reader_root")
            .fetchSemanticsNode().boundsInRoot
        val controls = rule
            .onNodeWithTag("image_reader_controls")
            .fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            assertTrue(
                controls.left >= root.left + 14.dp.toPx(),
            )
            assertTrue(
                controls.right <= root.right - 18.dp.toPx(),
            )
            assertTrue(
                controls.top >= root.top + 48.dp.toPx(),
            )
            assertTrue(
                controls.bottom <= root.bottom - 28.dp.toPx(),
            )
        }
    }

    @Test
    fun zoomCommandIsConsumedAndNewPageReportsItsOwnScale() {
        rule.setContent {
            var state by remember {
                mutableStateOf(
                    contentState(ImageReaderMode.SINGLE),
                )
            }
            MediaViewerTheme(darkTheme = true) {
                ImageReaderScreen(
                    state = state,
                    imageLoader = loader,
                    onModeChanged = {
                        state = state.copy(mode = it)
                    },
                    onSortChanged = {},
                    onAnchorChanged = {
                        state = state.copy(
                            anchorLogicalUrl = it,
                        )
                    },
                    onRetryDirectory = {},
                    onImageLoadError = { _, _ -> },
                    onImageLoadSuccess = {},
                    onRetryImage = {},
                    onBack = {},
                    safeDrawingInsets = WindowInsets(0),
                )
            }
        }

        rule.onNodeWithContentDescription("放大")
            .performClick()
        rule.onNodeWithText("125%").assertIsDisplayed()
        rule.onNodeWithContentDescription("下一张")
            .performClick()
        rule.onNodeWithText("100%").assertIsDisplayed()

        rule.onNodeWithTag("reader_mode_toggle")
            .performClick()
        rule.onNodeWithTag("reader_mode_toggle")
            .performClick()
        rule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun gifSegmentNavigatesOnlyWhenGifContentIsAvailable() {
        val base = contentState(ImageReaderMode.SINGLE)
        val gif = base.images.last().copy(name = "c.gif")
        var anchor = base.anchorLogicalUrl
        setScreen(
            state = base.copy(
                images = base.images.dropLast(1) + gif,
            ),
            onAnchorChanged = { anchor = it },
        )

        rule.onNodeWithTag("segment_gif")
            .assertIsDisplayed()
            .performClick()
        rule.runOnIdle {
            assertEquals(gif.logicalUrl, anchor)
        }
    }

    @Test
    fun modeSegmentsRemainReachableAt320DpWithTwoXFont() {
        val base = contentState(ImageReaderMode.SINGLE)
        val state = base.copy(
            images = base.images.mapIndexed { index, item ->
                if (index == 2) {
                    item.copy(name = "c.gif")
                } else {
                    item
                }
            },
        )
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 2f),
            ) {
                Box(
                    Modifier
                        .size(320.dp, 568.dp)
                        .testTag("compact_reader_window"),
                ) {
                    MediaViewerTheme(darkTheme = true) {
                        ImageReaderScreen(
                            state = state,
                            imageLoader = loader,
                            onModeChanged = {},
                            onSortChanged = {},
                            onAnchorChanged = {},
                            onRetryDirectory = {},
                            onImageLoadError = { _, _ -> },
                            onImageLoadSuccess = {},
                            onRetryImage = {},
                            onBack = {},
                            safeDrawingInsets = WindowInsets(0),
                        )
                    }
                }
            }
        }

        val window = rule
            .onNodeWithTag("compact_reader_window")
            .fetchSemanticsNode().boundsInRoot
        val modes = rule.onNodeWithTag("image_reader_modes")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(modes.left >= window.left)
        assertTrue(modes.right <= window.right)
        rule.onNodeWithTag("segment_comic")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun singleImageSwipesLeftToNextAndRightToPrevious() {
        val anchors = mutableListOf<String>()
        setScreen(
            state = contentState(ImageReaderMode.SINGLE),
            onAnchorChanged = anchors::add,
        )

        rule.onNodeWithTag("single_image_pager")
            .performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(
            "http://media.example/pik/c.webp",
            anchors.last(),
        )

        rule.onNodeWithTag("single_image_pager")
            .performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(
            "http://media.example/pik/b.png",
            anchors.last(),
        )
    }

    @Test
    fun singleImagePagerStopsAtSequenceEnd() {
        val base = contentState(ImageReaderMode.SINGLE)
        val lastAnchors = mutableListOf<String>()
        setScreen(
            state = base.copy(
                anchorLogicalUrl =
                    base.images.last().logicalUrl,
            ),
            onAnchorChanged = lastAnchors::add,
        )
        rule.onNodeWithTag("single_image_pager")
            .performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(
            base.images.last().logicalUrl,
            lastAnchors.last(),
        )
    }

    @Test
    fun singleImagePagerStopsAtSequenceStart() {
        val base = contentState(ImageReaderMode.SINGLE)
        val firstAnchors = mutableListOf<String>()
        setScreen(
            state = base.copy(
                anchorLogicalUrl =
                    base.images.first().logicalUrl,
            ),
            onAnchorChanged = firstAnchors::add,
        )
        rule.onNodeWithTag("single_image_pager")
            .performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(
            base.images.first().logicalUrl,
            firstAnchors.last(),
        )
    }

    @Test
    fun zoomedSingleImagePansWithoutPagingUntilDoubleTapReset() {
        val anchors = mutableListOf<String>()
        setScreen(
            state = contentState(ImageReaderMode.SINGLE),
            onAnchorChanged = anchors::add,
        )
        zoomSingleImage()

        rule.onNodeWithTag("single_image_pager")
            .performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(
            "http://media.example/pik/b.png",
            anchors.last(),
        )

        rule.onNodeWithTag("media_image")
            .performTouchInput { doubleClick() }
        rule.onNodeWithTag("single_image_pager")
            .performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(
            "http://media.example/pik/c.webp",
            anchors.last(),
        )
    }

    @Test
    fun tapTogglesImmersiveToolbar() {
        setScreen(contentState(ImageReaderMode.SINGLE))
        rule.onNodeWithTag("image_reader_scrim").assertIsDisplayed()
        rule.onNodeWithText("2 / 3").assertIsDisplayed()

        // 规格 §8.4：轻触切换顶部工具栏。
        rule.onNodeWithTag("media_image").performTouchInput { click() }
        // 注册了双击手势时单击在双击超时窗口后生效。
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()

        rule.onNodeWithTag("image_reader_scrim").assertDoesNotExist()
        rule.onNodeWithText("2 / 3").assertDoesNotExist()

        rule.onNodeWithTag("media_image").performTouchInput { click() }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()

        rule.onNodeWithTag("image_reader_scrim").assertIsDisplayed()
        rule.onNodeWithText("2 / 3").assertIsDisplayed()
    }

    @Test
    fun comicTapTogglesImmersiveToolbar() {
        setScreen(contentState(ImageReaderMode.COMIC))
        rule.onNodeWithTag("image_reader_scrim").assertIsDisplayed()

        rule.onNodeWithTag("comic_reader").performTouchInput { click() }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()

        rule.onNodeWithTag("image_reader_scrim").assertDoesNotExist()

        rule.onNodeWithTag("comic_reader").performTouchInput { click() }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()

        rule.onNodeWithTag("image_reader_scrim").assertIsDisplayed()
    }

    @Test
    fun comicDoubleTapResetsSharedTransform() {
        setScreen(contentState(ImageReaderMode.COMIC))
        zoomAndPanComic()
        assertTrue(comicScale() > 1f)
        assertTrue(abs(comicOffset()) > 0.1f)

        rule.onNodeWithTag("comic_reader")
            .performTouchInput {
                doubleClick()
            }

        assertEquals(1f, comicScale(), 0.001f)
        assertEquals(0f, comicOffset(), 0.001f)
    }

    @Test
    fun comicTransformSurvivesReaderModeSwitches() {
        rule.setContent {
            var state by remember {
                mutableStateOf(contentState())
            }
            MediaViewerTheme(darkTheme = true) {
                ImageReaderScreen(
                    state = state,
                    imageLoader = loader,
                    onModeChanged = {
                        state = state.copy(mode = it)
                    },
                    onSortChanged = {},
                    onAnchorChanged = {},
                    onRetryDirectory = {},
                    onImageLoadError = { _, _ -> },
                    onImageLoadSuccess = {},
                    onRetryImage = {},
                    onBack = {},
                )
            }
        }
        zoomAndPanComic()
        val scaleBeforeSwitch = comicScale()
        val offsetBeforeSwitch = comicOffset()

        rule.onNodeWithTag("reader_mode_toggle")
            .performClick()
        rule.onNodeWithTag("media_image")
            .assertIsDisplayed()
        rule.onNodeWithTag("reader_mode_toggle")
            .performClick()
        rule.onNodeWithTag("comic_reader")
            .assertIsDisplayed()

        assertEquals(
            scaleBeforeSwitch,
            comicScale(),
            0.001f,
        )
        assertEquals(
            offsetBeforeSwitch,
            comicOffset(),
            0.001f,
        )
    }

    @Test
    fun itemFailureIsInlineAndRetriesOnlyThatImage() {
        val base = contentState()
        val failedUrl = base.anchorLogicalUrl
        var retriedUrl: String? = null
        setScreen(
            state = base.copy(
                itemFailures = mapOf(
                    failedUrl to
                        ImageItemFailure(
                            message = "图片解码失败",
                            kind =
                                ImageLoadFailureKind.DECODE,
                        ),
                ),
            ),
            onRetryImage = {
                retriedUrl = it
            },
        )

        rule.onNodeWithText("图片解码失败")
            .assertIsDisplayed()
        rule
            .onNodeWithTag(
                "retry_image:${failedUrl.hashCode()}",
            )
            .performClick()
        rule.runOnIdle {
            assertEquals(failedUrl, retriedUrl)
        }
        rule.onNodeWithTag("comic_reader")
            .assertIsDisplayed()
    }

    @Test
    fun readerUsesImmersiveToolbarAndKeepsNetworkRetryExplicit() {
        val base = contentState()
        val failedLogicalUrl = base.anchorLogicalUrl
        var retriedLogicalUrl: String? = null
        setScreen(
            state = base.copy(
                itemFailures = mapOf(
                    failedLogicalUrl to
                        ImageItemFailure(
                            message = "连接已失效",
                            kind =
                                ImageLoadFailureKind.NETWORK,
                        ),
                ),
            ),
            onRetryImage = {
                retriedLogicalUrl = it
            },
        )

        rule.onNodeWithTag("image_reader_scrim")
            .assertIsDisplayed()
        rule.onNodeWithText("2 / 3")
            .assertIsDisplayed()
        rule.onNodeWithText("重新连接并重试")
            .performClick()
        rule.runOnIdle {
            assertEquals(
                failedLogicalUrl,
                retriedLogicalUrl,
            )
        }
    }

    @Test
    fun immersiveToolbarKeepsControlsInsideA320DpTwoXFontWindow() {
        val base = contentState()
        val longTitle =
            "这是一个非常非常长但不能挤出操作按钮的图片文件名.png"
        val state = base.copy(
            images = base.images.map { item ->
                if (
                    item.logicalUrl ==
                    base.anchorLogicalUrl
                ) {
                    item.copy(name = longTitle)
                } else {
                    item
                }
            },
        )
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = 1f,
                    fontScale = 2f,
                ),
            ) {
                Box(
                    Modifier
                        .size(
                            width = 320.dp,
                            height = 568.dp,
                        )
                        .testTag("reader_window"),
                ) {
                    MediaViewerTheme(darkTheme = true) {
                        ImageReaderScreen(
                            state = state,
                            imageLoader = loader,
                            onModeChanged = {},
                            onSortChanged = {},
                            onAnchorChanged = {},
                            onRetryDirectory = {},
                            onImageLoadError = { _, _ -> },
                            onImageLoadSuccess = {},
                            onRetryImage = {},
                            onBack = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithText(longTitle)
            .assertIsDisplayed()
        rule.onNodeWithText("2 / 3")
            .assertIsDisplayed()
        rule.onNodeWithTag("comic_reader")
            .assertIsDisplayed()
        val window = rule.onNodeWithTag("reader_window")
            .fetchSemanticsNode()
            .boundsInRoot
        listOf(
            rule.onNodeWithContentDescription("返回")
                .fetchSemanticsNode()
                .boundsInRoot,
            rule.onNodeWithTag("reader_mode_toggle")
                .fetchSemanticsNode()
                .boundsInRoot,
            rule.onNodeWithTag("image_sort_menu")
                .fetchSemanticsNode()
                .boundsInRoot,
            rule.onNodeWithTag("image_reader_scrim")
                .fetchSemanticsNode()
                .boundsInRoot,
        ).forEach { bounds ->
            assertTrue(bounds.left >= window.left)
            assertTrue(bounds.right <= window.right)
            assertTrue(bounds.top >= window.top)
            assertTrue(bounds.bottom <= window.bottom)
        }
    }

    @Test
    fun immersiveCanvasRemainsFullWidthAt600Dp() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
            ) {
                Box(
                    Modifier
                        .size(
                            width = 600.dp,
                            height = 568.dp,
                        )
                        .testTag("wide_reader_window"),
                ) {
                    MediaViewerTheme(darkTheme = true) {
                        ImageReaderScreen(
                            state = contentState(
                                ImageReaderMode.SINGLE,
                            ),
                            imageLoader = loader,
                            onModeChanged = {},
                            onSortChanged = {},
                            onAnchorChanged = {},
                            onRetryDirectory = {},
                            onImageLoadError = { _, _ -> },
                            onImageLoadSuccess = {},
                            onRetryImage = {},
                            onBack = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithText("2 / 3")
            .assertIsDisplayed()
        val window =
            rule.onNodeWithTag("wide_reader_window")
                .fetchSemanticsNode()
                .boundsInRoot
        val canvas =
            rule.onNodeWithTag("image_reader_canvas")
                .fetchSemanticsNode()
                .boundsInRoot
        val reader = rule.onNodeWithTag("media_image")
            .fetchSemanticsNode()
            .boundsInRoot
        val scrim =
            rule.onNodeWithTag("image_reader_scrim")
                .fetchSemanticsNode()
                .boundsInRoot
        listOf(canvas, reader, scrim).forEach { bounds ->
            assertEquals(
                window.left,
                bounds.left,
                0.001f,
            )
            assertEquals(
                window.right,
                bounds.right,
                0.001f,
            )
        }
    }

    @Test
    fun immersiveToolbarRespectsInjectedCutoutInsets() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
            ) {
                Box(
                    Modifier
                        .size(600.dp, 568.dp)
                        .testTag("cutout_reader_window"),
                ) {
                    MediaViewerTheme(darkTheme = true) {
                        ImageReaderScreen(
                            state = contentState(
                                ImageReaderMode.SINGLE,
                            ),
                            imageLoader = loader,
                            onModeChanged = {},
                            onSortChanged = {},
                            onAnchorChanged = {},
                            onRetryDirectory = {},
                            onImageLoadError = { _, _ -> },
                            onImageLoadSuccess = {},
                            onRetryImage = {},
                            onBack = {},
                            safeDrawingInsets = WindowInsets(
                                left = 16.dp,
                                top = 24.dp,
                                right = 20.dp,
                                bottom = 32.dp,
                            ),
                        )
                    }
                }
            }
        }

        val window = rule.onNodeWithTag(
            "cutout_reader_window",
        ).fetchSemanticsNode().boundsInRoot
        val canvas = rule.onNodeWithTag("image_reader_canvas")
            .fetchSemanticsNode().boundsInRoot
        val back = rule.onNodeWithTag("image_reader_back")
            .fetchSemanticsNode().boundsInRoot
        val sort = rule.onNodeWithTag("image_sort_menu")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(window.left, canvas.left, 0.001f)
        assertEquals(window.top, canvas.top, 0.001f)
        assertEquals(window.right, canvas.right, 0.001f)
        assertEquals(window.bottom, canvas.bottom, 0.001f)
        assertTrue(back.left >= window.left + 16f)
        assertTrue(back.top >= window.top + 24f)
        assertTrue(sort.right <= window.right - 20f)
    }

    @Test
    fun network_error_offers_reconnect_but_decode_error_only_retries_the_item() {
        val networkItem = ImageReaderItem(
            name = "network.jpg",
            size = 1L,
            modifiedAt = Instant.EPOCH,
            logicalUrl = "http://media.example/network.jpg",
            requestUrl = "http://192.0.2.1/network.jpg",
        )
        val decodeItem = networkItem.copy(
            name = "decode.jpg",
            logicalUrl = "http://media.example/decode.jpg",
            requestUrl = "http://192.0.2.1/decode.jpg",
        )
        rule.setContent {
            MediaViewerTheme(darkTheme = true) {
                Column {
                    ImageItemErrorPanel(
                        item = networkItem,
                        failure = ImageItemFailure(
                            message = "图片网络加载失败",
                            kind =
                                ImageLoadFailureKind.NETWORK,
                        ),
                        onRetry = {},
                    )
                    ImageItemErrorPanel(
                        item = decodeItem,
                        failure = ImageItemFailure(
                            message = "图片解码失败",
                            kind =
                                ImageLoadFailureKind.DECODE,
                        ),
                        onRetry = {},
                    )
                }
            }
        }

        rule.onNodeWithText("重新连接并重试").assertHasClickAction()
        rule.onNodeWithText("重试此图").assertHasClickAction()
    }

    @Test
    fun comicEndpointRefreshOnlyDisablesAndLoadsTheTargetFailure() {
        val base = contentState(ImageReaderMode.COMIC)
        val target = base.images[0]
        val other = base.images[1]
        val networkFailure = ImageItemFailure(
            message = "图片网络加载失败",
            kind = ImageLoadFailureKind.NETWORK,
        )
        setScreen(
            state = base.copy(
                images = listOf(target, other),
                anchorLogicalUrl = target.logicalUrl,
                isRefreshingEndpoint = true,
                refreshingImageLogicalUrl =
                    target.logicalUrl,
                itemFailures = mapOf(
                    target.logicalUrl to networkFailure,
                    other.logicalUrl to networkFailure,
                ),
            ),
        )

        rule
            .onNodeWithTag(
                "retry_image:" +
                    target.logicalUrl.hashCode(),
            )
            .assertIsNotEnabled()
        rule
            .onNodeWithTag(
                "retry_image_loading:" +
                    target.logicalUrl.hashCode(),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        rule
            .onNodeWithTag(
                "retry_image:" +
                    other.logicalUrl.hashCode(),
            )
            .assertIsEnabled()
        rule
            .onNodeWithTag(
                "retry_image_loading:" +
                    other.logicalUrl.hashCode(),
                useUnmergedTree = true,
            )
            .assertIsNotDisplayed()
        rule.onNodeWithTag("comic_reader")
            .assertIsDisplayed()
        rule.onNodeWithTag("image_reader_refresh_chip")
            .assertIsDisplayed()
        rule.onNodeWithText("正在重新连接")
            .assertIsDisplayed()
        assertIndeterminateProgressCount(1)
    }

    @Test
    fun singleEndpointRefreshDisablesAndLoadsTheCurrentTarget() {
        val base = contentState(ImageReaderMode.SINGLE)
        val current = base.images.single {
            it.logicalUrl == base.anchorLogicalUrl
        }
        setScreen(
            state = base.copy(
                isRefreshingEndpoint = true,
                refreshingImageLogicalUrl =
                    current.logicalUrl,
                itemFailures = mapOf(
                    current.logicalUrl to
                        ImageItemFailure(
                            message =
                                "图片网络加载失败",
                            kind =
                                ImageLoadFailureKind.NETWORK,
                        ),
                ),
            ),
        )

        rule
            .onNodeWithTag(
                "retry_image:" +
                    current.logicalUrl.hashCode(),
            )
            .assertIsNotEnabled()
        rule
            .onNodeWithTag(
                "retry_image_loading:" +
                    current.logicalUrl.hashCode(),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        rule.onNodeWithTag("image_reader_refresh_chip")
            .assertIsDisplayed()
        assertIndeterminateProgressCount(1)
    }

    @Test
    fun singleEndpointRefreshLeavesANonTargetFailureEnabled() {
        val base = contentState(ImageReaderMode.SINGLE)
        val current = base.images.single {
            it.logicalUrl == base.anchorLogicalUrl
        }
        val refreshTarget = base.images.first {
            it.logicalUrl != current.logicalUrl
        }
        setScreen(
            state = base.copy(
                isRefreshingEndpoint = true,
                refreshingImageLogicalUrl =
                    refreshTarget.logicalUrl,
                itemFailures = mapOf(
                    current.logicalUrl to
                        ImageItemFailure(
                            message =
                                "图片网络加载失败",
                            kind =
                                ImageLoadFailureKind.NETWORK,
                        ),
                ),
            ),
        )

        rule
            .onNodeWithTag(
                "retry_image:" +
                    current.logicalUrl.hashCode(),
            )
            .assertIsEnabled()
        rule
            .onNodeWithTag(
                "retry_image_loading:" +
                    current.logicalUrl.hashCode(),
                useUnmergedTree = true,
            )
            .assertIsNotDisplayed()
        rule.onNodeWithTag("image_reader_refresh_chip")
            .assertIsDisplayed()
        assertIndeterminateProgressCount(0)
    }

    private fun assertIndeterminateProgressCount(
        expected: Int,
    ) {
        rule.onAllNodes(
            matcher =
                SemanticsMatcher.expectValue(
                    SemanticsProperties
                        .ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate,
                ),
            useUnmergedTree = true,
        ).assertCountEquals(expected)
    }

    private fun setScreen(
        state: ImageReaderUiState,
        onModeChanged: (ImageReaderMode) -> Unit = {},
        onSortChanged: (ImageSortOrder) -> Unit = {},
        onAnchorChanged: (String) -> Unit = {},
        onRetryDirectory: () -> Unit = {},
        onImageLoadError:
            (String, ImageLoadFailureKind) -> Unit =
            { _, _ -> },
        onImageLoadSuccess: (String) -> Unit = {},
        onRetryImage: (String) -> Unit = {},
        safeDrawingInsets: WindowInsets = WindowInsets(0),
    ) {
        rule.setContent {
            MediaViewerTheme(darkTheme = true) {
                ImageReaderScreen(
                    state = state,
                    imageLoader = loader,
                    onModeChanged = onModeChanged,
                    onSortChanged = onSortChanged,
                    onAnchorChanged = onAnchorChanged,
                    onRetryDirectory = onRetryDirectory,
                    onImageLoadError =
                        onImageLoadError,
                    onImageLoadSuccess =
                        onImageLoadSuccess,
                    onRetryImage = onRetryImage,
                    onBack = {},
                    safeDrawingInsets = safeDrawingInsets,
                )
            }
        }
    }

    private fun zoomSingleImage() {
        rule.onNodeWithTag("media_image")
            .performTouchInput {
                val middle = center
                down(0, middle + Offset(-40f, 0f))
                down(1, middle + Offset(40f, 0f))
                moveTo(
                    0,
                    middle + Offset(-150f, 0f),
                    delayMillis = 120L,
                )
                moveTo(
                    1,
                    middle + Offset(150f, 0f),
                    delayMillis = 120L,
                )
                up(0)
                up(1)
            }
    }

    private fun zoomAndPanComic() {
        rule.onNodeWithTag("comic_reader")
            .performTouchInput {
                val middle = center
                down(
                    pointerId = 0,
                    position =
                        middle + Offset(-40f, 0f),
                )
                down(
                    pointerId = 1,
                    position =
                        middle + Offset(40f, 0f),
                )
                moveTo(
                    pointerId = 0,
                    position =
                        middle + Offset(-100f, 0f),
                    delayMillis = 100L,
                )
                moveTo(
                    pointerId = 1,
                    position =
                        middle + Offset(100f, 0f),
                    delayMillis = 100L,
                )
                moveTo(
                    pointerId = 0,
                    position =
                        middle + Offset(-160f, 0f),
                    delayMillis = 100L,
                )
                moveTo(
                    pointerId = 1,
                    position =
                        middle + Offset(160f, 0f),
                    delayMillis = 100L,
                )
                up(pointerId = 0)
                up(pointerId = 1)
            }
        rule.onNodeWithTag("comic_reader")
            .performTouchInput {
                val start = center
                down(
                    pointerId = 0,
                    position = start,
                )
                moveTo(
                    pointerId = 0,
                    position =
                        start + Offset(80f, 0f),
                    delayMillis = 200L,
                )
                up(pointerId = 0)
            }
    }

    private fun comicScale(): Float =
        rule.onNodeWithTag("comic_item:b.png")
            .fetchSemanticsNode()
            .config[ComicScaleSemanticsKey]

    private fun comicOffset(): Float =
        rule.onNodeWithTag("comic_item:b.png")
            .fetchSemanticsNode()
            .config[
                ComicHorizontalOffsetSemanticsKey
            ]
}

private fun contentState(
    mode: ImageReaderMode = ImageReaderMode.COMIC,
): ImageReaderUiState.Content {
    val images = listOf("a.jpg", "b.png", "c.webp")
        .mapIndexed { index, name ->
            ImageReaderItem(
                name = name,
                size = (index + 1) * 1_024L,
                modifiedAt =
                    Instant.parse(
                        "2026-07-2${index + 1}T00:00:00Z",
                    ),
                logicalUrl =
                    "http://media.example/pik/$name",
                requestUrl =
                    "http://192.0.2.1/pik/$name",
            )
        }
    return ImageReaderUiState.Content(
        images = images,
        mode = mode,
        sortOrder = ImageSortOrder.NAME_ASC,
        anchorLogicalUrl = images[1].logicalUrl,
    )
}
