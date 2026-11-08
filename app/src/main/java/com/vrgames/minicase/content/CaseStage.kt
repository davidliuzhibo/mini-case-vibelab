package com.vrgames.minicase.content

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.minicase.BOARD_ID
import com.vrgames.minicase.FILE_ID
import com.vrgames.minicase.core.Action
import com.vrgames.minicase.core.Cases
import com.vrgames.minicase.core.Investigation
import com.vrgames.minicase.core.Vec3
import com.vrgames.minicase.game.AutoPilot
import com.vrgames.minicase.game.Game
import com.vrgames.minicase.render.Diorama
import com.vrgames.minicase.render.Music
import com.vrgames.minicase.render.Sfx
import com.vrgames.minicase.render.Sound
import com.vrgames.minicase.render.TableFinder
import com.vrgames.minicase.render.setPos
import com.vrgames.minicase.render.transform
import kotlinx.coroutines.delay

/**
 * The detective's table (Stage): the case's miniature scene stands on the player's real table
 * (found by plane detection when the headset offers it), in front of them. Pinch to open, lift,
 * pick up and turn things, pinch a figurine to question it. Opens the 案情板 and 卷宗 windows.
 */
@Composable
fun CaseStage() {
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    val converter = LocalPhysicalLengthConverter.current
    var table by remember { mutableStateOf<Diorama?>(null) }
    var inspecting by remember { mutableStateOf<String?>(null) }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    val main = remember { Handler(Looper.getMainLooper()) }
    // Last gesture callback (uptime ms), for the stuck-drag check in the frame loop.
    val lastInput = remember { longArrayOf(0L) }
    // Hand tracking, to see the fingers open even when the system never ends the drag.
    val hands = remember { HandTrackingProvider() }
    // The hand that pinched the current drag (0 left, 1 right, -1 unknown) and open frames seen.
    val pinch = remember { intArrayOf(-1, 0) }
    val head = remember { HMDTrackingProvider() }
    val headPose = { runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull() }
    // Turns the table to where the player looks now; false until the pose is in.
    val facePlayer: () -> Boolean = {
        val t = table
        val pose = headPose()
        val f = pose?.rotation?.rotateVector(Vector3(0f, 0f, -1f))
        // Looking straight down or up gives no heading to go by.
        if (t == null || pose == null || f == null || kotlin.math.hypot(f.x, f.z) < 0.2f) {
            false
        } else {
            val yaw = Math.toDegrees(kotlin.math.atan2(-f.x, -f.z).toDouble()).toFloat()
            t.faceFrom(pose.position.x, pose.position.z, yaw)
            t.placeInRoom(Game.tableHeight)
            Log.i(TAG, "table faces the player at (${pose.position.x}, ${pose.position.z}), yaw $yaw")
            true
        }
    }
    // One window at a time, each a toggle: opening a window that is already open starts a second copy.
    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }
    val toggleBoard = { toggleWindow(BOARD_ID) }
    // Shown in the menu, so a device test can tell which build is on.
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val toggleFile = { toggleWindow(FILE_ID) }
    val putBack = {
        table?.let { t -> if (synchronized(Game.lock) { t.putBack() }) Game.sound?.play(Sfx.PUT) }
        inspecting = null
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        val started = runCatching { hands.start() }
        Log.i(INPUT_TAG, "hand tracking ${started.getOrNull() ?: started.exceptionOrNull()?.message} support=${runCatching { hands.supportState }.getOrNull()}")
        val headStarted = runCatching { head.start() }
        Log.i(TAG, "head tracking ${headStarted.getOrNull() ?: headStarted.exceptionOrNull()?.message}")
        onDispose {
            runCatching { hands.stop() }
            runCatching { head.stop() }
            TableFinder.stop()
            subscription[0]?.cancel()
            AutoPilot.stepper = null
            Game.closeWindow(BOARD_ID)
            Game.closeWindow(FILE_ID)
            Game.closeWindow = {}
        }
    }

    // Background music while the app is in front.
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }

    // The thick case boards behind the panels whose text is printed on paper.
    LaunchedEffect(table) {
        val t = table ?: return@LaunchedEffect
        for ((id, name) in PANEL_FRAMES) {
            runCatching { Entity.loadSuspend("asset://panels/$name.glb") }
                .onSuccess { t.setFrame(id, it) }
                .onFailure { Log.w(TAG, "frame $name: ${it.message}") }
        }
    }

    // Load the case's miniature when the case changes (or restarts).
    LaunchedEffect(Game.sceneVersion, table) {
        val t = table ?: return@LaunchedEffect
        val c = Cases.byId(Game.caseId)
        inspecting = null
        Game.note = "正在布置现场……"
        val loaded = runCatching { Entity.loadSuspend("asset://models/case_${c.id}.glb") }
            .onFailure { Log.w(TAG, "case ${c.id}: ${it.message}") }.getOrNull()
        Game.note = null
        if (loaded == null) {
            Game.note = "现场模型没有载入。"
            return@LaunchedEffect
        }
        synchronized(Game.lock) { t.build(c, loaded, Game.inv) }
        t.showHint(Game.hint)
    }
    LaunchedEffect(Game.tableHeight, Game.debugView, table) {
        val t = table ?: return@LaunchedEffect
        val v = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        // "x,y,z,yaw,scale[,pitch]": the pitch tips the table toward the emulator's fixed camera.
        if (v != null && v.size >= 5) {
            t.root.setPos(Vector3(v[0], v[1], v[2]))
            t.root.transform().eulerAngles = EulerAngles(v.getOrElse(5) { 0f }, v[3], 0f)
            t.root.transform().setScaleVector(Vector3(v[4]))
        } else {
            t.root.transform().setScaleVector(Vector3(1f))
            t.placeInRoom(Game.tableHeight)
        }
    }
    // The room's -z is wherever the headset was last recentered: once the head pose is in, set the
    // table in front of the player; then look for a real table to stand it on.
    LaunchedEffect(table) {
        val t = table ?: return@LaunchedEffect
        var faced = false
        repeat(30) {
            if (!faced && facePlayer()) faced = true
            if (faced) return@repeat
            delay(100)
        }
        if (!faced) Log.w(TAG, "no head pose: the table stays at the room's front")
        val pose = headPose() ?: return@LaunchedEffect
        TableFinder.find(pose.position.x, pose.position.z, t.facing)?.let { found ->
            Log.i(TAG, "real table at ${found.height} m, ${found.distance} m ahead")
            Game.tableFound(found.height)
            t.placeInRoom(found.height, found.distance)
        }
    }
    LaunchedEffect(Game.hint, table) { table?.showHint(Game.hint) }
    LaunchedEffect(Game.toastVersion) {
        if (Game.toast.isEmpty()) return@LaunchedEffect
        delay(5000)
        Game.clearToast()
    }
    LaunchedEffect(Game.note) {
        val n = Game.note ?: return@LaunchedEffect
        if (n.startsWith("正在")) return@LaunchedEffect
        delay(3500)
        if (Game.note == n) Game.note = null
    }
    LaunchedEffect(Game.verdict) {
        when (Game.verdict) {
            Investigation.Verdict.Right -> {
                Game.sound?.play(Sfx.SOLVED)
                table?.celebrate()
            }
            null -> Unit
            else -> Game.sound?.play(Sfx.WRONG)
        }
    }
    // Scene panels: one explanation at a time.
    LaunchedEffect(Game.showTitle, Game.caseCardDismissed, Game.talkingTo, inspecting, Game.version, Game.coached, table) {
        val t = table ?: return@LaunchedEffect
        val solved = Game.inv.solved
        val busy = Game.showTitle
        // Case closed: put down whatever is held up, so it doesn't hang in front of the verdict.
        if (solved && inspecting != null) putBack()
        t.showPanel(PANEL_INTRO, busy)
        t.showPanel(PANEL_CASE, !busy && !Game.caseCardDismissed && !solved)
        t.showPanel(PANEL_TALK, !busy && Game.talkingTo != null)
        t.showPanel(PANEL_INSPECT, !busy && inspecting != null && !solved)
        t.showPanel(PANEL_RESULT, !busy && solved)
        t.showPanel(PANEL_MENU, !busy)
        t.showPanel(PANEL_BOARD_TAB, !busy)
        t.showPanel(PANEL_TOAST, !busy && !solved)
        t.showPanel(PANEL_COACH, !busy && !solved && Game.caseCardDismissed && !Game.coached && Game.talkingTo == null && inspecting == null)
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "board" -> if (Game.window != BOARD_ID) toggleBoard()
            "file" -> if (Game.window != FILE_ID) toggleFile()
        }
        Game.debugOpen = null
    }
    LaunchedEffect(Game.menuOpen, table) { table?.placePanel(PANEL_MENU, if (Game.menuOpen) MENU_OPEN_POS else MENU_POS) }

    SpatialView(
        Modifier.pointerInput(Unit) {
            fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
            detectSpatialDragGesture(
                context = context,
                targetedToEntity = TargetEntity.any(),
                onDragStart = { _, value ->
                    val t = table ?: return@detectSpatialDragGesture
                    lastInput[0] = SystemClock.uptimeMillis()
                    val (l, r) = pinchValues(latestHands(hands))
                    pinch[0] = when {
                        (r ?: 0f) >= (l ?: 0f) -> if ((r ?: 0f) > PINCH_HELD) 1 else -1
                        else -> if ((l ?: 0f) > PINCH_HELD) 0 else -1
                    }
                    pinch[1] = 0
                    // A start while a drag is still open means its end never came: close it first.
                    if (t.dragging) release(t, "restart")
                    if (Game.showTitle) return@detectSpatialDragGesture
                    val g = t.grabOf(value.targetEntity)
                    val began = g?.let { synchronized(Game.lock) { t.begin(it, Game.inv) } }
                    Log.i(INPUT_TAG, "start $g -> $began target=${value.targetEntity?.getName()} pinchL=$l pinchR=$r hand=${pinch[0]}")
                    when (began) {
                        // Talking and holding a prop up both explain on the right: one at a time.
                        is Diorama.Began.Talk -> {
                            Game.sound?.play(Sfx.PAGE)
                            if (inspecting != null) putBack()
                            Game.talk(began.person)
                        }
                        is Diorama.Began.PickedUp -> {
                            Game.sound?.play(Sfx.PICK)
                            Game.talk(null)
                            inspecting = began.prop
                            val p = Game.inv.case.prop(began.prop)
                            // Looking is enough unless something hides on one side.
                            if (p.hiddenSide == null) Game.handled(p.id) else Game.caseCardDismissed = true
                        }
                        is Diorama.Began.Blocked -> {
                            val p = Game.inv.case.prop(began.prop)
                            val first = p.needs.firstOrNull { it !in Game.inv.opened }?.let { Game.inv.case.prop(it).name }
                            Game.note = if (first != null) "${p.name}还够不着，先打开「$first」。" else "${p.name}还够不着。"
                        }
                        else -> Unit
                    }
                },
                onDragEnd = { release(table, "end") },
                onDragCancel = { release(table, "cancel") },
            ) { value ->
                val t = table ?: return@detectSpatialDragGesture
                lastInput[0] = SystemClock.uptimeMillis()
                // Compose y points down; space y points up.
                val d = Vec3(meters(value.dragAmount.x), -meters(value.dragAmount.y), meters(value.dragAmount.z))
                synchronized(Game.lock) { t.drag(d) }
            }
        },
        attachments = {
            AttachmentPanel(PANEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(PANEL_CASE) { CaseCard(converter) }
            AttachmentPanel(PANEL_TALK) { TalkPanel(converter) }
            AttachmentPanel(PANEL_INSPECT) {
                val id = inspecting
                @Suppress("UNUSED_VARIABLE") val v = Game.version
                // The case can change before the load effect clears [inspecting]: look the prop up softly.
                val name = id?.let { i -> Game.inv.case.props.firstOrNull { it.id == i }?.name }
                val hidden = table?.inspectingHidden(Game.inv)?.label
                InspectPanel(converter, name, hidden, putBack)
            }
            AttachmentPanel(PANEL_TOAST) { ToastPanel(converter) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter) }
            AttachmentPanel(PANEL_BOARD_TAB) { BoardTab(converter, toggleBoard) }
            AttachmentPanel(PANEL_COACH) { CoachPanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(
                    converter,
                    toggleBoard = toggleBoard,
                    toggleFile = toggleFile,
                    faceMe = { facePlayer() },
                    quit = { context.findActivity()?.finishAndRemoveTask() },
                    version = appVersion,
                )
            }
        },
        update = { _, attachments -> table?.let { attachPanels(it, attachments) } },
        initial = { content, attachments ->
            // A desk lamp from the upper left, and a cool fill from the right.
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFE2B8).toColor4(), 900f, false))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-55f, 35f, 0f))
            })
            // The cool fill light was dropped: the IBL gives the shadows their colour, for less per-pixel work.
            val t = Diorama()
            t.placeInRoom(Game.tableHeight)
            content.addEntity(t.root)
            attachPanels(t, attachments)
            Game.sound = Sound(t.root).also { it.load() }
            t.onHiddenSeen = { id -> main.post { Game.handled(id, sawHidden = true) } }
            table = t

            AutoPilot.stepper = { autoStep(t) { inspecting = it } }

            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                headPose()?.let { p -> t.viewer = t.toTable(p.position.x, p.position.y, p.position.z) }
                synchronized(Game.lock) { t.frame(e.deltaTime.toDouble()) }
                // A pinched hand never holds perfectly still, so a drag that has been silent this
                // long has lost its end (hand tracking dropped the release): treat it as let go.
                if (t.dragging && lastInput[0] > 0 && SystemClock.uptimeMillis() - lastInput[0] > STUCK_DRAG_MS) {
                    lastInput[0] = 0L
                    main.post { if (t.dragging) release(t, "watchdog") }
                }
                // The pinching hand's fingers have opened: that is the release, whatever the
                // gesture system says (it sometimes keeps the drag alive after the fingers part).
                if (t.dragging && pinch[0] >= 0) {
                    val v = pinchValues(latestHands(hands)).toList()[pinch[0]]
                    pinch[1] = if (v != null && v < PINCH_OPEN) pinch[1] + 1 else 0
                    if (pinch[1] >= PINCH_OPEN_FRAMES) {
                        pinch[0] = -1
                        main.post { if (t.dragging) release(t, "fingers-open") }
                    }
                }
            }
        },
    )
}

