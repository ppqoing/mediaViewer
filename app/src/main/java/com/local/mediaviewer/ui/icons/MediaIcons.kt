package com.local.mediaviewer.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ViewStream

object MediaIcons {
    val Back = Icons.AutoMirrored.Filled.ArrowBack
    val Settings = Icons.Filled.Settings
    val Folder = Icons.Filled.Folder
    val Video = Icons.Filled.Movie
    val Audio = Icons.Filled.AudioFile
    val Image = Icons.Filled.Image
    val File = Icons.AutoMirrored.Filled.InsertDriveFile
    val Connected = Icons.Filled.CheckCircle
    val Offline = Icons.Filled.CloudOff
    val Empty = Icons.Filled.Inbox
    val Error = Icons.Filled.ErrorOutline
    val Refresh = Icons.Filled.Refresh
    val More = Icons.Filled.MoreVert
    val PlayNow = Icons.Filled.PlayArrow
    val PlayNext = Icons.Filled.SkipNext
    val AddQueue = Icons.Filled.AddToQueue
    val Queue = Icons.AutoMirrored.Filled.QueueMusic
    val ReaderMode = Icons.Filled.ViewStream
    val Sort = Icons.AutoMirrored.Filled.Sort
    val Check = Icons.Filled.Check

    val all = listOf(
        Back, Settings, Folder, Video, Audio, Image, File,
        Connected, Offline, Empty, Error, Refresh, More,
        PlayNow, PlayNext, AddQueue, Queue, ReaderMode, Sort,
        Check,
    )
}
