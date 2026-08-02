package com.local.mediaviewer.ui.player

import com.local.mediaviewer.R
import com.local.mediaviewer.ui.icons.MediaIcon

object PlayerIcons {
    val Play = MediaIcon(R.drawable.ic_wp_play)
    val Pause = MediaIcon(R.drawable.ic_wp_pause)
    val Replay = MediaIcon(R.drawable.ic_wp_replay)
    val Back10 = MediaIcon(R.drawable.ic_wp_back_10)
    val Forward10 = MediaIcon(R.drawable.ic_wp_forward_10)
    val Previous = MediaIcon(R.drawable.ic_wp_previous)
    val Next = MediaIcon(R.drawable.ic_wp_next)
    val Queue = MediaIcon(R.drawable.ic_wp_queue)
    val Delete = MediaIcon(R.drawable.ic_wp_delete)
    val Drag = MediaIcon(R.drawable.ic_wp_drag)
    val Volume = MediaIcon(R.drawable.ic_wp_volume)
    val Muted = MediaIcon(R.drawable.ic_wp_muted)
    val Brightness = MediaIcon(R.drawable.ic_wp_brightness)
    val Lock = MediaIcon(R.drawable.ic_wp_lock)
    val Unlock = MediaIcon(R.drawable.ic_wp_unlock)
    val FullscreenEnter = MediaIcon(R.drawable.ic_wp_fullscreen_enter)
    val FullscreenExit = MediaIcon(R.drawable.ic_wp_fullscreen_exit)
    val Speed = MediaIcon(R.drawable.ic_wp_speed)
    val Scale = MediaIcon(R.drawable.ic_wp_scale)
    val Sequential = MediaIcon(R.drawable.ic_wp_sequential)
    val RepeatAll = MediaIcon(R.drawable.ic_wp_repeat_all)
    val RepeatOne = MediaIcon(R.drawable.ic_wp_repeat_one)
    val Shuffle = MediaIcon(R.drawable.ic_wp_shuffle)
    val Playing = MediaIcon(R.drawable.ic_wp_playing)
    val BackgroundPlay = MediaIcon(R.drawable.ic_wp_background_play)

    val all = listOf(
        Play, Pause, Replay, Back10, Forward10, Previous, Next, Queue, Delete, Drag,
        Volume, Muted, Brightness, Lock, Unlock, FullscreenEnter, FullscreenExit, Speed,
        Scale, Sequential, RepeatAll, RepeatOne, Shuffle, Playing, BackgroundPlay,
    )
}
