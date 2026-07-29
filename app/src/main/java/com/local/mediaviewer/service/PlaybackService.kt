package com.local.mediaviewer.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.local.mediaviewer.MainActivity
import com.local.mediaviewer.MediaViewerApplication
import com.local.mediaviewer.navigation.ACTION_OPEN_CURRENT_PLAYER
import com.local.mediaviewer.navigation.EXTRA_OPEN_CURRENT_PLAYER
import com.local.mediaviewer.playback.PlaybackInterruption
import com.local.mediaviewer.playback.PlaybackInterruptions
import com.local.mediaviewer.queue.PlaybackCoordinator
import com.local.mediaviewer.queue.PlaybackPersistenceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal class PlaybackReleaseSequence<T>(
    private val saveCurrentSnapshot: suspend () -> Unit,
    private val captureCurrentSnapshot: () -> T,
    private val persistAfterDestroy: (T) -> Unit,
    private val releaseResources: () -> Unit,
) {
    private val started = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val completed = CompletableDeferred<Unit>()

    fun isStarted(): Boolean = started.get()

    suspend fun releaseAfterSave() {
        if (!started.compareAndSet(false, true)) {
            completed.await()
            return
        }
        try {
            saveCurrentSnapshot()
            releaseNow()
            completed.complete(Unit)
        } catch (error: Throwable) {
            releaseNow()
            completed.completeExceptionally(error)
            throw error
        }
    }

    fun releaseFromDestroy() {
        if (started.compareAndSet(false, true)) {
            persistAfterDestroy(captureCurrentSnapshot())
        }
        releaseNow()
        completed.complete(Unit)
    }

    private fun releaseNow() {
        if (released.compareAndSet(false, true)) {
            releaseResources()
        }
    }
}

internal class PlaybackFocusGate(
    private val acquireFocus: () -> Boolean,
    private val pauseForInterruption: () -> Unit,
    private val pausePermanently: () -> Unit,
    private val resume: () -> Unit,
    private val publishError: (String) -> Unit,
) {
    private var resumeAfterFocusGain = false

    fun onUserPlayRequest(): Boolean {
        if (acquireFocus()) return true
        publishError("无法获取音频焦点，暂时不能播放")
        return false
    }

    fun onUserPause() {
        resumeAfterFocusGain = false
    }

    fun onInterruption(
        event: PlaybackInterruption,
        wasPlaying: Boolean,
    ) {
        when (event) {
            PlaybackInterruption.TransientLoss -> {
                resumeAfterFocusGain = wasPlaying
                if (wasPlaying) pauseForInterruption()
            }

            PlaybackInterruption.FocusGained -> {
                val shouldResume = resumeAfterFocusGain && wasPlaying
                resumeAfterFocusGain = false
                if (shouldResume) {
                    resume()
                }
            }

            PlaybackInterruption.PermanentLoss,
            PlaybackInterruption.BecomingNoisy,
            -> {
                resumeAfterFocusGain = false
                pausePermanently()
            }
        }
    }
}

internal class PlaybackSnapshotTicker(
    scope: CoroutineScope,
    intervalMs: Long = 5_000L,
    save: suspend () -> Unit,
) : AutoCloseable {
    private val job: Job = scope.launch {
        while (isActive) {
            delay(intervalMs)
            save()
        }
    }

    override fun close() {
        job.cancel()
    }
}

