package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.mediaviewer.navigation.PlayerEntryState
import com.local.mediaviewer.ui.player.PlayerBootstrapContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerBootstrapContentTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun failed_state_has_reconnect_and_safe_back_actions() {
        var reconnects = 0
        var backs = 0
        rule.setContent {
            PlayerBootstrapContent(
                state = PlayerEntryState.Failed("服务未响应"),
                onReconnect = { reconnects++ },
                onBack = { backs++ },
            )
        }

        rule.onNodeWithText("服务未响应").assertIsDisplayed()
        rule.onNodeWithText("重连播放器").performClick()
        rule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, reconnects)
        assertEquals(1, backs)
    }
}
