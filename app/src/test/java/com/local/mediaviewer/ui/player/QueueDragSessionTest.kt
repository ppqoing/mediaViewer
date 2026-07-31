package com.local.mediaviewer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueDragSessionTest {
    @Test
    fun `one gesture crosses several rows and preserves residual movement`() {
        val start = QueueDragSession(
            mediaKey = "c",
            startIndex = 2,
        )
        val update = start.advance(
            deltaPx = 170f,
            rowExtentPx = 60f,
            lastIndex = 6,
        )

        assertEquals(listOf(3, 4), update.crossedIndices)
        assertEquals(4, update.session.currentIndex)
        assertEquals(50f, update.session.residualPx, 0.001f)
        assertEquals(QueueDrop("c", 4), update.session.finish())
    }

    @Test
    fun `drag clamps to list bounds`() {
        val update = QueueDragSession("a", startIndex = 0)
            .advance(-180f, rowExtentPx = 60f, lastIndex = 4)
        assertTrue(update.crossedIndices.isEmpty())
        assertEquals(0, update.session.currentIndex)
        assertNull(update.session.finish())
    }

    @Test
    fun `reversing after overshoot crosses back to the net target`() {
        val down = QueueDragSession("c", startIndex = 2)
            .advance(170f, rowExtentPx = 60f, lastIndex = 6)
        val reversed = down.session
            .advance(-60f, rowExtentPx = 60f, lastIndex = 6)

        assertEquals(listOf(3), reversed.crossedIndices)
        assertEquals(3, reversed.session.currentIndex)
        assertEquals(110f, reversed.session.totalDisplacementPx, 0.001f)
        assertEquals(50f, reversed.session.residualPx, 0.001f)
        assertEquals(QueueDrop("c", 3), reversed.session.finish())
    }
}
