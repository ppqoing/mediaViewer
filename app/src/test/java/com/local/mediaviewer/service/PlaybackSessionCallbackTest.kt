package com.local.mediaviewer.service

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import com.local.mediaviewer.queue.PlaybackQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlaybackSessionCallbackTest {
    @Test
    fun `cold resumption returns queue index and position without playing`() = runTest {
        val engine = ServiceTestEngine()
        val repository = ServiceTestQueueRepository(
            PlaybackQueue(
                items = listOf(serviceTestItem("a"), serviceTestItem("b")),
                currentMediaKey = "b",
            ),
        )
        val coordinator = serviceTestCoordinator(
            scope = this,
            engine = engine,
            repository = repository,
            positions = ServiceTestPositionStore(mapOf("b" to 30_000L)),
        )
        val callback = PlaybackSessionCallback(coordinator, this)
        val sessionFixture = mediaSession(coordinator, this)

        listOf(false, true).forEach { isForPlayback ->
            val future = callback.onPlaybackResumption(
                sessionFixture.session,
                controllerInfo(),
                isForPlayback,
            )
            advanceUntilIdle()
            val result = future.get()

            assertEquals(listOf("a", "b"), result.mediaItems.map { it.mediaId })
            assertEquals(1, result.startIndex)
            assertEquals(30_000L, result.startPositionMs)
        }
        assertEquals(0, engine.prepareCalls)
        assertEquals(0, engine.playCalls)

        sessionFixture.session.release()
        sessionFixture.player.release()
        coordinator.close()
    }

    @Test
    fun `empty or damaged queue resumes as empty without crashing`() = runTest {
        val cases = listOf(
            ServiceTestQueueRepository(),
            ServiceTestQueueRepository(
                restoreFailure = IllegalStateException("damaged snapshot"),
            ),
        )

        cases.forEach { repository ->
            val coordinator = serviceTestCoordinator(
                scope = this,
                repository = repository,
            )
            val callback = PlaybackSessionCallback(coordinator, this)
            val sessionFixture = mediaSession(coordinator, this)
            val future = callback.onPlaybackResumption(
                sessionFixture.session,
                controllerInfo(),
                false,
            )
            advanceUntilIdle()

            assertTrue(future.get().mediaItems.isEmpty())
            sessionFixture.session.release()
            sessionFixture.player.release()
            coordinator.close()
        }
    }

    @Test
    fun `session commands gate play track user pause and release without clearing queue`() =
        runTest {
            val repository = ServiceTestQueueRepository(
                PlaybackQueue(
                    items = listOf(serviceTestItem("a")),
                    currentMediaKey = "a",
                ),
            )
            val coordinator = serviceTestCoordinator(
                scope = this,
                repository = repository,
            )
            var playRequests = 0
            var userPauses = 0
            var releases = 0
            val callback = PlaybackSessionCallback(
                coordinator = coordinator,
                scope = this,
                beforePlay = {
                    playRequests += 1
                    false
                },
                onUserPause = { userPauses += 1 },
                onStopAndRelease = { releases += 1 },
            )
            val sessionFixture = mediaSession(coordinator, this)
            val controller = controllerInfo()

            assertEquals(
                SessionResult.RESULT_ERROR_INVALID_STATE,
                callback.onPlayerCommandRequest(
                    sessionFixture.session,
                    controller,
                    androidx.media3.common.Player.COMMAND_PLAY_PAUSE,
                ),
            )
            assertEquals(1, playRequests)

            coordinator.replaceQueue(listOf(serviceTestItem("a")), "a")
            advanceUntilIdle()
            assertEquals(
                SessionResult.RESULT_SUCCESS,
                callback.onPlayerCommandRequest(
                    sessionFixture.session,
                    controller,
                    androidx.media3.common.Player.COMMAND_PLAY_PAUSE,
                ),
            )
            assertEquals(1, userPauses)

            val stopCommand = SessionCommand(ACTION_STOP_AND_RELEASE, Bundle.EMPTY)
            assertTrue(
                callback.onConnect(
                    sessionFixture.session,
                    controller,
                ).availableSessionCommands.contains(stopCommand),
            )
            val stopFuture = callback.onCustomCommand(
                sessionFixture.session,
                controller,
                stopCommand,
                Bundle.EMPTY,
            )
            advanceUntilIdle()
            assertEquals(SessionResult.RESULT_SUCCESS, stopFuture.get().resultCode)
            assertEquals(1, releases)
            assertEquals(listOf("a"), repository.queue.value.items.map { it.mediaKey })

            sessionFixture.session.release()
            sessionFixture.player.release()
            coordinator.close()
        }

    @Test
    fun `stop command future completes only after suspend release finishes`() = runTest {
        val coordinator = serviceTestCoordinator(scope = this)
        val allowRelease = CompletableDeferred<Unit>()
        val callback = PlaybackSessionCallback(
            coordinator = coordinator,
            scope = this,
            onStopAndRelease = { allowRelease.await() },
        )
        val sessionFixture = mediaSession(coordinator, this)

        val future = callback.onCustomCommand(
            sessionFixture.session,
            controllerInfo(),
            SessionCommand(ACTION_STOP_AND_RELEASE, Bundle.EMPTY),
            Bundle.EMPTY,
        )
        advanceUntilIdle()
        assertFalse(future.isDone)

        allowRelease.complete(Unit)
        advanceUntilIdle()
        assertEquals(SessionResult.RESULT_SUCCESS, future.get().resultCode)

        sessionFixture.session.release()
        sessionFixture.player.release()
        coordinator.close()
    }

    private fun mediaSession(
        coordinator: com.local.mediaviewer.queue.PlaybackCoordinator,
        scope: CoroutineScope,
    ): SessionFixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = VlcSessionPlayer(Looper.getMainLooper(), coordinator, scope)
        return SessionFixture(
            session = MediaSession.Builder(context, player).build(),
            player = player,
        )
    }

    private fun controllerInfo(): MediaSession.ControllerInfo =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            "com.local.mediaviewer.test",
            1,
            1,
            1,
            10,
            true,
            Bundle.EMPTY,
            true,
        )

    private data class SessionFixture(
        val session: MediaSession,
        val player: VlcSessionPlayer,
    )
}
