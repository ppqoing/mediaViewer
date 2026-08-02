package com.local.mediaviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.app.MediaViewerApp
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackNotice
import com.local.mediaviewer.queue.PlaybackNoticeAction
import com.local.mediaviewer.queue.PlaybackNoticeKind
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.testing.FakeAppContainer
import com.local.mediaviewer.ui.player.NowPlayingBar
import com.local.mediaviewer.ui.player.PlaybackModeButton
import com.local.mediaviewer.ui.player.PlaybackQueueSheet
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val appContainers = mutableListOf<FakeAppContainer>()

    @After
    fun closeAppContainers() {
        appContainers.forEach(FakeAppContainer::close)
        appContainers.clear()
    }

    // 计划的 mini→ordinary→fullscreen 序列在 compose 测试宿主上不可完整执行：
    // 真实 FullscreenWindowPolicy 进入全屏时设置 SENSOR_LANDSCAPE，
    // androidx.test.core 的 EmptyActivity 宿主未声明 configChanges，
    // Activity 立即重建并清空 setContent 层次（logcat 可见
    // VRI[InstrumentationActivityInvoker$EmptyActivity] 销毁，
    // 后续断言报 "No compose hierarchies found"）。
    // 普通与全屏队列入口在 root 共享同一个 onOpenQueue 回调
    // （VideoPlayerScreen 内部把两者都接到传入的 onOpenQueue），
    // 因此根级接线由 mini/ordinary 两条腿覆盖；
    // queue_entry_fullscreen 按钮本身由 VideoControlsOverlayTest 锁定。
    @Test
    fun miniThenOrdinaryQueueEntriesOpenTheSameRootQueueSheet() {
        launchRootQueueApp()

        rule.onNodeWithTag("queue_entry_mini").performClick()
        rule.onNodeWithText("播放队列 · 3 项").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭播放队列").performClick()

        rule.onNodeWithContentDescription("打开播放器：movie.mp4").performClick()
        rule.onNodeWithTag("queue_entry_ordinary").performClick()
        rule.onNodeWithText("播放队列 · 3 项").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭播放队列").performClick()
    }

    @Test
    fun ordinaryDeleteUndoRestoresTheOriginalItemAndIndex() {
        val container = launchRootQueueApp()
        rule.onNodeWithTag("queue_entry_mini").performClick()
        rule.onNodeWithContentDescription("删除 第二首.mp3").performClick()
        rule.onNodeWithText("已从队列删除 第二首.mp3").assertIsDisplayed()
        rule.onNodeWithText("撤销").performClick()

        rule.waitUntil(5_000) {
            container.fakePlaybackController.sessionState.value.queue.items
                .map(QueueMediaItem::mediaKey) ==
                listOf("video", "second", "third")
        }
        assertEquals(
            listOf("second"),
            container.fakePlaybackController.removeCalls,
        )
        assertEquals(
            listOf("second"),
            container.fakePlaybackController.appendCalls.map { it.mediaKey },
        )
        assertEquals(
            listOf("second" to 1),
            container.fakePlaybackController.moveCalls,
        )
    }

    @Test
    fun persistenceNoticeRetryKeepsTheRootQueueSheetOpenAndDeduplicatesId() {
        val container = launchRootQueueApp()
        rule.onNodeWithTag("queue_entry_mini").performClick()
        val notice = PlaybackNotice(
            id = 9L,
            kind = PlaybackNoticeKind.QUEUE_SAVE_FAILED,
            message = "播放队列保存失败",
            action = PlaybackNoticeAction.RETRY_PERSISTENCE,
        )
        container.fakePlaybackController.emitNotice(notice)
        container.fakePlaybackController.emitNotice(notice)

        rule.onNodeWithText("播放队列保存失败").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle {
            assertEquals(
                1,
                container.fakePlaybackController.retryPersistenceCalls,
            )
        }
        rule.onNodeWithText("播放队列 · 3 项").assertIsDisplayed()
    }

    @Test
    fun persistence_notice_is_visible_with_queue_open_and_retry_keeps_the_sheet_open() {
        val container = FakeAppContainer(
            ApplicationProvider.getApplicationContext(),
        )
        appContainers += container
        rule.setContent { MediaViewerApp(container) }
        val fakePlaybackController = container.fakePlaybackController
        fakePlaybackController.replaceQueue(
            items = listOf(
                QueueMediaItem(
                    mediaKey = "a",
                    name = "第一首",
                    logicalUrl = "http://media.test/a.mp3",
                    kind = MediaKind.AUDIO,
                ),
            ),
            startMediaKey = "a",
        )
        rule.onNodeWithTag("queue_entry_mini").assertIsDisplayed().performClick()
        val notice = PlaybackNotice(
            id = 9L,
            kind = PlaybackNoticeKind.QUEUE_SAVE_FAILED,
            message = "播放队列保存失败",
            action = PlaybackNoticeAction.RETRY_PERSISTENCE,
        )
        fakePlaybackController.emitNotice(notice)
        fakePlaybackController.emitNotice(notice)

        rule.onNodeWithText("播放队列保存失败").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        assertEquals(1, fakePlaybackController.retryPersistenceCalls)
        rule.onNodeWithText("播放队列 · 1 项").assertIsDisplayed()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("播放队列保存失败")
                .fetchSemanticsNodes().isEmpty()
        }
        assertEquals(1, fakePlaybackController.retryPersistenceCalls)
    }

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

        rule.onNodeWithTag("queue_warm_paper").assertIsDisplayed()
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
    fun queueKeepsDragDeleteAndCurrentItemSemantics() {
        val current = item("current", "当前曲目.flac", MediaKind.AUDIO)
        showQueue(
            PlaybackQueue(
                items = listOf(current),
                currentMediaKey = current.mediaKey,
            ),
        )

        rule.onNodeWithTag("queue_row:current").assertIsSelected()
        rule.onNodeWithContentDescription("拖动排序 当前曲目.flac")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("删除 当前曲目.flac")
            .assertIsDisplayed()
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

        // 自适应高列表 + 半展开 Sheet：首刷的 fling 大半被 Sheet 展开/嵌套滚动消费，
        // 列表只前进约一屏余量；断言滚过后才进入可视区的项，而不是列底最后一项
        rule.onNodeWithTag("queue_list").performTouchInput {
            swipeUp()
            swipeUp()
        }

        rule.runOnIdle { assertTrue(moves.isEmpty()) }
        rule.onNodeWithContentDescription("队列项 第7首").assertIsDisplayed()
    }

    @Test
    fun handleDragAcrossThreeRowsCommitsOneFinalMoveAndNeverSelects() {
        val moves = mutableListOf<Pair<String, Int>>()
        var selected: String? = null
        val queue = PlaybackQueue(
            items = (0..5).map { index ->
                item("$index", "第${index + 1}首")
            },
            currentMediaKey = "0",
        )
        showQueue(
            queue = queue,
            onSelect = { selected = it },
            onMove = { key, index -> moves += key to index },
        )
        val rowExtent = rule.onNodeWithTag("queue_row:0")
            .fetchSemanticsNode().boundsInRoot.height

        rule.onNodeWithContentDescription("拖动排序 第1首")
            .performTouchInput {
                down(center)
                advanceEventTime(1_000L)
                moveBy(Offset(0f, rowExtent * 3.2f), delayMillis = 300L)
                up()
            }

        rule.runOnIdle {
            assertEquals(listOf("0" to 3), moves)
            assertNull(selected)
        }
    }

    @Test
    fun handleOvershootThenReverseCommitsTheNetIndexExactlyOnce() {
        val moves = mutableListOf<Pair<String, Int>>()
        val queue = PlaybackQueue(
            items = (0..5).map { index ->
                item("$index", "第${index + 1}首")
            },
            currentMediaKey = "0",
        )
        showQueue(
            queue = queue,
            onMove = { key, index -> moves += key to index },
        )
        val rowExtent = rule.onNodeWithTag("queue_row:2")
            .fetchSemanticsNode().boundsInRoot.height

        rule.onNodeWithContentDescription("拖动排序 第3首")
            .performTouchInput {
                down(center)
                advanceEventTime(1_000L)
                moveBy(
                    Offset(0f, rowExtent * 2.8f),
                    delayMillis = 200L,
                )
                moveBy(
                    Offset(0f, -rowExtent),
                    delayMillis = 200L,
                )
                up()
            }

        rule.runOnIdle {
            assertEquals(listOf("2" to 3), moves)
        }
    }

    @Test
    fun longQueueUsesAvailableHeightAndStaysAboveInjectedNavigationInset() {
        var keys by mutableStateOf((0 until 30).toList())
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MediaViewerTheme {
                    PlaybackQueueSheet(
                        queue = PlaybackQueue(
                            items = keys.map { item("$it", "第${it + 1}首") },
                            currentMediaKey = "0",
                        ),
                        onSelect = {},
                        onMove = { _, _ -> },
                        onRemove = {},
                        onRemoveRequest = { _, _ -> },
                        onClearExceptCurrent = {},
                        onStopAndClear = {},
                        onDismiss = {},
                        navigationBarsInsets = WindowInsets(bottom = 32.dp),
                    )
                }
            }
        }
        val sheet = rule.onNodeWithTag("queue_sheet")
            .fetchSemanticsNode().boundsInRoot
        val list = rule.onNodeWithTag("queue_list")
            .fetchSemanticsNode().boundsInRoot
        // 自适应：超过旧固定 480dp；受可用高度约束（30 项内容高 1920px，列表被截断）
        assertTrue(sheet.height > 480f)
        assertTrue(list.height > 480f)
        assertTrue(list.height < 30 * 64f)

        // 注入的导航栏 inset 被列表消费一次：短队列无需滚动，
        // 末项底部必须精确停在列表底边上 32px 处（少消费/重复消费都会失败）
        rule.runOnIdle { keys = (0 until 6).toList() }
        // 对话框使用真实密度：从 12dp 水平内边距实测密度，
        // inset 期望 = 32dp×density + 行标签之外的纵向边距（实测，随行结构自适应）
        val listBounds = rule.onNodeWithTag("queue_list")
            .fetchSemanticsNode().boundsInRoot
        val firstRow = rule.onNodeWithTag("queue_row:0")
            .fetchSemanticsNode().boundsInRoot
        val secondRow = rule.onNodeWithTag("queue_row:1")
            .fetchSemanticsNode().boundsInRoot
        val lastRow = rule.onNodeWithTag("queue_row:5")
            .fetchSemanticsNode().boundsInRoot
        val density = firstRow.height / 64f
        // 行标签之外的纵向边距对称分布（上/下各半），末项下方只计一半
        val rowMargin = (secondRow.top - firstRow.top) - firstRow.height
        val expectedGap = 32f * density + rowMargin / 2f
        assertEquals(
            expectedGap.toDouble(),
            (listBounds.bottom - lastRow.bottom).toDouble(),
            1.5,
        )
    }

    @Test
    fun draggingNearViewportEdgeAutoScrollsButStillCommitsOnce() {
        val moves = mutableListOf<Pair<String, Int>>()
        // 与生产一致的动态 fixture：onMove 真正应用移动，
        // 落点后列表滚动锚点才能保持（静态队列会按 key 锚回顶部）
        var keys by mutableStateOf((0 until 40).toList())
        rule.setContent {
            MediaViewerTheme {
                PlaybackQueueSheet(
                    queue = PlaybackQueue(
                        items = keys.map { item("$it", "第${it + 1}首") },
                        currentMediaKey = "0",
                    ),
                    onSelect = {},
                    onMove = { key, index ->
                        moves += key to index
                        keys = keys.toMutableList().apply {
                            add(index, removeAt(indexOf(key.toInt())))
                        }
                    },
                    onRemove = {},
                    onClearExceptCurrent = {},
                    onStopAndClear = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithContentDescription("拖动排序 第1首")
            .performTouchInput {
                down(center)
                advanceEventTime(1_000L)
                moveTo(Offset(center.x, height + 420f), delayMillis = 100L)
                advanceEventTime(1_500L)
                up()
            }
        // 落点把首项钉在视口底部（first≈dropIndex），第11首必在可见窗口内
        rule.onNodeWithText("第11首").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(1, moves.size)
            assertTrue(moves.single().second >= 8)
        }
    }

    @Test
    fun queueStatesCustomActionsAndRemovalContractsAreExplicit() {
        val current = item("a", "第一首")
        val next = item("b", "第二首")
        val later = item("c", "第三首")
        val fourth = item("d", "第四首")
        val accessibilityMoves = mutableListOf<Pair<String, Int>>()
        var ordinaryRemoval: Pair<QueueMediaItem, Int>? = null
        var currentRemoval: String? = null
        showQueue(
            queue = PlaybackQueue(
                items = listOf(current, next, later, fourth),
                currentMediaKey = current.mediaKey,
            ),
            onMove = { key, index -> accessibilityMoves += key to index },
            onRemove = { currentRemoval = it },
            onRemoveRequest = { item, index ->
                ordinaryRemoval = item to index
            },
        )

        rule.onNodeWithContentDescription("队列项 第一首，正在播放")
            .assertIsSelected()
        rule.onNodeWithContentDescription("队列项 第二首，即将播放")
            .assertIsDisplayed()
        val actions = rule.onNodeWithContentDescription(
            "队列项 第三首",
        ).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("上移", "下移", "删除"), actions.map { it.label })
        actions.first { it.label == "上移" }.action()
        rule.runOnIdle {
            assertEquals(listOf("c" to 1), accessibilityMoves)
        }
        rule.onNodeWithTag("queue_move_announcement")
            .assertTextEquals("已移动到第 2 项")

        rule.onNodeWithContentDescription("删除 第三首").performClick()
        rule.runOnIdle {
            assertEquals(later to 2, ordinaryRemoval)
            assertNull(currentRemoval)
        }
        rule.onNodeWithContentDescription("删除 第一首").performClick()
        rule.onNodeWithText("删除正在播放的项目？").assertIsDisplayed()
        rule.onNodeWithText("删除").performClick()
        rule.runOnIdle { assertEquals("a", currentRemoval) }
    }

    @Test
    fun oneItemDisablesClearOtherAndEmptyQueueHasAState() {
        showQueue(
            PlaybackQueue(
                items = listOf(item("a", "第一首")),
                currentMediaKey = "a",
            ),
        )
        rule.onNodeWithText("清空其他").assertIsNotEnabled()
    }

    @Test
    fun emptyQueueShowsExplicitStateAndCanClose() {
        var dismissCalls = 0
        showQueue(
            queue = PlaybackQueue(),
            onDismiss = { dismissCalls++ },
        )
        rule.onNodeWithText("播放队列为空").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭播放队列").performClick()
        rule.runOnIdle { assertEquals(1, dismissCalls) }
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
    fun miniPlayerControlsStayVisibleOnLightSurface() {
        val current = item("video", "movie.mp4", MediaKind.VIDEO)
        rule.setContent {
            MediaViewerTheme(darkTheme = false) {
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

        // 规格 §6.1：浅色页面上关键非文字控制至少 3:1，
        // 迷你条不得把黑底 PlayerColors 的近白控件放在浅色 surface 上。
        rule.onNodeWithTag("now_playing_warm_paper").assertIsDisplayed()
        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
        assertControlHasDarkPixels("播放")
        assertControlHasDarkPixels("打开播放队列")
    }

    @Test
    fun queueRowControlsStayVisibleOnLightSurface() {
        val first = item("a", "song-a.mp3")
        val second = item("b", "song-b.mp3")
        rule.setContent {
            MediaViewerTheme(darkTheme = false) {
                PlaybackQueueSheet(
                    queue = PlaybackQueue(
                        listOf(first, second),
                        first.mediaKey,
                    ),
                    onSelect = {},
                    onMove = { _, _ -> },
                    onRemove = {},
                    onClearExceptCurrent = {},
                    onStopAndClear = {},
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithContentDescription("删除 song-b.mp3")
            .assertIsDisplayed()
        assertControlHasDarkPixels("删除 song-b.mp3")
        assertControlHasDarkPixels("拖动排序 song-b.mp3")
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

    private fun rootItems() = listOf(
        item("video", "movie.mp4", MediaKind.VIDEO),
        item("second", "第二首.mp3", MediaKind.AUDIO),
        item("third", "第三首.mp3", MediaKind.AUDIO),
    )

    private fun launchRootQueueApp(
        items: List<QueueMediaItem> = rootItems(),
    ): FakeAppContainer {
        val container = FakeAppContainer(
            context = ApplicationProvider.getApplicationContext(),
            initialHasShownVideoGestures = true,
        )
        appContainers += container
        container.fakePlaybackController.replaceQueue(
            items = items,
            startMediaKey = items.first().mediaKey,
        )
        rule.setContent { MediaViewerApp(container) }
        rule.onNodeWithTag("queue_entry_mini").assertIsDisplayed()
        return container
    }

    private fun showQueue(
        queue: PlaybackQueue,
        onSelect: (String) -> Unit = {},
        onMove: (String, Int) -> Unit = { _, _ -> },
        onRemove: (String) -> Unit = {},
        onRemoveRequest: ((QueueMediaItem, Int) -> Unit)? = null,
        onDismiss: () -> Unit = {},
    ) {
        rule.setContent {
            MediaViewerTheme {
                PlaybackQueueSheet(
                    queue = queue,
                    onSelect = onSelect,
                    onMove = onMove,
                    onRemove = onRemove,
                    onRemoveRequest = onRemoveRequest,
                    onClearExceptCurrent = {},
                    onStopAndClear = {},
                    onDismiss = onDismiss,
                )
            }
        }
    }

    private fun assertControlHasDarkPixels(contentDescription: String) {
        val pixels = rule.onNodeWithContentDescription(contentDescription)
            .captureToImage()
            .toPixelMap()
        var dark = 0
        var samples = 0
        var y = 0
        while (y < pixels.height) {
            var x = 0
            while (x < pixels.width) {
                val color = pixels[x, y]
                val luminance =
                    0.2126 * color.red +
                        0.7152 * color.green +
                        0.0722 * color.blue
                if (luminance < 0.5) {
                    dark++
                }
                samples++
                x += 2
            }
            y += 2
        }
        val darkFraction = dark.toDouble() / samples
        assertTrue(
            "$contentDescription must stay visible on a light surface " +
                "(dark pixel fraction=$darkFraction)",
            darkFraction > 0.02,
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
