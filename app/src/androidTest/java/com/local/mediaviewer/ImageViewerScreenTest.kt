package com.local.mediaviewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.local.mediaviewer.image.ImageViewerUiState
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.ui.image.ImageViewerScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImageViewerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun errorStateShowsChineseRetry() {
        val loader = MediaImageLoaderFactory.create(
            ApplicationProvider.getApplicationContext(),
        )
        var retries = 0
        rule.setContent {
            MaterialTheme {
                ImageViewerScreen(
                    name = "海报.png",
                    state = ImageViewerUiState(
                        requestUrl =
                            "http://192.0.2.1/pik/poster.png",
                        errorMessage = "图片加载失败",
                    ),
                    imageLoader = loader,
                    onLoadError = {},
                    onRetry = { retries += 1 },
                    onBack = {},
                )
            }
        }

        rule.onNodeWithText("海报.png").assertIsDisplayed()
        rule.onNodeWithText("图片加载失败").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle {
            assertEquals(1, retries)
        }
        loader.shutdown()
    }
}
