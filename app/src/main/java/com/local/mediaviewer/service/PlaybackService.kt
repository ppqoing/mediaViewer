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
import com.local.mediaviewer.playback.PlaybackInterruption
import com.local.mediaviewer.playback.PlaybackInterruptions
import com.local.mediaviewer.queue.PlaybackCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class PlaybackFocusGate(
    private val acquireFocus: () -> Boolean,
    private val pause: () -> Unit,
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
                if (wasPlaying) pause()
            }

            PlaybackInterruption.FocusGained -> {
                if (resumeAfterFocusGain) {
                    resumeAfterFocusGain = false
                    resume()
                }
            }

            PlaybackInterruption.PermanentLoss,
            PlaybackInterruption.BecomingNoisy,
            -> {
                resumeAfterFocusGain = false
                pause()
            }
        }
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
    private var resourcesReleased = false

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
            pause = coordinator::pause,
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
                    onStopAndRelease = {
                        mainHandler.post(::stopAndRelease)
                    },
                ),
            )
            .setMediaButtonPreferences(mediaButtonPreferences())
            .build()
        localVideoBinder = LocalVideoOutputBinder(coordinator)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaSession? = if (resourcesReleased) null else session

    override fun onBind(intent: Intent): IBinder? =
        if (intent.action == ACTION_LOCAL_VIDEO_OUTPUT) {
            localVideoBinder
        } else {
            super.onBind(intent)
        }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP_AND_RELEASE) {
            stopAndRelease()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (
            !resourcesReleased &&
            !player.playWhenReady &&
            session.connectedControllers.isEmpty()
        ) {
            stopAndRelease()
        }
    }

    override fun onDestroy() {
        releaseOwnedResources()
        super.onDestroy()
    }

    private fun stopAndRelease() {
        releaseOwnedResources()
        stopSelf()
    }

    private fun releaseOwnedResources() {
        if (resourcesReleased) return
        resourcesReleased = true
        runBlocking {
            coordinator.saveCurrentSnapshot()
        }
        localVideoBinder.detachFromOwner()
        session.release()
        player.release()
        interruptions.close()
        coordinator.close()
        serviceScope.cancel()
    }

    private fun currentPlayerPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ),
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
