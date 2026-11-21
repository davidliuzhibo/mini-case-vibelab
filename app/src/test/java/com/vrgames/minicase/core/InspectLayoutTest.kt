package com.vrgames.minicase.core

import com.vrgames.minicase.core.InspectLayout.Layout
import com.vrgames.minicase.core.InspectLayout.Panel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** v0.9.2: the thing held up close, its item box and the clue box keep apart, and 放回原处 is clear of the thing. */
class InspectLayoutTest {
    private val eyes = InspectLayout.eyes()

    @Test fun theThreeKeepApartFromEverySeat() {
        assertEquals(264, eyes.size)
        assertEquals(emptyList<String>(), InspectLayout.problems(InspectLayout.NOW, eyes))
        val worst = eyes.minOf { InspectLayout.separation(InspectLayout.NOW, it) }
        assertTrue("worst separation $worst", worst >= InspectLayout.MIN_GAP_DEG)
        // From the default seat there is plenty of room (5.4 degrees between the thing and its box, 6.2 to the clue box).
        assertTrue(InspectLayout.separation(InspectLayout.NOW, InspectLayout.DEFAULT_EYE) >= 5.0)
        // The two boxes never overlap, from anywhere in the range.
        for (eye in eyes) {
            val box = InspectLayout.span(eye, InspectLayout.NOW.itemBox)
            val clue = InspectLayout.span(eye, InspectLayout.NOW.clue)
            assertTrue(InspectLayout.gapDeg(box, clue) > 0)
        }
    }

    @Test fun aLookAtTheButtonMissesTheThing() {
        val b = InspectLayout.BUTTON
        // The button sits inside its box, at the bottom left.
        val box = InspectLayout.ITEM_BOX
        assertEquals(box.at.x - box.w / 2 + InspectLayout.ITEM_BOX_PAD_X, b.at.x - b.w / 2, 1e-9)
        assertEquals(box.at.y - box.h / 2 + InspectLayout.ITEM_BOX_PAD_Y, b.at.y - b.h / 2, 1e-9)
        assertTrue(b.at.x + b.w / 2 <= box.at.x + box.w / 2 - InspectLayout.ITEM_BOX_PAD_X && b.at.y + b.h / 2 <= box.at.y)
        // 3 x 3 sight lines to it pass clear of the pinch box turned any way (13 cm thing + 1 cm each side).
        var clearance = Double.MAX_VALUE
        for (eye in eyes) for (p in InspectLayout.buttonPoints(b)) {
            clearance = minOf(clearance, InspectLayout.lineClearance(eye, p, InspectLayout.ITEM_AT) - InspectLayout.HIT_REACH)
        }
        assertTrue("clearance $clearance", clearance > 0.03)
    }

    @Test fun theHeldPinchBoxIsTheVisibleSizeAndOneCentimetre() {
        // Small things grow 2.6-11.8 times up close (largest edge 1.1-5 cm).
        for (size in listOf(0.011, 0.02, 0.03, 0.05)) {
            val k = InspectLayout.heldScale(size)
            assertEquals(InspectLayout.ITEM_SIZE, size * k, 1e-9)
            for (edge in listOf(0.002, size / 3, size)) {
                val visible = edge * k
                val held = InspectLayout.heldHitLocal(edge, k) * k
                val perSide = (held - visible) / 2
                assertEquals(0.01, perSide, 1e-9)
                assertTrue(perSide <= 0.02)
                // The 0.9.1 box (the table's: +1.2 cm, at least 4 cm, then scaled with the thing) grew far beyond.
                val old = maxOf(edge + 0.024, 0.04) * k
                if (size <= 0.03) assertTrue((old - visible) / 2 > 0.02)
            }
        }
        // Any held box fits in the reach the layout keeps the button clear of.
        assertTrue((InspectLayout.ITEM_SIZE + 0.02) * sqrt(3.0) / 2 <= InspectLayout.HIT_REACH + 1e-9)
    }

