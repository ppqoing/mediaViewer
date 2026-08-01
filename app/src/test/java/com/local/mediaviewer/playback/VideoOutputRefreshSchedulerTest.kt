package com.local.mediaviewer.playback

import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoOutputRefreshSchedulerTest {
    @Test
    fun `vout during resume keeps exactly one pending fallback refresh`() {
        val ownerTasks = ArrayDeque<Runnable>()
        val fallbackTasks = mutableListOf<Runnable>()
        val outputEvents = mutableListOf<String>()
        val scheduler = VideoOutputRefreshScheduler(
            postToOwner = ownerTasks::addLast,
            postDelayedToOwner = { task, _ -> fallbackTasks += task },
            removeFromOwner = fallbackTasks::remove,
            isActive = { true },
            updateVideoSurfaces = {
                outputEvents += "update"
            },
        )

        scheduler.refresh {
            outputEvents += "rebind"
        }
        scheduler.onVout()

        assertTrue(outputEvents.isEmpty())
        assertEquals(1, ownerTasks.size)
        assertEquals(1, fallbackTasks.size)

        ownerTasks.removeFirst().run()
        val fallback = fallbackTasks.single()
        fallbackTasks.remove(fallback)
        fallback.run()

        assertEquals(listOf("update", "rebind"), outputEvents)
        assertTrue(fallbackTasks.isEmpty())
    }

    @Test
    fun `repeated refresh keeps only the latest fallback refresh`() {
        val fallbackTasks = mutableListOf<Runnable>()
        val rebinds = mutableListOf<String>()
        val scheduler = VideoOutputRefreshScheduler(
            postToOwner = {},
            postDelayedToOwner = { task, _ -> fallbackTasks += task },
            removeFromOwner = fallbackTasks::remove,
            isActive = { true },
            updateVideoSurfaces = {},
        )

        scheduler.refresh { rebinds += "old" }
        scheduler.refresh { rebinds += "latest" }

        assertEquals(1, fallbackTasks.size)
        fallbackTasks.single().run()
        assertEquals(listOf("latest"), rebinds)
    }

    @Test
    fun `detach cancellation removes pending fallback refresh`() {
        val fallbackTasks = mutableListOf<Runnable>()
        var rebinds = 0
        val scheduler = VideoOutputRefreshScheduler(
            postToOwner = {},
            postDelayedToOwner = { task, _ -> fallbackTasks += task },
            removeFromOwner = fallbackTasks::remove,
            isActive = { true },
            updateVideoSurfaces = {},
        )

        scheduler.refresh { rebinds += 1 }
        scheduler.cancel()
        fallbackTasks.toList().forEach(Runnable::run)

        assertEquals(0, rebinds)
        assertTrue(fallbackTasks.isEmpty())
    }

    @Test
    fun `queued vout rechecks active state before updating surfaces`() {
        val ownerTasks = ArrayDeque<Runnable>()
        var active = true
        var updateCalls = 0
        val scheduler = VideoOutputRefreshScheduler(
            postToOwner = ownerTasks::addLast,
            postDelayedToOwner = { _, _ -> },
            removeFromOwner = {},
            isActive = { active },
            updateVideoSurfaces = { updateCalls += 1 },
        )

        scheduler.onVout()
        active = false
        ownerTasks.removeFirst().run()

        assertEquals(0, updateCalls)
    }
}
