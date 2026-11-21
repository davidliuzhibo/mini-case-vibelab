package com.vrgames.minicase.core

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * v0.9.2 (device 2026-11-19: a picked-up rent ledger / medicine pack floated right under its item box, covered
 * 「放回原处」 so only a finger poke reached it, and hid the new clue behind): where the thing held up close, its item
 * box and the clue box stand, and the angle maths that keeps them apart from wherever a seated player's eyes are.
 *
 * All in the table's frame (metres, +z toward the player, the table top at y = 0). Panels are upright, face +z and
 * are anchored at their middle.
 */
object InspectLayout {
    /** Where the thing held up close floats: left of the middle, in front of the scene. */
    val ITEM_AT = Vec3(-0.10, 0.20, 0.21)
    /** Its largest edge up close (metres), as before. */
    const val ITEM_SIZE = 0.13

    /** The item box: to the right of the thing, a fixed size, its 放回原处 button at the bottom left. */
    val ITEM_BOX_AT = Vec3(0.19, 0.21, 0.24)
    const val ITEM_BOX_W = 0.26
    const val ITEM_BOX_H = 0.18
    /** The item box's inner padding (sides, top and bottom). */
    const val ITEM_BOX_PAD_X = 0.022
    const val ITEM_BOX_PAD_Y = 0.016
    /** The 放回原处 button (a PinchLine: icon + four characters). */
    const val BUTTON_W = 0.15
    const val BUTTON_H = 0.05

    /** The clue box (new clues, notes, the hint) when nothing is held: over the middle of the table, as since 0.6.1. */
    val CLUE_AT = Vec3(0.0, 0.36, 0.12)
    /** The clue box while something is held: higher and further back, clear above the thing and its item box. */
    val CLUE_HOLDING_AT = Vec3(0.0, 0.46, 0.02)
    const val CLUE_W = 0.46
    /** The clue box's height at most while something is held (one clue, no hint line). */
    const val CLUE_MAX_H = 0.20

    /** The held thing's pinch box: the visible size plus this much each side (no minimum). */
    const val HELD_PAD = 0.01

    /** A seated player's eyes by default, and the range the layout must work for. */
    val DEFAULT_EYE = Vec3(0.0, 0.42, 0.75)
    /** Separation required between the thing, the item box and the clue box (degrees, across or up/down). */
    const val MIN_GAP_DEG = 2.5

    /** How far the visible thing reaches from [ITEM_AT], turned any way: a cube of [ITEM_SIZE]. */
    val ITEM_REACH = ITEM_SIZE * sqrt(3.0) / 2
    /** How far its pinch box reaches, turned any way: a cube of [ITEM_SIZE] + 2 × [HELD_PAD]. */
    val HIT_REACH = (ITEM_SIZE + 2 * HELD_PAD) * sqrt(3.0) / 2

    /** The scale that makes a thing of largest edge [size] (its own frame) [ITEM_SIZE] long. */
    fun heldScale(size: Double): Double = ITEM_SIZE / maxOf(size, 0.01)

    /** The held pinch box's edge in the thing's own frame, for an edge [local] long shown at [scale]: [HELD_PAD] each side once scaled. */
    fun heldHitLocal(local: Double, scale: Double): Double = local + 2 * HELD_PAD / scale

    /** The item box's line: the turning nudge while a hidden side still hides its clue, otherwise the thing's own line. */
    fun itemLine(p: Prop, stillHidden: Side?): String =
        if (stillHidden != null) "捏住它转动手腕或拖动，把${stillHidden.label}转过来对着自己。" else p.look

    // ---- Geometry -------------------------------------------------------------------------------

    /** An upright panel facing the player (+z), [w] × [h] round [at]. */
    data class Panel(val at: Vec3, val w: Double, val h: Double)

    /** What the eye sees of something: its range across (azimuth) and up/down (elevation), in degrees. */
    data class Span(val azMin: Double, val azMax: Double, val elMin: Double, val elMax: Double)

