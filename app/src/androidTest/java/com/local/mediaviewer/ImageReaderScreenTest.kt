package com.local.mediaviewer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import com.local.mediaviewer.ui.image.ImageReaderScreen
import com.local.mediaviewer.ui.image.ImageItemErrorPanel
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
            .assertIsDisplayed()
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
    }

    @Test
    fun emptyStateUsesChineseText() {
        setScreen(ImageReaderUiState.Empty)
        rule.onNodeWithText("此目录没有图片")
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
            MaterialTheme {
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
            Column {
                ImageItemErrorPanel(
                    item = networkItem,
                    failure = ImageItemFailure(
                        message = "图片网络加载失败",
                        kind = ImageLoadFailureKind.NETWORK,
                    ),
                    onRetry = {},
                )
                ImageItemErrorPanel(
                    item = decodeItem,
                    failure = ImageItemFailure(
                        message = "图片解码失败",
                        kind = ImageLoadFailureKind.DECODE,
                    ),
                    onRetry = {},
                )
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
        onRetryDirectory: () -> Unit = {},
        onImageLoadError:
            (String, ImageLoadFailureKind) -> Unit =
            { _, _ -> },
        onImageLoadSuccess: (String) -> Unit = {},
        onRetryImage: (String) -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                ImageReaderScreen(
                    state = state,
                    imageLoader = loader,
                    onModeChanged = onModeChanged,
                    onSortChanged = onSortChanged,
                    onAnchorChanged = {},
                    onRetryDirectory = onRetryDirectory,
                    onImageLoadError =
                        onImageLoadError,
                    onImageLoadSuccess =
                        onImageLoadSuccess,
                    onRetryImage = onRetryImage,
                    onBack = {},
                )
            }
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
