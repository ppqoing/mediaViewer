package com.local.mediaviewer.ui.player

import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.ui.icons.MediaIcon

enum class PlaybackPrimaryCommand {
    PLAY,
    PAUSE,
    REPLAY,
    NONE,
}

data class PlaybackPrimaryAction(
    val command: PlaybackPrimaryCommand,
    val icon: MediaIcon,
    val contentDescription: String,
    val stateDescription: String? = null,
    val enabled: Boolean = true,
    val loading: Boolean = false,
)

fun playbackPrimaryAction(status: PlaybackStatus): PlaybackPrimaryAction =
    when (status) {
        PlaybackStatus.IDLE,
        PlaybackStatus.PAUSED,
        -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.PLAY,
            icon = PlayerIcons.Play,
            contentDescription = "播放",
        )

        PlaybackStatus.PLAYING -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.PAUSE,
            icon = PlayerIcons.Pause,
            contentDescription = "暂停",
        )

        PlaybackStatus.BUFFERING -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.PAUSE,
            icon = PlayerIcons.Pause,
            contentDescription = "暂停",
            stateDescription = "正在缓冲，可暂停",
        )

        PlaybackStatus.ENDED -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.REPLAY,
            icon = PlayerIcons.Replay,
            contentDescription = "重新播放",
        )

        PlaybackStatus.OPENING -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.NONE,
            icon = PlayerIcons.Play,
            contentDescription = "正在打开",
            stateDescription = "正在打开媒体",
            enabled = false,
            loading = true,
        )

        PlaybackStatus.ERROR -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.NONE,
            icon = PlayerIcons.Play,
            contentDescription = "播放不可用",
            stateDescription = "播放错误",
            enabled = false,
        )
    }

internal fun PlaybackPrimaryCommand.invoke(
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
) = when (this) {
    PlaybackPrimaryCommand.PLAY -> onPlay()
    PlaybackPrimaryCommand.PAUSE -> onPause()
    PlaybackPrimaryCommand.REPLAY -> onReplay()
    PlaybackPrimaryCommand.NONE -> Unit
}
