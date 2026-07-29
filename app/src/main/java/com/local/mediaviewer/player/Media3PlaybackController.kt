package com.local.mediaviewer.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.service.ACTION_LOCAL_VIDEO_OUTPUT
import com.local.mediaviewer.service.LocalVideoOutputBinder
import com.local.mediaviewer.service.MediaItemMapper
import com.local.mediaviewer.service.PlaybackService
import com.local.mediaviewer.service.toMedia3Item
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Media3PlaybackController(
    context: Context,
    private val scope: CoroutineScope,
) : QueuePlaybackController {
    private val appContext = context.applicationContext
    private val mutableConnectionState =
        MutableStateFlow<ControllerConnectionState>(
            ControllerConnectionState.Connecting,
        )
    val connectionState: StateFlow<ControllerConnectionState>
        get() = mutableConnectionState.asStateFlow()
    private val mutableSessionState = MutableStateFlow(
        Media3StateMapper.map(mutableConnectionState.value, null),
    )
    override val sessionState: StateFlow<PlaybackSessionState> =
        mutableSessionState.asStateFlow()
    private val mutableState = MutableStateFlow(mutableSessionState.value.playback)
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private val mutableVideoOutputState =
        MutableStateFlow<VideoOutputConnectionState>(
            VideoOutputConnectionState.Detached,
        )
    override val videoOutputState: StateFlow<VideoOutputConnectionState> =
        mutableVideoOutputState.asStateFlow()

    private val pendingCommands = ArrayDeque<(MediaController) -> Unit>()
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java),
        ),
    ).buildAsync()
    private var mediaController: MediaController? = null
    private var positionObserver: Job? = null
    private var closed = false
    private var pendingVideoHost: ViewGroup? = null
    private var pendingScaleMode = VideoScaleMode.BEST_FIT
    private var localVideoBinder: LocalVideoOutputBinder? = null
    private var localVideoBinding = false
    private var localVideoBound = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) {
            publish(player)
        }
    }

    private val localVideoConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder,
        ) {
            localVideoBinding = false
            localVideoBound = true
            val binder = service as? LocalVideoOutputBinder
            if (binder == null) {
                failVideoOutput("画面恢复失败")
                return
            }
            localVideoBinder = binder
            attachPendingVideoOutput()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            localVideoBinder = null
            localVideoBinding = false
            localVideoBound = true
            if (pendingVideoHost != null && !closed) {
                failVideoOutput("画面恢复失败")
            }
        }

        override fun onBindingDied(name: ComponentName) {
            onServiceDisconnected(name)
        }

        override fun onNullBinding(name: ComponentName) {
            localVideoBinding = false
            localVideoBound = true
            failVideoOutput("画面恢复失败")
        }
    }

    init {
        scope.launch {
            try {
                val connected = controllerFuture.await()
                if (closed) {
                    connected.release()
                    return@launch
                }
                mediaController = connected
                connected.addListener(playerListener)
                mutableConnectionState.value = ControllerConnectionState.Connected
                publish(connected)
                positionObserver = scope.launch {
                    while (
                        isActive &&
                        !closed &&
                        mediaController === connected
                    ) {
                        delay(500L)
                        publish(connected)
                    }
                }
                while (pendingCommands.isNotEmpty()) {
                    pendingCommands.removeFirst()(connected)
                }
            } catch (error: Throwable) {
                if (!closed) {
                    mutableConnectionState.value = ControllerConnectionState.Failed(
                        error.message ?: "无法连接后台播放器",
                    )
                    publish(null)
                }
            }
        }
    }

    override fun prepare(url: String) = withController { controller ->
        controller.setMediaItem(MediaItem.fromUri(url))
        controller.prepare()
    }

    override fun play() = withController(Player::play)

    override fun pause() = withController(Player::pause)

    override fun stop() = withController(Player::stop)

    override fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setPlaybackSpeed(speed: Float) = withController {
        it.setPlaybackSpeed(speed)
    }

    override fun replaceQueue(
        items: List<QueueMediaItem>,
        startMediaKey: String,
    ) = withController { controller ->
        if (items.isEmpty()) {
            controller.clearMediaItems()
            return@withController
        }
        val startIndex = items.indexOfFirst { it.mediaKey == startMediaKey }
            .takeIf { it >= 0 } ?: 0
        controller.setMediaItems(
            items.map(QueueMediaItem::toMedia3Item),
            startIndex,
            C.TIME_UNSET,
        )
        controller.prepare()
        controller.play()
    }

    override fun playNext(item: QueueMediaItem) = withController { controller ->
        val existingIndex = controller.indexOf(item.mediaKey)
        val currentIndex = controller.currentMediaItemIndex
        if (existingIndex == currentIndex) return@withController
        val insertionIndex = if (existingIndex in 0 until currentIndex) {
            currentIndex
        } else {
            currentIndex + 1
        }
        if (existingIndex >= 0) controller.removeMediaItem(existingIndex)
        controller.addMediaItem(
            insertionIndex.coerceIn(0, controller.mediaItemCount),
            item.toMedia3Item(),
        )
    }

    override fun append(item: QueueMediaItem) = withController { controller ->
        val existingIndex = controller.indexOf(item.mediaKey)
        if (existingIndex == controller.currentMediaItemIndex) return@withController
        if (existingIndex >= 0) controller.removeMediaItem(existingIndex)
        controller.addMediaItem(item.toMedia3Item())
    }

    override fun select(mediaKey: String) = withController { controller ->
        controller.indexOf(mediaKey).takeIf { it >= 0 }?.let { index ->
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    override fun skipPrevious() = withController(Player::seekToPreviousMediaItem)

    override fun skipNext() = withController(Player::seekToNextMediaItem)

    override fun move(
        mediaKey: String,
        toIndex: Int,
    ) = withController { controller ->
        val fromIndex = controller.indexOf(mediaKey)
        if (fromIndex >= 0 && controller.mediaItemCount > 0) {
            controller.moveMediaItem(
                fromIndex,
                toIndex.coerceIn(0, controller.mediaItemCount - 1),
            )
        }
    }

    override fun remove(mediaKey: String) = withController { controller ->
        controller.indexOf(mediaKey).takeIf { it >= 0 }?.let(controller::removeMediaItem)
    }

    override fun clearExceptCurrent() = withController { controller ->
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex !in 0 until controller.mediaItemCount) return@withController
        if (currentIndex + 1 < controller.mediaItemCount) {
            controller.removeMediaItems(currentIndex + 1, controller.mediaItemCount)
        }
        if (currentIndex > 0) controller.removeMediaItems(0, currentIndex)
    }

    override fun clearAll() = withController { controller ->
        controller.pause()
        controller.clearMediaItems()
    }

    override fun setPlaybackMode(mode: PlaybackMode) = withController { controller ->
        controller.shuffleModeEnabled = mode == PlaybackMode.SHUFFLE
        controller.repeatMode = when (mode) {
            PlaybackMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            PlaybackMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            PlaybackMode.SEQUENTIAL,
            PlaybackMode.SHUFFLE,
            -> Player.REPEAT_MODE_OFF
        }
    }

    override fun attachVideoOutput(host: ViewGroup) {
        if (closed) return
        pendingVideoHost = host
        localVideoBinder?.let {
            attachPendingVideoOutput()
            return
        }
        bindLocalVideoOutput()
    }

    override fun detachVideoOutput() {
        pendingVideoHost = null
        runCatching { localVideoBinder?.detach() }
        localVideoBinder = null
        if (localVideoBound || localVideoBinding) {
            runCatching { appContext.unbindService(localVideoConnection) }
        }
        localVideoBound = false
        localVideoBinding = false
        mutableVideoOutputState.value = VideoOutputConnectionState.Detached
    }

    override fun retryVideoOutput() {
        if (pendingVideoHost == null || closed) return
        if (localVideoBound || localVideoBinding) {
            runCatching { appContext.unbindService(localVideoConnection) }
        }
        localVideoBinder = null
        localVideoBound = false
        localVideoBinding = false
        bindLocalVideoOutput()
    }

    override fun setVideoScaleMode(mode: VideoScaleMode) {
        pendingScaleMode = mode
        runCatching {
            localVideoBinder?.setScaleMode(mode)
        }.onFailure {
            if (pendingVideoHost != null) failVideoOutput("画面恢复失败")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        detachVideoOutput()
        pendingCommands.clear()
        positionObserver?.cancel()
        positionObserver = null
        mediaController?.removeListener(playerListener)
        mediaController = null
        MediaController.releaseFuture(controllerFuture)
    }

    private fun withController(command: (MediaController) -> Unit) {
        if (closed) return
        val controller = mediaController
        if (controller == null) {
            pendingCommands.addLast(command)
        } else {
            command(controller)
        }
    }

    private fun publish(player: Player?) {
        val snapshot = player?.snapshot()
        val mapped = Media3StateMapper.map(mutableConnectionState.value, snapshot)
        mutableSessionState.value = mapped
        mutableState.value = mapped.playback
    }

    private fun bindLocalVideoOutput() {
        if (localVideoBinding || localVideoBound || closed) return
        mutableVideoOutputState.value = VideoOutputConnectionState.Connecting
        localVideoBinding = true
        val didBind = runCatching {
            appContext.bindService(
                Intent(ACTION_LOCAL_VIDEO_OUTPUT)
                    .setClass(appContext, PlaybackService::class.java),
                localVideoConnection,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!didBind) {
            localVideoBinding = false
            failVideoOutput("画面恢复失败")
        }
    }

    private fun attachPendingVideoOutput() {
        val host = pendingVideoHost ?: return
        val binder = localVideoBinder ?: return
        runCatching {
            binder.attach(host)
            binder.setScaleMode(pendingScaleMode)
        }.onSuccess {
            mutableVideoOutputState.value = VideoOutputConnectionState.Attached
        }.onFailure {
            failVideoOutput("画面恢复失败")
        }
    }

    private fun failVideoOutput(message: String) {
        mutableVideoOutputState.value = VideoOutputConnectionState.Failed(message)
    }

    private fun MediaController.indexOf(mediaKey: String): Int =
        (0 until mediaItemCount).indexOfFirst {
            getMediaItemAt(it).mediaId == mediaKey
        }

    private fun Player.snapshot(): Media3StateSnapshot = Media3StateSnapshot(
        playbackState = playbackState,
        playWhenReady = playWhenReady,
        isPlaying = isPlaying,
        positionMs = currentPosition,
        durationMs = duration,
        bufferedPositionMs = bufferedPosition,
        isSeekable = isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
        errorMessage = playerError?.message,
        items = (0 until mediaItemCount).map {
            MediaItemMapper.fromMedia3(getMediaItemAt(it))
        },
        currentMediaItemIndex = currentMediaItemIndex,
        canSkipPrevious = isCommandAvailable(
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        ),
        canSkipNext = isCommandAvailable(
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        ),
        repeatMode = repeatMode,
        shuffleModeEnabled = shuffleModeEnabled,
        playbackSpeed = playbackParameters.speed,
    )
}
