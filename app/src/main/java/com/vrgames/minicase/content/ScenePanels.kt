package com.vrgames.minicase.content

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.minicase.BOARD_ID
import com.vrgames.minicase.FILE_ID
import com.vrgames.minicase.core.Cases
import com.vrgames.minicase.core.Investigation
import com.vrgames.minicase.game.Game

/** Attachment panel ids of the scene panels (placed by the stage on the table). */
const val PANEL_INTRO = "intro"
const val PANEL_CASE = "case_card"
const val PANEL_TALK = "talk"
const val PANEL_INSPECT = "inspect"
const val PANEL_TOAST = "toast"
const val PANEL_RESULT = "result"
const val PANEL_MENU = "menu"
const val PANEL_BOARD_TAB = "board_tab"
const val PANEL_COACH = "coach"

/** Text height in the scene, so the panels read at arm's length to about 1.5 m. */
private const val TEXT_METERS = 0.02f

val LabelInk = Color(0xFFF4EEE2)
val Brass = Color(0xFFE2B75A)
val Dim = Color(0xFFC2B8A6)
val Clay = Color(0xFFE07A4F)
val Jade = Color(0xFF8FD3A8)
private val Panel = Color(0xD9151A22)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xF2151A22)

/**
 * A window's title row with its 关闭 button. It also tells [Game] while this copy of window [id] is
 * on screen, so a window the system closes does not count as open.
 */
@Composable
fun WindowHeader(id: String, title: String, color: Color, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = color)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}

@Composable
private fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
private fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

/**
 * A pinchable line with an animated pinch icon. Sized in meters through [converter], so it stays
 * the same physical size on any panel density.
 */
@Composable
fun PinchLine(text: String, accent: Color, converter: PhysicalLengthConverter, maxWidth: Float = 0.42f, onPinch: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(converter.m(0.016f))
    var mod = Modifier
        .widthIn(max = converter.m(maxWidth))
        .clip(shape)
        .background(Panel)
        .border(converter.m(0.002f), accent, shape)
    if (onPinch != null) mod = mod.clickable { onPinch() }
    Row(
        mod.padding(horizontal = converter.m(0.014f), vertical = converter.m(0.009f)),
        horizontalArrangement = Arrangement.spacedBy(converter.m(0.01f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onPinch != null) PinchIcon(accent, converter.m(0.03f))
        Text(text, fontSize = converter.sz(TEXT_METERS), color = LabelInk)
    }
}

/** Thumb and index tip closing on each other, with a flash when they meet. */
@Composable
private fun PinchIcon(accent: Color, size: Dp) {
    val t by rememberInfiniteTransition(label = "pinch").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "pinchT",
    )
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val close = when {
            t < 0.45f -> t / 0.45f
            t < 0.7f -> 1f
            else -> 1f - (t - 0.7f) / 0.3f
        }
        val gap = w * (0.32f - 0.27f * close)
        val tip = Offset(w * 0.62f, w * 0.5f)
        val palm = Offset(w * 0.12f, w * 0.5f)
        val index = Offset(tip.x, tip.y - gap)
        val thumb = Offset(tip.x, tip.y + gap)
        val stroke = w * 0.07f
        drawLine(accent.copy(alpha = 0.7f), palm, index, strokeWidth = stroke)
        drawLine(accent.copy(alpha = 0.7f), palm, thumb, strokeWidth = stroke)
        drawCircle(accent, w * 0.1f, index)
        drawCircle(accent, w * 0.1f, thumb)
        if (close >= 1f) drawCircle(accent.copy(alpha = 0.6f), w * 0.3f, tip, style = Stroke(stroke))
    }
}

/** A dark rounded card in the scene, [width] meters wide. */
@Composable
private fun SceneCard(converter: PhysicalLengthConverter, width: Float, accent: Color = Brass, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(converter.m(0.022f))
    Column(
        Modifier.width(converter.m(width)).clip(shape).background(Panel).border(converter.m(0.002f), accent, shape)
            .padding(horizontal = converter.m(0.024f), vertical = converter.m(0.018f)),
        verticalArrangement = Arrangement.spacedBy(converter.m(0.01f)),
    ) { content() }
}

// Ink on the manila case paper inside the board frames.
private val PaperInk = Color(0xFF2A2118)
private val PaperDim = Color(0xFF65543F)
private val PaperTitle = Color(0xFF7B2415)
private val PaperGreen = Color(0xFF1F5438)

/** Inside sizes of the thick case-board frames (panels/panel_*.glb); text must fit them. */
const val INTRO_W = 0.56f; const val INTRO_H = 0.46f
const val CASE_W = 0.48f; const val CASE_H = 0.32f
const val TALK_W = 0.5f; const val TALK_H = 0.5f
const val RESULT_W = 0.54f; const val RESULT_H = 0.42f

