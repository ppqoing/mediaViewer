package com.local.mediaviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    @Test
    fun miniPlayerShowsRealProgressAndDisablesUnavailableNext() {
        val current = item("video", "movie.mp4", MediaKind.VIDEO)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(600.dp)) {
                        NowPlayingBar(
                            state = PlaybackSessionState(
                                playback = PlaybackState(
                                    status = PlaybackStatus.BUFFERING,
                                    positionMs = 25_000L,
                                    durationMs = 100_000L,
                                ),
                                queue = PlaybackQueue(
                                    items = listOf(current),
                                    currentMediaKey = current.mediaKey,
                                ),
                                currentItem = current,
                                canSkipNext = false,
                            ),
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = {},
                            onOpenPlayer = {},
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("mini_player_progress")
            .assertIsDisplayed()
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.25f, 0f..1f))
        rule.onNodeWithContentDescription("暂停")
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "正在缓冲，可暂停",
                ),
            )
        rule.onNodeWithContentDescription("下一项").assertIsNotEnabled()
        rule.onNodeWithContentDescription("调节音量").assertDoesNotExist()
    }

    @Test
    fun compactMiniKeepsPrimaryAndQueueWithoutNextOrVolume() {
        val current = item("audio", "long song name.flac", MediaKind.AUDIO)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(320.dp)) {
                        NowPlayingBar(
                            state = PlaybackSessionState(
                                playback = PlaybackState(
                                    status = PlaybackStatus.PAUSED,
                                    positionMs = 10_000L,
                                    durationMs = 100_000L,
                                ),
                                queue = PlaybackQueue(
                                    items = listOf(
                                        current,
                                        item("next", "next.flac", MediaKind.AUDIO),
                                    ),
                                    currentMediaKey = current.mediaKey,
                                ),
                                currentItem = current,
                                canSkipNext = true,
                            ),
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = {},
                            onOpenPlayer = {},
                        )
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
        rule.onNodeWithTag("queue_entry_mini").assertIsDisplayed()
        rule.onNodeWithContentDescription("下一项").assertDoesNotExist()
        rule.onNodeWithContentDescription("调节音量").assertDoesNotExist()
    }

    @Test
    fun miniProgressUsesClampedActualPosition() {
        val current = item("prog", "clip.mp4", MediaKind.VIDEO)
        var session by mutableStateOf(
            miniSession(
                current,
                status = PlaybackStatus.PLAYING,
                positionMs = 25_000L,
                durationMs = 100_000L,
                bufferedPercent = 0.9f,
            ),
        )
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(600.dp)) {
                        NowPlayingBar(
                            state = session,
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = {},
                            onOpenPlayer = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag("mini_player_progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.25f, 0f..1f))
        rule.runOnIdle {
            session = session.copy(
                playback = session.playback.copy(positionMs = 150_000L),
            )
        }
        rule.onNodeWithTag("mini_player_progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
        rule.runOnIdle {
            session = session.copy(
                playback = session.playback.copy(positionMs = 5_000L, durationMs = 0L),
            )
        }
        rule.onNodeWithTag("mini_player_progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
    }

    @Test
    fun unavailableNextIsDisabledAndCannotInvokeCallback() {
        val current = item("last", "last.flac", MediaKind.AUDIO)
        var nextCalls = 0
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(600.dp)) {
                        NowPlayingBar(
                            state = miniSession(
                                current,
                                items = listOf(
                                    current,
                                    item("other", "other.flac", MediaKind.AUDIO),
                                ),
                                canSkipNext = false,
                            ),
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = { nextCalls++ },
                            onOpenQueue = {},
                            onOpenPlayer = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithContentDescription("下一项").assertIsNotEnabled()
        rule.onNodeWithContentDescription("下一项").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(0, nextCalls) }
    }

    @Test
    fun responsiveThresholdTreatsOnlyWidthsBelow360AsCompact() {
        val current = item("edge", "edge.flac", MediaKind.AUDIO)
        var width by mutableStateOf(359.dp)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(width)) {
                        NowPlayingBar(
                            state = miniSession(current),
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = {},
                            onOpenPlayer = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithContentDescription("下一项").assertDoesNotExist()
        rule.runOnIdle { width = 360.dp }
        rule.onNodeWithContentDescription("下一项").assertIsDisplayed()
    }

    @Test
    fun twoXFontUsesCompactActionsEvenAtWideWidth() {
        val current = item("wide", "wide.flac", MediaKind.AUDIO)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(600.dp)) {
                        NowPlayingBar(
                            state = miniSession(current),
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = {},
                            onOpenPlayer = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
        rule.onNodeWithTag("queue_entry_mini").assertIsDisplayed()
        rule.onNodeWithContentDescription("下一项").assertDoesNotExist()
    }

    @Test
    fun mediaIdentityOpensPlayerWithoutStealingControlClicks() {
        val current = item("identity", "song.flac", MediaKind.AUDIO)
        var opens = 0
        var plays = 0
        var queueCalls = 0
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(600.dp)) {
                        NowPlayingBar(
                            state = miniSession(current),
                            onPlay = { plays++ },
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = { queueCalls++ },
                            onOpenPlayer = { opens++ },
                        )
                    }
                }
            }
        }

        rule.onNodeWithContentDescription("打开播放器：song.flac").performClick()
        rule.runOnIdle {
            assertEquals(1, opens)
            assertEquals(0, plays)
            assertEquals(0, queueCalls)
        }
        rule.onNodeWithContentDescription("播放").performClick()
        rule.runOnIdle {
            assertEquals(1, plays)
            assertEquals(1, opens)
        }
        val identityBounds = rule.onNodeWithContentDescription("打开播放器：song.flac")
            .getBoundsInRoot()
        assertTrue(identityBounds.bottom - identityBounds.top >= 48.dp)
        rule.onNodeWithTag("queue_entry_mini").performClick()
        rule.runOnIdle {
            assertEquals(1, queueCalls)
            assertEquals(1, opens)
        }
    }

    @Test
    fun miniQueueEntryUsesStableTagDescriptionAndCallback() {
        val current = item("queue", "queued.flac", MediaKind.AUDIO)
        var queueCalls = 0
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(600.dp)) {
                        NowPlayingBar(
                            state = miniSession(current),
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = { queueCalls++ },
                            onOpenPlayer = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag("queue_entry_mini")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("打开播放队列"),
                ),
            )
            .performClick()
        rule.runOnIdle { assertEquals(1, queueCalls) }
    }

    @Test
    fun emptyQueueDoesNotRenderMiniPlayer() {
        val stale = item("stale", "stale.flac", MediaKind.AUDIO)
        var session by mutableStateOf(
            PlaybackSessionState(
                playback = PlaybackState(
                    status = PlaybackStatus.PAUSED,
                    positionMs = 10_000L,
                    durationMs = 100_000L,
                ),
            ),
        )
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(600.dp)) {
                        NowPlayingBar(
                            state = session,
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = {},
                            onOpenPlayer = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithContentDescription("下一项").assertDoesNotExist()
        rule.onNodeWithTag("mini_player_progress").assertDoesNotExist()
        rule.runOnIdle { session = session.copy(currentItem = stale) }
        rule.onNodeWithContentDescription("下一项").assertDoesNotExist()
        rule.onNodeWithTag("mini_player_progress").assertDoesNotExist()
    }

    @Test
    fun miniDockIsSeventyTwoDpAndActionsDoNotOverlapTextAtLargeFont() {
        val current = item("dock", "dock song.flac", MediaKind.AUDIO)
        var width by mutableStateOf(600.dp)
        var fontScale by mutableStateOf(2f)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(width)) {
                        NowPlayingBar(
                            state = miniSession(current),
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = {},
                            onOpenPlayer = {},
                            modifier = Modifier.testTag("mini_dock"),
                        )
                    }
                }
            }
        }

        fun assertDockLayout() {
            val dock = rule.onNodeWithTag("mini_dock").getBoundsInRoot()
            assertEquals(72.0, (dock.bottom - dock.top).value.toDouble(), 0.5)
            val identity = rule.onNodeWithContentDescription("打开播放器：${current.name}")
                .getBoundsInRoot()
            val primary = rule.onNodeWithContentDescription("播放").getBoundsInRoot()
            val queue = rule.onNodeWithTag("queue_entry_mini").getBoundsInRoot()
            assertTrue(identity.right <= primary.left + 0.5.dp)
            assertTrue(primary.right <= queue.left + 0.5.dp)
            assertTrue(primary.right - primary.left >= 48.dp)
            assertTrue(primary.bottom - primary.top >= 48.dp)
            assertTrue(queue.right - queue.left >= 48.dp)
            assertTrue(queue.bottom - queue.top >= 48.dp)
        }

        assertDockLayout()
        rule.runOnIdle {
            width = 320.dp
            fontScale = 1f
        }
        assertDockLayout()
    }

    @Test
    fun bufferingRingIsVisibleButDecorative() {
        val current = item("ring", "ring.flac", MediaKind.AUDIO)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
            ) {
                MediaViewerTheme {
                    Box(Modifier.width(600.dp)) {
                        NowPlayingBar(
                            state = miniSession(
                                current,
                                status = PlaybackStatus.BUFFERING,
                            ),
                            onPlay = {},
                            onPause = {},
                            onReplay = {},
                            onNext = {},
                            onOpenQueue = {},
                            onOpenPlayer = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag("mini_buffering_ring").assertIsDisplayed()
        rule.onAllNodes(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            ),
        ).assertCountEquals(0)
        rule.onNodeWithContentDescription("暂停")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "正在缓冲，可暂停",
                ),
            )
    }

    private fun miniSession(
        current: QueueMediaItem,
        status: PlaybackStatus = PlaybackStatus.PAUSED,
        positionMs: Long = 10_000L,
        durationMs: Long = 100_000L,
        bufferedPercent: Float = 0f,
        items: List<QueueMediaItem> = listOf(current),
        currentItem: QueueMediaItem? = current,
        canSkipNext: Boolean = true,
    ) = PlaybackSessionState(
        playback = PlaybackState(
            status = status,
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedPercent = bufferedPercent,
        ),
        queue = PlaybackQueue(items = items, currentMediaKey = current.mediaKey),
        currentItem = currentItem,
        canSkipNext = canSkipNext,
    )

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
