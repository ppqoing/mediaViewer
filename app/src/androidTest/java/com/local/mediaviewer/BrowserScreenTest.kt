package com.local.mediaviewer

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.local.mediaviewer.browser.Breadcrumb
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserUiState
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.navigation.HomeRoute
import com.local.mediaviewer.navigation.ImageReaderRoute
import com.local.mediaviewer.navigation.PlayerRoute
import com.local.mediaviewer.ui.browser.BrowserScreen
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowserScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun contentShowsFullNameMetadataAndForwardsClicks() {
        val entry = DirectoryEntry(
            name = "很长的 動画 (1) 😀.mkv",
            size = 1536,
            modifiedAt = Instant.parse("2026-07-28T01:02:03Z"),
            mode = 420,
            isDirectory = false,
            isSymlink = false,
            logicalUrl = "http://media.example/middle/video.mkv",
            requestUrl = "http://192.0.2.1/middle/video.mkv",
            kind = MediaKind.VIDEO,
        )
        var clicked: DirectoryEntry? = null
        var breadcrumbIndex: Int? = null
        rule.setContent {
            BrowserScreen(
                state = BrowserUiState.Content(
                    browserPage(entries = listOf(entry)),
                ),
                onEntryClick = { clicked = it },
                onBreadcrumbClick = { breadcrumbIndex = it },
                onRetry = {},
                onBack = {},
            )
        }

        rule.onNodeWithText(entry.name).assertIsDisplayed().performClick()
        rule.onNodeWithText("1.5 KiB", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("breadcrumb_0").performClick()
        rule.runOnIdle {
            assertEquals(entry, clicked)
            assertEquals(0, breadcrumbIndex)
        }
    }

    @Test
    fun emptyDirectoryHasExplicitState() {
        rule.setContent {
            BrowserScreen(
                state = BrowserUiState.Empty(browserPage(entries = emptyList())),
                onEntryClick = {},
                onBreadcrumbClick = {},
                onRetry = {},
                onBack = {},
            )
        }

        rule.onNodeWithText("此目录为空").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsMessageAndRetry() {
        var retried = false
        rule.setContent {
            BrowserScreen(
                state = BrowserUiState.Error(AppError.HttpFailure(404)),
                onEntryClick = {},
                onBreadcrumbClick = {},
                onRetry = { retried = true },
                onBack = {},
            )
        }

        rule.onNodeWithText("服务器返回 HTTP 404").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    @Test
    fun typedPlayerRoutePreservesEncodedUrlsExactly() {
        val original = PlayerRoute(
            name = "動画 (1) 😀.mkv",
            logicalUrl =
                "http://media.example:8080/middle/%E5%8B%95%E7%94%BB%20(1)%20%F0%9F%98%80.mkv",
            requestUrl =
                "http://203.0.113.7:8080/middle/%E5%8B%95%E7%94%BB%20(1)%20%F0%9F%98%80.mkv",
            mediaKey =
                "http://media.example:8080/middle/%E5%8B%95%E7%94%BB%20(1)%20%F0%9F%98%80.mkv",
            kind = MediaKind.VIDEO,
        )
        var decoded: PlayerRoute? = null
        rule.setContent {
            val navController = rememberNavController()
            NavHost(navController, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    LaunchedEffect(Unit) {
                        navController.navigate(original)
                    }
                }
                composable<PlayerRoute> { entry ->
                    decoded = entry.toRoute()
                    androidx.compose.material3.Text("已到达播放器")
                }
            }
        }

        rule.onNodeWithText("已到达播放器").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(original, decoded)
        }
    }

    @Test
    fun typedImageReaderRoutePreservesDirectoryContextExactly() {
        val original = ImageReaderRoute(
            rootId = "pik",
            directoryLogicalUrl =
                "http://media.example:8080/pik/%E6%9D%A1%E6%BC%AB%20(1)/?folder=a%26b#part",
            selectedLogicalUrl =
                "http://media.example:8080/pik/%E6%9D%A1%E6%BC%AB%20(1)/%E7%AC%AC%2001%20%E9%A1%B5%20%F0%9F%98%80.jpg",
            selectedName = "第 01 页 😀.jpg",
        )
        var decoded: ImageReaderRoute? = null
        rule.setContent {
            val navController = rememberNavController()
            NavHost(
                navController,
                startDestination = HomeRoute,
            ) {
                composable<HomeRoute> {
                    LaunchedEffect(Unit) {
                        navController.navigate(original)
                    }
                }
                composable<ImageReaderRoute> { entry ->
                    decoded = entry.toRoute()
                    androidx.compose.material3.Text(
                        "已到达图片阅读器",
                    )
                }
            }
        }

        rule
            .onNodeWithText("已到达图片阅读器")
            .assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(original, decoded)
        }
    }
}

private fun browserPage(entries: List<DirectoryEntry>) = BrowserPage(
    root = RootShare.MIDDLE,
    logicalDirectoryUrl = "http://media.example/middle/",
    requestDirectoryUrl = "http://192.0.2.1/middle/",
    breadcrumbs = listOf(
        Breadcrumb("MiddleDir", "http://media.example/middle/"),
    ),
    entries = entries,
)
