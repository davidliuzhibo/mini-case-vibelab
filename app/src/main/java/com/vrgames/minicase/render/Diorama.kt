package com.vrgames.minicase.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.vrgames.minicase.core.Action
import com.vrgames.minicase.core.Case
import com.vrgames.minicase.core.Investigation
import com.vrgames.minicase.core.Side
import com.vrgames.minicase.core.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The miniature crime scene on the table. The case's model (tools/make_cases.py) has a node per
 * prop, named by its id, with its origin where it turns (a hinge) or at its middle (things to pick
 * up), and a node "fig_<person>" per figurine. Drawers, doors and lids open as the hand pulls;
 * a roof lifts off; a small thing flies up between the scene and the player, larger, and turns
 * with the hand until the side that matters faces the player. Pinching a figurine starts the
 * questioning (the stage shows the panel).
 *
 * Frame: the root sits on the table top, +z toward the player, turned to face them.
 */
class Diorama {
    val root = Entity()
    private val stage = Entity().also { root.addChild(it) }
    private var model: Entity? = null
    var case: Case? = null; private set

    // ---- Placement (as the bench in 鲁班机关匣) -------------------------------------------------

    private var standX = 0f
    private var standZ = 0f
    var facing = 0f
        private set
    private var height = 0.8f
    private var distance = TABLE_Z

    fun faceFrom(x: Float, z: Float, yawDegrees: Float) {
        standX = x
        standZ = z
        facing = yawDegrees + YAW_TRIM
    }

    fun placeInRoom(height: Float, distance: Float = this.distance) {
        this.height = height
        this.distance = distance
        val a = Math.toRadians(facing.toDouble())
        root.setPos(Vector3(standX - distance * sin(a).toFloat(), height, standZ - distance * cos(a).toFloat()))
        root.transform().eulerAngles = EulerAngles(0f, facing, 0f)
    }

    /** A point in the room (world) in the table's frame. */
    fun toTable(x: Float, y: Float, z: Float): Vec3 {
        val p = root.transform().position
        return turnY(Vec3((x - p.x).toDouble(), (y - p.y).toDouble(), (z - p.z).toDouble()), -facing.toDouble())
    }

    /** Where the player's eyes are, in the table's frame (the stage updates it from the headset). */
    var viewer = Vec3(0.0, 0.45, 0.7)

    // ---- Building a case ------------------------------------------------------------------------

    private class PropView(
        val id: String,
        val e: Entity,
        val action: Action,
        val motion: Motion?,
        val hidden: Side?,
        val homeParent: Entity?,
        val homePos: Vector3,
        val homeRot: Quat,
        val homeScale: Vector3,
        /** Size of the thing (meters, its own frame), for the close-up scale. */
        val size: Float,
    ) {
        var progress = 0.0
        var target = 0.0
        var done = false
    }

    private val props = HashMap<String, PropView>()
    private val figures = HashMap<String, Entity>()
    /** Collider owner → what pinching it means. */
    private val hits = HashMap<Entity, Grab>()

    sealed interface Grab {
        data class Prop(val id: String) : Grab
        data class Figure(val id: String) : Grab
    }

    /** Puts [loaded] (the case's model) on the table and makes its props live; [inv] says what is already open. */
    fun build(c: Case, loaded: Entity, inv: Investigation) {
        clear()
        case = c
        model = loaded
        stage.addChild(loaded)
        for (p in c.props) {
            val e = loaded.findEntity(p.id) ?: continue
            val t = e.transform()
            val b = e.getVisualBounds(e, true, false)
            val size = b.size
            val v = PropView(
                p.id, e, p.action, Layouts.motion(c.id, p.id), p.hiddenSide, e.getParent(),
                t.position, t.quaternion, t.scaleVector, maxOf(size.x, size.y, size.z),
            )
            props[p.id] = v
            collide(e, b.size, b.center)
            hits[e] = Grab.Prop(p.id)
            if (p.id in inv.opened && p.action != Action.EXAMINE) {
                v.progress = 1.0
                v.target = 1.0
                v.done = true
                pose(v)
            }
        }
        for (person in c.people) {
            val e = loaded.findEntity("fig_${person.id}") ?: continue
            val b = e.getVisualBounds(e, true, false)
            collide(e, b.size, b.center)
            hits[e] = Grab.Figure(person.id)
            figures[person.id] = e
        }
        showHint(null)
    }

    fun clear() {
        putBack()
        grab = null
        props.clear()
        figures.clear()
        hits.clear()
        model?.let { stage.removeChild(it); it.destroy(true) }
        model = null
        case = null
        sparks.clear()
    }

