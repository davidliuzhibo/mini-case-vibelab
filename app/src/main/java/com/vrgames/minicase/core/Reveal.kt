package com.vrgames.minicase.core

import kotlin.math.acos

/**
 * v0.9 (device 2026-11-18: 「转动发现信息」 hardly ever triggered): the hidden side counts as found as soon as the prop is
 * turned a little toward showing it. Facing is the dot of the side's outward normal with the way to the eyes (-1 away,
 * +1 straight at the player); it is turned into an angle, since near "straight away" the dot hardly moves for a turn.
 * Found when the side has come [TURN_DEG] degrees round toward the player since the prop was picked up, or when it is
 * within [FACING_DEG] of facing the player. Before it needed to be within 44 degrees for 0.35 s.
 */
object Reveal {
    const val TURN_DEG = 25.0
    const val FACING_DEG = 85.0

    private fun deg(dot: Double) = Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))

    fun seen(start: Double, now: Double): Boolean = deg(now) < FACING_DEG || deg(start) - deg(now) >= TURN_DEG
}
