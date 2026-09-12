package com.leo.imessage.ui

import com.leo.imessage.ui.components.levelFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of the unread fill curve.
 *
 * Asserting the shape rather than a table of numbers on purpose. The exact
 * percentages are taste and will get nudged; what must not change is why the
 * curve is not a straight line - one unread has to be visible, the early
 * messages have to be the ones that move it most, and it has to approach a
 * ceiling it never reaches, because the gap left at the top is what makes it
 * read as liquid in a container rather than a filled shape.
 */
class UnreadLevelTest {

    @Test
    fun `nothing waiting is empty`() {
        assertEquals(0f, levelFor(0), 0.0001f)
        assertEquals(0f, levelFor(-3), 0.0001f)
    }

    @Test
    fun `one unread is plainly visible`() {
        assertTrue("a single unread barely registers", levelFor(1) >= 0.12f)
    }

    @Test
    fun `it never fills`() {
        // The ceiling, tested well past any count anybody will have.
        listOf(1, 10, 50, 500, 10_000).forEach {
            assertTrue("$it unread fills it to ${levelFor(it)}", levelFor(it) <= 0.9f)
        }
    }

    @Test
    fun `more waiting is always more liquid`() {
        var previous = levelFor(1)
        (2..200).forEach {
            val level = levelFor(it)
            assertTrue("$it went backwards", level >= previous)
            previous = level
        }
    }

    @Test
    fun `the early messages are the ones that move it`() {
        // Saturating, not proportional: the step from one to five has to be
        // worth more than the step from twenty to fifty, or high counts drown
        // the face underneath.
        val early = levelFor(5) - levelFor(1)
        val late = levelFor(50) - levelFor(20)
        assertTrue("the curve is not saturating: early=$early late=$late", early > late * 2)
    }

    @Test
    fun `it is not a straight line`() {
        // Proportional scaling would put ten unread at exactly twice five.
        assertTrue(levelFor(10) < levelFor(5) * 2f)
    }
}