    private fun collide(e: Entity, size: Vector3, center: Vector3) {
        val s = Vector3(maxOf(size.x, MIN_HIT), maxOf(size.y, MIN_HIT), maxOf(size.z, MIN_HIT))
        e.components.set(CollisionComponent(listOf(ShapeResource.createBox(s).offsetByTranslation(center)), PhysicsMaterialResource()))
        e.components.set(InteractableComponent())
        e.components.set(HoverEffectComponent())
    }

    /** Where a prop or figure is, in the table's frame (for the hint arrow). */
    private fun spot(e: Entity): Vec3 {
        val p = e.convertPositionTo(Vector3(0f, 0f, 0f), root)
        val b = e.getVisualBounds(e, true, false)
        return Vec3(p.x.toDouble(), (p.y + b.center.y + b.halfExtent.y).toDouble(), p.z.toDouble())
    }

    // ---- Gestures -------------------------------------------------------------------------------

    private var grab: Grab? = null
    /** The prop held up close, if any (it stays up after the fingers open, until put back). */
    var inspecting: String? = null; private set
    private var inspectT = 0.0
    private var yaw = 0.0
    private var pitch = 0.0
    private var hiddenSeen = 0.0

    val dragging get() = grab != null

    fun grabOf(e: Entity?): Grab? {
        var cur = e
        while (cur != null) {
            hits[cur]?.let { return it }
            cur = cur.getParent()
        }
        return null
    }

    /** What pinching something did, for the stage to act on. */
    sealed interface Began {
        data class Talk(val person: String) : Began
        data class PickedUp(val prop: String) : Began
        data object Pull : Began
        /** It is inside something still closed (or under the roof). */
        data class Blocked(val prop: String) : Began
        data object Nothing : Began
    }

    fun begin(g: Grab, inv: Investigation): Began {
        grab = null
        // Between a case change and the new build, the old miniature is still on the table.
        if (inv.case.id != case?.id) return Began.Nothing
        return when (g) {
            is Grab.Figure -> Began.Talk(g.id)
            is Grab.Prop -> {
                val v = props[g.id] ?: return Began.Nothing
                if (!inv.reachable(g.id)) return Began.Blocked(g.id)
                when (v.action) {
                    Action.EXAMINE -> {
                        grab = g
                        if (inspecting != g.id) {
                            putBack()
                            pickUp(v)
                            Began.PickedUp(g.id)
                        } else Began.Pull
                    }
                    else -> if (v.done) Began.Nothing else {
                        grab = g
                        Began.Pull
                    }
                }
            }
        }
    }

    /** [room]: the hand's movement in the room (meters). */
    fun drag(room: Vec3) {
        val d = turnY(room, -facing.toDouble())
        val g = grab as? Grab.Prop ?: return
        val v = props[g.id] ?: return
        when (v.action) {
            Action.EXAMINE -> if (inspecting == v.id) {
                // Sideways turns it about the vertical, up and down tips it toward the player.
                yaw += d.x * TURN_PER_METER
                pitch = (pitch - d.y * TURN_PER_METER).coerceIn(-170.0, 170.0)
            }
            else -> {
                val m = v.motion ?: return
                val step = when (m) {
                    is Motion.Slide -> d.dot(m.dir) / m.dist
                    is Motion.Hinge -> d.dot(m.pull) / Layouts.PULL_RANGE
                    is Motion.Lift -> d.y / Layouts.PULL_RANGE
                }
                v.progress = (v.progress + step).coerceIn(0.0, 1.0)
                v.target = v.progress
                pose(v)
            }
        }
    }

    /** What letting go did. */
    sealed interface Released {
        data class Opened(val prop: String) : Released
        data object Nothing : Released
    }

    fun end(): Released {
        val g = grab as? Grab.Prop
        grab = null
        val v = g?.let { props[it.id] } ?: return Released.Nothing
        if (v.action == Action.EXAMINE || v.done) return Released.Nothing
        return if (v.progress >= OPEN_AT) {
            v.target = 1.0
            v.done = true
            Released.Opened(v.id)
        } else {
            v.target = 0.0
            Released.Nothing
        }
    }

    /** AutoPilot: opens or lifts [id] as if pulled; true if it was closed. */
    fun autoOpen(id: String): Boolean {
        val v = props[id] ?: return false
        if (v.action == Action.EXAMINE || v.done) return false
        v.target = 1.0
        v.done = true
        return true
    }

