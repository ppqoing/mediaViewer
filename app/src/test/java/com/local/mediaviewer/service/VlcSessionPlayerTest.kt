package com.local.mediaviewer.service

import android.os.Looper
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackCoordinator
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackQueueRepository
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class VlcSessionPlayerTest {
    @Test
    fun `maps LibVLC playback statuses to Media3 state`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a")), "a")
        settle()
        val cases = listOf(
            Triple(PlaybackStatus.OPENING, Player.STATE_BUFFERING, true),
            Triple(PlaybackStatus.BUFFERING, Player.STATE_BUFFERING, true),
            Triple(PlaybackStatus.PLAYING, Player.STATE_READY, true),
            Triple(PlaybackStatus.PAUSED, Player.STATE_READY, false),
            Triple(PlaybackStatus.ENDED, Player.STATE_ENDED, false),
            Triple(PlaybackStatus.ERROR, Player.STATE_IDLE, false),
        )

        for ((status, expectedState, playIntent) in cases) {
            fixture.coordinator.setPlayWhenReadyFromSession(playIntent)
            fixture.engine.emit(
                PlaybackState(
                    status = status,
                    positionMs = 12_345L,
                    durationMs = 60_000L,
                    bufferedPercent = 25f,
                    isSeekable = true,
                    errorMessage = if (status == PlaybackStatus.ERROR) "boom" else null,
                ),
            )
            settle()

            assertEquals(expectedState, fixture.player.playbackState)
            assertEquals(playIntent, fixture.player.playWhenReady)
            assertEquals(12_345L, fixture.player.currentPosition)
            assertEquals(60_000L, fixture.player.duration)
            assertEquals(15_000L, fixture.player.bufferedPosition)
            if (status == PlaybackStatus.ERROR) {
                assertEquals("boom", fixture.player.playerError?.message)
            } else {
                assertNull(fixture.player.playerError)
            }
        }

        fixture.close()
    }

    @Test
    fun `maps queue order current index metadata and logical URI`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b")), "b")
        settle()

        assertEquals(2, fixture.player.mediaItemCount)
        assertEquals(1, fixture.player.currentMediaItemIndex)
        assertEquals("b", fixture.player.currentMediaItem?.mediaMetadata?.title?.toString())
        assertEquals(
            "http://media.example:8080/b.mp4",
            fixture.player.currentMediaItem?.localConfiguration?.uri?.toString(),
        )

        fixture.close()
    }

    @Test
    fun `advertises standard playback and queue commands`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        fixture.engine.emit(PlaybackState(status = PlaybackStatus.PAUSED, isSeekable = true))
        settle()

        assertTrue(fixture.player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertTrue(fixture.player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(fixture.player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(fixture.player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(fixture.player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertTrue(fixture.player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH))

        fixture.close()
    }

    @Test
    fun `non seekable item keeps previous and next but removes in-item seeks`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        fixture.engine.emit(PlaybackState(status = PlaybackStatus.PAUSED, isSeekable = false))
        settle()

        assertFalse(fixture.player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertFalse(fixture.player.isCommandAvailable(Player.COMMAND_SEEK_BACK))
        assertFalse(fixture.player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
        assertTrue(fixture.player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(fixture.player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))

        fixture.close()
    }

    @Test
    fun `delegates play pause seek next and playback speed`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        fixture.engine.emit(
            PlaybackState(
                status = PlaybackStatus.PAUSED,
                durationMs = 60_000L,
                isSeekable = true,
            ),
        )
        settle()
        fixture.engine.clearCalls()

        fixture.player.play()
        settle()
        assertEquals(1, fixture.engine.playCalls)

        fixture.engine.emit(PlaybackState(status = PlaybackStatus.PLAYING, isSeekable = true))
        settle()
        fixture.player.pause()
        settle()
        assertEquals(1, fixture.engine.pauseCalls)

        fixture.player.seekTo(4_321L)
        settle()
        assertEquals(listOf(4_321L), fixture.engine.seekCalls)

        fixture.player.seekToNextMediaItem()
        settle()
        assertEquals("b", fixture.coordinator.sessionState.value.currentItem?.mediaKey)

        fixture.player.setPlaybackSpeed(1.5f)
        settle()
        assertEquals(1.5f, fixture.coordinator.sessionState.value.queue.playbackSpeed)
        assertEquals(1.5f, fixture.engine.speedCalls.last())

        fixture.close()
    }

    @Test
    fun `buffering play intent survives until pause and Media3 next stays paused`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b"), item("c")), "a")
        settle()
        fixture.engine.emit(PlaybackState(status = PlaybackStatus.BUFFERING, isSeekable = true))
        settle()

        assertTrue(fixture.player.playWhenReady)

        fixture.engine.clearCalls()
        fixture.player.pause()
        settle()
        assertFalse(fixture.player.playWhenReady)
        assertEquals(1, fixture.engine.pauseCalls)

        fixture.engine.clearCalls()
        fixture.player.seekToNextMediaItem()
        settle()
        assertEquals("b", fixture.coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals(0, fixture.engine.playCalls)

        fixture.coordinator.skipNext()
        settle()
        assertEquals("c", fixture.coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals(1, fixture.engine.playCalls)
        fixture.close()
    }

    @Test
    fun `late playing after buffering pause is corrected once until paused`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a")), "a")
        settle()
        fixture.engine.emit(PlaybackState(status = PlaybackStatus.BUFFERING))
        settle()
        fixture.engine.clearCalls()

        fixture.player.pause()
        settle()
        assertEquals(1, fixture.engine.pauseCalls)
        assertFalse(fixture.player.playWhenReady)

        fixture.engine.emit(PlaybackState(status = PlaybackStatus.PLAYING, positionMs = 1L))
        settle()
        assertEquals(2, fixture.engine.pauseCalls)

        fixture.engine.emit(PlaybackState(status = PlaybackStatus.PLAYING, positionMs = 2L))
        settle()
        assertEquals(2, fixture.engine.pauseCalls)

        fixture.engine.emit(PlaybackState(status = PlaybackStatus.PAUSED, positionMs = 2L))
        settle()
        fixture.player.play()
        settle()
        fixture.engine.emit(PlaybackState(status = PlaybackStatus.PLAYING, positionMs = 3L))
        settle()
        assertEquals(2, fixture.engine.pauseCalls)
        assertTrue(fixture.player.playWhenReady)
        fixture.close()
    }

    @Test
    fun `Media3 next continues playback when play intent is true`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        fixture.engine.emit(PlaybackState(status = PlaybackStatus.PLAYING, isSeekable = true))
        settle()
        fixture.engine.clearCalls()

        fixture.player.seekToNextMediaItem()
        settle()

        assertEquals("b", fixture.coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals(1, fixture.engine.playCalls)
        assertTrue(fixture.player.playWhenReady)
        fixture.close()
    }

    @Test
    fun `delegates add move remove and replace media items`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        settle()

        fixture.player.addMediaItem(1, media3Item("c"))
        settle()
        assertEquals(listOf("a", "c", "b"), fixture.keys())

        fixture.player.moveMediaItem(2, 0)
        settle()
        assertEquals(listOf("b", "a", "c"), fixture.keys())

        fixture.player.removeMediaItem(1)
        settle()
        assertEquals(listOf("b", "c"), fixture.keys())

        fixture.player.setMediaItems(
            listOf(media3Item("x"), media3Item("y")),
            1,
            7_000L,
        )
        settle()
        assertEquals(listOf("x", "y"), fixture.keys())
        assertEquals("y", fixture.coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals(7_000L, fixture.engine.seekCalls.last())

        fixture.close()
    }

    @Test
    fun `removing current media item selects its following item`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b"), item("c")), "b")
        settle()

        fixture.player.removeMediaItem(1)
        settle()

        assertEquals(listOf("a", "c"), fixture.keys())
        assertEquals("c", fixture.coordinator.sessionState.value.currentItem?.mediaKey)
        fixture.close()
    }

    @Test
    fun `setting an empty playlist stops LibVLC and publishes legal empty state`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a")), "a")
        settle()
        fixture.engine.clearCalls()

        fixture.player.setMediaItems(emptyList())
        settle()

        assertEquals(0, fixture.player.mediaItemCount)
        assertEquals(0, fixture.player.currentMediaItemIndex)
        assertEquals(Player.STATE_IDLE, fixture.player.playbackState)
        assertEquals(1, fixture.engine.stopCalls)
        fixture.close()
    }

    @Test
    fun `paused Media3 replacement atomically selects replacement without playing`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b"), item("c")), "b")
        settle()
        fixture.player.pause()
        settle()
        fixture.engine.clearCalls()
        fixture.repository.clearSaveCalls()

        fixture.player.replaceMediaItems(1, 2, listOf(media3Item("x"), media3Item("y")))
        settle()

        assertEquals(listOf("a", "x", "y", "c"), fixture.keys())
        assertEquals("x", fixture.coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals(1, fixture.repository.saveCalls)
        assertEquals(0, fixture.engine.playCalls)
        assertEquals(requestUrlFor("x"), fixture.engine.prepareCalls.single())
        fixture.close()
    }

    @Test
    fun `playing Media3 replacement atomically selects and plays replacement`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b"), item("c")), "b")
        fixture.engine.emit(PlaybackState(status = PlaybackStatus.PLAYING, isSeekable = true))
        settle()
        fixture.engine.clearCalls()
        fixture.repository.clearSaveCalls()

        fixture.player.replaceMediaItem(1, media3Item("x"))
        settle()

        assertEquals(listOf("a", "x", "c"), fixture.keys())
        assertEquals("x", fixture.coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals(1, fixture.repository.saveCalls)
        assertEquals(1, fixture.engine.playCalls)
        assertEquals(requestUrlFor("x"), fixture.engine.prepareCalls.single())
        fixture.close()
    }

    @Test
    fun `replacing sole current item loads replacement without transient stop`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a")), "a")
        settle()
        fixture.player.pause()
        settle()
        fixture.engine.clearCalls()
        fixture.repository.clearSaveCalls()

        fixture.player.replaceMediaItem(0, media3Item("x"))
        settle()

        assertEquals(listOf("x"), fixture.keys())
        assertEquals("x", fixture.coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals(1, fixture.repository.saveCalls)
        assertEquals(0, fixture.engine.stopCalls)
        assertEquals(listOf(requestUrlFor("x")), fixture.engine.prepareCalls)
        fixture.close()
    }

    @Test
    fun `shuffle batch add keeps played prefix and inserts new keys after cursor`() = runTest {
        val (fixture, before) = advancedShuffleFixture()
        fixture.repository.clearSaveCalls()

        fixture.coordinator.add(2, listOf(item("x"), item("y")))
        settle()

        val after = fixture.coordinator.sessionState.value.queue
        assertEquals(before.shuffleCursor, after.shuffleCursor)
        assertEquals(
            before.shuffleOrder.take(before.shuffleCursor + 1),
            after.shuffleOrder.take(after.shuffleCursor + 1),
        )
        assertEquals(
            listOf("x", "y"),
            after.shuffleOrder.drop(after.shuffleCursor + 1).take(2),
        )
        assertEquals(1, fixture.repository.saveCalls)
        fixture.close()
    }

    @Test
    fun `shuffle batch move changes presentation order without changing shuffle history`() = runTest {
        val (fixture, before) = advancedShuffleFixture()
        fixture.repository.clearSaveCalls()

        fixture.coordinator.moveRange(3, 4, 0)
        settle()

        val after = fixture.coordinator.sessionState.value.queue
        assertEquals(before.shuffleOrder, after.shuffleOrder)
        assertEquals(before.shuffleCursor, after.shuffleCursor)
        assertEquals(1, fixture.repository.saveCalls)
        fixture.close()
    }

    @Test
    fun `shuffle batch remove only drops pending key and keeps cursor`() = runTest {
        val (fixture, before) = advancedShuffleFixture()
        val removedKey = before.shuffleOrder.last()
        val removedIndex = before.items.indexOfFirst { it.mediaKey == removedKey }
        fixture.repository.clearSaveCalls()

        fixture.coordinator.removeRange(removedIndex, removedIndex + 1)
        settle()

        val after = fixture.coordinator.sessionState.value.queue
        assertEquals(before.shuffleOrder.filterNot { it == removedKey }, after.shuffleOrder)
        assertEquals(before.shuffleCursor, after.shuffleCursor)
        assertEquals(1, fixture.repository.saveCalls)
        fixture.close()
    }

    @Test
    fun `shuffle range replacement keeps played prefix and queues replacements next`() = runTest {
        val (fixture, before) = advancedShuffleFixture()
        val replacedKey = before.shuffleOrder.last()
        val replacedIndex = before.items.indexOfFirst { it.mediaKey == replacedKey }
        fixture.repository.clearSaveCalls()

        fixture.coordinator.replaceRange(
            replacedIndex,
            replacedIndex + 1,
            listOf(item("x"), item("y")),
        )
        settle()

        val after = fixture.coordinator.sessionState.value.queue
        assertEquals(before.shuffleCursor, after.shuffleCursor)
        assertEquals(
            before.shuffleOrder.take(before.shuffleCursor + 1),
            after.shuffleOrder.take(after.shuffleCursor + 1),
        )
        assertEquals(
            listOf("x", "y"),
            after.shuffleOrder.drop(after.shuffleCursor + 1).take(2),
        )
        assertFalse(replacedKey in after.shuffleOrder)
        assertEquals(1, fixture.repository.saveCalls)
        fixture.close()
    }

    @Test
    fun `maps repeat and shuffle as mutually exclusive queue modes`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        settle()

        fixture.player.repeatMode = Player.REPEAT_MODE_ALL
        settle()
        assertEquals(PlaybackMode.REPEAT_ALL, fixture.mode())
        assertEquals(Player.REPEAT_MODE_ALL, fixture.player.repeatMode)
        assertFalse(fixture.player.shuffleModeEnabled)

        fixture.player.shuffleModeEnabled = true
        settle()
        assertEquals(PlaybackMode.SHUFFLE, fixture.mode())
        assertEquals(Player.REPEAT_MODE_OFF, fixture.player.repeatMode)
        assertTrue(fixture.player.shuffleModeEnabled)

        fixture.player.repeatMode = Player.REPEAT_MODE_ONE
        settle()
        assertEquals(PlaybackMode.REPEAT_ONE, fixture.mode())
        assertFalse(fixture.player.shuffleModeEnabled)

        fixture.player.shuffleModeEnabled = true
        settle()
        fixture.player.shuffleModeEnabled = false
        settle()
        assertEquals(PlaybackMode.SEQUENTIAL, fixture.mode())
        assertEquals(Player.REPEAT_MODE_OFF, fixture.player.repeatMode)

        fixture.close()
    }

    @Test
    fun `maps Media3 item back to queue item without resolving logical URI`() {
        val mapped = MediaItemMapper.fromMedia3(media3Item("audio", MediaKind.AUDIO))

        assertEquals("audio", mapped.mediaKey)
        assertEquals("audio", mapped.name)
        assertEquals("http://media.example:8080/audio.mp4", mapped.logicalUrl)
        assertEquals(MediaKind.AUDIO, mapped.kind)
    }

    private fun fixture(scope: CoroutineScope): Fixture {
        val engine = FakeEngine()
        val repository = FakeQueueRepository()
        val coordinator = PlaybackCoordinator(
            engine = engine,
            queueRepository = repository,
            positionStore = FakePositionStore(),
            session = FakeSession(),
            scope = scope,
        )
        return Fixture(
            engine = engine,
            repository = repository,
            coordinator = coordinator,
            player = VlcSessionPlayer(Looper.getMainLooper(), coordinator, scope),
        )
    }

    private suspend fun TestScope.settle() {
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun TestScope.advancedShuffleFixture(): Pair<Fixture, PlaybackQueue> {
        val fixture = fixture(this)
        fixture.coordinator.replaceQueue(
            listOf(item("a"), item("b"), item("c"), item("d")),
            "a",
        )
        settle()
        fixture.coordinator.setPlaybackModeFromSession(PlaybackMode.SHUFFLE)
        settle()
        val initial = fixture.coordinator.sessionState.value.queue
        val nextKey = initial.shuffleOrder[1]
        fixture.coordinator.seek(
            mediaItemIndex = initial.items.indexOfFirst { it.mediaKey == nextKey },
            positionMs = 0L,
            seekCommand = Player.COMMAND_SEEK_TO_MEDIA_ITEM,
        )
        settle()
        return fixture to fixture.coordinator.sessionState.value.queue
    }

    private fun item(key: String) = QueueMediaItem(
        mediaKey = key,
        name = key,
        logicalUrl = "http://media.example:8080/$key.mp4",
        kind = MediaKind.VIDEO,
    )

    private fun media3Item(
        key: String,
        kind: MediaKind = MediaKind.VIDEO,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(key)
        .setUri("http://media.example:8080/$key.mp4")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(key)
                .setMediaType(
                    if (kind == MediaKind.AUDIO) {
                        MediaMetadata.MEDIA_TYPE_MUSIC
                    } else {
                        MediaMetadata.MEDIA_TYPE_VIDEO
                    },
                )
                .build(),
        )
        .build()

    private fun requestUrlFor(key: String) = "http://10.0.0.9:8080/$key.mp4"

    private class Fixture(
        val engine: FakeEngine,
        val repository: FakeQueueRepository,
        val coordinator: PlaybackCoordinator,
        val player: VlcSessionPlayer,
    ) {
        fun keys() = coordinator.sessionState.value.queue.items.map { it.mediaKey }
        fun mode() = coordinator.sessionState.value.queue.mode
        fun close() {
            player.release()
            coordinator.close()
        }
    }
}

