package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.app.MediaViewerApp
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.testing.FakeAppContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaViewerNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: FakeAppContainer

    @Before
    fun setUp() {
        container = FakeAppContainer(
            ApplicationProvider.getApplicationContext(),
        )
        rule.setContent {
            MediaViewerApp(container)
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("MiddleDir")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        container.close()
    }

    @Test
    fun homeOpensNestedVideo() {
        openNestedDirectory()
        rule.onNodeWithText("样例.mp4").performClick()
        rule.onNodeWithText("样例.mp4").assertIsDisplayed()
        rule.onNodeWithTag("vlc_surface").assertExists()
    }

    @Test
    fun homeOpensNestedAudio() {
        openNestedDirectory()
        rule.onNodeWithText("样例.wav").performClick()
        rule.onNodeWithText("样例.wav").assertIsDisplayed()
        rule.onNodeWithTag("seek").assertExists()
    }

    @Test
    fun homeOpensNestedImage() {
        openNestedDirectory()
        rule.onNodeWithText("样例.png").performClick()
        rule.onNodeWithText("样例.png").assertIsDisplayed()
        rule.onNodeWithTag("comic_reader").assertExists()
    }

    @Test
    fun homeUsesConfiguredSingleImageMode() {
        runBlocking {
            container.readerPreferencesRepository
                .setDefaultMode(ImageReaderMode.SINGLE)
        }
        openNestedDirectory()
        rule.onNodeWithText("样例.png").performClick()
        rule.onNodeWithText("样例.png").assertIsDisplayed()
        rule.onNodeWithTag("media_image").assertExists()
    }

    private fun openNestedDirectory() {
        rule.onNodeWithText("MiddleDir").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("示例目录")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText("示例目录").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("样例.mp4")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText("MiddleDir").assertExists()
        rule.onNodeWithTag("breadcrumb_1").assertExists()
    }
}
