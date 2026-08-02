package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.FilterChipItem
import com.local.mediaviewer.ui.components.MediaBottomNavigation
import com.local.mediaviewer.ui.components.MediaFilterChips
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.components.MediaPrimaryButton
import com.local.mediaviewer.ui.components.MediaSegmentedControl
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel
import com.local.mediaviewer.ui.components.SegmentItem
import com.local.mediaviewer.ui.components.TopLevelDestination
import com.local.mediaviewer.ui.components.WarmPaperCard
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun warmPaperCardHostsItsContent() {
        rule.setContent {
            MediaViewerTheme {
                WarmPaperCard(Modifier.testTag("paper_card")) {
                    Text("纸张内容")
                }
            }
        }

        rule.onNodeWithTag("paper_card").assertIsDisplayed()
        rule.onNodeWithText("纸张内容").assertIsDisplayed()
    }

    @Test
    fun filterChipsExposeOneSelectedItemAndReachableTouchTargets() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MediaViewerTheme {
                    MediaFilterChips(
                        items = listOf(
                            FilterChipItem("all", "全部"),
                            FilterChipItem("video", "视频"),
                        ),
                        selectedId = "video",
                        onSelected = {},
                    )
                }
            }
        }

        rule.onNodeWithTag("filter_video").assertIsSelected()
        listOf("filter_all", "filter_video").forEach { tag ->
            val bounds = rule.onNodeWithTag(tag)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$tag width", bounds.width >= 48f)
            assertTrue("$tag height", bounds.height >= 48f)
        }
    }

    @Test
    fun segmentedControlAndBottomNavigationUseStableIds() {
        val selectedSegments = mutableListOf<String>()
        val selectedDestinations = mutableListOf<TopLevelDestination>()
        rule.setContent {
            MediaViewerTheme {
                MediaSegmentedControl(
                    items = listOf(
                        SegmentItem("image", "图片", MediaIcons.ImageMode),
                        SegmentItem("comic", "漫画", MediaIcons.ComicMode),
                    ),
                    selectedId = "image",
                    onSelected = selectedSegments::add,
                )
                MediaBottomNavigation(
                    selected = TopLevelDestination.MEDIA_SOURCES,
                    onSelect = selectedDestinations::add,
                )
            }
        }

        rule.onNodeWithTag("segment_image").assertIsSelected()
        rule.onNodeWithTag("segment_comic").performClick()
        rule.onNodeWithTag("bottom_nav_sources").assertIsSelected()
        rule.onNodeWithTag("bottom_nav_settings").performClick()
        rule.runOnIdle {
            assertEquals(listOf("comic"), selectedSegments)
            assertEquals(
                listOf(TopLevelDestination.SETTINGS),
                selectedDestinations,
            )
        }
    }

    @Test
    fun generatedIconButtonKeepsTouchTargetAndDescription() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MediaViewerTheme {
                    MediaIconButton(
                        icon = MediaIcons.Search,
                        contentDescription = "搜索",
                        onClick = {},
                    )
                }
            }
        }

        val bounds = rule.onNodeWithContentDescription("搜索")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width >= 48f)
        assertTrue(bounds.height >= 48f)
    }

    @Test
    fun loadingButtonIsDisabledAndExposesState() {
        rule.setContent {
            MediaViewerTheme {
                MediaPrimaryButton(
                    label = "保存",
                    onClick = {},
                    loading = true,
                )
            }
        }

        rule.onNodeWithText("保存")
            .assertIsNotEnabled()
        rule.onNodeWithContentDescription("保存，正在处理")
            .assertIsDisplayed()
    }

    @Test
    fun selectedIconAndStatePanelExposeActions() {
        var calls = 0
        rule.setContent {
            MediaViewerTheme {
                MediaIconButton(
                    icon = MediaIcons.ReaderMode,
                    contentDescription = "阅读模式",
                    stateDescription = "条漫",
                    selected = true,
                    onClick = {},
                )
                MediaStatePanel(
                    kind = MediaStateKind.ERROR,
                    title = "无法连接服务器",
                    message = "请检查地址后重试",
                    primaryAction = MediaAction("重试") { calls++ },
                    secondaryAction = MediaAction("打开设置") {},
                )
            }
        }

        rule.onNodeWithContentDescription("阅读模式")
            .assertIsSelected()
        rule.onNodeWithText("无法连接服务器").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun ordinaryIconOmitsSelectionAndExplicitStateWinsWhileLoading() {
        rule.setContent {
            MediaViewerTheme {
                MediaIconButton(
                    icon = MediaIcons.Refresh,
                    contentDescription = "刷新",
                    onClick = {},
                )
                MediaIconButton(
                    icon = MediaIcons.Refresh,
                    contentDescription = "重新连接",
                    onClick = {},
                    loading = true,
                    stateDescription = "正在重新连接服务器",
                )
            }
        }

        rule.onNodeWithContentDescription("刷新")
            .assert(
                SemanticsMatcher.keyNotDefined(
                    SemanticsProperties.Selected,
                ),
            )
        rule.onNodeWithContentDescription("重新连接")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "正在重新连接服务器",
                ),
            )
            .assertIsNotEnabled()
    }
}