/** Text on the paper of a case board (the frame model is added by the scene). */
@Composable
private fun BoardCard(converter: PhysicalLengthConverter, width: Float, height: Float, content: @Composable () -> Unit) {
    Column(
        Modifier.width(converter.m(width)).height(converter.m(height)).padding(horizontal = converter.m(0.02f), vertical = converter.m(0.008f)),
        verticalArrangement = Arrangement.spacedBy(converter.m(0.008f), Alignment.CenterVertically),
    ) { content() }
}

/** The brass speaker: plays a character's recorded line again, or stops it. */
@Composable
private fun SpeakerButton(converter: PhysicalLengthConverter, playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(converter.m(0.05f)).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFE9C46A), Color(0xFF8A6420))))
            .border(converter.m(0.002f), Color(0xFF4F3A12), CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(if (playing) "■" else "▶", fontSize = converter.sz(0.02f), color = Color(0xFF2A1F00)) }
}

/** The title card over the back of the table. */
@Composable
fun IntroPanel(converter: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val fresh = Cases.all.none { Game.book.investigation(it.id).found.isNotEmpty() }
    BoardCard(converter, INTRO_W, INTRO_H) {
        Text("微缩奇案", fontSize = converter.sz(0.045f), color = PaperTitle)
        Text("案发现场，按二十分之一缩小，送到了你的桌上。", fontSize = converter.sz(0.021f), color = PaperInk)
        Text("怎么查：", fontSize = converter.sz(0.021f), color = PaperTitle)
        Text("① 用拇指和食指捏住抽屉、箱盖往外拉；捏住屋顶往上提", fontSize = converter.sz(0.019f), color = PaperInk)
        Text("② 捏起小物件，它会飞到你面前；捏着转动，看看每一面", fontSize = converter.sz(0.019f), color = PaperInk)
        Text("③ 捏一下小人偶，就能向他问话", fontSize = converter.sz(0.019f), color = PaperInk)
        Text("④ 在「案情板」上把两条线索联系起来，最后指认真凶", fontSize = converter.sz(0.019f), color = PaperInk)
        Text("两根手指捏，不要整只手去抓；张开手指就是松手。进度自动保存。", fontSize = converter.sz(0.016f), color = PaperDim)
        PinchLine(if (fresh) "捏一下这里：开始查案" else "捏一下这里：继续查案", Brass, converter) { Game.start() }
        Text(DISCLAIMER, fontSize = converter.sz(0.012f), color = PaperDim)
    }
}

/** The case on the table: where, when, what happened, who was there. */
@Composable
fun CaseCard(converter: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val c = Game.inv.case
    BoardCard(converter, CASE_W, CASE_H) {
        Text("第 ${c.number} 案　${c.title}", fontSize = converter.sz(0.03f), color = PaperTitle)
        Text("${c.place}　${c.time}", fontSize = converter.sz(0.016f), color = PaperDim)
        Text(c.brief, fontSize = converter.sz(0.018f), color = PaperInk)
        PinchLine("开始调查", Brass, converter) { Game.caseCardDismissed = true }
    }
}

