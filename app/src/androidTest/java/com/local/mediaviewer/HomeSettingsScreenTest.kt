package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.mediaviewer.home.HomeUiState
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.ShareAuthenticationMode
import com.local.mediaviewer.settings.SettingsUiState
import com.local.mediaviewer.ui.home.HomeScreen
import com.local.mediaviewer.ui.settings.SettingsScreen
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

        rule.onNodeWithText("当前 IPv4：192.168.1.17").assertIsDisplayed()
        rule.onNodeWithText("MiddleDir").assertIsDisplayed().performClick()
        assertEquals(HOME_ANONYMOUS_SHARE, openedShare)
        rule.onNodeWithText("私有目录").assertIsDisplayed()
        rule.onNodeWithText("需要 Basic Auth，当前版本暂不能进入")
            .assertIsDisplayed()
        rule.onNodeWithText("私有目录").assertIsNotEnabled()
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
