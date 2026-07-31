package com.local.mediaviewer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerConnectionMachineTest {
    @Test
    fun `explicit reconnect after failure creates a new generation without dropping commands`() {
        val requestedGenerations = mutableListOf<Long>()
        val executedWith = mutableListOf<String>()
        val machine = ControllerConnectionMachine<String>(
            maxPendingCommands = 8,
            onStateChanged = {},
            requestConnection = requestedGenerations::add,
            release = {},
        )
        machine.start()
        val firstGeneration = requestedGenerations.single()
        machine.submit { executedWith += it }
        machine.onConnectionFailed(
            generation = firstGeneration,
            message = "offline",
            shouldReconnect = false,
        )

        machine.demandConnection()
        val secondGeneration = requestedGenerations.last()
        machine.onConnected(secondGeneration, "second connection")

        assertTrue(secondGeneration > firstGeneration)
        assertEquals(listOf("second connection"), executedWith)
    }

    @Test
    fun `连接失败进入新代次且旧代次成功不能覆盖新连接`() {
        val requested = mutableListOf<Long>()
        val released = mutableListOf<String>()
        val states = mutableListOf<ControllerConnectionState>()
        val machine = ControllerConnectionMachine<String>(
            maxPendingCommands = 4,
            onStateChanged = states::add,
            requestConnection = requested::add,
            release = released::add,
        )

        machine.start()
        val firstGeneration = requested.single()
        machine.onConnectionFailed(
            firstGeneration,
            "session unavailable",
            shouldReconnect = true,
        )
        val secondGeneration = requested.last()
        machine.onConnected(secondGeneration, "second")
        machine.onConnected(firstGeneration, "stale-first")

        assertTrue(secondGeneration > firstGeneration)
        assertEquals("second", machine.currentOrNull())
        assertEquals(listOf("stale-first"), released)
        assertEquals(ControllerConnectionState.Connected, states.last())
        assertTrue(states.contains(ControllerConnectionState.Failed("session unavailable")))
    }

    @Test
    fun `暂停意图下连接失败停留等待而不循环重建服务`() {
        val requested = mutableListOf<Long>()
        val states = mutableListOf<ControllerConnectionState>()
        val machine = ControllerConnectionMachine<String>(
            maxPendingCommands = 4,
            onStateChanged = states::add,
            requestConnection = requested::add,
            release = {},
        )
        machine.start()

        machine.onConnectionFailed(
            generation = requested.single(),
            message = "service stopped",
            shouldReconnect = false,
        )

        assertEquals(1, requested.size)
        assertEquals(
            ControllerConnectionState.Failed("service stopped"),
            states.last(),
        )

        machine.submit {}
        assertEquals(2, requested.size)
    }

    @Test
    fun `断开当前连接会释放旧代次并自动请求重连`() {
        val requested = mutableListOf<Long>()
        val released = mutableListOf<String>()
        val states = mutableListOf<ControllerConnectionState>()
        val machine = ControllerConnectionMachine<String>(
            maxPendingCommands = 4,
            onStateChanged = states::add,
            requestConnection = requested::add,
            release = released::add,
        )
        machine.start()
        machine.onConnected(requested.single(), "first")

        machine.onDisconnected("first", shouldReconnect = true)

        assertEquals(listOf("first"), released)
        assertEquals(2, requested.size)
        assertEquals(null, machine.currentOrNull())
        assertEquals(ControllerConnectionState.Connecting, states.last())
    }

    @Test
    fun `暂停态断开进入休眠且下一条用户命令才重新连接`() {
        val requested = mutableListOf<Long>()
        val released = mutableListOf<String>()
        val executed = mutableListOf<String>()
        val states = mutableListOf<ControllerConnectionState>()
        val machine = ControllerConnectionMachine<String>(
            maxPendingCommands = 4,
            onStateChanged = states::add,
            requestConnection = requested::add,
            release = released::add,
        )
        machine.start()
        machine.onConnected(requested.single(), "first")

        machine.onDisconnected("first", shouldReconnect = false)

        assertEquals(1, requested.size)
        assertEquals(listOf("first"), released)
        assertEquals(ControllerConnectionState.Dormant, states.last())

        machine.submit { executed += "$it:play" }
        assertEquals(2, requested.size)
        machine.onConnected(requested.last(), "second")

        assertEquals(listOf("second:play"), executed)
    }

    @Test
    fun `暂停态应用停止释放连接而播放态应用停止保持连接`() {
        val requested = mutableListOf<Long>()
        val released = mutableListOf<String>()
        val machine = ControllerConnectionMachine<String>(
            maxPendingCommands = 4,
            onStateChanged = {},
            requestConnection = requested::add,
            release = released::add,
        )
        machine.start()
        machine.onConnected(requested.single(), "playing")

        machine.onAppStopped(playWhenReady = true)

        assertEquals("playing", machine.currentOrNull())
        assertTrue(released.isEmpty())

        machine.onAppStopped(playWhenReady = false)

        assertEquals(null, machine.currentOrNull())
        assertEquals(listOf("playing"), released)
        assertEquals(1, requested.size)
    }

    @Test
    fun `离线命令有界保留最新命令并在重连后按序执行`() {
        val requested = mutableListOf<Long>()
        val executed = mutableListOf<String>()
        val machine = ControllerConnectionMachine<String>(
            maxPendingCommands = 2,
            onStateChanged = {},
            requestConnection = requested::add,
            release = {},
        )
        machine.start()

        machine.submit { executed += "$it:old" }
        machine.submit { executed += "$it:middle" }
        machine.submit { executed += "$it:new" }
        machine.onConnected(requested.single(), "controller")

        assertEquals(
            listOf("controller:middle", "controller:new"),
            executed,
        )
    }

    @Test
    fun `关闭幂等释放当前连接且忽略后续回调和命令`() {
        val requested = mutableListOf<Long>()
        val released = mutableListOf<String>()
        var commandRan = false
        val machine = ControllerConnectionMachine<String>(
            maxPendingCommands = 2,
            onStateChanged = {},
            requestConnection = requested::add,
            release = released::add,
        )
        machine.start()
        val generation = requested.single()
        machine.onConnected(generation, "controller")

        machine.close()
        machine.close()
        machine.submit { commandRan = true }
        machine.onConnected(generation, "late")

        assertEquals(listOf("controller", "late"), released)
        assertFalse(commandRan)
        assertTrue(machine.isClosed())
    }
}
