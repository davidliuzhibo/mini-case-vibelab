package com.vrgames.minicase

import com.vrgames.minicase.core.Cases
import com.vrgames.minicase.core.Placement
import com.vrgames.minicase.core.Reveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class FixesV09Test {
    @Test
    fun tableComesFromTheEyesWhateverTheOrigin() {
        // Seated with the origin on the floor, standing, and with the origin at the head: always 42 cm under the eyes.
        assertEquals(0.78f, Placement.tableFor(1.2f), 1e-4f)
        assertEquals(1.23f, Placement.tableFor(1.65f), 1e-4f)
        assertEquals(-0.42f, Placement.tableFor(0f), 1e-4f)
        // The player's own adjustment rides on top.
        assertEquals(0.88f, Placement.tableFor(1.2f, 0.1f), 1e-4f)
        // One jumpy head sample does not move it.
        assertEquals(1.21f, Placement.steadyEye(listOf(1.2f, 1.21f, 1.22f, 0.3f, 1.21f))!!, 1e-4f)
    }

    @Test
    fun aSlightTurnShowsTheHiddenSide() {
        fun facing(deg: Double) = cos(Math.toRadians(deg))
        // Held with its hidden side straight away (180 deg): a 30 deg turn is enough...
        assertTrue(Reveal.seen(facing(180.0), facing(150.0)))
        // ...a 15 deg wobble is not.
        assertFalse(Reveal.seen(facing(180.0), facing(165.0)))
        // Held sideways (90 deg): turning it to face the player even a little more.
        assertTrue(Reveal.seen(facing(90.0), facing(80.0)))
        // Before v0.9 it needed facing > 0.72 (within 44 degrees).
        assertTrue(Reveal.seen(facing(150.0), facing(100.0)))
    }

    @Test
    fun theWatchBoxIsTheOnlyThingInTheDrawer() {
        val c = Cases.byId("wutong")
        assertEquals(listOf("watchbox"), c.props.filter { "drawer" in it.needs }.map { it.id })
    }
}