/** Scene panels, placed in the table's frame (+z toward the player). */
private val PANEL_FRAMES = mapOf(PANEL_INTRO to "panel_intro", PANEL_CASE to "panel_case", PANEL_TALK to "panel_talk", PANEL_RESULT to "panel_result")

private fun attachPanels(t: Diorama, attachments: SpatialViewAttachments) {
    fun put(id: String, pos: Vector3) {
        if (!t.hasPanel(id)) t.attachPanel(id, attachments.entity(id), pos)
    }
    put(PANEL_INTRO, Vector3(0f, 0.5f, -0.15f))
    // The case card on the left, questioning on the right, so the scene itself stays in view.
    put(PANEL_CASE, Vector3(-0.5f, 0.3f, -0.02f))
    put(PANEL_TALK, Vector3(0.5f, 0.3f, -0.02f))
    // Beside the thing held up close (it floats at Diorama.INSPECT_AT).
    put(PANEL_INSPECT, Vector3(0.26f, 0.24f, 0.17f))
    put(PANEL_TOAST, Vector3(0f, 0.5f, -0.12f))
    put(PANEL_RESULT, Vector3(0f, 0.45f, -0.05f))
    put(PANEL_COACH, Vector3(0f, 0.36f, 0.08f))
    put(PANEL_BOARD_TAB, Vector3(0.36f, 0.04f, 0.17f))
    put(PANEL_MENU, MENU_POS)
}

