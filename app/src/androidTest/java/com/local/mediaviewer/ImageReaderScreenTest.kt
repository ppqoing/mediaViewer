package com.local.mediaviewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.image.ImageReaderUiState
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.ui.image.ImageReaderScreen
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun singleModeUsesImageViewer() {
        setScreen(contentState(ImageReaderMode.SINGLE))
        rule.onNodeWithTag("media_image")
            .assertIsDisplayed()
    }

    private fun setScreen(
        state: ImageReaderUiState,
        onModeChanged: (ImageReaderMode) -> Unit = {},
        onSortChanged: (ImageSortOrder) -> Unit = {},
        onRetryDirectory: () -> Unit = {},
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
                    onBack = {},
                )
            }
        }
    }
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