    @Test fun theItemBoxLines() {
        val examine = Cases.all.flatMap { c -> c.props.filter { it.action == Action.EXAMINE }.map { c to it } }
        assertEquals(32, examine.size)
        assertEquals(19, examine.count { it.second.hiddenSide == null })
        assertEquals(13, examine.count { it.second.hiddenSide != null })
        for ((c, p) in examine) {
            val look = p.look
            assertTrue("${p.id}: no line", look.isNotBlank())
            assertTrue("${p.id}: ${look.length} chars", look.length <= 14)
            for (bad in listOf("转", "翻", "每一面")) assertFalse("${p.id}: '$bad' in 「$look」", bad in look)
            for (clueId in p.gives) {
                val clue = c.clue(clueId)
                for (text in listOf(clue.title, clue.text)) {
                    val shared = longestShared(look, text)
                    assertTrue("${p.id}: 「$shared」 is also in its clue", shared.length < 4)
                }
            }
            // A thing whose clue shows on pick-up never asks to be turned; a hidden side does until it is found.
            assertEquals(look, InspectLayout.itemLine(p, null))
            p.hiddenSide?.let { side ->
                val nudge = InspectLayout.itemLine(p, side)
                assertTrue(side.label in nudge && "转" in nudge)
                // Two lines at most in the item box (about 13 characters a line).
                assertTrue(nudge.length <= 26)
            }
        }
    }

    @Test fun theLayoutOf091FailsTheCheck() {
        // 0.9.1: the thing at (0, 0.24, 0.17) under both boxes; the item box (0.36 wide) at (0, 0.385, 0.15), the clue box
        // 3 cm behind it at (0, 0.36, 0.12); the medicine pack's pinch box up close 27 x 26 x 29 cm.
        val box = Panel(Vec3(0.0, 0.385, 0.15), 0.36, 0.16)
        val button = Panel(Vec3(-0.18 + 0.024 + 0.074, 0.3475, 0.15), 0.148, 0.048)
        val old = Layout(
            item = Vec3(0.0, 0.24, 0.17),
            itemReach = InspectLayout.ITEM_REACH,
            hitReach = sqrt(0.27 * 0.27 + 0.26 * 0.26 + 0.29 * 0.29) / 2,
            itemBox = box,
            button = button,
            clue = Panel(Vec3(0.0, 0.36, 0.12), 0.46, 0.20),
        )
        val problems = InspectLayout.problems(old, eyes)
        assertTrue(problems.toString(), "item box and clue box overlap" in problems)
        assertTrue(problems.toString(), "a look at the button passes through the item's pinch box" in problems)
        // Even from the default seat.
        assertTrue(InspectLayout.problems(old, listOf(InspectLayout.DEFAULT_EYE)).isNotEmpty())
    }

    @Test fun thePickUpPinchDoesNotTurnTheThing() {
        val g = TurnGate()
        g.close(1000)
        // Jitter while the pinch closes, and a small wrist wobble: dropped, even after 150 ms.
        assertFalse(g.move(1010, 0.004, 0.0, 0.0))
        assertFalse(g.move(1100, -0.003, 0.002, 0.0))
        assertFalse(g.turn(1120, 3.0, 0.0, 0.0))
        assertFalse(g.move(1300, 0.002, -0.002, 0.0))
        assertFalse(g.turn(1320, -2.0, 1.0, 0.0))
        assertFalse(g.isOpen)
        // A real move past 1 cm: from then on it turns.
        assertFalse(g.move(1400, 0.012, 0.0, 0.0))
        assertTrue(g.isOpen)
        assertTrue(g.move(1410, 0.001, 0.0, 0.0))
        assertTrue(g.turn(1420, 1.0, 0.0, 0.0))
        // A quick 1 cm flick inside the first 150 ms is still the pinch.
        g.close(5000)
        assertFalse(g.move(5050, 0.02, 0.0, 0.0))
        assertFalse(g.isOpen)
        // A deliberate wrist turn after it opens it too.
        g.close(8000)
        assertFalse(g.turn(8200, 0.0, 9.0, 0.0))
        assertTrue(g.isOpen)
        // A new pinch on the thing already up close is not gated.
        g.open()
        assertTrue(g.move(9000, 0.001, 0.0, 0.0))
    }

    private fun longestShared(a: String, b: String): String {
        var best = ""
        for (i in a.indices) for (j in i + best.length + 1..a.length) {
            val s = a.substring(i, j)
            if (s in b) best = s else break
        }
        return best
    }
}
