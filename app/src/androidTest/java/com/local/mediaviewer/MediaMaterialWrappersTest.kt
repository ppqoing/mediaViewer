package com.local.mediaviewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.components.MediaConfirmDialog
import com.local.mediaviewer.ui.components.MediaOption
import com.local.mediaviewer.ui.components.MediaOptionMenu
import com.local.mediaviewer.ui.components.MediaTimelineSlider
import com.local.mediaviewer.ui.components.MediaVerticalLevelControl
import com.local.mediaviewer.ui.settings.MediaUrlField
import com.local.mediaviewer.ui.settings.MediaUrlFieldState
import com.local.mediaviewer.ui.settings.SettingsChoiceRow
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaMaterialWrappersTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun verticalLevelUsesRangeSemanticsPointerAndBottomAlignedFill() {
        var value by mutableFloatStateOf(0.25f)
        rule.setContent {
            MediaViewerTheme {
                Column(modifier = Modifier.padding(top = 64.dp)) {
                    MediaVerticalLevelControl(
                        value = value,
                        label = "音量 25%",
                        onValueChanged = { value = it },
                        modifier = Modifier.testTag("vertical_level"),
                    )
                }
            }
        }

        rule.onNodeWithTag("vertical_level")
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(0.25f, 0f..1f),
            )
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(0.75f)
            }
        rule.runOnIdle { assertEquals(0.75f, value, 0.001f) }

        rule.onNodeWithTag("vertical_level").performTouchInput {
            click(Offset(center.x, 1f))
        }
        rule.runOnIdle { assertTrue(value > 0.95f) }
        rule.onNodeWithTag("vertical_level").performTouchInput {
            click(Offset(center.x, height - 1f))
        }
        rule.runOnIdle { assertTrue(value < 0.05f) }

        rule.runOnIdle { value = 0.25f }
        val pixels = rule.onNodeWithTag("vertical_level")
            .captureToImage()
            .toPixelMap()
        val x = pixels.width / 2
        assertNotEquals(
            "top remains unfilled while bottom is filled",
            pixels[x, 2],
            pixels[x, pixels.height - 3],
        )
    }

    @Test
    fun verticalLevelContinuesDragAcrossValueRecomposition() {
        var value by mutableFloatStateOf(0.5f)
        val values = mutableListOf<Float>()
        rule.setContent {
            MediaViewerTheme {
                Column(modifier = Modifier.padding(top = 64.dp)) {
                    MediaVerticalLevelControl(
                        value = value,
                        label = "音量",
                        onValueChanged = {
                            values += it
                            value = it
                        },
                        modifier = Modifier.testTag("drag_level"),
                    )
                }
            }
        }

        rule.onNodeWithTag("drag_level").performTouchInput {
            down(Offset(center.x, height - 8f))
        }
        rule.runOnIdle { assertTrue(values.isNotEmpty()) }
        rule.onNodeWithTag("drag_level").performTouchInput {
            moveTo(Offset(center.x, 8f))
        }
        rule.runOnIdle {
            assertTrue(
                "drag stopped after recomposition: values=$values, value=$value",
                values.size >= 2 && value > 0.9f,
            )
        }
        rule.onNodeWithTag("drag_level").performTouchInput { up() }
    }

    @Test
    fun disabledVerticalLevelIgnoresPointerInput() {
        var calls = 0
        rule.setContent {
            MediaViewerTheme {
                MediaVerticalLevelControl(
                    value = 0.5f,
                    label = "亮度 50%",
                    enabled = false,
                    onValueChanged = { calls++ },
                    modifier = Modifier.testTag("disabled_level"),
                )
            }
        }
        rule.onNodeWithTag("disabled_level")
            .assertIsNotEnabled()
            .performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun timelineBeginsOncePreviewsCommitsOnceAndClamps() {
        var position by mutableLongStateOf(5_000L)
        var starts = 0
        val previews = mutableListOf<Long>()
        val commits = mutableListOf<Long>()
        rule.setContent {
            MediaViewerTheme {
                MediaTimelineSlider(
                    durationMs = 10_000L,
                    positionMs = position,
                    onDragStart = { starts++ },
                    onPositionPreview = {
                        previews += it
                        position = it
                    },
                    onPositionCommit = {
                        commits += it
                        position = it
                    },
                    modifier = Modifier
                        .width(240.dp)
                        .testTag("timeline"),
                )
            }
        }

        rule.onNodeWithTag("timeline").performTouchInput {
            down(Offset(8f, center.y))
            moveTo(Offset(width * 0.55f, center.y))
            moveTo(Offset(width - 8f, center.y))
            up()
        }
        rule.runOnIdle {
            assertEquals(
                "timeline callbacks: previews=$previews, commits=$commits",
                1,
                starts,
            )
            assertTrue(previews.isNotEmpty())
            assertTrue(previews.all { it in 0L..10_000L })
            assertEquals(1, commits.size)
            assertTrue(commits.single() in 0L..10_000L)
        }

        rule.runOnIdle { position = 50_000L }
        rule.onNodeWithTag("timeline").assertRangeInfoEquals(
            ProgressBarRangeInfo(10_000f, 0f..10_000f),
        )
    }

    @Test
    fun disabledTimelineDoesNotBeginPreviewOrCommit() {
        var calls = 0
        rule.setContent {
            MediaViewerTheme {
                MediaTimelineSlider(
                    durationMs = 10_000L,
                    positionMs = 2_000L,
                    enabled = false,
                    onDragStart = { calls++ },
                    onPositionPreview = { calls++ },
                    onPositionCommit = { calls++ },
                    modifier = Modifier
                        .width(240.dp)
                        .testTag("disabled_timeline"),
                )
            }
        }
        rule.onNodeWithTag("disabled_timeline")
            .assertIsNotEnabled()
            .performTouchInput {
                swipe(centerLeft, centerRight, 300L)
            }
        rule.runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun dialogExposesOnlyRealActions() {
        rule.setContent {
            MediaViewerTheme {
                MediaConfirmDialog(
                    title = "删除队列项",
                    message = "此操作不会删除文件",
                    confirmLabel = "删除",
                    dismissLabel = "取消",
                    destructive = true,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("删除").assertHasClickAction()
        rule.onNodeWithText("取消").assertHasClickAction()
        rule.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun optionMenuExposesRealSelection() {
        rule.setContent {
            MediaViewerTheme {
                MediaOptionMenu(
                    expanded = true,
                    options = listOf(
                        MediaOption("name", "按名称"),
                        MediaOption("date", "按日期"),
                    ),
                    selectedKey = "date",
                    onSelect = {},
                    onDismissRequest = {},
                )
            }
        }
        rule.onNodeWithText("按日期").assertIsSelected()
        rule.onNodeWithText("按名称").assertIsNotSelected()
    }

    @Test
    fun optionMenuWithoutSelectionOmitsSelectionSemantics() {
        rule.setContent {
            MediaViewerTheme {
                MediaOptionMenu(
                    expanded = true,
                    options = listOf(
                        MediaOption("play", "立即播放"),
                        MediaOption("next", "下一项播放"),
                    ),
                    selectedKey = null,
                    onSelect = {},
                    onDismissRequest = {},
                )
            }
        }

        rule.onNodeWithText("立即播放")
            .assert(
                SemanticsMatcher.keyNotDefined(
                    SemanticsProperties.Selected,
                ),
            )
        rule.onNodeWithText("下一项播放")
            .assert(
                SemanticsMatcher.keyNotDefined(
                    SemanticsProperties.Selected,
                ),
            )
    }

    @Test
    fun urlFieldAndChoiceRowExposeResultAndSelectionSemantics() {
        rule.setContent {
            MediaViewerTheme {
                Column {
                    MediaUrlField(
                        value = "http://media.example:8080",
                        onValueChange = {},
                        state = MediaUrlFieldState.TESTING,
                        modifier = Modifier.testTag("testing_url"),
                    )
                    MediaUrlField(
                        value = "http://media.example:8080",
                        onValueChange = {},
                        state = MediaUrlFieldState.SUCCESS,
                        selectedIpv4 = "192.0.2.8",
                    )
                    MediaUrlField(
                        value = "bad-url",
                        onValueChange = {},
                        state = MediaUrlFieldState.ERROR,
                        errorMessage = "URL 无效",
                        modifier = Modifier.testTag("error_url"),
                    )
                    SettingsChoiceRow(
                        title = "条漫",
                        description = "纵向连续阅读图片",
                        selected = true,
                        onClick = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("testing_url").assertIsNotEnabled()
        rule.onNodeWithText("正在测试连接").assertIsDisplayed()
        rule.onNodeWithText("将连接到 192.0.2.8").assertIsDisplayed()
        rule.onNodeWithText("URL 无效")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        rule.onNodeWithText("条漫").assertIsSelected()
    }
}
