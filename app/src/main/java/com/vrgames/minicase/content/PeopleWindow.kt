package com.vrgames.minicase.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.minicase.PEOPLE_ID
import com.vrgames.minicase.game.Game
import com.vrgames.minicase.platform.AppExit

/**
 * 人物档案 (planar window, contest round 2026-11-15): everyone at the scene with their figurine's
 * portrait, who they are, and every answer heard so far, each one replayable; the answers to come
 * show as locked lines, so the player sees who still has more to tell.
 */
@Composable
fun PeopleWindow() {
    AppExit.CloseWithStage(PEOPLE_ID)
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val context = LocalContext.current
    val inv = Game.inv
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(26.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WindowHeader(PEOPLE_ID, "人物档案", Brass, 32.sp)
        Text("第 ${inv.case.number} 案　${inv.case.title}", fontSize = 19.sp, color = Dim)
        for (p in inv.case.people) {
            val shape = RoundedCornerShape(16.dp)
            val talking = Game.talkingTo == p.id
            Column(
                Modifier.fillMaxWidth().clip(shape).background(Color(0x22FFFFFF)).border(if (talking) 2.dp else 1.dp, if (talking) Brass else Color(0x33FFFFFF), shape).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Thumbs.get(context, "fig_${p.id}")?.let {
                        Image(it, p.name, Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3E7CC)))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 26.sp, color = if (p.suspect) LabelInk else Jade)
                        Text(p.role + if (p.suspect) "" else "（委托人）", fontSize = 17.sp, color = Dim)
                    }
                    Button(onClick = { Game.talk(p.id) }) { Text(if (talking) "正在问话" else "去问话", fontSize = 18.sp) }
                }
                Text(p.intro, fontSize = 17.sp, color = LabelInk)
                val heard = p.questions.filter { it.id in inv.asked }
                val waiting = p.questions.count { it.id !in inv.asked }
                for (q in heard) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (Game.speaking == q.id) "■" else "▶",
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0x55E2B75A)).clickable { if (Game.speaking == q.id) Game.stopSpeaking() else Game.speak(q.id) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 16.sp, color = LabelInk,
                        )
                        Column(Modifier.weight(1f)) {
                            Text("问：${q.ask}", fontSize = 16.sp, color = Jade)
                            Text("答：「${q.answer}」", fontSize = 17.sp, color = LabelInk)
                        }
                    }
                }
                if (waiting > 0) Text(
                    if (heard.isEmpty()) "还没问过话（$waiting 个问题）" else "还有 $waiting 个问题没问（有的要先找到相关线索）",
                    fontSize = 15.sp, color = Dim,
                )
            }
        }
    }
}