/** The menu tab at the table's front left, and where the unfolded menu stands so all of it is above the table. */
private val MENU_POS = Vector3(-0.38f, 0.04f, 0.17f)
private val MENU_OPEN_POS = Vector3(-0.42f, 0.34f, 0.12f)

private const val TAG = "CaseStage"
/** Logcat tag for every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "CaseInput"
/** A drag with no callback for this long is taken as released. */
private const val STUCK_DRAG_MS = 2000L
/** Pinch strength (0 open .. 1 closed) above which a hand counts as the one pinching. */
private const val PINCH_HELD = 0.5f
/** Below this the fingers have parted; it must hold for a few frames (tracking jitters). */
private const val PINCH_OPEN = 0.3f
private const val PINCH_OPEN_FRAMES = 3

private fun latestHands(hands: HandTrackingProvider): HandTrackingData? =
    runCatching { if (hands.state == DataProvider.State.STARTED) hands.latestData else null }.getOrNull()

/** Left and right pinch strength, null where the hand is not tracked. */
private fun pinchValues(h: HandTrackingData?): Pair<Float?, Float?> {
    fun v(p: com.pico.spatial.tracking.hand.HandPinch?) = p?.takeIf { it.isReady }?.pinchValue
    return v(h?.leftPinch) to v(h?.rightPinch)
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private fun release(t: Diorama?, why: String) {
    if (t == null) return
    val r = synchronized(Game.lock) { t.end() }
    Log.i(INPUT_TAG, "$why -> $r")
    if (r is Diorama.Released.Opened) {
        val p = Game.inv.case.props.firstOrNull { it.id == r.prop } ?: return
        Game.sound?.play(if (p.action == Action.LIFT) Sfx.LIFT else Sfx.OPEN)
        Game.handled(r.prop)
    }
}

/** AutoPilot: one step of the hints, carried out in the scene. False once the case is closed. */
private fun autoStep(t: Diorama, setInspecting: (String?) -> Unit): Boolean {
    if (Game.showTitle) Game.start()
    Game.caseCardDismissed = true
    val h = synchronized(Game.lock) { Game.inv.nextStep() }
    Log.i("CaseTest", "step $h")
    when (h) {
        is Investigation.Hint.Look -> {
            val p = h.prop
            if (p.action == Action.EXAMINE) {
                Game.talk(null)
                t.autoPick(p.id)
                setInspecting(p.id)
                Game.handled(p.id, sawHidden = true)
            } else {
                setInspecting(null)
                t.putBack()
                t.autoOpen(p.id)
                Game.handled(p.id)
            }
        }
        is Investigation.Hint.Ask -> {
            setInspecting(null)
            t.putBack()
            Game.talk(h.person.id)
            Game.ask(h.question.id)
        }
        is Investigation.Hint.Link -> {
            Game.picked = emptyList()
            Game.pick(h.a.id)
            Game.pick(h.b.id)
        }
        Investigation.Hint.Accuse -> {
            val s = Game.inv.case.solution
            Game.accuse(s.culprit, s.motive.first { it in Game.inv.found }, s.proof.first { it in Game.inv.found })
        }
        Investigation.Hint.Done -> return false
    }
    return true
}
