package com.vrgames.minicase.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.minicase.BOARD_ID
import com.vrgames.minicase.core.Clue
import com.vrgames.minicase.core.ClueKind
import com.vrgames.minicase.core.Investigation
import com.vrgames.minicase.game.Game

private val CardBg = Color(0x26F4EEE2)

private fun kindColor(k: ClueKind) = when (k) {
    ClueKind.EVIDENCE -> Brass
    ClueKind.TESTIMONY -> Jade
    ClueKind.DEDUCTION -> Color(0xFF9DB8FF)
}

/**
 * 案情板: every clue found so far as a card; pick two to link them into a deduction; the
 * questions asked and their answers; and the accusation (who, why, the proof).
 */
@Composable
fun BoardWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    var tab by remember { mutableStateOf(0) }
    val inv = Game.inv
    // The accusation's picks, kept here so the 指认 button can stand below the scrolling part.
    // Keyed by the case: picks from another case must not carry over.
    val picks = remember(inv.case.id) { AccusePicks() }
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WindowHeader(BOARD_ID, "案情板　第 ${inv.case.number} 案 · ${inv.case.title}", Brass, 30.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("线索 ${inv.found.size}/${inv.case.clues.size}", "问话记录", "指认真凶").forEachIndexed { i, s ->
                Button(onClick = { tab = i }) { Text(if (tab == i) "【$s】" else s, fontSize = 22.sp) }
            }
        }
        // Above the scrolling part: under the picks, the answer to an accusation scrolled out of sight.
        if (inv.solved) Solved(inv) else Game.verdict?.let { Verdict(it) }
        // Up here, never scrolled away and clear of the window's bottom edge: a button down at the
        // edge took no pinch on the device (the system's own tools caught it), so no accusation went through.
        if (tab == 2 && !inv.solved) AccuseBar(inv, picks)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> Clues(inv)
                1 -> Talks(inv)
                else -> Accuse(inv, picks)
            }
        }
    }
}

/** Who, why and the proof picked for the accusation (clue ids for the last two). */
private class AccusePicks {
    var who by mutableStateOf<String?>(null)
    var why by mutableStateOf<String?>(null)
    var proof by mutableStateOf<String?>(null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Clues(inv: Investigation) {
    // [inv] is the same object after every change, so the composable reads the version to recompose.
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    Text("捏两张卡片，把它们联系起来：有关联的会得出新的推论。", fontSize = 20.sp, color = Dim)
    Game.linkResult?.let { Text(it, fontSize = 22.sp, color = Jade) }
    if (inv.found.isEmpty()) Text("还没有线索。去现场看看：拉开抽屉、捏起物件、问问人偶。", fontSize = 21.sp, color = LabelInk)
    for (kind in ClueKind.entries) {
        val list = inv.found.map { inv.case.clue(it) }.filter { it.kind == kind }
        if (list.isEmpty()) continue
        Text(kind.label, fontSize = 22.sp, color = kindColor(kind))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in list) ClueCard(c, c.id in Game.picked) { Game.pick(c.id) }
        }
    }
}

@Composable
private fun ClueCard(c: Clue, picked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val accent = kindColor(c.kind)
    Column(
        Modifier.width(300.dp).clip(shape).background(if (picked) accent.copy(alpha = 0.28f) else CardBg)
            .border(if (picked) 3.dp else 1.dp, accent, shape).clickable { onClick() }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(c.title, fontSize = 21.sp, color = accent)
        Text(c.text, fontSize = 17.sp, color = LabelInk)
    }
}

