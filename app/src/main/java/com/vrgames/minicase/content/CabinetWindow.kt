package com.vrgames.minicase.content

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.vrgames.minicase.CABINET_ID
import com.vrgames.minicase.core.Action
import com.vrgames.minicase.core.ClueKind
import com.vrgames.minicase.game.Game
import com.vrgames.minicase.platform.AppExit
import com.vrgames.minicase.render.color
import com.vrgames.minicase.render.model
import com.vrgames.minicase.render.pbr
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 证物柜 (volume window, contest round 2026-11-15): every object that has given a clue stands on the
 * shelves at a size to see, the same pieces as on the table; pinch one to bring it forward turning,
 * with what it told on the right. Fills up as the case goes on.
 */
@Composable
fun CabinetWindow() {
    AppExit.CloseWithStage(CABINET_ID)
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val context = LocalContext.current
    val inv = Game.inv
    val shelf = remember { Shelf() }
    val sub = remember { arrayOfNulls<Cancellable>(1) }
    var loadedCase by remember { mutableStateOf<String?>(null) }
    val evidence = inv.case.props.filter { p -> p.action == Action.EXAMINE && p.gives.any { it in inv.found } }.map { it.id }

    DisposableEffect(Unit) {
        val copy = Any()
        Game.windowShown(CABINET_ID, copy)
        onDispose {
            Game.windowGone(CABINET_ID, copy)
            sub[0]?.cancel()
        }
    }
    LaunchedEffect(inv.case.id) {
        val e = runCatching { Entity.loadSuspend("asset://models/case_${inv.case.id}.glb") }.onFailure { Log.w("CaseCabinet", "case: ${it.message}") }.getOrNull()
        shelf.setSource(e)
        loadedCase = inv.case.id
    }
    LaunchedEffect(evidence, loadedCase, Game.cabinetPick) {
        if (loadedCase != inv.case.id) return@LaunchedEffect
        shelf.show(evidence, Game.cabinetPick)
    }

    Row(Modifier.fillMaxSize()) {
        SpatialView(
            Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                detectSpatialTapGesture(context, TargetEntity.any()) { value ->
                    shelf.propOf(value.targetEntity)?.let { id ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post { Game.cabinetPick = if (Game.cabinetPick == id) null else id }
                    }
                }
            },
        ) { content, _ ->
            content.addEntity(shelf.root)
            val corner = content.convertPosition(Vector3.ZERO, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
            shelf.fit(abs(corner.x), abs(corner.y), abs(corner.z))
            sub[0] = content.subscribe(SceneEvents.Update::class.java) { e -> shelf.frame(e.deltaTime.toDouble()) }
        }
        Column(
            Modifier.width(330.dp).fillMaxHeight().background(WindowBg).padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WindowHeader(CABINET_ID, "证物柜", Brass, 26.sp, register = false)
            Text("已找到 ${evidence.size} 件证物。捏一下柜子里的东西，拿到前面转着看。", fontSize = 15.sp, color = Dim)
            val pick = Game.cabinetPick?.let { id -> inv.case.props.firstOrNull { it.id == id } }
            if (pick == null) {
                Text(if (evidence.isEmpty()) "还没有找到证物。在桌上的现场里拉开抽屉、拿起东西细看，找到的证物会放进这里。" else "选一件证物，这里会显示它告诉了你什么。", fontSize = 17.sp, color = LabelInk)
            } else {
                Text(pick.name, fontSize = 24.sp, color = Brass)
                Text("在哪里找到：${pick.where}", fontSize = 15.sp, color = Dim)
                for (cid in pick.gives.filter { it in inv.found }) {
                    val c = inv.case.clue(cid)
                    Text("【${c.kind.label}】${c.title}", fontSize = 18.sp, color = if (c.kind == ClueKind.EVIDENCE) Jade else LabelInk)
                    Text(c.text, fontSize = 16.sp, color = LabelInk)
                }
            }
        }
    }
}

/** The shelves: three planks, the evidence in rows; the chosen piece forward and turning. */
/** Until a piece's bounds are read: the scene's 1:20 pieces at about the size of a hand-held thing. */
private const val GUESS_SCALE = 3f

private class Shelf {
    val root = Entity()
    private val items = Entity().also { root.addChild(it) }
    private var source: Entity? = null
    private val views = HashMap<String, Entity>()
    private val baseScale = HashMap<String, Float>()
    private var cell = 0.08f
    private var shown: List<String> = emptyList()
    private var picked: String? = null
    private var t = 0.0
    private var halfW = 0.25f
    private var halfH = 0.25f
    private val plank = MeshResource.createBox(Vector3(1f, 1f, 1f))
    private val wood = pbr(color(0.36f, 0.22f, 0.13f), roughness = 0.5f)
    private val brass = pbr(color(0.83f, 0.66f, 0.33f), metallic = 0.9f, roughness = 0.3f)

