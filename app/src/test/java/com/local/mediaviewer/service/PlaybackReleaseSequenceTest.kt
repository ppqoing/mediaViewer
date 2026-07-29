package com.local.mediaviewer.service

import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackQueueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackReleaseSequenceTest {
    @Test
    fun `stop waits for occupied coordinator without blocking main and releases once`() =
        runTest {
            val repository = BlockingRestoreRepository()
            val coordinator = serviceTestCoordinator(
                scope = this,
                repository = repository,
            )
            coordinator.start()
            runCurrent()
            repository.restoreStarted.await()
            var releaseCalls = 0
            val events = mutableListOf<String>()
            val sequence = PlaybackReleaseSequence(
                saveCurrentSnapshot = {
                    coordinator.saveCurrentSnapshot()
                    events += "save"
                },
                captureCurrentSnapshot = { "snapshot" },
                persistAfterDestroy = {},
                releaseResources = {
                    events += "release"
                    releaseCalls += 1
                },
            )

            val firstStop = async { sequence.releaseAfterSave() }
            val secondStop = async { sequence.releaseAfterSave() }
            runCurrent()
            assertFalse(firstStop.isCompleted)
            assertFalse(secondStop.isCompleted)

            var mainProgressed = false
            launch { mainProgressed = true }
            runCurrent()
            assertTrue(mainProgressed)

            repository.allowRestore.complete(Unit)
            advanceUntilIdle()

            assertTrue(firstStop.isCompleted)
            assertTrue(secondStop.isCompleted)
            assertEquals(1, releaseCalls)
            assertEquals(listOf("save", "release"), events)
            coordinator.close()
        }

    @Test
    fun `destroy captures persistence request and closes synchronously without dispatcher progress`() {
        var releaseCalls = 0
        var captured = 0
        var pendingSnapshot: String? = null
        val sequence = PlaybackReleaseSequence(
            saveCurrentSnapshot = { error("explicit stop not used") },
            captureCurrentSnapshot = {
                captured += 1
                "snapshot"
            },
            persistAfterDestroy = { pendingSnapshot = it },
            releaseResources = { releaseCalls += 1 },
        )

        sequence.releaseFromDestroy()
        sequence.releaseFromDestroy()

        assertEquals(1, captured)
        assertEquals("snapshot", pendingSnapshot)
        assertEquals(1, releaseCalls)
    }
}

private class BlockingRestoreRepository : PlaybackQueueRepository {
    private val mutable = MutableStateFlow(PlaybackQueue())
    override val queue: StateFlow<PlaybackQueue> = mutable
    val restoreStarted = CompletableDeferred<Unit>()
    val allowRestore = CompletableDeferred<Unit>()

    override suspend fun restore(): PlaybackQueue {
        restoreStarted.complete(Unit)
        allowRestore.await()
        return mutable.value
    }

    override suspend fun save(queue: PlaybackQueue) {
        mutable.value = queue
    }
}
