package com.local.mediaviewer.service

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackCoordinator
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

@UnstableApi
class VlcSessionPlayer(
    looper: Looper,
    private val coordinator: PlaybackCoordinator,
    private val scope: CoroutineScope,
) : SimpleBasePlayer(looper) {
    private val applicationHandler = Handler(looper)
    private val stateObserver = scope.launch {
        coordinator.sessionState.collect {
            if (Looper.myLooper() == applicationLooper) {
                invalidateState()
            } else {
                applicationHandler.post { invalidateState() }
            }
        }
    }

    override fun getState(): State {
        val session = coordinator.sessionState.value
        val playback = session.playback
        val bufferedPositionMs = (
            playback.durationMs.toDouble() *
                playback.bufferedPercent.coerceIn(0f, 100f) /
                100.0
            ).toLong()
        return State.Builder()
            .setAvailableCommands(commandsFor(playback))
            .setPlayWhenReady(
                playback.status == PlaybackStatus.PLAYING,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaybackState(
                if (session.queue.items.isEmpty()) {
                    Player.STATE_IDLE
                } else {
                    playback.status.toMedia3State()
                },
            )
            .setPlayerError(playback.toMedia3Error())
            .setIsLoading(
                playback.status == PlaybackStatus.OPENING ||
                    playback.status == PlaybackStatus.BUFFERING,
            )
            .setPlaylist(
                session.queue.items.map { item ->
                    item.toMediaItemData(session)
                },
            )
            .setCurrentMediaItemIndex(session.queue.currentIndex.coerceAtLeast(0))
            .setContentPositionMs(playback.positionMs)
            .setContentBufferedPositionMs(
                PositionSupplier.getConstant(bufferedPositionMs),
            )
            .setTotalBufferedDurationMs(
                PositionSupplier.getConstant(
                    (bufferedPositionMs - playback.positionMs).coerceAtLeast(0L),
                ),
            )
            .setPlaybackParameters(PlaybackParameters(session.queue.playbackSpeed))
            .setRepeatMode(session.queue.mode.toMedia3RepeatMode())
            .setShuffleModeEnabled(session.queue.mode == PlaybackMode.SHUFFLE)
            .build()
    }

    override fun handleSetPlayWhenReady(
        playWhenReady: Boolean,
    ): ListenableFuture<*> = immediate {
        coordinator.setPlayWhenReadyFromSession(playWhenReady)
    }

    override fun handleRelease(): ListenableFuture<*> {
        stateObserver.cancel()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> = immediate {
        coordinator.seek(mediaItemIndex, positionMs, seekCommand)
    }

    override fun handleSetPlaybackParameters(
        playbackParameters: PlaybackParameters,
    ): ListenableFuture<*> = immediate {
        coordinator.setPlaybackSpeedFromSession(playbackParameters.speed)
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<*> = immediate {
        coordinator.add(index, mediaItems.map(MediaItemMapper::fromMedia3))
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int,
    ): ListenableFuture<*> = immediate {
        coordinator.moveRange(fromIndex, toIndex, newIndex)
    }

    override fun handleRemoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
    ): ListenableFuture<*> = immediate {
        coordinator.removeRange(fromIndex, toIndex)
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newMediaItems: List<MediaItem>,
    ): ListenableFuture<*> = immediate {
        coordinator.removeRange(fromIndex, toIndex)
        coordinator.add(fromIndex, newMediaItems.map(MediaItemMapper::fromMedia3))
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> = immediate {
        coordinator.replaceFromMedia3(
            items = mediaItems.map(MediaItemMapper::fromMedia3),
            startIndex = startIndex,
            startPositionMs = startPositionMs,
        )
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> = immediate {
        val mode = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> PlaybackMode.REPEAT_ONE
            Player.REPEAT_MODE_ALL -> PlaybackMode.REPEAT_ALL
            else -> if (
                coordinator.sessionState.value.queue.mode == PlaybackMode.SHUFFLE
            ) {
                PlaybackMode.SHUFFLE
            } else {
                PlaybackMode.SEQUENTIAL
            }
        }
        coordinator.setPlaybackModeFromSession(mode)
    }

    override fun handleSetShuffleModeEnabled(
        shuffleModeEnabled: Boolean,
    ): ListenableFuture<*> = immediate {
        coordinator.setPlaybackModeFromSession(
            if (shuffleModeEnabled) PlaybackMode.SHUFFLE else PlaybackMode.SEQUENTIAL,
        )
    }

    private fun immediate(
        mutation: suspend () -> Unit,
    ): ListenableFuture<*> = scope.future {
        mutation()
    }

    private fun QueueMediaItem.toMediaItemData(
        session: PlaybackSessionState,
    ): MediaItemData {
        val isCurrent = mediaKey == session.queue.currentMediaKey
        val durationUs = if (isCurrent && session.playback.durationMs > 0L) {
            session.playback.durationMs * 1_000L
        } else {
            C.TIME_UNSET
        }
        val mediaItem = toMedia3Item()
        return MediaItemData.Builder(mediaKey)
            .setMediaItem(mediaItem)
            .setMediaMetadata(mediaItem.mediaMetadata)
            .setIsSeekable(isCurrent && session.playback.isSeekable)
            .setDurationUs(durationUs)
            .build()
    }

    private fun commandsFor(playback: PlaybackState): Player.Commands {
        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_RELEASE,
            )
        if (!playback.isSeekable) {
            commands.removeAll(
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
            )
        }
        return commands.build()
    }
}

private fun PlaybackStatus.toMedia3State(): Int = when (this) {
    PlaybackStatus.OPENING,
    PlaybackStatus.BUFFERING,
    -> Player.STATE_BUFFERING
    PlaybackStatus.PLAYING,
    PlaybackStatus.PAUSED,
    -> Player.STATE_READY
    PlaybackStatus.ENDED -> Player.STATE_ENDED
    PlaybackStatus.IDLE,
    PlaybackStatus.ERROR,
    -> Player.STATE_IDLE
}

private fun PlaybackState.toMedia3Error(): PlaybackException? =
    if (status == PlaybackStatus.ERROR) {
        PlaybackException(
            errorMessage ?: "LibVLC playback failed",
            null,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )
    } else {
        null
    }

private fun PlaybackMode.toMedia3RepeatMode(): Int = when (this) {
    PlaybackMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
    PlaybackMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
    PlaybackMode.SEQUENTIAL,
    PlaybackMode.SHUFFLE,
    -> Player.REPEAT_MODE_OFF
}
