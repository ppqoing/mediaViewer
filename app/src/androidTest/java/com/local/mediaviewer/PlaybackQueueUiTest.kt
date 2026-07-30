package com.local.mediaviewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
import com.local.mediaviewer.ui.player.VolumeState
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
    fun queueSheetSelectsRemovesClearsAndExposesMoveDownAction() {
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

        rule.onNodeWithContentDescription("正在播放：第一首").assertIsDisplayed()
        rule.onNodeWithText("第二首").performClick()
        assertEquals("b", selected)
        rule.onNodeWithContentDescription("拖动排序 第一首")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .first { it.label == "下移" }
            .action()
        assertEquals("a" to 1, moved)
        rule.onNodeWithContentDescription("删除 第三首").performClick()
        assertEquals("c", removed)
        rule.onNodeWithContentDescription("清空其他").performClick()
        assertTrue(cleared)
    }

    @Test
    fun draggingQueueHandleDownMovesItemOnce() {
        var moved: Pair<String, Int>? = null
        val queue = PlaybackQueue(
            items = listOf(item("a", "第一首"), item("b", "第二首")),
            currentMediaKey = "a",
        )

        rule.setContent {
            MaterialTheme {
                PlaybackQueueSheet(
                    queue = queue,
                    onSelect = {},
                    onMove = { key, index -> moved = key to index },
                    onRemove = {},
                    onClearExceptCurrent = {},
                    onStopAndClear = {},
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithContentDescription("拖动排序 第一首")
            .performTouchInput { swipeDown() }
        assertEquals("a" to 1, moved)
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
    fun nowPlayingBarExposesTransportAndNavigationActions() {
        var toggled = false
        var next = false
        var openedQueue = false
        var openedPlayer = false
        rule.setContent {
            MaterialTheme {
                NowPlayingBar(
                    state = PlaybackSessionState(
                        playback = PlaybackState(status = PlaybackStatus.PLAYING),
                        queue = PlaybackQueue(
                            items = listOf(item("a", "正在播放的歌曲")),
                            currentMediaKey = "a",
                        ),
                        currentItem = item("a", "正在播放的歌曲"),
                    ),
                    volumeState = VolumeState(current = 5, maximum = 10, muted = false),
                    onVolumeRefresh = {},
                    onToggleMute = {},
                    onVolumeChanged = {},
                    onToggle = { toggled = true },
                    onNext = { next = true },
                    onOpenQueue = { openedQueue = true },
                    onOpenPlayer = { openedPlayer = true },
                )
            }
        }

        rule.onNodeWithText("正在播放的歌曲").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("暂停").performClick()
        rule.onNodeWithContentDescription("下一项").performClick()
        rule.onNodeWithContentDescription("音量，当前 50%，未静音").performClick()
        rule.onNodeWithTag("volume_slider_vertical").assertIsDisplayed()
        rule.onNodeWithContentDescription("打开队列").performClick()
        assertTrue(toggled)
        assertTrue(next)
        assertTrue(openedQueue)
        assertTrue(openedPlayer)
    }

    private fun item(key: String, name: String) = QueueMediaItem(
        mediaKey = key,
        name = name,
        logicalUrl = "http://media.test/$key.mp3",
        kind = MediaKind.AUDIO,
    )
}
