package com.local.mediaviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.components.MediaAppScaffold
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun bottomBarParticipatesInLayoutInsteadOfCoveringTheLastItem() {
        rule.setContent {
            MediaViewerTheme {
                MediaAppScaffold(
                    snackbarHostState = remember { SnackbarHostState() },
                    bottomBar = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .testTag("dock"),
                        )
                    },
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        Text(
                            "最后一项",
                            Modifier
                                .align(Alignment.BottomStart)
                                .testTag("last_item"),
                        )
                    }
                }
            }
        }

        val dockTop = rule.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot.top
        val itemBottom = rule.onNodeWithTag("last_item").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(itemBottom <= dockTop)
    }
}