@UnstableApi
class PlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var coordinator: PlaybackCoordinator
    private lateinit var player: VlcSessionPlayer
    private lateinit var session: MediaSession
    private lateinit var interruptions: PlaybackInterruptions
    private lateinit var focusGate: PlaybackFocusGate
    private lateinit var localVideoBinder: LocalVideoOutputBinder
    private lateinit var localVideoBindingChannel: LocalVideoOutputBindingChannel
    private lateinit var snapshotTicker: PlaybackSnapshotTicker
    private lateinit var releaseSequence:
        PlaybackReleaseSequence<PlaybackPersistenceSnapshot>

    override fun onCreate() {
        super.onCreate()
        val container = (application as MediaViewerApplication).container
        coordinator = container.createPlaybackCoordinator(serviceScope)
        player = VlcSessionPlayer(
            Looper.getMainLooper(),
            coordinator,
            serviceScope,
        )
        focusGate = PlaybackFocusGate(
            acquireFocus = { interruptions.acquireFocus() },
            pauseForInterruption = coordinator::pauseForInterruption,
            pausePermanently = coordinator::pause,
            resume = coordinator::play,
            publishError = coordinator::publishError,
        )
        interruptions = PlaybackInterruptions(this) { event ->
            serviceScope.launch {
                focusGate.onInterruption(
                    event = event,
                    wasPlaying = coordinator.sessionState.value.playWhenReady,
                )
                if (
                    event == PlaybackInterruption.PermanentLoss ||
                    event == PlaybackInterruption.BecomingNoisy
                ) {
                    interruptions.abandonFocus()
                }
            }
        }
        session = MediaSession.Builder(this, player)
            .setSessionActivity(currentPlayerPendingIntent())
            .setCallback(
                PlaybackSessionCallback(
                    coordinator = coordinator,
                    scope = serviceScope,
                    beforePlay = focusGate::onUserPlayRequest,
                    onUserPause = {
                        focusGate.onUserPause()
                        interruptions.abandonFocus()
                    },
                    onStopAndRelease = ::stopAndRelease,
                ),
            )
            .setMediaButtonPreferences(mediaButtonPreferences())
            .build()
        localVideoBinder = LocalVideoOutputBinder(coordinator)
        localVideoBindingChannel = LocalVideoOutputBindingChannel(localVideoBinder)
        snapshotTicker = PlaybackSnapshotTicker(
            scope = serviceScope,
            save = coordinator::saveCurrentSnapshot,
        )
        releaseSequence = PlaybackReleaseSequence(
            saveCurrentSnapshot = {
                coordinator.saveCurrentSnapshot()
                coordinator.setPlayWhenReadyFromSession(false)
            },
            captureCurrentSnapshot = coordinator::captureCurrentSnapshot,
            persistAfterDestroy = { snapshot ->
                (application as MediaViewerApplication).persistPlaybackSnapshot {
                    coordinator.persistSnapshot(snapshot)
                }
            },
            releaseResources = {
                snapshotTicker.close()
                localVideoBindingChannel.invalidate()
                session.release()
                player.release()
                interruptions.close()
                coordinator.close()
            },
        )
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaSession? = if (releaseSequence.isStarted()) null else session

    override fun onBind(intent: Intent): IBinder? =
        if (intent.action == ACTION_LOCAL_VIDEO_OUTPUT) {
            if (releaseSequence.isStarted()) null else localVideoBindingChannel.bind()
        } else {
            super.onBind(intent)
        }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP_AND_RELEASE) {
            requestStopAndRelease()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (
            shouldStopAfterTaskRemoved(
                releaseStarted = releaseSequence.isStarted(),
                playWhenReady = player.playWhenReady,
                hasConnectedControllers = session.connectedControllers.isNotEmpty(),
            )
        ) {
            requestStopAndRelease()
        }
    }

    override fun onDestroy() {
        releaseSequence.releaseFromDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun requestStopAndRelease() {
        serviceScope.launch {
            stopAndRelease()
        }
    }

    private suspend fun stopAndRelease() {
        releaseSequence.releaseAfterSave()
        stopSelf()
        mainHandler.post {
            serviceScope.cancel()
        }
    }

    private fun currentPlayerPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setAction(ACTION_OPEN_CURRENT_PLAYER)
            .putExtra(EXTRA_OPEN_CURRENT_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun mediaButtonPreferences(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .setDisplayName("上一项")
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .setDisplayName("下一项")
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName("后退 10 秒")
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("前进 10 秒")
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )
}

internal fun shouldStopAfterTaskRemoved(
    releaseStarted: Boolean,
    playWhenReady: Boolean,
    hasConnectedControllers: Boolean,
): Boolean = !releaseStarted && !playWhenReady && !hasConnectedControllers
