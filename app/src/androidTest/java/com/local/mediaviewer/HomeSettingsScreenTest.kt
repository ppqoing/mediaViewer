package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.mediaviewer.home.HomeUiState
import com.local.mediaviewer.model.RootShare
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
        var openedRoot: RootShare? = null
        var openedSettings = false
        rule.setContent {
            HomeScreen(
                state = HomeUiState.Connected("192.168.1.17"),
                onRetry = {},
                onOpenSettings = { openedSettings = true },
                onOpenRoot = { openedRoot = it },
            )
        }

        rule.onNodeWithText("当前 IPv4：192.168.1.17").assertIsDisplayed()
        rule.onNodeWithText("MiddleDir").assertIsDisplayed().performClick()
        assertEquals(RootShare.MIDDLE, openedRoot)
        rule.onNodeWithText("pik").assertIsDisplayed()
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
                onOpenRoot = {},
            )
        }

        rule.onNodeWithText("网络连接失败：timeout").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    @Test
    fun settingsSaveFollowsProbeState() {
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
                onBack = {},
            )
        }

        rule.onNodeWithTag("server_url").assertIsDisplayed()
        rule.onNodeWithTag("test_connection").assertIsEnabled()
        rule.onNodeWithTag("save_server").assertIsNotEnabled()
        rule.onNodeWithText("10.0.0.8").assertIsDisplayed()
        rule.onNodeWithText("已选择：203.0.113.7").assertIsDisplayed()
    }
}
