package com.vrgames.minicase.content

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.minicase.core.Case
import com.vrgames.minicase.core.Clue
import com.vrgames.minicase.core.ClueKind
import com.vrgames.minicase.core.Investigation
import com.vrgames.minicase.game.Game

/** Inside of the cork board's frame (panels/panel_board.glb), meters. */
const val BOARD_W = 0.9f
const val BOARD_H = 0.5f

private val Ink = Color(0xFF2A2118)
private val InkDim = Color(0xFF65543F)
private val InkTitle = Color(0xFF7B2415)
private val InkGreen = Color(0xFF1F5438)
private val Paper = Color(0xFFF3E7CC)
private val PaperPicked = Color(0xFFFFE9A8)
private val Thread = Color(0xFFB0201A)
private val Gold = Color(0xFFC9962E)

private fun kindColor(k: ClueKind) = when (k) {
    ClueKind.EVIDENCE -> Color(0xFF8A5A1C)
    ClueKind.TESTIMONY -> Color(0xFF2E6B4A)
    ClueKind.DEDUCTION -> Thread
}

@Composable
private fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
private fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

/** The clue cards' little pictures (assets/thumbs/<prop or fig_person>.png, tools/render_thumbs.py). */
object Thumbs {
    private val cache = HashMap<String, ImageBitmap?>()

    fun get(context: Context, name: String): ImageBitmap? = cache.getOrPut(name) {
        runCatching { context.assets.open("thumbs/$name.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
    }

    /** The picture of clue [c]: the prop that gives it, or the person who said it. Null for a deduction. */
    fun nameOf(case: Case, c: Clue): String? = when (c.kind) {
        ClueKind.EVIDENCE -> case.props.firstOrNull { c.id in it.gives }?.id
        ClueKind.TESTIMONY -> case.people.firstOrNull { p -> p.questions.any { c.id in it.gives } }?.let { "fig_${it.id}" }
        ClueKind.DEDUCTION -> null
    }
}

/**
 * 案情板, standing in the scene behind the miniature (2026-11-10 review: the case board was a flat
 * window and the text around the models had to be read sideways). A cork board with the clues
 * pinned up as cards, each with a picture of what it came from; a red thread joins clues already
 * linked. To link two, pull a thread from one card to the other (or pinch the two cards in turn).
 * Tabs: the clues, what each person said, and the accusation.
 */
@Composable
fun BoardPanel(c: PhysicalLengthConverter, close: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val inv = Game.inv
    var tab by remember { mutableStateOf(0) }
    val picks = remember(inv.case.id) { AccusePicks() }
    Column(
        Modifier.width(c.m(BOARD_W)).height(c.m(BOARD_H)).padding(horizontal = c.m(0.012f), vertical = c.m(0.008f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.007f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            Label(c, "第 ${inv.case.number} 案 · ${inv.case.title}", InkTitle, 0.019f)
            listOf("线索 ${inv.found.size}/${inv.case.clues.size}", "问话记录", "指认真凶").forEachIndexed { i, s ->
                Tag(c, s, tab == i) { tab = i }
            }
            Spacer(Modifier.weight(1f))
            Tag(c, "收起", false, Color(0xFF5A4630)) { close() }
        }
        if (inv.solved) Solved(c, inv)
        else Game.verdict?.let { Verdict(c, it) }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                0 -> ClueWall(c, inv)
                1 -> Talks(c, inv)
                else -> Accuse(c, inv, picks)
            }
        }
    }
}

/** A word on a slip of paper pinned to the cork. */
@Composable
private fun Label(c: PhysicalLengthConverter, text: String, color: Color, size: Float) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(c.m(0.004f))).background(Paper).padding(horizontal = c.m(0.01f), vertical = c.m(0.004f)),
        fontSize = c.sz(size), color = color,
    )
}

/** A tab: a paper tag, gold when it is the one shown. */
@Composable
private fun Tag(c: PhysicalLengthConverter, text: String, on: Boolean, color: Color = InkTitle, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.006f))
    Text(
        text,
        Modifier.clip(shape).background(if (on) Gold else Paper).border(c.m(0.0015f), color, shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.012f), vertical = c.m(0.006f)),
        fontSize = c.sz(0.016f), color = if (on) Color(0xFF1B1408) else color,
    )
}

