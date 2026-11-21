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
import com.vrgames.minicase.core.InspectLayout
import com.vrgames.minicase.core.Investigation
import com.vrgames.minicase.core.Side
import com.vrgames.minicase.core.TurnGate
import com.vrgames.minicase.core.Vec3
import kotlin.math.abs
import kotlin.math.acos
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
        /** v0.9.2: its visible bounds (own frame), for the pinch box on the table and up close. */
        val boundsSize: Vector3,
        val boundsCenter: Vector3,
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
                t.position, t.quaternion, t.scaleVector, maxOf(size.x, size.y, size.z), b.size, b.center,
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

    /** The middle of [personId]'s figurine, in the table's frame (where their voice comes from), or null. */
    fun figurePos(personId: String): Vec3? {
        val e = figures[personId] ?: return null
        val b = runCatching { e.getVisualBounds(root, true, false) }.getOrNull() ?: return null
        val c = b.center
        return Vec3(c.x.toDouble(), c.y.toDouble(), c.z.toDouble())
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

    /**
     * v0.9 (device 2026-11-18: the box in the drawer was very hard to pick): an opened drawer's box still holds what is in
     * it, so a pinch aimed at the watch box landed on the drawer. Once a container is fully open, a pinch on it goes to
     * the one thing inside it (the prop that needs it opened).
     */
    private fun intoOpened(g: Grab): Grab {
        if (g !is Grab.Prop) return g
        val v = props[g.id] ?: return g
        if (v.action == Action.EXAMINE || !v.done) return g
        val inner = case?.props?.filter { g.id in it.needs && props.containsKey(it.id) } ?: return g
        return if (inner.size == 1) Grab.Prop(inner[0].id).also { android.util.Log.i("MiniCaseTable", "pinch on opened ${g.id} -> ${it.id}") } else g
    }

    private fun collide(e: Entity, size0: Vector3, center: Vector3) {
        // v0.9: every pinch box 1.2 cm larger each way than the thing, and at least 4 cm (was 2.5): flat things such as
        // the ledgers on the desk were thin slivers seen from the seat.
        val size = Vector3(size0.x + 2 * PAD, size0.y + 2 * PAD, size0.z + 2 * PAD)
        val s = Vector3(maxOf(size.x, MIN_HIT), maxOf(size.y, MIN_HIT), maxOf(size.z, MIN_HIT))
        setBox(e, s, center)
        e.components.set(InteractableComponent())
        e.components.set(HoverEffectComponent())
    }

    private fun setBox(e: Entity, size: Vector3, center: Vector3) {
        e.components.set(CollisionComponent(listOf(ShapeResource.createBox(size).offsetByTranslation(center)), PhysicsMaterialResource()))
    }

    /**
     * v0.9.2 (device 2026-11-19): the table's enlarged pinch box (+1.2 cm, at least 4 cm) stayed on a thing held up close
     * and grew with it (2.6-11.8 times): the medicine pack's reached 27 x 26 x 29 cm, past the item box's 放回原处, so a
     * gaze-pinch on the button took the pack instead. Up close the box is the visible size plus [InspectLayout.HELD_PAD]
     * each side; [putBack] gives the table's box back.
     */
    private fun heldBox(v: PropView) {
        val k = InspectLayout.heldScale(v.size.toDouble())
        fun edge(local: Float, home: Float) = InspectLayout.heldHitLocal(local.toDouble(), maxOf(abs(home.toDouble()), 1e-4) * k).toFloat()
        val s = Vector3(edge(v.boundsSize.x, v.homeScale.x), edge(v.boundsSize.y, v.homeScale.y), edge(v.boundsSize.z, v.homeScale.z))
        setBox(v.e, s, v.boundsCenter)
        android.util.Log.i(
            "MiniCaseTable",
            "held ${v.id}: x%.2f, visible %.3f x %.3f x %.3f m, pinch box %.3f x %.3f x %.3f m".format(
                k, v.boundsSize.x * v.homeScale.x * k, v.boundsSize.y * v.homeScale.y * k, v.boundsSize.z * v.homeScale.z * k,
                s.x * v.homeScale.x * k, s.y * v.homeScale.y * k, s.z * v.homeScale.z * k,
            ),
        )
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

    /** Test hook: the visible centre of the thing held up close, in the table's frame (null when nothing is held). */
    fun heldCentre(): Vec3? {
        val v = props[inspecting ?: return null] ?: return null
        val b = v.e.getVisualBounds(root, true, false)
        return Vec3(b.center.x.toDouble(), b.center.y.toDouble(), b.center.z.toDouble())
    }
    private var inspectT = 0.0
    private var yaw = 0.0
    private var pitch = 0.0
    /** The held prop's turn by the hand's own rotation (table frame), under the drag's yaw and pitch. */
    private var handTurn = Quat(0f, 0f, 0f, 1f)
    private var hiddenSeen = 0.0
    /** v0.9.2: the pick-up pinch itself does not turn the thing (its first ~150 ms and first centimetre are dropped). */
    private val gate = TurnGate()

    val dragging get() = grab != null

    fun grabOf(e: Entity?): Grab? {
        var cur = e
        while (cur != null) {
            hits[cur]?.let { return it }
            cur = cur.getParent()
        }
        return null
    }

    /** Test hook (rays): what a ray hit — "prop:<id>", "figure:<id>", "panel:<id>", else the entity's name. */
    fun describe(e: Entity?): String {
        if (e == null) return "nothing"
        when (val g = grabOf(e)) {
            is Grab.Prop -> return "prop:${g.id}"
            is Grab.Figure -> return "figure:${g.id}"
            null -> Unit
        }
        var cur: Entity? = e
        while (cur != null) {
            panels.entries.firstOrNull { it.value == cur }?.let { return "panel:${it.key}" }
            cur = cur.getParent()
        }
        return "other:${runCatching { e.getName() }.getOrNull().orEmpty()}"
    }

    /** A point in the table's frame, in the room (the frame the table root is placed in). */
    fun toRoom(p: Vec3): Vec3 {
        val r = root.transform().position
        val d = turnY(p, facing.toDouble())
        return Vec3(r.x + d.x, r.y + d.y, r.z + d.z)
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

    fun begin(g0: Grab, inv: Investigation): Began {
        grab = null
        val g = intoOpened(g0)
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
                            gate.close(android.os.SystemClock.uptimeMillis())
                            Began.PickedUp(g.id)
                        } else {
                            // A new pinch on the thing already up close: turning it is what it is for.
                            gate.open()
                            Began.Pull
                        }
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
                if (!gate.move(android.os.SystemClock.uptimeMillis(), d.x, d.y, d.z)) return
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

    /**
     * The pinching hand turned by [rel] (content frame) since the last move: a prop held up close turns
     * with it, as if held in the fingers (PICO's advice, 2026-11-12). Jumps of tracking are ignored.
     */
    fun turnByHand(rel: Quat) {
        if (inspecting == null || grab !is Grab.Prop) return
        val w = abs(rel.w.toDouble()).coerceAtMost(1.0)
        val deg = Math.toDegrees(2 * acos(w))
        if (deg > 30) return
        val sinHalf = kotlin.math.sqrt(maxOf(0.0, 1 - w * w))
        val k = if (sinHalf < 1e-6) 0.0 else deg / sinHalf * kotlin.math.sign(rel.w.toDouble())
        if (!gate.turn(android.os.SystemClock.uptimeMillis(), rel.x * k, rel.y * k, rel.z * k)) return
        val r = root.transform().quaternion
        val local = r.conjugate().times(rel).times(r)
        handTurn = local.times(handTurn).normalize()
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

    /** AutoPilot: holds [id] up close, turned to show its hidden side (if any) when [turned]. */
    fun autoPick(id: String, turned: Boolean = true) {
        val v = props[id] ?: return
        if (v.action != Action.EXAMINE) return
        putBack()
        pickUp(v)
        gate.open()
        if (!turned) return
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
        handTurn = Quat(0f, 0f, 0f, 1f)
        hiddenSeen = 0.0
        revealFrom = Double.NaN
        // Into the table's frame (keeping where it is), then fly to the close-up spot.
        val at = v.e.convertPositionTo(Vector3(0f, 0f, 0f), root)
        val rot = v.e.convertRotationTo(Quat(0f, 0f, 0f, 1f), root)
        v.homeParent?.removeChild(v.e)
        root.addChild(v.e)
        v.e.setPos(at)
        v.e.transform().quaternion = rot
        flyFrom = Vec3(at.x.toDouble(), at.y.toDouble(), at.z.toDouble())
        flyRot = rot
        heldBox(v)
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
        // The table's (enlarged) pinch box again.
        collide(v.e, v.boundsSize, v.boundsCenter)
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

    /** v0.9: how squarely the hidden side faced the player when the prop came up close (the turn is counted from there). */
    private var revealFrom = Double.NaN

    private fun inspectFrame(v: PropView, dt: Double) {
        inspectT = minOf(1.0, inspectT + dt / FLY_S)
        val e = inspectT * inspectT * (3 - 2 * inspectT)
        val to = INSPECT_AT
        val pos = flyFrom + (to - flyFrom) * e
        val scale = 1.0 + (InspectLayout.heldScale(v.size.toDouble()) - 1.0) * e
        val t = v.e.transform()
        t.position = pos.toVector3()
        t.scaleVector = Vector3((v.homeScale.x * scale).toFloat(), (v.homeScale.y * scale).toFloat(), (v.homeScale.z * scale).toFloat())
        // Face the player, then the hand's turn on top.
        val turn = handTurn.times(axisAngle(Vec3(0.0, 1.0, 0.0), yaw).times(axisAngle(Vec3(1.0, 0.0, 0.0), pitch)))
        t.quaternion = Quat.slerp(flyRot, turn, e.toFloat())
        val side = v.hidden ?: return
        if (inspectT < 1.0) return
        val s = turn.rotateVector(Vector3(side.x.toFloat(), side.y.toFloat(), side.z.toFloat()))
        val toEye = (viewer - to).normalized()
        val facing = s.x * toEye.x + s.y * toEye.y + s.z * toEye.z
        if (revealFrom.isNaN()) revealFrom = facing.toDouble()
        hiddenSeen = if (com.vrgames.minicase.core.Reveal.seen(revealFrom, facing.toDouble())) hiddenSeen + dt else 0.0
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

    /** Thick case-board frames, by panel id, placed behind their panels when these attach. */
    private val frames = HashMap<String, Entity>()

    /** Gives a panel its frame: now if the panel is already attached, otherwise when it attaches. */
    fun setFrame(id: String, frame: Entity) {
        frames[id] = frame
        panels[id]?.let { e -> frame.setPos(Vector3(0f, 0f, -0.006f)); e.addChild(frame) }
    }

    fun attachPanel(id: String, e: Entity?, pos: Vector3) {
        if (e == null || id in panels) return
        root.addChild(e)
        frames[id]?.let { f -> f.setPos(Vector3(0f, 0f, -0.006f)); e.addChild(f) }
        e.setPos(pos)
        e.enabled = false
        panels[id] = e
    }

    /**
     * The detective's desk round the scene (2026-11-10 review: the scene needed more around it): a
     * leather mat under the miniature, and real-size things at its sides, clear of the panels and of
     * the line of sight to the case board. [props]: name to the loaded model (any may be missing).
     */
    fun setDesk(props: Map<String, Entity>) {
        if (desk != null) return
        val d = Entity().also { root.addChild(it) }
        // Its top just under the scene's floor, so the two never fight.
        d.addChild(model(MeshResource.createBox(Vector3(0.82f, 0.003f, 0.52f), 0.006f), pbr(color(0.10f, 0.20f, 0.14f), 0f, 0.75f).shared()).apply {
            setPos(Vector3(0f, -0.0028f, -0.01f))
        })
        d.addChild(model(MeshResource.createBox(Vector3(0.84f, 0.002f, 0.54f), 0.008f), pbr(color(0.45f, 0.30f, 0.12f), 0.6f, 0.4f).shared()).apply {
            setPos(Vector3(0f, -0.0042f, -0.01f))
        })
        for ((name, at) in DESK) {
            val e = props[name] ?: continue
            // Laid flat ones turn about x first (their face up), inside a holder that turns about y.
            val holder = Entity().apply { setPos(Vector3(at.x, 0f, at.z)); transform().eulerAngles = EulerAngles(0f, at.yaw, 0f) }
            holder.addChild(e)
            if (at.flat) {
                e.setPos(Vector3(0f, at.thick / 2, at.long / 2))
                e.transform().eulerAngles = EulerAngles(-90f, 0f, 0f)
            }
            d.addChild(holder)
        }
        desk = d
    }

    private var desk: Entity? = null

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
         * The desk props (models/desk_<name>.glb): where on the table, the turn about y (degrees), and
         * for things made standing on edge, laid flat: their thickness and their length (meters).
         */
        class DeskProp(val x: Float, val z: Float, val yaw: Float, val flat: Boolean = false, val thick: Float = 0f, val long: Float = 0f)

        val DESK = mapOf(
            "lamp" to DeskProp(-0.6f, -0.42f, 35f),
            "files" to DeskProp(-0.5f, -0.1f, 12f),
            "magnifier" to DeskProp(0.52f, -0.02f, -25f),
            "inkwell" to DeskProp(0.5f, -0.26f, -10f),
        )
        /**
         * Added to the head's heading: on the device the scene came out turned 10-15 degrees
         * counterclockwise of straight ahead (negative turns it clockwise, seen from above).
         */
        const val YAW_TRIM = -12f
        /**
         * Where a picked-up thing floats (table frame): v0.9.2 left of the middle and lower, (-0.10, 0.20, 0.21), with its
         * item box to its right and the clue box above (was (0, 0.24, 0.17), right under both boxes). See [InspectLayout].
         */
        val INSPECT_AT = InspectLayout.ITEM_AT
        const val FLY_S = 0.35
        /** Degrees of turn per meter the hand moves. */
        const val TURN_PER_METER = 1300.0
        /** How squarely the hidden side must face the player, and for how long. */
        const val SEEN_DOT = 0.72
        const val SEEN_S = 0.12
        /** Let go past this much and a drawer or lid finishes opening. */
        const val OPEN_AT = 0.45
        const val MIN_HIT = 0.04f
        const val PAD = 0.012f

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