@Composable
private fun Talks(inv: Investigation) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    for (p in inv.case.people) {
        Text("${p.name}　${p.role}${if (p.suspect) "" else "（委托人）"}", fontSize = 23.sp, color = Jade)
        val asked = p.questions.filter { it.id in inv.asked }
        if (asked.isEmpty()) Text("还没问过。到现场捏一下这个人偶就能问话。", fontSize = 18.sp, color = Dim)
        for (q in asked) {
            Text("问：${q.ask}", fontSize = 19.sp, color = Brass)
            Text("答：${q.answer}", fontSize = 18.sp, color = LabelInk)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Accuse(inv: Investigation, picks: AccusePicks) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    if (inv.solved) {
        Text(inv.case.truth, fontSize = 19.sp, color = LabelInk)
        return
    }
    Text("指认要说清三件事：是谁、为什么、凭什么。", fontSize = 20.sp, color = Dim)
    Text("一、是谁", fontSize = 22.sp, color = Brass)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (p in inv.case.suspects) Button(onClick = { picks.who = p.id; Game.verdict = null }) { Text(if (picks.who == p.id) "【${p.name}】" else p.name, fontSize = 21.sp) }
    }
    val cards = inv.found.map { inv.case.clue(it) }
    Text("二、为什么（动机）", fontSize = 22.sp, color = Brass)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in cards) ClueCard(c, picks.why == c.id) { picks.why = c.id; Game.verdict = null }
    }
    Text("三、凭什么（最有力的证据）", fontSize = 22.sp, color = Brass)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in cards) ClueCard(c, picks.proof == c.id) { picks.proof = c.id; Game.verdict = null }
    }
}

/** The head of the 指认 tab: what is picked so far, and the button once all three are. */
@Composable
private fun AccuseBar(inv: Investigation, picks: AccusePicks) {
    val w = picks.who
    val m = picks.why
    val p = picks.proof
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brass.copy(alpha = 0.16f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val name = w?.let { inv.case.person(it).name } ?: "（未选）"
        val motive = m?.let { inv.case.clue(it).title } ?: "（未选）"
        val evidence = p?.let { inv.case.clue(it).title } ?: "（未选）"
        Text("是谁：$name　为什么：$motive　凭什么：$evidence", fontSize = 19.sp, color = LabelInk)
        if (w != null && m != null && p != null) {
            Text("最后一步：捏一下金色按钮，正式指认。", fontSize = 21.sp, color = Brass)
            CallToAction("指认：就是${name}！") { Game.accuse(w, m, p) }
        } else {
            val missing = listOfNotNull("是谁".takeIf { w == null }, "为什么（动机）".takeIf { m == null }, "凭什么（证据）".takeIf { p == null })
            Text("还要在下面选：${missing.joinToString("、")}。三样选齐，这里会出现「指认」按钮。", fontSize = 21.sp, color = Brass)
        }
    }
}

/** What the last accusation came to; a wrong one says which of the three to change. */
@Composable
private fun Verdict(v: Investigation.Verdict) {
    val (text, color) = when (v) {
        is Investigation.Verdict.WrongPerson -> "指认不成立：${v.why}" to Clay
        Investigation.Verdict.WeakMotive -> "指认不成立：人可能找对了，可选的“为什么”说明不了他为什么要这么做。换一条动机再试。" to Clay
        Investigation.Verdict.WeakProof -> "指认不成立：人可能找对了，可选的“凭什么”还不足以证明是他干的。换一条证据再试。" to Clay
        Investigation.Verdict.Right -> "结案！" to Brass
    }
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(14.dp), fontSize = 23.sp, color = color)
}

/** The case is closed: who did it, and the way on to the next case (on every tab). */
@Composable
private fun Solved(inv: Investigation) {
    val next = Game.book.next()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brass.copy(alpha = 0.2f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("结案！真凶就是${inv.case.person(inv.case.solution.culprit).name}。", fontSize = 26.sp, color = Brass)
        if (next != null) {
            CallToAction("进入下一案：第 ${next.number} 案 ${next.title} →") { Game.nextCase() }
        } else {
            Text("三个案子全部告破。谢谢你，探长。", fontSize = 22.sp, color = Brass)
        }
    }
}

/**
 * The one step that moves the story on, filled in brass: the design system's dark buttons read as
 * plain text on the dark board, and players on the device never thought to pinch them.
 */
@Composable
private fun CallToAction(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text,
        Modifier.clip(shape).background(Brass).border(3.dp, Color(0xFFFFF1C9), shape)
            .clickable { onClick() }.padding(horizontal = 30.dp, vertical = 16.dp),
        fontSize = 28.sp,
        color = Color(0xFF1B1408),
    )
}
