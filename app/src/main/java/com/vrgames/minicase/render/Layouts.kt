package com.vrgames.minicase.render

import com.vrgames.minicase.core.Vec3

/**
 * How the moving props of each case's model move (tools/make_cases.py builds them to match):
 * a drawer slides, a door or lid swings on a hinge at the node's origin, a roof lifts off.
 * Directions are in the model's frame: +x right, +y up, +z toward the player.
 */
sealed interface Motion {
    /** Slides [dist] meters along [dir]; the hand pulls the same way. */
    data class Slide(val dir: Vec3, val dist: Double) : Motion

    /** Turns [degrees] about [axis] (through the node's origin); the hand pulls along [pull]. */
    data class Hinge(val axis: Vec3, val degrees: Double, val pull: Vec3) : Motion

    /** Rises [height] meters, then is set aside out of the way. */
    data class Lift(val height: Double) : Motion
}

object Layouts {
    private val TOWARD = Vec3(0.0, 0.0, 1.0)
    private val UP = Vec3(0.0, 1.0, 0.0)
    private val X = Vec3(1.0, 0.0, 0.0)
    private val Y = Vec3(0.0, 1.0, 0.0)

    /** Lids hinge at their back edge and open upward; doors hinge at their left edge and swing toward the player. */
    private val lid = Motion.Hinge(X, -105.0, UP)
    private val door = Motion.Hinge(Y, -100.0, TOWARD)

    private val motions: Map<String, Map<String, Motion>> = mapOf(
        "wutong" to mapOf("drawer" to Motion.Slide(TOWARD, 0.035)),
        "inn" to mapOf(
            "roof" to Motion.Lift(0.12),
            "trunk" to lid,
            "counterdrawer" to Motion.Slide(TOWARD, 0.03),
            "stove" to door,
        ),
        "train" to mapOf(
            "roof" to Motion.Lift(0.12),
            "medcase" to lid,
            "propbox" to lid,
        ),
    )

    fun motion(caseId: String, propId: String): Motion? = motions[caseId]?.get(propId)

    /** Hand travel (meters) that takes a hinge or a lift all the way. */
    const val PULL_RANGE = 0.07
}
