package com.local.mediaviewer.service

import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.view.ViewGroup
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackCoordinator
import java.util.concurrent.atomic.AtomicBoolean

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

    private val released = AtomicBoolean(false)
    private val lock = Any()
    private var attachedHost: ViewGroup? = null

    fun attach(host: ViewGroup) {
        enforceSameUid()
        synchronized(lock) {
            checkActive()
            if (attachedHost === host) return
            coordinator.attachVideoOutput(host)
            attachedHost = host
        }
    }

    fun detach() {
        enforceSameUid()
        synchronized(lock) {
            checkActive()
            detachLocked()
        }
    }

    fun refresh() {
        enforceSameUid()
        synchronized(lock) {
            checkActive()
            if (attachedHost != null) coordinator.refreshVideoOutput()
        }
    }

    internal fun invalidate() {
        if (!released.compareAndSet(false, true)) return
        synchronized(lock) {
            detachLocked()
        }
    }

    internal fun isReleased(): Boolean = released.get()

    private fun detachLocked() {
        if (attachedHost == null) return
        coordinator.detachVideoOutput()
        attachedHost = null
    }

    fun setScaleMode(mode: VideoScaleMode) {
        enforceSameUid()
        synchronized(lock) {
            checkActive()
            coordinator.setVideoScaleMode(mode)
        }
    }

    private fun enforceSameUid() {
        if (callingUid() != processUid()) {
            throw SecurityException("本地视频输出仅允许应用自身进程访问")
        }
    }

    private fun checkActive() {
        check(!released.get()) { "本地视频输出 binder 已释放" }
    }
}

internal class LocalVideoOutputBindingChannel(
    private val binder: LocalVideoOutputBinder,
) {
    fun bind(): IBinder? = if (binder.isReleased()) null else binder

    fun invalidate() {
        binder.invalidate()
    }
}

const val ACTION_LOCAL_VIDEO_OUTPUT =
    "com.local.mediaviewer.action.LOCAL_VIDEO_OUTPUT"

const val ACTION_STOP_AND_RELEASE =
    "com.local.mediaviewer.action.STOP_AND_RELEASE"

const val ACTION_RELOAD_CURRENT =
    "com.local.mediaviewer.action.RELOAD_CURRENT"
