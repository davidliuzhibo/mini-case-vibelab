package com.vrgames.minicase.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Quat
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
import com.vrgames.minicase.ALL_WINDOWS
import com.vrgames.minicase.CABINET_ID
import com.vrgames.minicase.FILE_ID
import com.vrgames.minicase.PEOPLE_ID
import com.vrgames.minicase.platform.AppExit
import com.vrgames.minicase.core.Action
import com.vrgames.minicase.core.Cases
import com.vrgames.minicase.core.InspectLayout
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
import com.vrgames.minicase.render.toVector3
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
    // The hand's orientation at the last drag move, and the content (to bring it into its frame).
    val lastHand = remember { arrayOfNulls<Quat>(1) }
    val contentRef = remember { arrayOfNulls<SpatialViewContent>(1) }
    val head = remember { HMDTrackingProvider() }
    val headPose = { runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull() }
    // Turns the table to where the player looks now; false until the pose is in.
    val facePlayer: () -> Boolean = {
        val t = table
        val pose = headPose()
        val f = pose?.rotation?.rotateVector(Vector3(0f, 0f, -1f))
        // Looking straight down or up gives no heading to go by.
        if (t == null || pose == null || f == null || kotlin.math.hypot(f.x, f.z) < 0.2f ||
            !com.vrgames.minicase.core.EyeHeight.valid(pose.position.x, pose.position.y, pose.position.z)) {
            false
        } else {
            val yaw = Math.toDegrees(kotlin.math.atan2(-f.x, -f.z).toDouble()).toFloat()
            t.faceFrom(pose.position.x, pose.position.z, yaw)
            // v0.9.1: the height is NOT taken from this first pose (on the device it can still be all zeros right
            // after tracking starts: 0.9.0 put the table near the floor). The EyeHeight loop below sets it.
            t.placeInRoom(Game.tableHeight)
            Log.i(TAG, "table faces the player at (${pose.position.x}, ${pose.position.z}), yaw $yaw")
            true
        }
    }
    // Each window a toggle; several may be up at once, each beside the table (contest round 2026-11-15).
    // Opening a window that is already open would start a second copy.
    val toggleWindow: (String) -> Unit = { id ->
        if (Game.isOpen(id)) {
            Game.closeWindow(id)
        } else {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }
    // The board stands in the scene: opening it puts the talk away, and the 卷宗 window too.
    val toggleBoard = {
        Game.boardOpen = !Game.boardOpen
        if (Game.boardOpen) {
            Game.talk(null)
        }
    }
    // Shown in the menu, so a device test can tell which build is on.
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val toggleFile = { toggleWindow(FILE_ID) }
    val putBack = {
        table?.let { t -> if (synchronized(Game.lock) { t.putBack() }) Game.sound?.play(Sfx.PUT) }
        inspecting = null
    }

    com.vrgames.minicase.platform.AppExit.TrackStage()
    DisposableEffect(Unit) {
        Game.init(context)
        AppExit.stageAlive = true
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
            AutoPilot.picker = null
            AutoPilot.rays = null
            AutoPilot.putBack = null
            AppExit.stageAlive = false
            Game.openWindows.forEach { Game.closeWindow(it) }
            for (id in ALL_WINDOWS) Game.closeWindow(id)
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
            // Home pressed mid-narration: the voice stops with the game (device test 2026-11-12). ON_STOP, as a window of
            // the game's own opening also pauses the stage.
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) Game.stopSpeaking()
            // Leaving the scene (Home, or the scene closed) must not leave the 卷宗 hanging in the room
            // (2026-11-12 review: a clue panel stayed open after the main scene was gone).
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) Game.openWindows.forEach { Game.closeWindow(it) }
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

    // The desk round the scene, once.
    LaunchedEffect(table) {
        val t = table ?: return@LaunchedEffect
        val loaded = HashMap<String, Entity>()
        for (name in Diorama.DESK.keys) {
            runCatching { Entity.loadSuspend("asset://models/desk_$name.glb") }
                .onSuccess { loaded[name] = it }
                .onFailure { Log.w(TAG, "desk $name: ${it.message}") }
        }
        synchronized(Game.lock) { t.setDesk(loaded) }
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
    LaunchedEffect(table, Game.placeRequest) {
        val t = table ?: return@LaunchedEffect
        var faced = false
        repeat(30) {
            if (!faced && facePlayer()) faced = true
            if (faced) return@repeat
            delay(100)
        }
        if (!faced) Log.w(TAG, "no head pose: the table stays at the room's front")
        // v0.9.1 (device 2026-11-19: 0.9.0 put the table almost on the floor): the eye height comes from EyeHeight, which
        // ignores the all-zero and out-of-range poses a headset reports while tracking starts, waits for steady readings,
        // falls back to a seated eye after 4 s and corrects once if a real reading comes later.
        val eyes = com.vrgames.minicase.core.EyeHeight()
        var looked = false
        val step = 50L
        val zeroUntil = android.os.SystemClock.uptimeMillis() + (Game.debugZeroHeadS * 1000).toLong()
        while (true) {
            // Debug only: a headset that reports all-zero poses for a while after start (as seen on the device).
            val p = if (android.os.SystemClock.uptimeMillis() < zeroUntil) null else headPose()
            val zero = android.os.SystemClock.uptimeMillis() < zeroUntil
            val got = if (zero) eyes.feed(0f, 0f, 0f, step / 1000.0) else eyes.feed(p?.position?.x, p?.position?.y, p?.position?.z, step / 1000.0)
            if (got) {
                val eye = eyes.value!!
                Game.fromEye(eye)
                t.placeInRoom(Game.tableHeight)
                Log.i(TAG, "table top at ${Game.tableHeight} for eyes at $eye (${if (eyes.guessed) "fallback" else "measured"}, adjust ${Game.tableAdjust})")
                // A real table found ahead only moves the scene nearer or farther, never up or down.
                if (!looked && p != null && !eyes.guessed) {
                    looked = true
                    TableFinder.find(p.position.x, p.position.z, t.facing)?.let { found ->
                        Log.i(TAG, "real surface at ${found.height} m, ${found.distance} m ahead: distance taken, height kept")
                        t.placeInRoom(Game.tableHeight, found.distance)
                    }
                }
            }
            if (!eyes.listening) break
            delay(step)
        }
    }
    LaunchedEffect(Game.hint, table) { table?.showHint(Game.hint) }
    LaunchedEffect(Game.toastVersion) {
        if (Game.toast.isEmpty()) return@LaunchedEffect
        // Time to notice it first, then read it (device test 2026-11-12: gone before it was even seen).
        delay(14000)
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
    LaunchedEffect(Game.showTitle, Game.caseCardDismissed, Game.talkingTo, inspecting, Game.version, Game.coached, Game.boardOpen, table) {
        val t = table ?: return@LaunchedEffect
        val solved = Game.inv.solved
        val busy = Game.showTitle
        // Case closed: put down whatever is held up, so it doesn't hang in front of the verdict.
        if (solved && inspecting != null) putBack()
        // Behind the scene, one sheet at a time: the board, else the talk, else the case card (or the
        // result once solved); the coach line only while none of them is up.
        val board = !busy && Game.boardOpen
        t.showPanel(PANEL_INTRO, busy)
        t.showPanel(PANEL_BOARD, board)
        t.showPanel(PANEL_CASE, !busy && !board && !Game.caseCardDismissed && !solved && Game.talkingTo == null)
        t.showPanel(PANEL_TALK, !busy && !board && Game.talkingTo != null)
        t.showPanel(PANEL_INSPECT, !busy && inspecting != null && !solved)
        t.showPanel(PANEL_RESULT, !busy && !board && solved && Game.talkingTo == null)
        t.showPanel(PANEL_MENU, !busy)
        // 0.9.2: while an item is held the item box sits to its right and could cover the tab from a high, close seat.
        t.showPanel(PANEL_BOARD_TAB, !busy && inspecting == null)
        t.showPanel(PANEL_TOAST, !busy && !solved)
        t.showPanel(PANEL_COACH, !busy && !board && !solved && Game.caseCardDismissed && !Game.coached && Game.talkingTo == null && inspecting == null)
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "board" -> if (!Game.boardOpen) toggleBoard()
            "file" -> if (!Game.isOpen(FILE_ID)) toggleFile()
            "people" -> if (!Game.isOpen(PEOPLE_ID)) toggleWindow(PEOPLE_ID)
            "cabinet" -> if (!Game.isOpen(CABINET_ID)) toggleWindow(CABINET_ID)
        }
        Game.debugOpen = null
    }
    LaunchedEffect(Game.menuOpen, table) { table?.placePanel(PANEL_MENU, if (Game.menuOpen) MENU_OPEN_POS else MENU_POS) }
    // v0.9.2: while something is held the clue box stands higher and further back, clear above the thing and its item
    // box (since 0.6.1 it stood 3 cm behind the item box, and a clue found on pick-up was hidden behind it).
    LaunchedEffect(inspecting, table) {
        table?.placePanel(PANEL_TOAST, (if (inspecting != null) InspectLayout.CLUE_HOLDING_AT else InspectLayout.CLUE_AT).toVector3())
    }

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
                    lastHand[0] = handRotation(contentRef[0], value.inputDevicePose.rawRotation)
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
                // The hand's own turn since the last move (a prop held up close turns with it).
                val now = handRotation(contentRef[0], value.inputDevicePose.rawRotation)
                val before = lastHand[0]
                lastHand[0] = now
                synchronized(Game.lock) {
                    t.drag(d)
                    if (now != null && before != null) t.turnByHand(now.times(before.conjugate()))
                }
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
                val prop = id?.let { i -> Game.inv.case.props.firstOrNull { it.id == i } }
                // v0.9.2: the turning nudge only while a hidden side still hides its clue; otherwise one plain line.
                val line = prop?.let { InspectLayout.itemLine(it, table?.inspectingHidden(Game.inv)) }
                InspectPanel(converter, prop?.name, line, putBack)
            }
            AttachmentPanel(PANEL_TOAST) { ToastPanel(converter, holding = inspecting != null) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter) }
            AttachmentPanel(PANEL_BOARD_TAB) { BoardTab(converter, toggleBoard) }
            AttachmentPanel(PANEL_COACH) { CoachPanel(converter) }
            AttachmentPanel(PANEL_BOARD) { BoardPanel(converter) { Game.boardOpen = false } }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(
                    converter,
                    toggleBoard = toggleBoard,
                    toggleFile = toggleFile,
                    toggleWindow = toggleWindow,
                    faceMe = { facePlayer() },
                    quit = {
                        for (id in ALL_WINDOWS) Game.closeWindow(id)
                        AppExit.finishAll(context)
                    },
                    version = appVersion,
                )
            }
        },
        update = { _, attachments -> table?.let { attachPanels(it, attachments) } },
        initial = { content, attachments ->
            contentRef[0] = content
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
            Game.voice = com.vrgames.minicase.render.Voice(t.root)
            Game.figureAt = { id -> t.figurePos(id) }
            t.onHiddenSeen = { id -> main.post { Game.handled(id, sawHidden = true) } }
            table = t

            AutoPilot.stepper = { autoStep(t) { inspecting = it } }
            AutoPilot.picker = { spec -> autoPickNamed(t, spec) { inspecting = it } }
            AutoPilot.putBack = { putBack(); Log.i("CaseTest", "put back, holding ${t.inspecting}") }
            AutoPilot.rays = { castRays(t) }

            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                headPose()?.let { p -> t.viewer = t.toTable(p.position.x, p.position.y, p.position.z) }
                synchronized(Game.lock) { t.frame(e.deltaTime.toDouble()) }
                if (Game.speaking != null && Game.voice?.finished() == true) main.post { Game.stopSpeaking() }
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
private val PANEL_FRAMES = mapOf(PANEL_INTRO to "panel_intro", PANEL_CASE to "panel_case", PANEL_TALK to "panel_talk", PANEL_RESULT to "panel_result", PANEL_BOARD to "panel_board")

private fun attachPanels(t: Diorama, attachments: SpatialViewAttachments) {
    fun put(id: String, pos: Vector3) {
        if (!t.hasPanel(id)) t.attachPanel(id, attachments.entity(id), pos)
    }
    // Everything to read stands in one place: upright behind the miniature, straight ahead at eye
    // height (2026-11-10 review: text placed around the models had to be read sideways, and some
    // panels cut into them). Nothing stands inside the scene's own space (|x| < 0.32, z -0.2..0.2,
    // up to 0.3 m), and the sheets keep clear of the back wall's top as seen from the seat.
    put(PANEL_INTRO, READ_AT)
    put(PANEL_BOARD, Vector3(0f, 0.49f, -0.44f))
    put(PANEL_CASE, READ_AT)
    put(PANEL_TALK, Vector3(0f, 0.5f, -0.4f))
    put(PANEL_RESULT, READ_AT)
    put(PANEL_COACH, Vector3(0f, 0.36f, -0.4f))
    // v0.9.2: to the right of the thing held up close (it floats at InspectLayout.ITEM_AT, left of the middle); until
    // 0.9.1 it stood right above the thing, and the thing's grown pinch box reached its 放回原处.
    put(PANEL_INSPECT, InspectLayout.ITEM_BOX_AT.toVector3())
    // New clues and answers: a strip over the middle of the table, 18 cm further away and higher than before (device test
    // 2026-11-12: just above the front edge it was too close to notice or read). It only stays a while, so it may stand
    // in the middle of the view. While something is held it moves up and back (InspectLayout.CLUE_HOLDING_AT).
    put(PANEL_TOAST, InspectLayout.CLUE_AT.toVector3())
    put(PANEL_BOARD_TAB, Vector3(0.36f, 0.04f, 0.17f))
    put(PANEL_MENU, MENU_POS)
}

/** Where the case card, the result and the title stand: behind the miniature, at eye height. */
private val READ_AT = Vector3(0f, 0.46f, -0.4f)

/** The menu tab at the table's front left, and where the unfolded menu stands so all of it is above the table. */
private val MENU_POS = Vector3(-0.38f, 0.04f, 0.17f)
private val MENU_OPEN_POS = Vector3(-0.42f, 0.34f, 0.12f)

private const val TAG = "CaseStage"
/** A table top for the player's eyes: this far below them at most, at least, and by default. */
private const val TABLE_BELOW_EYE_MAX = 0.75f
private const val TABLE_BELOW_EYE_MIN = 0.3f
private const val TABLE_BELOW_EYE = 0.42f
/** Logcat tag for every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "CaseInput"
/** A drag with no callback for this long is taken as released. */
private const val STUCK_DRAG_MS = 2000L
/** Pinch strength (0 open .. 1 closed) above which a hand counts as the one pinching. */
private const val PINCH_HELD = 0.5f
/** Below this the fingers have parted; it must hold for a few frames (tracking jitters). */
private const val PINCH_OPEN = 0.3f
private const val PINCH_OPEN_FRAMES = 3

/** The pinching hand's orientation in the content's frame, or null when none comes (gaze without a hand). */
private fun handRotation(content: SpatialViewContent?, raw: com.pico.spatial.ui.foundation.geometry.Rotation3D?): Quat? {
    if (content == null || raw == null) return null
    val q = runCatching { content.convertRotation(raw.toQuaternion(), ViewCoordinateSpace.Global, content.localSpatialCoordinateSpace) }.getOrNull() ?: return null
    val n = q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w
    return q.takeIf { n > 0.5f && !(q.x == 0f && q.y == 0f && q.z == 0f) }
}

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

/**
 * Test hook `pick --es id <prop>[,turn]`: picks up [spec]'s prop on the table as a pinch would (opening what it is in
 * first), so its clue shows if looking is enough; ",turn" also turns it to its hidden side and counts that as found.
 */
private fun autoPickNamed(t: Diorama, spec: String, setInspecting: (String?) -> Unit) {
    val turned = spec.endsWith(",turn")
    val id = spec.removeSuffix(",turn").trim()
    if (Game.showTitle) Game.start()
    Game.caseCardDismissed = true
    val p = Game.inv.case.props.firstOrNull { it.id == id && it.action == Action.EXAMINE }
    if (p == null) {
        Log.w("CaseTest", "pick: no thing to pick up called '$id' in case ${Game.caseId}")
        return
    }
    fun openFor(propId: String) {
        for (n in Game.inv.case.prop(propId).needs) {
            if (n in Game.inv.opened) continue
            openFor(n)
            t.autoOpen(n)
            Game.handled(n)
        }
    }
    openFor(p.id)
    Game.talk(null)
    synchronized(Game.lock) { t.autoPick(p.id, turned) }
    setInspecting(p.id)
    Game.sound?.play(Sfx.PICK)
    if (p.hiddenSide == null) Game.handled(p.id) else if (turned) Game.handled(p.id, sawHidden = true)
    val line = InspectLayout.itemLine(p, t.inspectingHidden(Game.inv))
    Log.i("CaseTest", "picked ${p.id} hidden=${p.hiddenSide} line=「$line」 clue=${Game.toast.map { it.title }}")
}

/**
 * Test hook `rays`: from the eyes (headset pose, table frame) to 9 points on the item box's 放回原处 and to the held
 * thing's middle, the first thing each ray hits (all hits, nearest first). Cast twice: relative to the table root, and in
 * the room frame as a cross-check.
 */
private fun castRays(t: Diorama) {
    val held = t.inspecting
    val sc = t.root.scene
    if (sc == null) {
        Log.w("CaseTest", "rays: the table is not in a scene")
        return
    }
    val eye = t.viewer
    Log.i("CaseTest", "rays from eye (%.3f, %.3f, %.3f) table frame, holding $held".format(eye.x, eye.y, eye.z))
    val group = com.pico.spatial.core.ecs.simulation.CollisionGroup(com.pico.spatial.core.ecs.simulation.CollisionGroup.COLLISION_GROUP_ALL)
    val all = com.pico.spatial.core.ecs.simulation.CollisionCastHitMode.ALL
    val targets = InspectLayout.buttonPoints(InspectLayout.BUTTON).mapIndexed { i, p -> "button${i + 1}" to p } + listOf("item" to (t.heldCentre() ?: InspectLayout.ITEM_AT))
    var bad = 0
    for ((name, p) in targets) {
        val d = p - eye
        val len = d.length()
        val dir = d.normalized()
        val rel = runCatching { sc.rayCast(eye.toVector3(), dir.toVector3(), (len + 0.5).toFloat(), all, group, t.root).results.sortedBy { it.distance } }
            .onFailure { Log.w("CaseTest", "ray $name relative: ${it.message}") }.getOrNull()
        val o = t.toRoom(eye)
        val dr = t.toRoom(eye + dir) - o
        val room = runCatching { sc.rayCast(o.toVector3(), dr.toVector3(), (len + 0.5).toFloat(), all, group, null).results.sortedBy { it.distance } }
            .onFailure { Log.w("CaseTest", "ray $name room: ${it.message}") }.getOrNull()
        val first = rel?.firstOrNull()
        val what = t.describe(first?.entity)
        if (name.startsWith("button") && held != null && what == "prop:$held") bad++
        Log.i(
            "CaseTest",
            "ray $name: first $what at %.3f of %.3f m, ${rel?.size} hits [${rel?.take(4)?.joinToString { t.describe(it.entity) }}]; room frame first ${t.describe(room?.firstOrNull()?.entity)}".format(first?.distance ?: -1f, len),
        )
    }
    Log.i("CaseTest", "rays done: $bad of 9 button rays hit the held thing first")
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