    fun fit(hw: Float, hh: Float, hd: Float) {
        halfW = if (hw > 0.05f) hw else 0.25f
        halfH = if (hh > 0.05f) hh else 0.25f
        root.getChildren().filter { it != items }.forEach { it.destroy(true) }
        for (i in 0..2) {
            val y = -halfH + 0.04f + i * (halfH * 2 - 0.08f) / 3f
            root.addChild(model(plank, wood).apply {
                components[TransformComponent::class.java]?.setPosition(Vector3(0f, y, -0.02f))
                components[TransformComponent::class.java]?.setScaleVector(Vector3(halfW * 1.9f, 0.012f, min(hd * 1.4f, 0.22f)))
            })
            root.addChild(model(plank, brass).apply {
                components[TransformComponent::class.java]?.setPosition(Vector3(0f, y + 0.007f, min(hd * 0.7f, 0.11f) - 0.02f))
                components[TransformComponent::class.java]?.setScaleVector(Vector3(halfW * 1.9f, 0.003f, 0.004f))
            })
        }
    }

    fun setSource(e: Entity?) {
        source = e
        shown = emptyList()
    }

    fun show(ids: List<String>, pick: String?) {
        picked = pick
        if (ids == shown) return
        shown = ids
        views.values.forEach { it.destroy(true) }
        views.clear()
        val src = source ?: return
        val perRow = 4
        val cellW = halfW * 1.8f / perRow
        val rowH = (halfH * 2 - 0.08f) / 3f
        for ((i, id) in ids.withIndex()) {
            val part = src.findEntity(id)?.clone(Entity.CloneOptions(true, true)) ?: run { Log.w("CaseCabinet", "no part $id"); null } ?: continue
            part.components[TransformComponent::class.java]?.let { tr ->
                tr.setPosition(Vector3.ZERO)
                tr.eulerAngles = EulerAngles(0f, 0f, 0f)
                tr.setScaleVector(Vector3(1f))
            }
            val holder = Entity()
            val inner = Entity().also { holder.addChild(it); it.addChild(part) }
            // Each piece is scaled to fit its cell once it is in the scene (bounds of a detached entity come back empty).
            cell = min(cellW * 0.8f, rowH * 0.75f)
            inner.components[TransformComponent::class.java]?.setScaleVector(Vector3(GUESS_SCALE))
            holder.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(cellW * 0.9f, rowH * 0.8f, 0.1f)).offsetByTranslation(Vector3(0f, rowH * 0.4f, 0f))), PhysicsMaterialResource()))
            holder.components.set(InteractableComponent())
            holder.components.set(HoverEffectComponent())
            val row = 2 - (i / perRow).coerceAtMost(2)
            val col = i % perRow
            val x = -halfW * 0.9f + cellW * (col + 0.5f)
            val y = -halfH + 0.046f + row * rowH
            holder.components[TransformComponent::class.java]?.setPosition(Vector3(x, y, 0f))
            items.addChild(holder)
            views[id] = holder
        }
        Log.i("CaseCabinet", "show ${views.size} of ${ids.size}: $ids")
    }

    fun propOf(e: Entity?): String? {
        var x = e
        while (x != null) {
            views.entries.firstOrNull { it.value == x }?.let { return it.key }
            x = x.getParent()
        }
        return null
    }

    /** Fits a piece to its cell by its bounds, once they can be read; true when done. */
    private fun fitPiece(id: String, inner: Entity): Boolean {
        val part = inner.getChildren().firstOrNull() ?: return false
        val b = runCatching { part.getVisualBounds(part, true, true) }.getOrNull() ?: return false
        val size = b.size
        val big = max(size.x, max(size.y, size.z))
        if (big < 1e-5f || big.isNaN()) return false
        val s = cell / big
        baseScale[id] = s
        part.components[TransformComponent::class.java]?.setPosition(Vector3(-b.center.x, -b.min.y, -b.center.z))
        inner.components[TransformComponent::class.java]?.setScaleVector(Vector3(s))
        Log.i("CaseCabinet", "fit $id size $size -> scale $s")
        return true
    }

    fun frame(dt: Double) {
        t += dt
        for ((id, h) in views) {
            val inner = h.getChildren().firstOrNull() ?: continue
            if (id !in baseScale && !fitPiece(id, inner)) continue
            val tr = inner.components[TransformComponent::class.java] ?: continue
            if (id == picked) {
                // Forward, bigger, turning.
                tr.setPosition(Vector3(0f, 0.02f, 0.09f))
                tr.eulerAngles = EulerAngles(0f, (t * 40 % 360).toFloat(), 0f)
                tr.setScaleVector(Vector3((baseScale[id] ?: 1f) * 1.7f))
            } else {
                tr.setPosition(Vector3.ZERO)
                tr.setScaleVector(Vector3(baseScale[id] ?: 1f))
                tr.eulerAngles = EulerAngles(0f, 0f, 0f)
            }
        }
    }
}