    data class Layout(
        val item: Vec3,
        /** How far the visible thing reaches from [item], turned any way. */
        val itemReach: Double,
        /** How far its pinch box reaches from [item], turned any way. */
        val hitReach: Double,
        val itemBox: Panel,
        val button: Panel,
        val clue: Panel,
    )

    fun buttonOf(box: Panel) = Panel(
        Vec3(box.at.x - box.w / 2 + ITEM_BOX_PAD_X + BUTTON_W / 2, box.at.y - box.h / 2 + ITEM_BOX_PAD_Y + BUTTON_H / 2, box.at.z),
        BUTTON_W, BUTTON_H,
    )

    val ITEM_BOX = Panel(ITEM_BOX_AT, ITEM_BOX_W, ITEM_BOX_H)
    val BUTTON = buttonOf(ITEM_BOX)

    /** This version's layout while something is held. */
    val NOW = Layout(ITEM_AT, ITEM_REACH, HIT_REACH, ITEM_BOX, BUTTON, Panel(CLUE_HOLDING_AT, CLUE_W, CLUE_MAX_H))

    private fun az(eye: Vec3, p: Vec3) = Math.toDegrees(atan2(p.x - eye.x, -(p.z - eye.z)))
    private fun el(eye: Vec3, p: Vec3) = Math.toDegrees(atan2(p.y - eye.y, hypot(p.x - eye.x, p.z - eye.z)))

    fun span(eye: Vec3, panel: Panel): Span {
        val pts = ArrayList<Vec3>()
        val n = 21
        val (c, w, h) = panel
        for (i in 0 until n) {
            val t = -0.5 + i.toDouble() / (n - 1)
            pts += Vec3(c.x + t * w, c.y - h / 2, c.z)
            pts += Vec3(c.x + t * w, c.y + h / 2, c.z)
            pts += Vec3(c.x - w / 2, c.y + t * h, c.z)
            pts += Vec3(c.x + w / 2, c.y + t * h, c.z)
        }
        // Up/down is steepest straight in front of the eye.
        val x = eye.x.coerceIn(c.x - w / 2, c.x + w / 2)
        pts += Vec3(x, c.y - h / 2, c.z)
        pts += Vec3(x, c.y + h / 2, c.z)
        return Span(pts.minOf { az(eye, it) }, pts.maxOf { az(eye, it) }, pts.minOf { el(eye, it) }, pts.maxOf { el(eye, it) })
    }

    /** What the eye sees of a ball of radius [r] round [c]. */
    fun span(eye: Vec3, c: Vec3, r: Double): Span {
        val across = Math.toDegrees(asin((r / hypot(c.x - eye.x, c.z - eye.z)).coerceAtMost(1.0)))
        val up = Math.toDegrees(asin((r / eye.distanceTo(c)).coerceAtMost(1.0)))
        val a = az(eye, c)
        val e = el(eye, c)
        return Span(a - across, a + across, e - up, e + up)
    }

    /** The gap between two things as seen (degrees): the larger of the gaps across and up/down; negative where they overlap. */
    fun gapDeg(a: Span, b: Span): Double {
        val across = maxOf(b.azMin - a.azMax, a.azMin - b.azMax)
        val up = maxOf(b.elMin - a.elMax, a.elMin - b.elMax)
        return maxOf(across, up)
    }

    /**
     * Eyes to check: 0.32–0.72 m above the table, 0.62–0.90 m from its middle, up to 8 cm to either side (seated, leaning,
     * the table raised or lowered in the menu).
     */
    fun eyes(): List<Vec3> {
        val out = ArrayList<Vec3>()
        for (i in 0..10) for (j in 0..7) for (x in listOf(-0.08, 0.0, 0.08)) out += Vec3(x, 0.32 + 0.04 * i, 0.62 + 0.04 * j)
        return out
    }