/** Questioning a figurine: who they are, what can be asked now, and the last answer. */
@Composable
fun TalkPanel(converter: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val id = Game.talkingTo ?: return
    val inv = Game.inv
    val p = inv.case.people.firstOrNull { it.id == id } ?: return
    BoardCard(converter, TALK_W, TALK_H) {
        Text("${p.name}　${p.role}", fontSize = converter.sz(0.028f), color = PaperGreen)
        Text(p.intro, fontSize = converter.sz(0.016f), color = PaperDim)
        Game.lastAnswer?.let { answer ->
            Row(horizontalArrangement = Arrangement.spacedBy(converter.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
                Game.lastQuestion?.let { q ->
                    SpeakerButton(converter, Game.speaking == q) { if (Game.speaking == q) Game.stopSpeaking() else Game.speak(q) }
                }
                Text("「$answer」", fontSize = converter.sz(0.019f), color = PaperInk)
            }
        }
        val open = p.questions.filter { inv.canAsk(it) }
        for (q in open) {
            val asked = q.id in inv.asked
            PinchLine((if (asked) "（问过）" else "") + q.ask, if (asked) Dim else Jade, converter, 0.42f) { Game.ask(q.id) }
        }
        val locked = p.questions.size - open.size
        if (locked > 0) Text("还有 $locked 个问题，要等找到相关的线索才能问。", fontSize = converter.sz(0.015f), color = PaperDim)
        PinchLine("结束问话", Dim, converter) { Game.talk(null) }
    }
}

/** Under the thing held up close: what it is, a nudge to turn it, and "put back". */
@Composable
fun InspectPanel(converter: PhysicalLengthConverter, name: String?, hidden: String?, putBack: () -> Unit) {
    if (name == null) return
    SceneCard(converter, 0.36f) {
        Text(name, fontSize = converter.sz(0.024f), color = Brass)
        Text(
            if (hidden != null) "捏住它转一转，把$hidden 转过来对着自己看看。" else "捏住它可以转动，看看每一面。",
            fontSize = converter.sz(0.017f), color = LabelInk,
        )
        PinchLine("放回原处", Dim, converter, onPinch = putBack)
    }
}

/** "New clue" for a moment over the scene, and the current hint. */
@Composable
fun ToastPanel(converter: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val tv = Game.toastVersion
    val clues = Game.toast
    val hint = Game.hint
    val note = Game.note
    if (clues.isEmpty() && hint == null && note == null) return
    SceneCard(converter, 0.46f) {
        note?.let { Text(it, fontSize = converter.sz(0.02f), color = LabelInk) }
        for (c in clues) {
            Text("新${c.kind.label}：${c.title}", fontSize = converter.sz(0.022f), color = Brass)
            Text(c.text, fontSize = converter.sz(0.016f), color = LabelInk)
        }
        if (clues.isNotEmpty()) Text("已记进案情板。", fontSize = converter.sz(0.014f), color = Dim)
        hint?.let { Text("提示：" + hintText(it), fontSize = converter.sz(0.018f), color = Jade) }
    }
}

fun hintText(h: Investigation.Hint): String = when (h) {
    is Investigation.Hint.Look -> "去看看${h.prop.where}。"
    is Investigation.Hint.Ask -> "问问${h.person.name}：「${h.question.ask}」"
    is Investigation.Hint.Link -> "在案情板上把「${h.a.title}」和「${h.b.title}」联系起来。"
    Investigation.Hint.Accuse -> "证据够了，打开案情板的「指认」，说出真凶。"
    Investigation.Hint.Done -> "这个案子已经破了。"
}

/** Case closed: the truth, then the next case. */
@Composable
fun ResultPanel(converter: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val inv = Game.inv
    if (!inv.solved) return
    val next = Game.book.next()
    BoardCard(converter, RESULT_W, RESULT_H) {
        Text("结案！真凶是${inv.case.person(inv.case.solution.culprit).name}", fontSize = converter.sz(0.03f), color = PaperTitle)
        Text(inv.case.truth, fontSize = converter.sz(0.017f), color = PaperInk)
        Text("找到线索 ${inv.found.size} / ${inv.case.clues.size}　用了提示 ${inv.hintsUsed} 次　指认错 ${inv.wrongAccusations} 次", fontSize = converter.sz(0.015f), color = PaperDim)
        if (next != null) PinchLine("下一案：${next.title} →", Brass, converter) { Game.nextCase() }
        else Text("三个案子全部告破。谢谢你，探长。", fontSize = converter.sz(0.02f), color = PaperTitle)
        PinchLine("从头再查一遍", Dim, converter) { Game.restartCase() }
    }
}

/** First time: what to do with the scene at all. */
@Composable
fun CoachPanel(converter: PhysicalLengthConverter) =
    PinchLine("用拇指和食指捏一下人偶问话，或者捏起桌上的东西细看", Brass, converter, 0.5f)

/** The tab that opens (or puts away) the case board window, with the clue count. */
@Composable
fun BoardTab(converter: PhysicalLengthConverter, toggle: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val text = if (Game.window == BOARD_ID) "收起案情板" else "案情板（${Game.inv.found.size} 条线索）"
    PinchLine(text, Brass, converter, onPinch = toggle)
}

/** The way to everything else: windows, hints, the table's place, starting over, quitting. */
@Composable
fun MenuPanel(
    converter: PhysicalLengthConverter,
    toggleBoard: () -> Unit,
    toggleFile: () -> Unit,
    faceMe: () -> Unit,
    quit: () -> Unit,
    version: String,
) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val next = if (Game.inv.solved) Game.book.next() else null
    if (!Game.menuOpen) {
        if (next != null) PinchLine("菜单 · 可以进入下一案", Brass, converter) { Game.menuOpen = true }
        else PinchLine("菜单", Dim, converter) { Game.menuOpen = true }
        return
    }
    SceneCard(converter, 0.42f, Dim) {
        if (next != null) PinchLine("进入下一案：${next.title} →", Brass, converter) { Game.nextCase(); Game.menuOpen = false }
        PinchLine("给点提示", Brass, converter) { Game.askHint(); Game.menuOpen = false }
        val board = if (Game.window == BOARD_ID) "收起案情板" else "案情板：线索、推理、指认"
        val file = if (Game.window == FILE_ID) "收起卷宗" else "卷宗：案件、人物、设置"
        PinchLine(board, Brass, converter) { toggleBoard(); Game.menuOpen = false }
        PinchLine(file, Brass, converter) { toggleFile(); Game.menuOpen = false }
        PinchLine("把案发现场摆到我正前方", Brass, converter) { faceMe(); Game.menuOpen = false }
        PinchLine("这个案子从头再查", Dim, converter) { Game.restartCase(); Game.menuOpen = false }
        Text("进度自动保存。版本 $version", fontSize = converter.sz(0.015f), color = Dim)
        PinchLine("退出游戏", Clay, converter) { quit() }
        PinchLine("收起菜单", Dim, converter) { Game.menuOpen = false }
    }
}

const val DISCLAIMER = "本作品的人物、店铺和案件纯属虚构。音效取自 Kenney（CC0），人偶和部分道具模型由 AI 生成，背景音乐见素材说明。"
