package com.local.mediaviewer.player

import java.util.ArrayDeque

internal class ControllerConnectionMachine<T : Any>(
    private val maxPendingCommands: Int,
    private val onStateChanged: (ControllerConnectionState) -> Unit,
    private val requestConnection: (generation: Long) -> Unit,
    private val release: (T) -> Unit,
) {
    private val pendingCommands = ArrayDeque<(T) -> Unit>()
    private var generation = 0L
    private var current: T? = null
    private var connectionInProgress = false
    private var started = false
    private var closed = false

    init {
        require(maxPendingCommands > 0)
    }

    fun start() {
        if (started || closed) return
        started = true
        demandConnection()
    }

    fun submit(command: (T) -> Unit) {
        if (closed) return
        current?.let {
            runCatching { command(it) }
            return
        }
        if (pendingCommands.size == maxPendingCommands) {
            pendingCommands.removeFirst()
        }
        pendingCommands.addLast(command)
        demandConnection()
    }

    fun onConnected(
        generation: Long,
        value: T,
    ) {
        if (closed || generation != this.generation) {
            release(value)
            return
        }
        current?.takeIf { it !== value }?.let(release)
        connectionInProgress = false
        current = value
        onStateChanged(ControllerConnectionState.Connected)
        while (pendingCommands.isNotEmpty() && current === value) {
            runCatching { pendingCommands.removeFirst()(value) }
        }
    }

    fun onConnectionFailed(
        generation: Long,
        message: String,
        shouldReconnect: Boolean,
    ) {
        if (closed || generation != this.generation) return
        connectionInProgress = false
        current?.let(release)
        current = null
        onStateChanged(ControllerConnectionState.Failed(message))
        if (shouldReconnect) beginConnection()
    }

    fun onDisconnected(
        value: T,
        shouldReconnect: Boolean,
    ) {
        if (closed || current !== value) return
        current = null
        release(value)
        if (shouldReconnect) beginConnection() else enterDormant()
    }

    fun onAppStopped(playWhenReady: Boolean) {
        if (!playWhenReady) enterDormant()
    }

    fun demandConnection() {
        if (closed || current != null || connectionInProgress) return
        beginConnection()
    }

    fun currentOrNull(): T? = current

    fun isCurrent(value: T): Boolean = current === value

    fun isCurrentGeneration(generation: Long): Boolean =
        !closed && generation == this.generation

    fun isClosed(): Boolean = closed

    fun close() {
        if (closed) return
        closed = true
        pendingCommands.clear()
        connectionInProgress = false
        current?.let(release)
        current = null
    }

    private fun beginConnection() {
        if (closed || current != null || connectionInProgress) return
        connectionInProgress = true
        generation += 1
        onStateChanged(ControllerConnectionState.Connecting)
        requestConnection(generation)
    }

    private fun enterDormant() {
        if (closed) return
        generation += 1
        connectionInProgress = false
        current?.let(release)
        current = null
        onStateChanged(ControllerConnectionState.Dormant)
    }
}