    /** The smallest gap among the thing, the item box and the clue box from [eye] (degrees). */
    fun separation(l: Layout, eye: Vec3): Double {
        val item = span(eye, l.item, l.itemReach)
        val box = span(eye, l.itemBox)
        val clue = span(eye, l.clue)
        return minOf(gapDeg(item, box), gapDeg(item, clue), gapDeg(box, clue))
    }

    /** 3 × 3 points over the button, inset from its edges. */
    fun buttonPoints(b: Panel): List<Vec3> {
        val out = ArrayList<Vec3>()
        for (j in listOf(1, 0, -1)) for (i in listOf(-1, 0, 1)) out += Vec3(b.at.x + i * 0.4 * b.w, b.at.y + j * 0.4 * b.h, b.at.z)
        return out
    }

    /** How far the sight line from [eye] to [target] passes from [c] (metres). */
    fun lineClearance(eye: Vec3, target: Vec3, c: Vec3): Double {
        val d = target - eye
        val s = ((c - eye).dot(d) / d.dot(d)).coerceIn(0.0, 1.0)
        return (eye + d * s).distanceTo(c)
    }

    /** What is wrong with [l] over [eyes]: empty when the three keep apart and the button is clear of the pinch box. */
    fun problems(l: Layout, eyes: List<Vec3> = eyes()): List<String> {
        val out = LinkedHashSet<String>()
        for (eye in eyes) {
            val item = span(eye, l.item, l.itemReach)
            val box = span(eye, l.itemBox)
            val clue = span(eye, l.clue)
            if (gapDeg(box, clue) <= 0) out += "item box and clue box overlap"
            if (gapDeg(item, box) < MIN_GAP_DEG) out += "item too close to its box"
            if (gapDeg(item, clue) < MIN_GAP_DEG) out += "item too close to the clue box"
            if (gapDeg(box, clue) < MIN_GAP_DEG) out += "item box too close to the clue box"
            if (buttonPoints(l.button).any { lineClearance(eye, it, l.item) <= l.hitReach }) out += "a look at the button passes through the item's pinch box"
        }
        return out.toList()
    }
}

/**
 * v0.9.2: the pinch that picks a thing up must not also turn it (the 0.9.0 turn is fast, 1300 degrees a metre, and a
 * pinch never holds still). After a pick-up the hand's moves and turns are dropped until [holdMs] have passed and the
 * hand has moved [moveM] or turned [turnDeg] since the pick-up (net, so jitter does not add up); then they turn it as before.
 */
class TurnGate(
    private val holdMs: Long = 150,
    private val moveM: Double = 0.01,
    private val turnDeg: Double = 8.0,
) {
    private var since = OPEN
    private var mx = 0.0; private var my = 0.0; private var mz = 0.0
    private var tx = 0.0; private var ty = 0.0; private var tz = 0.0

    val isOpen get() = since == OPEN

    /** A pick-up at [now] (ms). */
    fun close(now: Long) {
        since = now
        mx = 0.0; my = 0.0; mz = 0.0
        tx = 0.0; ty = 0.0; tz = 0.0
    }

    fun open() { since = OPEN }

    /** The hand moved by ([x], [y], [z]) m at [now]: true when it may turn the held thing. */
    fun move(now: Long, x: Double, y: Double, z: Double): Boolean {
        if (isOpen) return true
        mx += x; my += y; mz += z
        settle(now)
        return false
    }

    /** The hand turned by the rotation vector ([x], [y], [z]) in degrees at [now]: true when it may turn the held thing. */
    fun turn(now: Long, x: Double, y: Double, z: Double): Boolean {
        if (isOpen) return true
        tx += x; ty += y; tz += z
        settle(now)
        return false
    }

    private fun settle(now: Long) {
        if (now - since < holdMs) return
        if (sqrt(mx * mx + my * my + mz * mz) >= moveM || sqrt(tx * tx + ty * ty + tz * tz) >= turnDeg) open()
    }

    private companion object { const val OPEN = Long.MIN_VALUE }
}
