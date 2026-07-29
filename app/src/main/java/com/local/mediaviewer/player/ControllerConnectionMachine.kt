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
    private var started = false
    private var closed = false

    init {
        require(maxPendingCommands > 0)
    }

    fun start() {
        if (started || closed) return
        started = true
        beginConnection()
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
        current = value
        onStateChanged(ControllerConnectionState.Connected)
        while (pendingCommands.isNotEmpty() && current === value) {
            runCatching { pendingCommands.removeFirst()(value) }
        }
    }

    fun onConnectionFailed(
        generation: Long,
        message: String,
    ) {
        if (closed || generation != this.generation) return
        current?.let(release)
        current = null
        onStateChanged(ControllerConnectionState.Failed(message))
        beginConnection()
    }

    fun onDisconnected(value: T) {
        if (closed || current !== value) return
        current = null
        release(value)
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
        current?.let(release)
        current = null
    }

    private fun beginConnection() {
        if (closed) return
        generation += 1
        onStateChanged(ControllerConnectionState.Connecting)
        requestConnection(generation)
    }
}