    /** AutoPilot: holds [id] up close, turned to show its hidden side (if any). */
    fun autoPick(id: String) {
        val v = props[id] ?: return
        if (v.action != Action.EXAMINE) return
        putBack()
        pickUp(v)
        when (v.hidden) {
            Side.BOTTOM -> pitch = -90.0
            Side.TOP -> pitch = 60.0
            Side.BACK -> yaw = 180.0
            Side.LEFT -> yaw = 90.0
            Side.RIGHT -> yaw = -90.0
            null -> Unit
        }
    }

    private fun pose(v: PropView) {
        val m = v.motion ?: return
        val t = v.e.transform()
        val p = v.progress
        when (m) {
            is Motion.Slide -> t.position = add(v.homePos, m.dir * (m.dist * p))
            is Motion.Hinge -> t.quaternion = axisAngle(m.axis, m.degrees * p).times(v.homeRot)
            is Motion.Lift -> {
                // Up, then back over the far edge and out of the way.
                val up = m.height * minOf(1.0, p * 1.6)
                val back = 0.25 * maxOf(0.0, p - 0.6) / 0.4
                t.position = add(v.homePos, Vec3(0.0, up, -back))
                v.e.enabled = p < 0.999
            }
        }
    }

    private fun pickUp(v: PropView) {
        inspecting = v.id
        inspectT = 0.0
        yaw = 0.0
        pitch = 0.0
        hiddenSeen = 0.0
        // Into the table's frame (keeping where it is), then fly to the close-up spot.
        val at = v.e.convertPositionTo(Vector3(0f, 0f, 0f), root)
        val rot = v.e.convertRotationTo(Quat(0f, 0f, 0f, 1f), root)
        v.homeParent?.removeChild(v.e)
        root.addChild(v.e)
        v.e.setPos(at)
        v.e.transform().quaternion = rot
        flyFrom = Vec3(at.x.toDouble(), at.y.toDouble(), at.z.toDouble())
        flyRot = rot
    }

    private var flyFrom = Vec3.ZERO
    private var flyRot = Quat(0f, 0f, 0f, 1f)

    /** Sends the held-up prop back where it was. */
    fun putBack(): Boolean {
        val id = inspecting ?: return false
        inspecting = null
        val v = props[id] ?: return false
        root.removeChild(v.e)
        v.homeParent?.addChild(v.e)
        val t = v.e.transform()
        t.position = v.homePos
        t.quaternion = v.homeRot
        t.scaleVector = v.homeScale
        if (grab == Grab.Prop(id)) grab = null
        return true
    }

    // ---- Frame ----------------------------------------------------------------------------------

    /** Called when turning a held-up prop has shown its hidden side long enough. */
    var onHiddenSeen: ((String) -> Unit)? = null

    fun frame(dt: Double) {
        for (v in props.values) {
            if (v.action == Action.EXAMINE || grab == Grab.Prop(v.id) || abs(v.progress - v.target) < 1e-4) continue
            val k = minOf(1.0, dt * 6)
            v.progress += (v.target - v.progress) * k
            if (abs(v.progress - v.target) < 0.002) v.progress = v.target
            pose(v)
        }
        inspecting?.let { id -> props[id]?.let { inspectFrame(it, dt) } }
        updateHint(dt)
        sparks.update(dt)
    }

    private fun inspectFrame(v: PropView, dt: Double) {
        inspectT = minOf(1.0, inspectT + dt / FLY_S)
        val e = inspectT * inspectT * (3 - 2 * inspectT)
        val to = INSPECT_AT
        val pos = flyFrom + (to - flyFrom) * e
        val scale = 1.0 + (INSPECT_SIZE / maxOf(v.size.toDouble(), 0.01) - 1.0) * e
        val t = v.e.transform()
        t.position = pos.toVector3()
        t.scaleVector = Vector3((v.homeScale.x * scale).toFloat(), (v.homeScale.y * scale).toFloat(), (v.homeScale.z * scale).toFloat())
        // Face the player, then the hand's turn on top.
        val turn = axisAngle(Vec3(0.0, 1.0, 0.0), yaw).times(axisAngle(Vec3(1.0, 0.0, 0.0), pitch))
        t.quaternion = Quat.slerp(flyRot, turn, e.toFloat())
        val side = v.hidden ?: return
        if (inspectT < 1.0) return
        val s = turn.rotateVector(Vector3(side.x.toFloat(), side.y.toFloat(), side.z.toFloat()))
        val toEye = (viewer - to).normalized()
        val facing = s.x * toEye.x + s.y * toEye.y + s.z * toEye.z
        hiddenSeen = if (facing > SEEN_DOT) hiddenSeen + dt else 0.0
        if (hiddenSeen > SEEN_S) {
            hiddenSeen = -1e9
            onHiddenSeen?.invoke(v.id)
        }
    }

