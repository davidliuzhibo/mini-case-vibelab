package com.vrgames.minicase.core

/** v0.9: where the table top goes for the player's eyes (metres, in whatever frame the head pose is in). */
object Placement {
    /** The table top this far below the eyes: a seated player looking down onto the scene. */
    const val BELOW_EYE = 0.42f

    fun tableFor(eye: Float, adjust: Float = 0f): Float = eye - BELOW_EYE + adjust

    /** The middle value of [ys] (head samples over a moment), so one jumpy sample does not set the table. */
    fun steadyEye(ys: List<Float>): Float? = ys.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
}
