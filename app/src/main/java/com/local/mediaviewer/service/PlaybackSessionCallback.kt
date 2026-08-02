package com.local.mediaviewer.service

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.local.mediaviewer.queue.PlaybackCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

@UnstableApi
class PlaybackSessionCallback(
    private val coordinator: PlaybackCoordinator,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    ),
    private val beforePlay: () -> Boolean = { true },
    private val onUserPause: () -> Unit = {},
    private val onStopAndRelease: suspend () -> Unit = {},
) : MediaSession.Callback {
    private val stopAndReleaseCommand = SessionCommand(
        ACTION_STOP_AND_RELEASE,
        Bundle.EMPTY,
    )
    private val reloadCurrentCommand = SessionCommand(
        ACTION_RELOAD_CURRENT,
        Bundle.EMPTY,
    )
    private val retryPersistenceCommand = SessionCommand(
        ACTION_RETRY_PERSISTENCE,
        Bundle.EMPTY,
    )
    private val exactPositionCommand = SessionCommand(
        ACTION_GET_EXACT_PLAYBACK_POSITION,
        Bundle.EMPTY,
    )

    override fun onConnect(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(stopAndReleaseCommand)
                    .add(reloadCurrentCommand)
                    .add(retryPersistenceCommand)
                    .add(exactPositionCommand)
                    .build(),
            )
            .build()

    override fun onPlayerCommandRequest(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Int {
        if (playerCommand != Player.COMMAND_PLAY_PAUSE) {
            return SessionResult.RESULT_SUCCESS
        }
        if (mediaSession.player.playWhenReady) {
            onUserPause()
            return SessionResult.RESULT_SUCCESS
        }
        return if (beforePlay()) {
            SessionResult.RESULT_SUCCESS
        } else {
            SessionResult.RESULT_ERROR_INVALID_STATE
        }
    }

    override fun onCustomCommand(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        return when (customCommand.customAction) {
            ACTION_STOP_AND_RELEASE -> scope.future {
                onStopAndRelease()
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            ACTION_RELOAD_CURRENT -> scope.future {
                coordinator.reloadCurrentFromSession()
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            ACTION_RETRY_PERSISTENCE -> scope.future {
                coordinator.saveCurrentSnapshot()
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            ACTION_GET_EXACT_PLAYBACK_POSITION -> {
                val snapshot = coordinator.sessionState.value
                    .toPlaybackPositionSnapshot()
                Futures.immediateFuture(
                    if (snapshot == null) {
                        SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
                    } else {
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            PlaybackPositionSnapshotCodec.encode(snapshot),
                        )
                    },
                )
            }

            else -> Futures.immediateFuture(
                SessionResult(SessionError.ERROR_NOT_SUPPORTED),
            )
        }
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        scope.future {
            val snapshot = try {
                coordinator.playbackResumptionSnapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            MediaSession.MediaItemsWithStartPosition(
                snapshot?.items?.map { it.toMedia3Item() }.orEmpty(),
                snapshot?.startIndex ?: 0,
                snapshot?.startPositionMs ?: 0L,
            )
        }
}
