package com.local.mediaviewer.service

import android.os.Binder
import android.os.Process
import android.view.ViewGroup
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackCoordinator

class LocalVideoOutputBinder internal constructor(
    private val coordinator: PlaybackCoordinator,
    private val callingUid: () -> Int,
    private val processUid: () -> Int,
) : Binder() {
    constructor(coordinator: PlaybackCoordinator) : this(
        coordinator = coordinator,
        callingUid = Binder::getCallingUid,
        processUid = Process::myUid,
    )

    private var attachedHost: ViewGroup? = null

    fun attach(host: ViewGroup) {
        enforceSameUid()
        if (attachedHost === host) return
        coordinator.attachVideoOutput(host)
        attachedHost = host
    }

    fun detach() {
        enforceSameUid()
        detachFromOwner()
    }

    internal fun detachFromOwner() {
        if (attachedHost == null) return
        coordinator.detachVideoOutput()
        attachedHost = null
    }

    fun setScaleMode(mode: VideoScaleMode) {
        enforceSameUid()
        coordinator.setVideoScaleMode(mode)
    }

    private fun enforceSameUid() {
        if (callingUid() != processUid()) {
            throw SecurityException("本地视频输出仅允许应用自身进程访问")
        }
    }
}

const val ACTION_LOCAL_VIDEO_OUTPUT =
    "com.local.mediaviewer.action.LOCAL_VIDEO_OUTPUT"

const val ACTION_STOP_AND_RELEASE =
    "com.local.mediaviewer.action.STOP_AND_RELEASE"
