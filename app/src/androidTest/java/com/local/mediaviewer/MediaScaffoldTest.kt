package com.local.mediaviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.components.MediaAppScaffold
import com.local.mediaviewer.ui.components.MediaScreenScaffold
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun screenScaffoldKeepsTopActionsInsideSafeDrawing() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MediaViewerTheme {
                    Box(Modifier.size(360.dp, 640.dp).testTag("safe_root")) {
                        MediaScreenScaffold(
                            title = "安全区",
                            actions = {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .testTag("top_action_more"),
                                )
                            },
                            contentWindowInsets = WindowInsets(
                                left = 12.dp,
                                top = 42.dp,
                                right = 18.dp,
                                bottom = 24.dp,
                            ),
                        ) { }
                    }
                }
            }
        }

        val root = rule.onNodeWithTag("safe_root")
            .fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithTag("top_action_more")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(action.top >= root.top + 42f)
        assertTrue(action.right <= root.right - 18f)
    }

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

    @Test
    fun rootStaysEdgeToEdgeAndScreenConsumesInjectedSafeInsetsOnce() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
            ) {
                Box(
                    Modifier
                        .size(360.dp, 640.dp)
                        .testTag("app_window"),
                ) {
                    MediaViewerTheme {
                        MediaAppScaffold(
                            snackbarHostState = remember {
                                SnackbarHostState()
                            },
                        ) { appPadding ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(appPadding),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .testTag("app_content"),
                                ) {
                                    MediaScreenScaffold(
                                        title = "安全区测试",
                                        onBack = {},
                                        actions = {
                                            Box(
                                                Modifier
                                                    .size(48.dp)
                                                    .testTag("screen_action"),
                                            )
                                        },
                                        contentWindowInsets =
                                            WindowInsets(
                                                left = 16.dp,
                                                top = 24.dp,
                                                right = 20.dp,
                                                bottom = 32.dp,
                                            ),
                                    ) { screenPadding ->
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(screenPadding),
                                        ) {
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .testTag("screen_content"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val window = rule.onNodeWithTag("app_window")
            .fetchSemanticsNode().boundsInRoot
        val appContent = rule.onNodeWithTag("app_content")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(window.left, appContent.left, 0.001f)
        assertEquals(window.top, appContent.top, 0.001f)
        assertEquals(window.right, appContent.right, 0.001f)
        assertEquals(window.bottom, appContent.bottom, 0.001f)

        val back = rule.onNodeWithContentDescription("返回")
            .fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithTag("screen_action")
            .fetchSemanticsNode().boundsInRoot
        val screenContent = rule.onNodeWithTag("screen_content")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(back.left >= appContent.left + 16f)
        assertTrue(back.top >= appContent.top + 24f)
        assertTrue(action.right <= appContent.right - 20f)
        assertEquals(
            appContent.left + 16f,
            screenContent.left,
            0.001f,
        )
        assertEquals(
            appContent.right - 20f,
            screenContent.right,
            0.001f,
        )
        assertEquals(
            appContent.bottom - 32f,
            screenContent.bottom,
            0.001f,
        )
    }
}