    /** Whether the held-up prop still hides something (for the panel under it). */
    fun inspectingHidden(inv: Investigation): Side? {
        val id = inspecting ?: return null
        val p = inv.case.props.firstOrNull { it.id == id } ?: return null
        return p.hiddenSide?.takeIf { p.gives.any { it !in inv.found } }
    }

    // ---- Hints and celebration ------------------------------------------------------------------

    private val sparkMaterial = glow(color(1f, 0.82f, 0.45f)).shared()
    private val marker = Entity().apply {
        addChild(model(MeshResource.createSphere(1f), sparkMaterial).apply { setScale(0.008f) })
        addChild(model(MeshResource.createCone(0.03f, 0.012f), sparkMaterial).apply {
            setPos(Vector3(0f, 0.025f, 0f))
            transform().eulerAngles = EulerAngles(180f, 0f, 0f)
        })
        enabled = false
    }.also { root.addChild(it) }
    private var markAt: Vec3? = null
    private var markAge = 0.0
    private val sparks = Sparks("sparks", 140, MeshResource.createSphere(1f).shared(), sparkMaterial).also { root.addChild(it.entity) }

    /** Points the gold arrow at what the hint is about (a prop or a person), or hides it. */
    fun showHint(h: Investigation.Hint?) {
        markAt = when (h) {
            is Investigation.Hint.Look -> props[h.prop.id]?.let { spot(it.e) }
            is Investigation.Hint.Ask -> figures[h.person.id]?.let { spot(it) }
            else -> null
        }
        markAge = 0.0
        marker.enabled = markAt != null
    }

    private fun updateHint(dt: Double) {
        val at = markAt ?: return
        markAge += dt
        marker.setPos(at + Vec3(0.0, 0.03 + 0.01 * sin(markAge * 4), 0.0))
    }

    fun celebrate() {
        sparks.emit(Vec3(0.0, 0.18, 0.0), 120, 0.4, 1.8, 0.004f, Vec3(0.0, 0.1, 0.0))
    }

    // ---- Panels ---------------------------------------------------------------------------------

    private val panels = HashMap<String, Entity>()

    fun attachPanel(id: String, e: Entity?, pos: Vector3) {
        if (e == null || id in panels) return
        root.addChild(e)
        e.setPos(pos)
        e.enabled = false
        panels[id] = e
    }

    fun hasPanel(id: String) = id in panels

    fun showPanel(id: String, on: Boolean) {
        panels[id]?.let { if (it.enabled != on) it.enabled = on }
    }

    fun placePanel(id: String, pos: Vector3) {
        panels[id]?.setPos(pos)
    }

    companion object {
        /** Table middle, this far in front of the player (0.62 m felt in the player's lap on the device). */
        const val TABLE_Z = 0.75f
        /**
         * Added to the head's heading: on the device the scene came out turned 10-15 degrees
         * counterclockwise of straight ahead (negative turns it clockwise, seen from above).
         */
        const val YAW_TRIM = -12f
        /** Where a picked-up thing floats (table frame): between the scene and the player, raised. */
        val INSPECT_AT = Vec3(0.0, 0.24, 0.17)
        /** Its size up close (meters). */
        const val INSPECT_SIZE = 0.13
        const val FLY_S = 0.35
        /** Degrees of turn per meter the hand moves. */
        const val TURN_PER_METER = 900.0
        /** How squarely the hidden side must face the player, and for how long. */
        const val SEEN_DOT = 0.72
        const val SEEN_S = 0.35
        /** Let go past this much and a drawer or lid finishes opening. */
        const val OPEN_AT = 0.45
        const val MIN_HIT = 0.025f

        fun turnY(d: Vec3, degrees: Double): Vec3 {
            val a = Math.toRadians(degrees)
            return Vec3(d.x * cos(a) + d.z * sin(a), d.y, -d.x * sin(a) + d.z * cos(a))
        }

        fun axisAngle(axis: Vec3, degrees: Double): Quat {
            val a = axis.normalized()
            val h = Math.toRadians(degrees) / 2
            val s = sin(h)
            return Quat((a.x * s).toFloat(), (a.y * s).toFloat(), (a.z * s).toFloat(), cos(h).toFloat())
        }

        private fun add(p: Vector3, d: Vec3) = Vector3((p.x + d.x).toFloat(), (p.y + d.y).toFloat(), (p.z + d.z).toFloat())
    }
}
