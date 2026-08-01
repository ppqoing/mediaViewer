package com.local.mediaviewer

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.local.mediaviewer.browser.Breadcrumb
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserPlaybackAction
import com.local.mediaviewer.browser.BrowserUiState
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.ShareAuthenticationMode
import com.local.mediaviewer.navigation.HomeRoute
import com.local.mediaviewer.navigation.ImageReaderRoute
import com.local.mediaviewer.navigation.PlayerRoute
import com.local.mediaviewer.ui.browser.BrowserScreen
import com.local.mediaviewer.ui.theme.MediaViewerTheme
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
    fun retainedPageRemainsVisibleWhileChildLoads() {
        val page = browserPage(
            breadcrumbs = listOf(
                Breadcrumb(
                    label = "根",
                    logicalUrl = "http://media.example/middle/",
                ),
                Breadcrumb(
                    label = "视频",
                    logicalUrl = "http://media.example/middle/video/",
                ),
            ),
            entries = listOf(
                browserEntry(
                    name = "long movie name.mp4",
                    kind = MediaKind.VIDEO,
                ),
            ),
        )
        rule.setContent {
            MediaViewerTheme {
                BrowserScreen(
                    state = BrowserUiState.Loading(previous = page),
                    onEntryClick = {},
                    onBreadcrumbClick = {},
                    onPlaybackAction = { _, _ -> },
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        rule.onNodeWithText("long movie name.mp4").assertIsDisplayed()
        rule.onNodeWithTag("browser_list").assertIsDisplayed()
        rule.onNodeWithTag("browser_refreshing").assertIsDisplayed()
        rule.onNodeWithTag("breadcrumb_1").assertIsSelected()
    }

    @Test
    fun contentTopBarExposesRefreshEntry() {
        var retries = 0
        rule.setContent {
            MediaViewerTheme {
                BrowserScreen(
                    state = BrowserUiState.Content(
                        browserPage(
                            entries = listOf(
                                browserEntry("movie.mp4", MediaKind.VIDEO),
                            ),
                        ),
                    ),
                    onEntryClick = {},
                    onBreadcrumbClick = {},
                    onRetry = { retries += 1 },
                    onBack = {},
                )
            }
        }

        rule.onNodeWithContentDescription("刷新")
            .assertIsDisplayed()
            .performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun playableFilesExposeManualPlaybackActions() {
        val video = browserEntry("movie.mp4", MediaKind.VIDEO)
        val image = browserEntry("page.png", MediaKind.IMAGE)
        val directory = browserEntry("nested", MediaKind.DIRECTORY)
        rule.setContent {
            BrowserScreen(
                state = BrowserUiState.Content(
                    browserPage(entries = listOf(video, image, directory)),
                ),
                onEntryClick = {},
                onBreadcrumbClick = {},
                onRetry = {},
                onBack = {},
            )
        }

        rule.onNodeWithContentDescription("更多播放操作：movie.mp4").performClick()
        rule.onAllNodesWithContentDescription("更多播放操作：movie.mp4")
            .assertCountEquals(1)
        rule.onNodeWithContentDescription("更多播放操作：page.png")
            .assertDoesNotExist()
        rule.onNodeWithContentDescription("更多播放操作：nested")
            .assertDoesNotExist()
        rule.onNodeWithText("立即播放").assertIsDisplayed()
        rule.onNodeWithText("下一项播放").assertIsDisplayed()
        rule.onNodeWithText("添加到队列").assertIsDisplayed()
    }

    @Test
    fun playbackMenuNamesItsTargetAndKeepsApprovedOrder() {
        val entry = browserEntry("movie.mp4", MediaKind.VIDEO)
        val actions = mutableListOf<Pair<BrowserPlaybackAction, DirectoryEntry>>()
        rule.setContent {
            MediaViewerTheme {
                BrowserScreen(
                    state = BrowserUiState.Content(browserPage(entries = listOf(entry))),
                    onEntryClick = {},
                    onBreadcrumbClick = {},
                    onPlaybackAction = { action, target ->
                        actions += action to target
                    },
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        rule.onNodeWithContentDescription("更多播放操作：movie.mp4").performClick()
        val now = rule.onNodeWithText("立即播放")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val next = rule.onNodeWithText("下一项播放")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val enqueue = rule.onNodeWithText("添加到队列")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(now.top < next.top)
        assertTrue(next.top < enqueue.top)

        rule.onNodeWithText("立即播放").performClick()
        rule.onNodeWithContentDescription("更多播放操作：movie.mp4").performClick()
        rule.onNodeWithText("下一项播放").performClick()
        rule.onNodeWithContentDescription("更多播放操作：movie.mp4").performClick()
        rule.onNodeWithText("添加到队列").performClick()
        rule.runOnIdle {
            assertEquals(
                listOf(
                    BrowserPlaybackAction.PLAY_DIRECTORY to entry,
                    BrowserPlaybackAction.PLAY_NEXT to entry,
                    BrowserPlaybackAction.ADD_TO_QUEUE to entry,
                ),
                actions,
            )
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
    fun existing_page_remains_visible_while_child_load_fails() {
        val previous = browserPage(
            entries = listOf(
                browserEntry("旧页面视频.mp4", MediaKind.VIDEO),
            ),
        )
        val retainedName = previous.entries.single().name
        rule.setContent {
            MediaViewerTheme {
                BrowserScreen(
                    state = BrowserUiState.Error(
                        error = AppError.NetworkFailure("offline"),
                        previous = previous,
                        failedLogicalUrl = "http://media/child/",
                    ),
                    onRetry = {},
                    onBack = {},
                    onEntryClick = {},
                    onBreadcrumbClick = {},
                )
            }
        }

        rule.onNodeWithText(retainedName).assertIsDisplayed()
        rule.onNodeWithText("加载子目录失败").assertIsDisplayed()
        rule.onNodeWithText("重试").assertHasClickAction()
    }

    @Test
    fun typedPlayerRoutePreservesOnlyStableMediaKey() {
        val original = PlayerRoute(
            mediaKey =
                "http://media.example:8080/middle/%E5%8B%95%E7%94%BB%20(1)%20%F0%9F%98%80.mkv",
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

private fun browserPage(
    entries: List<DirectoryEntry>,
    breadcrumbs: List<Breadcrumb> = listOf(
        Breadcrumb(
            "MiddleDir",
            "http://media.example/middle/",
        ),
    ),
) = BrowserPage(
    root = BROWSER_SHARE,
    logicalDirectoryUrl = breadcrumbs.last().logicalUrl,
    requestDirectoryUrl = "http://192.0.2.1/middle/",
    breadcrumbs = breadcrumbs,
    entries = entries,
)

private fun browserEntry(name: String, kind: MediaKind) = DirectoryEntry(
    name = name,
    size = 1,
    modifiedAt = Instant.EPOCH,
    mode = 420,
    isDirectory = kind == MediaKind.DIRECTORY,
    isSymlink = false,
    logicalUrl = "http://media.example/middle/$name",
    requestUrl = "http://192.0.2.1/middle/$name",
    kind = kind,
)

private val BROWSER_SHARE = ServerShare(
    id = "4f01061d-9b75-4f7d-96db-49c801e96188",
    displayName = "MiddleDir",
    urlPrefix = "middle",
    directoryBrowsing = true,
    authenticationMode = ShareAuthenticationMode.ANONYMOUS,
)
