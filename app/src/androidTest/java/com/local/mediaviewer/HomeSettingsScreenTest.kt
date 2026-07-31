package com.local.mediaviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.home.HomeUiState
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.ShareAuthenticationMode
import com.local.mediaviewer.settings.SettingsUiState
import com.local.mediaviewer.ui.home.HomeScreen
import com.local.mediaviewer.ui.settings.SettingsScreen
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeSettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun connectedHomeShowsBothRootsAndActions() {
        var openedShare: ServerShare? = null
        var openedSettings = false
        rule.setContent {
            HomeScreen(
                state = HomeUiState.Connected(
                    "192.168.1.17",
                    listOf(HOME_ANONYMOUS_SHARE, HOME_BASIC_SHARE),
                ),
                onRetry = {},
                onOpenSettings = { openedSettings = true },
                onOpenShare = { openedShare = it },
            )
        }

        rule.onNodeWithText("已连接").assertIsDisplayed()
        rule.onNodeWithText("192.168.1.17").assertIsDisplayed()
        rule.onNodeWithText("MiddleDir").assertIsDisplayed().performClick()
        assertEquals(HOME_ANONYMOUS_SHARE, openedShare)
        rule.onNodeWithText("私有目录").assertIsDisplayed()
        rule.onNodeWithText("不支持当前认证方式").assertIsDisplayed()
        rule.onNodeWithTag("share:私有目录").assertIsNotEnabled()
        rule.onNodeWithContentDescription("设置").performClick()
        assertTrue(openedSettings)
    }

    @Test
    fun homeErrorShowsChineseMessageAndRetry() {
        var retried = false
        rule.setContent {
            HomeScreen(
                state = HomeUiState.Error("网络连接失败：timeout"),
                onRetry = { retried = true },
                onOpenSettings = {},
                onOpenShare = {},
            )
        }

        rule.onNodeWithText("网络连接失败：timeout").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    @Test
    fun connectingHomeShowsConnectionStatus() {
        rule.setContent {
            MediaViewerTheme {
                HomeScreen(
                    state = HomeUiState.Connecting,
                    onRetry = {},
                    onOpenSettings = {},
                    onOpenShare = {},
                )
            }
        }

        rule.onNodeWithText("正在连接服务器").assertIsDisplayed()
    }

    @Test
    fun connectedHomeForwardsDirectoryAndMediaRootsButDisablesUnavailableShares() {
        val directory = ServerShare(
            id = "directory",
            displayName = "目录入口",
            urlPrefix = "directory",
            directoryBrowsing = true,
            authenticationMode = ShareAuthenticationMode.ANONYMOUS,
        )
        val media = ServerShare(
            id = "media",
            displayName = "媒体入口",
            urlPrefix = "media",
            directoryBrowsing = true,
            authenticationMode = ShareAuthenticationMode.ANONYMOUS,
        )
        val basic = ServerShare(
            id = "private",
            displayName = "受限入口",
            urlPrefix = "private",
            directoryBrowsing = true,
            authenticationMode = ShareAuthenticationMode.BASIC,
        )
        val directoryClosed = ServerShare(
            id = "closed",
            displayName = "目录关闭入口",
            urlPrefix = "closed",
            directoryBrowsing = false,
            authenticationMode = ShareAuthenticationMode.ANONYMOUS,
        )
        val opened = mutableListOf<ServerShare>()
        rule.setContent {
            MediaViewerTheme {
                HomeScreen(
                    state = HomeUiState.Connected(
                        ipv4 = "192.0.2.10",
                        shares = listOf(directory, media, basic, directoryClosed),
                    ),
                    onRetry = {},
                    onOpenSettings = {},
                    onOpenShare = { opened += it },
                )
            }
        }

        rule.onNodeWithText("已连接").assertIsDisplayed()
        rule.onNodeWithText("192.0.2.10").assertIsDisplayed()
        rule.onNodeWithText("不支持当前认证方式").assertIsDisplayed()
        rule.onNodeWithText("目录浏览未开放").assertIsDisplayed()
        rule.onNodeWithTag("share:受限入口").assertIsNotEnabled()
        rule.onNodeWithTag("share:目录关闭入口").assertIsNotEnabled()
        rule.onNodeWithText("目录入口").performClick()
        rule.onNodeWithText("媒体入口").performClick()
        rule.runOnIdle {
            assertEquals(listOf(directory, media), opened)
        }
    }

    @Test
    fun connectedHomeWithNoBrowsableSharesShowsEmptyState() {
        rule.setContent {
            MediaViewerTheme {
                HomeScreen(
                    state = HomeUiState.Connected(
                        ipv4 = "192.0.2.10",
                        shares = emptyList(),
                    ),
                    onRetry = {},
                    onOpenSettings = {},
                    onOpenShare = {},
                )
            }
        }

        rule.onNodeWithText("没有可浏览的共享").assertIsDisplayed()
    }

    @Test
    fun errorHomeForwardsRetryExactlyOnceAndOpensServerSettings() {
        var retries = 0
        var settingsOpens = 0
        rule.setContent {
            MediaViewerTheme {
                HomeScreen(
                    state = HomeUiState.Error("网络连接失败：timeout"),
                    onRetry = { retries += 1 },
                    onOpenSettings = { settingsOpens += 1 },
                    onOpenShare = {},
                )
            }
        }

        rule.onNodeWithText("网络连接失败：timeout").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle { assertEquals(1, retries) }
        rule.onNodeWithText("服务器设置").performClick()
        rule.runOnIdle { assertEquals(1, settingsOpens) }
    }

    @Test
    fun thirtySharesRemainReachableAt320DpAndTwoXFont() {
        val shares = (1..30).map { index ->
            ServerShare(
                id = "share-$index",
                displayName = "共享 $index",
                urlPrefix = "share-$index",
                directoryBrowsing = true,
                authenticationMode = ShareAuthenticationMode.ANONYMOUS,
            )
        }
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = 1f,
                    fontScale = 2f,
                ),
            ) {
                Box(
                    Modifier
                        .size(width = 320.dp, height = 568.dp)
                        .testTag("home_window"),
                ) {
                    MediaViewerTheme {
                        HomeScreen(
                            state = HomeUiState.Connected(
                                ipv4 = "192.0.2.10",
                                shares = shares,
                            ),
                            onRetry = {},
                            onOpenSettings = {},
                            onOpenShare = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag("home_list")
            .performScrollToNode(hasText("共享 30"))
        rule.onNodeWithText("共享 30").assertIsDisplayed()
        val window = rule.onNodeWithTag("home_window")
            .fetchSemanticsNode().boundsInRoot
        val tail = rule.onNodeWithTag("share:共享 30")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(tail.left >= window.left)
        assertTrue(tail.right <= window.right)
        assertTrue(tail.bottom <= window.bottom)
    }

    @Test
    fun wideHomeUses24DpPageGutterAt600Dp() {
        val share = ServerShare(
            id = "wide-share",
            displayName = "宽屏入口",
            urlPrefix = "wide",
            directoryBrowsing = true,
            authenticationMode = ShareAuthenticationMode.ANONYMOUS,
        )
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(
                    Modifier
                        .size(width = 600.dp, height = 400.dp)
                        .testTag("wide_home_window"),
                ) {
                    MediaViewerTheme {
                        HomeScreen(
                            state = HomeUiState.Connected(
                                ipv4 = "192.0.2.10",
                                shares = listOf(share),
                            ),
                            onRetry = {},
                            onOpenSettings = {},
                            onOpenShare = {},
                        )
                    }
                }
            }
        }

        val window = rule.onNodeWithTag("wide_home_window")
            .fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("share:宽屏入口")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(24f, card.left - window.left, 0.01f)
    }

    @Test
    fun settingsSaveFollowsProbeState() {
        var selectedReaderMode: ImageReaderMode? = null
        rule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    input = "http://media.example:8080",
                    resolvedIpv4s = listOf("10.0.0.8", "203.0.113.7"),
                    selectedIpv4 = "203.0.113.7",
                    canSave = false,
                ),
                onInputChanged = {},
                onTest = {},
                onSave = {},
                onDefaultImageModeChanged = {
                    selectedReaderMode = it
                },
                onBack = {},
            )
        }

        rule.onNodeWithTag("server_url").assertIsDisplayed()
        rule.onNodeWithTag("test_connection").assertIsEnabled()
        rule.onNodeWithTag("save_server").assertIsNotEnabled()
        rule.onNodeWithText("10.0.0.8").assertIsDisplayed()
        rule.onNodeWithText("已选择：203.0.113.7").assertIsDisplayed()
        rule.onNodeWithText("图片阅读").assertIsDisplayed()
        rule.onNodeWithTag("default_reader_comic")
            .assertIsSelected()
        rule.onNodeWithTag("default_reader_single")
            .performClick()
        assertEquals(
            ImageReaderMode.SINGLE,
            selectedReaderMode,
        )
        rule.onNodeWithTag("save_server").assertIsNotEnabled()
    }
}

private val HOME_ANONYMOUS_SHARE = ServerShare(
    id = "4f01061d-9b75-4f7d-96db-49c801e96188",
    displayName = "MiddleDir",
    urlPrefix = "middle",
    directoryBrowsing = true,
    authenticationMode = ShareAuthenticationMode.ANONYMOUS,
)

private val HOME_BASIC_SHARE = ServerShare(
    id = "0447a975-eccb-4802-a8f5-5f574971876c",
    displayName = "私有目录",
    urlPrefix = "private",
    directoryBrowsing = true,
    authenticationMode = ShareAuthenticationMode.BASIC,
)