/**
 * The found clues as cards, five to a row; threads between the pairs already linked, and the one
 * being pulled. The text of the last card pinched shows in the strip below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClueWall(c: PhysicalLengthConverter, inv: Investigation) {
    // Read the version here too: the investigation object never changes identity, so without it this part was
    // skipped on recomposition and new clues showed only after another pinch (device test 2026-11-12).
    @Suppress("UNUSED_VARIABLE") val version = Game.version
    val context = LocalContext.current
    val cards = ClueKind.entries.flatMap { k -> inv.found.map { inv.case.clue(it) }.filter { it.kind == k } }
    val spots = remember(inv.case.id) { mutableStateMapOf<String, LayoutCoordinates>() }
    var wall by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pullFrom by remember { mutableStateOf<String?>(null) }
    var pullTo by remember { mutableStateOf(Offset.Zero) }
    var shown by remember(inv.case.id) { mutableStateOf<String?>(null) }

    fun rectOf(id: String): Rect? {
        val w = wall ?: return null
        val s = spots[id] ?: return null
        return if (w.isAttached && s.isAttached) w.localBoundingBoxOf(s) else null
    }
    fun cardAt(p: Offset) = cards.firstOrNull { rectOf(it.id)?.contains(p) == true }?.id

    Column(verticalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
        if (cards.isEmpty()) {
            Label(c, "还没有线索。去现场看看：拉开抽屉、捏起物件、问问人偶。", Ink, 0.016f)
            return
        }
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .onGloballyPositioned { wall = it }
                // Pull a red thread from one card to another to link them.
                .pointerInput(inv.case.id, cards.size) {
                    detectDragGestures(
                        onDragStart = { p -> pullFrom = cardAt(p); pullTo = p },
                        onDragEnd = {
                            val a = pullFrom
                            val b = cardAt(pullTo)
                            pullFrom = null
                            if (a != null && b != null && a != b) {
                                Game.picked = emptyList()
                                Game.pick(a)
                                Game.pick(b)
                                shown = b
                            }
                        },
                        onDragCancel = { pullFrom = null },
                    ) { change, amount ->
                        change.consume()
                        pullTo += amount
                    }
                },
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)),
                verticalArrangement = Arrangement.spacedBy(c.m(0.008f)),
                maxItemsInEachRow = 5,
            ) {
                for (clue in cards) {
                    ClueCard(
                        c, clue, Thumbs.nameOf(inv.case, clue)?.let { Thumbs.get(context, it) },
                        picked = clue.id in Game.picked,
                        Modifier.onGloballyPositioned { spots[clue.id] = it },
                    ) {
                        shown = clue.id
                        Game.pick(clue.id)
                    }
                }
            }
            // Threads: the links made (deduction found), and the one being pulled.
            val threadWidth = c.m(0.0022f)
            Canvas(Modifier.matchParentSize()) {
                val w = threadWidth.toPx()
                fun pin(p: Offset) {
                    drawCircle(Color(0xFF6E1410), w * 2.2f, p)
                    drawCircle(Color(0xFFE8B04A), w * 1.1f, p)
                }
                for (d in inv.case.deductions) {
                    if (d.gives !in inv.found) continue
                    val a = rectOf(d.a) ?: continue
                    val b = rectOf(d.b) ?: continue
                    val pa = Offset(a.center.x, a.top + w * 3)
                    val pb = Offset(b.center.x, b.top + w * 3)
                    drawLine(Thread.copy(alpha = 0.85f), pa, pb, w, StrokeCap.Round)
                    pin(pa)
                    pin(pb)
                }
                pullFrom?.let { id ->
                    val a = rectOf(id) ?: return@let
                    val pa = Offset(a.center.x, a.top + w * 3)
                    drawLine(Thread, pa, pullTo, w * 1.4f, StrokeCap.Round)
                    pin(pa)
                    drawCircle(Thread, w * 2.5f, pullTo)
                }
            }
        }
        // The strip: the result of the last link, else the text of the card last pinched.
        val line = Game.linkResult ?: shown?.let { id -> inv.case.clues.firstOrNull { it.id == id } }?.let { "${it.title}：${it.text}" }
            ?: "捏住一张卡片，拉一根红线到另一张卡片，就把两条线索联系起来；也可以先后捏两张卡片。"
        Text(
            line,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(c.m(0.005f))).background(Paper).padding(horizontal = c.m(0.012f), vertical = c.m(0.006f)),
            fontSize = c.sz(0.0145f), color = if (Game.linkResult != null) InkGreen else Ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A clue pinned up: a coloured band for its kind, its picture, its title. */