private class FakeEngine : PlaybackEngine {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState
    val seekCalls = mutableListOf<Long>()
    val speedCalls = mutableListOf<Float>()
    val prepareCalls = mutableListOf<String>()
    var playCalls = 0
    var pauseCalls = 0
    var stopCalls = 0

    fun emit(state: PlaybackState) {
        mutableState.value = state
    }

    fun clearCalls() {
        seekCalls.clear()
        speedCalls.clear()
        prepareCalls.clear()
        playCalls = 0
        pauseCalls = 0
        stopCalls = 0
    }

    override fun prepare(url: String) {
        prepareCalls += url
    }
    override fun attachVideoOutput(host: ViewGroup) = Unit
    override fun detachVideoOutput() = Unit
    override fun setVideoScaleMode(mode: VideoScaleMode) = Unit
    override fun setPlaybackSpeed(speed: Float) {
        speedCalls += speed
    }
    override fun play() {
        playCalls += 1
    }
    override fun pause() {
        pauseCalls += 1
    }
    override fun stop() {
        stopCalls += 1
    }
    override fun seekTo(positionMs: Long) {
        seekCalls += positionMs
    }
    override fun close() = Unit
}

private class FakeQueueRepository : PlaybackQueueRepository {
    private val mutableQueue = MutableStateFlow(PlaybackQueue())
    var saveCalls = 0
        private set
    override val queue: StateFlow<PlaybackQueue> = mutableQueue
    override suspend fun restore(): PlaybackQueue = mutableQueue.value
    override suspend fun save(queue: PlaybackQueue) {
        saveCalls += 1
        mutableQueue.value = queue
    }

    fun clearSaveCalls() {
        saveCalls = 0
    }
}

private class FakePositionStore : PlaybackPositionStore {
    override suspend fun resumePosition(mediaKey: String): Long? = null
    override suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean,
    ) = Unit
    override suspend fun clear(mediaKey: String) = Unit
}

private class FakeSession : ServerSessionManager {
    private val endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.example:8080",
        requestBaseUrl = "http://10.0.0.9:8080",
        ipv4 = "10.0.0.9",
    )
    override val state: StateFlow<ServerSessionState> = MutableStateFlow(
        ServerSessionState.Connected(endpoint, listOf(endpoint.ipv4)),
    )

    override suspend fun connectSaved() = Unit
    override suspend fun testCandidate(input: String): AppResult<ConnectionTestResult> =
        error("unused")
    override suspend fun saveCandidate(result: ConnectionTestResult) = Unit
    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        error("unused")
}
