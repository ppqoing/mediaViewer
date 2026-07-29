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
            val sequence = PlaybackReleaseSequence(
                saveCurrentSnapshot = coordinator::saveCurrentSnapshot,
                releaseResources = { releaseCalls += 1 },
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
            coordinator.close()
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