@Composable
private fun ClueCard(c: PhysicalLengthConverter, clue: Clue, picture: ImageBitmap?, picked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.004f))
    val accent = kindColor(clue.kind)
    Row(
        modifier.width(c.m(0.162f)).height(c.m(0.072f)).clip(shape).background(if (picked) PaperPicked else Paper)
            .border(if (picked) c.m(0.003f) else c.m(0.0012f), if (picked) Gold else accent, shape)
            .clickable { onClick() }.padding(c.m(0.005f)),
        horizontalArrangement = Arrangement.spacedBy(c.m(0.006f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(c.m(0.058f)).clip(RoundedCornerShape(c.m(0.003f))).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            if (picture != null) Image(picture, null, Modifier.fillMaxSize())
            else Knot(c, accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(c.m(0.002f))) {
            Text(clue.kind.label, fontSize = c.sz(0.0105f), color = accent)
            Text(clue.title.removePrefix("推论："), fontSize = c.sz(0.0135f), color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A deduction's picture: two pins and the thread between them. */
@Composable
private fun Knot(c: PhysicalLengthConverter, color: Color) {
    Canvas(Modifier.size(c.m(0.046f))) {
        val s = size.width
        val a = Offset(s * 0.2f, s * 0.3f)
        val b = Offset(s * 0.8f, s * 0.72f)
        drawLine(color, a, b, s * 0.06f, StrokeCap.Round)
        drawCircle(color, s * 0.11f, a)
        drawCircle(color, s * 0.11f, b)
        drawCircle(Color.White.copy(alpha = 0.6f), s * 0.04f, a)
        drawCircle(Color.White.copy(alpha = 0.6f), s * 0.04f, b)
    }
}

/** What each person said, on one sheet that scrolls. */
@Composable
private fun Talks(c: PhysicalLengthConverter, inv: Investigation) {
    // Read the version here too: the investigation object never changes identity, so without it this part was
    // skipped on recomposition and new clues showed only after another pinch (device test 2026-11-12).
    @Suppress("UNUSED_VARIABLE") val version = Game.version
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(c.m(0.005f))).background(Paper).verticalScroll(rememberScrollState())
            .padding(horizontal = c.m(0.014f), vertical = c.m(0.008f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.005f)),
    ) {
        for (p in inv.case.people) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                Thumbs.get(context, "fig_${p.id}")?.let { Image(it, null, Modifier.size(c.m(0.04f)).clip(CircleShape)) }
                Text("${p.name}　${p.role}${if (p.suspect) "" else "（委托人）"}", fontSize = c.sz(0.016f), color = InkGreen)
            }
            val asked = p.questions.filter { it.id in inv.asked }
            if (asked.isEmpty()) Text("还没问过。到现场捏一下这个人偶就能问话。", fontSize = c.sz(0.013f), color = InkDim)
            for (q in asked) {
                Text("问：${q.ask}", fontSize = c.sz(0.0135f), color = InkTitle)
                Text("答：${q.answer}", fontSize = c.sz(0.0135f), color = Ink)
            }
        }
    }
}

/** Who, why and the proof picked for the accusation (clue ids for the last two). */
private class AccusePicks {
    var who by mutableStateOf<String?>(null)
    var why by mutableStateOf<String?>(null)
    var proof by mutableStateOf<String?>(null)
    /** Which of the three is being chosen: 0 who, 1 why, 2 proof. */
    var step by mutableStateOf(0)
}

/**
 * The accusation, one question at a time so it fits the board: the three answers on top (pinch one
 * to change it), the choices for the open one below, and the gold button once all three are set.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Accuse(c: PhysicalLengthConverter, inv: Investigation, picks: AccusePicks) {
    // Read the version here too: the investigation object never changes identity, so without it this part was
    // skipped on recomposition and new clues showed only after another pinch (device test 2026-11-12).
    @Suppress("UNUSED_VARIABLE") val version = Game.version
    val context = LocalContext.current
    if (inv.solved) {
        Text(
            inv.case.truth,
            Modifier.fillMaxSize().clip(RoundedCornerShape(c.m(0.005f))).background(Paper).verticalScroll(rememberScrollState()).padding(c.m(0.012f)),
            fontSize = c.sz(0.0145f), color = Ink,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
        val name = picks.who?.let { inv.case.person(it).name } ?: "？"
        val motive = picks.why?.let { inv.case.clue(it).title } ?: "？"
        val proof = picks.proof?.let { inv.case.clue(it).title } ?: "？"
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
            Tag(c, "一、是谁：$name", picks.step == 0) { picks.step = 0 }
            Tag(c, "二、为什么：$motive", picks.step == 1) { picks.step = 1 }
            Tag(c, "三、凭什么：$proof", picks.step == 2) { picks.step = 2 }
        }
        val w = picks.who
        val m = picks.why
        val p = picks.proof
        if (w != null && m != null && p != null) {
            val shape = RoundedCornerShape(c.m(0.01f))
            Text(
                "指认：就是${inv.case.person(w).name}！",
                Modifier.clip(shape).background(Gold).border(c.m(0.002f), Color(0xFFFFF1C9), shape)
                    .clickable { Game.accuse(w, m, p) }.padding(horizontal = c.m(0.02f), vertical = c.m(0.008f)),
                fontSize = c.sz(0.02f), color = Color(0xFF1B1408),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalArrangement = Arrangement.spacedBy(c.m(0.008f)), maxItemsInEachRow = 5) {
            if (picks.step == 0) {
                for (s in inv.case.suspects) {
                    val on = picks.who == s.id
                    val shape = RoundedCornerShape(c.m(0.005f))
                    Column(
                        Modifier.width(c.m(0.13f)).clip(shape).background(if (on) PaperPicked else Paper)
                            .border(if (on) c.m(0.003f) else c.m(0.0012f), if (on) Gold else InkDim, shape)
                            .clickable { picks.who = s.id; picks.step = 1; Game.verdict = null }.padding(c.m(0.005f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Thumbs.get(context, "fig_${s.id}")?.let { Image(it, null, Modifier.size(c.m(0.08f))) }
                        Text(s.name, fontSize = c.sz(0.015f), color = Ink)
                    }
                }
            } else {
                val cards = inv.found.map { inv.case.clue(it) }
                for (clue in cards) {
                    val on = if (picks.step == 1) picks.why == clue.id else picks.proof == clue.id
                    ClueCard(c, clue, Thumbs.nameOf(inv.case, clue)?.let { Thumbs.get(context, it) }, on, Modifier) {
                        if (picks.step == 1) { picks.why = clue.id; picks.step = 2 } else picks.proof = clue.id
                        Game.verdict = null
                    }
                }
            }
        }
    }
}

/** What the last accusation came to; a wrong one says which of the three to change. */
@Composable
private fun Verdict(c: PhysicalLengthConverter, v: Investigation.Verdict) {
    // Read the version here too: the investigation object never changes identity, so without it this part was
    // skipped on recomposition and new clues showed only after another pinch (device test 2026-11-12).
    @Suppress("UNUSED_VARIABLE") val version = Game.version
    val text = when (v) {
        is Investigation.Verdict.WrongPerson -> "指认不成立：${v.why}"
        Investigation.Verdict.WeakMotive -> "指认不成立：人可能找对了，可选的“为什么”说明不了他为什么要这么做。换一条动机再试。"
        Investigation.Verdict.WeakProof -> "指认不成立：人可能找对了，可选的“凭什么”还不足以证明是他干的。换一条证据再试。"
        Investigation.Verdict.Right -> "结案！"
    }
    Text(
        text,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(c.m(0.005f))).background(Paper).padding(horizontal = c.m(0.012f), vertical = c.m(0.005f)),
        fontSize = c.sz(0.015f), color = if (v == Investigation.Verdict.Right) InkGreen else InkTitle, maxLines = 3,
    )
}

/** The case is closed: who did it, and the way on to the next case. */
@Composable
private fun Solved(c: PhysicalLengthConverter, inv: Investigation) {
    // Read the version here too: the investigation object never changes identity, so without it this part was
    // skipped on recomposition and new clues showed only after another pinch (device test 2026-11-12).
    @Suppress("UNUSED_VARIABLE") val version = Game.version
    val next = Game.book.next()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(c.m(0.005f))).background(Paper).padding(horizontal = c.m(0.012f), vertical = c.m(0.005f)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.012f)),
    ) {
        Text("结案！真凶就是${inv.case.person(inv.case.solution.culprit).name}。", fontSize = c.sz(0.018f), color = InkTitle)
        if (next != null) {
            val shape = RoundedCornerShape(c.m(0.01f))
            Text(
                "进入下一案：${next.title} →",
                Modifier.clip(shape).background(Gold).clickable { Game.nextCase() }.padding(horizontal = c.m(0.016f), vertical = c.m(0.006f)),
                fontSize = c.sz(0.017f), color = Color(0xFF1B1408),
            )
        }
    }
}
