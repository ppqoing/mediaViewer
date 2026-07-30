package com.local.mediaviewer.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object PlayerIcons {
    val Play = filledIcon("PlayerPlay", "M8,5 L19,12 L8,19 Z")
    val Pause = filledIcon("PlayerPause", "M6,5 H10 V19 H6 Z M14,5 H18 V19 H14 Z")
    val Replay = filledIcon("PlayerReplay", "M7,4 V7 C9,5 11,4 14,4 C18,4 21,7 21,12 C21,17 17,20 12,20 C8,20 5,18 4,14 H7 C8,16 10,17 12,17 C15,17 18,15 18,12 C18,9 16,7 13,7 C11,7 9,8 8,10 L11,10 L6,15 L1,10 H4 C4,7 5,5 7,4 Z")
    val Back10 = filledIcon("PlayerBack10", "M8,5 V2 L3,7 L8,12 V9 C12,6 18,9 18,14 C18,19 12,22 8,18 L10,16 C12,18 15,17 15,14 C15,11 11,10 9,12 L7,10 C7,8 7,7 8,5 Z M5,13 H7 V19 H5 Z")
    val Forward10 = filledIcon("PlayerForward10", "M16,5 V2 L21,7 L16,12 V9 C12,6 6,9 6,14 C6,19 12,22 16,18 L14,16 C12,18 9,17 9,14 C9,11 13,10 15,12 L17,10 C17,8 17,7 16,5 Z M17,13 H19 V19 H17 Z")
    val Previous = filledIcon("PlayerPrevious", "M6,5 H9 V19 H6 Z M10,12 L19,5 V19 Z")
    val Next = filledIcon("PlayerNext", "M15,5 H18 V19 H15 Z M5,5 L14,12 L5,19 Z")
    val Queue = filledIcon("PlayerQueue", "M4,5 H7 V8 H4 Z M9,5 H20 V8 H9 Z M4,10 H7 V13 H4 Z M9,10 H20 V13 H9 Z M4,15 H7 V18 H4 Z M9,15 H16 V18 H9 Z M18,14 L22,17 L18,20 Z")
    val Delete = filledIcon("PlayerDelete", "M7,7 H17 L16,20 H8 Z M9,4 H15 L16,6 H20 V8 H4 V6 H8 Z")
    val Drag = filledIcon("PlayerDrag", "M5,7 H19 V9 H5 Z M5,11 H19 V13 H5 Z M5,15 H19 V17 H5 Z")
    val Volume = filledIcon("PlayerVolume", "M4,9 H8 L13,5 V19 L8,15 H4 Z M15,9 C17,10 17,14 15,15 V12 Z")
    val Muted = filledIcon("PlayerMuted", "M4,9 H8 L13,5 V19 L8,15 H4 Z M15,8 L21,14 L19.5,15.5 L13.5,9.5 Z M19.5,8 L21,9.5 L15,15.5 L13.5,14 Z")
    val Brightness = filledIcon("PlayerBrightness", "M10,2 H14 V6 H10 Z M10,18 H14 V22 H10 Z M2,10 H6 V14 H2 Z M18,10 H22 V14 H18 Z M4,4 L7,6 L6,8 L3,6 Z M17,16 L21,18 L19,21 L16,17 Z M18,3 L21,6 L17,8 L16,6 Z M3,18 L6,16 L8,18 L6,21 Z M8,8 H16 V16 H8 Z")
    val Lock = filledIcon("PlayerLock", "M7,10 H17 V21 H7 Z M9,10 V7 C9,3 15,3 15,7 V10 H13 V7 C13,5 11,5 11,7 V10 Z")
    val Unlock = filledIcon("PlayerUnlock", "M7,10 H17 V21 H7 Z M9,10 V7 C9,3 15,3 16,7 L14,8 C14,5 11,5 11,7 V10 Z")
    val FullscreenExit = filledIcon("PlayerFullscreenExit", "M4,4 H10 V7 H7 V10 H4 Z M14,4 H20 V10 H17 V7 H14 Z M4,14 H7 V17 H10 V20 H4 Z M17,14 H20 V20 H14 V17 H17 Z")
    val Speed = filledIcon("PlayerSpeed", "M4,17 C2,11 6,5 12,5 C18,5 22,11 20,17 H17 C18,13 16,8 12,8 C8,8 6,13 7,17 Z M11,16 L16,10 L14,18 H10 Z")
    val Scale = filledIcon("PlayerScale", "M3,3 H10 V6 H7 V9 H4 V6 H3 Z M14,3 H21 V10 H18 V7 H15 V4 H14 Z M3,14 H6 V17 H9 V20 H3 Z M18,14 H21 V21 H14 V18 H17 V15 H18 Z")
    val RepeatAll = filledIcon("PlayerRepeatAll", "M6,5 H18 L22,9 L18,13 V10 H7 V13 L3,9 Z M18,19 H6 L2,15 L6,11 V14 H17 V11 L21,15 Z")
    val RepeatOne = filledIcon("PlayerRepeatOne", "M6,4 H18 L22,8 L18,12 V9 H7 V12 L3,8 Z M18,20 H6 L2,16 L6,12 V15 H17 V12 L21,16 Z M11,10 H14 V18 H11 Z")
    val Shuffle = filledIcon("PlayerShuffle", "M3,6 H7 L18,17 H21 V14 L23,18 L21,22 V20 H17 L6,9 H3 Z M15,6 H21 V4 L23,8 L21,12 V9 H15 L12,12 L10,10 Z M3,17 H7 L9,15 L11,17 L8,20 H3 Z")
    val Sequential = filledIcon("PlayerSequential", "M4,5 H16 V8 H4 Z M4,10 H16 V13 H4 Z M4,15 H13 V18 H4 Z M16,14 L21,17 L16,20 Z")
    val Playing = filledIcon("PlayerPlaying", "M5,14 H8 V20 H5 Z M10,9 H13 V20 H10 Z M15,4 H18 V20 H15 Z")

    val all: List<ImageVector>
        get() = listOf(
            Play, Pause, Replay, Back10, Forward10, Previous, Next,
            Queue, Delete, Drag, Volume, Muted, Brightness, Lock,
            Unlock, FullscreenExit, Speed, Scale, RepeatAll, RepeatOne,
            Shuffle, Sequential, Playing,
        )
}

private fun filledIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
