package com.local.mediaviewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.ui.player.NowPlayingBar
import com.local.mediaviewer.ui.player.PlaybackModeButton
import com.local.mediaviewer.ui.player.PlaybackQueueSheet
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun queueSheetShowsLayeredRowsAndExposesActionsWithoutVisibleMoveButtons() {
        var selected: String? = null
        var moved: Pair<String, Int>? = null
        var removed: String? = null
        var cleared = false
        val queue = PlaybackQueue(
            items = listOf(item("a", "第一首"), item("b", "第二首"), item("c", "第三首")),
            currentMediaKey = "a",
        )

        rule.setContent {
            MaterialTheme {
                PlaybackQueueSheet(
                    queue = queue,
                    onSelect = { selected = it },
                    onMove = { key, index -> moved = key to index },
                    onRemove = { removed = it },
                    onClearExceptCurrent = { cleared = true },
                    onStopAndClear = {},
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithText("播放队列 · 3 项").assertIsDisplayed()
        rule.onNodeWithText("顺序播放").assertIsDisplayed()
        rule.onNodeWithContentDescription("队列项 第一首，正在播放")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("队列项 第二首，即将播放")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("上移 第一首").assertDoesNotExist()
        rule.onNodeWithContentDescription("下移 第一首").assertDoesNotExist()

        rule.onNodeWithContentDescription("队列项 第二首，即将播放").performClick()
        assertEquals("b", selected)
        rule.onNodeWithContentDescription("队列项 第一首，正在播放")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .first { it.label == "下移" }
            .action()
        assertEquals("a" to 1, moved)
        rule.onNodeWithContentDescription("删除 第三首").performClick()
        assertEquals("c", removed)
        rule.onNodeWithText("清空其他").performClick()
        assertTrue(cleared)
    }

    @Test
    fun deletingCurrentQueueItemRequiresConfirmation() {
        var removed: String? = null
        val queue = PlaybackQueue(
            items = listOf(item("a", "第一首"), item("b", "第二首")),
            currentMediaKey = "a",
        )

        rule.setContent {
            MaterialTheme {
                PlaybackQueueSheet(
                    queue = queue,
                    onSelect = {},
                    onMove = { _, _ -> },
                    onRemove = { removed = it },
                    onClearExceptCurrent = {},
                    onStopAndClear = {},
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithContentDescription("删除 第一首").performClick()
        rule.onNodeWithText("删除正在播放的项目？").assertIsDisplayed()
        assertEquals(null, removed)
        rule.onNodeWithText("删除").performClick()
        assertEquals("a", removed)
    }

    @Test
    fun swipingQueueNormallyScrollsWithoutReordering() {
        val moves = mutableListOf<Pair<String, Int>>()
        val queue = PlaybackQueue(
            items = (1..12).map { index -> item("$index", "第${index}首") },
            currentMediaKey = "1",
        )

        rule.setContent {
            MaterialTheme {
                PlaybackQueueSheet(
                    queue = queue,
                    onSelect = {},
                    onMove = { key, index -> moves += key to index },
                    onRemove = {},
                    onClearExceptCurrent = {},
                    onStopAndClear = {},
                    onDismiss = {},
                )
            }
        }

        rule.onNode(hasScrollAction()).performTouchInput { swipeUp() }

        rule.runOnIdle { assertTrue(moves.isEmpty()) }
        rule.onNodeWithContentDescription("队列项 第12首").assertIsDisplayed()
    }

    @Test
    fun longPressThenDraggingWholeQueueRowDownMovesItemOnce() {
        val moves = mutableListOf<Pair<String, Int>>()
        val queue = PlaybackQueue(
            items = listOf(item("a", "第一首"), item("b", "第二首")),
            currentMediaKey = "a",
        )

        rule.setContent {
            MaterialTheme {
                PlaybackQueueSheet(
                    queue = queue,
                    onSelect = {},
                    onMove = { key, index -> moves += key to index },
                    onRemove = {},
                    onClearExceptCurrent = {},
                    onStopAndClear = {},
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithContentDescription("队列项 第一首，正在播放")
            .performTouchInput {
                val start = Offset(center.x, height * 0.2f)
                down(start)
                advanceEventTime(1_000L)
                moveTo(Offset(center.x, height * 0.9f))
                up()
            }
        rule.runOnIdle { assertEquals(listOf("a" to 1), moves) }
    }

    @Test
    fun modeButtonCyclesAllModesWithTextualState() {
        var mode by mutableStateOf(PlaybackMode.SEQUENTIAL)
        rule.setContent {
            MaterialTheme {
                PlaybackModeButton(
                    mode = mode,
                    onModeChanged = { mode = it },
                )
            }
        }

        rule.onNodeWithContentDescription("播放模式：顺序播放")
            .assertIsDisplayed()
            .assertIsSelected()
            .performClick()
        rule.onNodeWithContentDescription("播放模式：列表循环")
            .assertIsDisplayed()
            .assertIsSelected()
            .performClick()
        rule.onNodeWithContentDescription("播放模式：单曲循环").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("播放模式：随机播放").assertIsDisplayed()
    }

    @Test
    fun miniPrimaryUsesRealPlayPauseAndReplayCallbacks() {
        val item = item("a", "movie.mp4", MediaKind.VIDEO)
        var plays = 0
        var pauses = 0
        var replays = 0
        var state by mutableStateOf(
            PlaybackSessionState(
                playback = PlaybackState(status = PlaybackStatus.IDLE),
                queue = PlaybackQueue(listOf(item), item.mediaKey),
                currentItem = item,
            ),
        )
        rule.setContent {
            MediaViewerTheme {
                NowPlayingBar(
                    state = state,
                    onPlay = { plays++ },
                    onPause = { pauses++ },
                    onReplay = { replays++ },
                    onNext = {},
                    onOpenQueue = {},
                    onOpenPlayer = {},
                )
            }
        }

        rule.onNodeWithContentDescription("音量", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("音量，当前 50%，未静音").assertDoesNotExist()
        rule.onNodeWithContentDescription("播放").performClick()
        rule.runOnIdle {
            assertEquals(1, plays)
            state = state.copy(
                playback = state.playback.copy(status = PlaybackStatus.BUFFERING),
            )
        }
        rule.onNodeWithContentDescription("暂停")
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "正在缓冲，可暂停",
                ),
            )
            .performClick()
        rule.runOnIdle {
            assertEquals(1, pauses)
            state = state.copy(
                playback = state.playback.copy(status = PlaybackStatus.ENDED),
            )
        }
        rule.onNodeWithContentDescription("重新播放").performClick()
        rule.runOnIdle { assertEquals(1, replays) }
    }

    private fun item(
        key: String,
        name: String,
        kind: MediaKind = MediaKind.AUDIO,
    ) = QueueMediaItem(
        mediaKey = key,
        name = name,
        logicalUrl = "http://media.test/$key",
        kind = kind,
    )
}
