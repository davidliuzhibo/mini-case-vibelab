package com.vrgames.minicase.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.minicase.FILE_ID
import com.vrgames.minicase.core.Cases
import com.vrgames.minicase.game.Game

/** 卷宗: the cases (pick one), the case at hand with its people, a hint, and the table settings. */
@Composable
fun FileWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val book = Game.book
    val inv = Game.inv
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WindowHeader(FILE_ID, "卷宗", Brass, 34.sp)
        for (c in Cases.all) {
            val open = book.open(c)
            val state = when {
                book.solved(c.id) -> "已结案"
                !open -> "破了上一案才能看"
                c.id == inv.case.id -> "正在调查"
                else -> "待查"
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { Game.select(c.id) }, enabled = open && c.id != inv.case.id) {
                    Text("第 ${c.number} 案　${c.title}", fontSize = 22.sp)
                }
                Text(state, fontSize = 19.sp, color = if (book.solved(c.id)) Jade else Dim)
            }
        }
        Text("第 ${inv.case.number} 案　${inv.case.title}", fontSize = 27.sp, color = Brass)
        Text("${inv.case.place}　${inv.case.time}", fontSize = 19.sp, color = Dim)
        Text(inv.case.brief, fontSize = 20.sp, color = LabelInk)
        Text("在场的人", fontSize = 23.sp, color = Jade)
        for (p in inv.case.people) {
            Text("${p.name}（${p.role}${if (p.suspect) "" else "，委托人"}）：${p.intro}", fontSize = 19.sp, color = LabelInk)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { Game.askHint() }) { Text("给点提示", fontSize = 22.sp) }
            Button(onClick = { Game.restartCase() }) { Text("这个案子从头再查", fontSize = 22.sp) }
        }
        Game.hint?.let { Text("提示：" + hintText(it), fontSize = 21.sp, color = Jade) }
        Text("桌面", fontSize = 23.sp, color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { Game.raiseTable(-0.05f) }) { Text("降低", fontSize = 21.sp) }
            Button(onClick = { Game.raiseTable(0.05f) }) { Text("升高", fontSize = 21.sp) }
            Text("现场离地 ${(Game.tableHeight * 100).toInt()} 厘米", fontSize = 19.sp, color = Dim)
        }
        Button(onClick = { Game.toggleMusic() }) { Text(if (Game.musicOn) "音乐：开" else "音乐：关", fontSize = 21.sp) }
        Text(DISCLAIMER, fontSize = 16.sp, color = Dim)
    }
}
