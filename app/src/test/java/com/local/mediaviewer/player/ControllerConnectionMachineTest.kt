package com.local.mediaviewer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerConnectionMachineTest {
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
        machine.onConnectionFailed(firstGeneration, "session unavailable")
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

        machine.onDisconnected("first")

        assertEquals(listOf("first"), released)
        assertEquals(2, requested.size)
        assertEquals(null, machine.currentOrNull())
        assertEquals(ControllerConnectionState.Connecting, states.last())
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
