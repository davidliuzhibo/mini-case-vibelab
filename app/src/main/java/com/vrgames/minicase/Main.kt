package com.vrgames.minicase

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.minicase.content.CaseStage
import com.vrgames.minicase.content.FileWindow

const val FILE_ID = "File"

/**
 * Two linked spaces: the miniature crime scene on the table (Stage), with the 案情板 (clues, links,
 * questions asked, the accusation) standing behind it, and the 卷宗 window (cases, people, hints,
 * settings). They share
 * [com.vrgames.minicase.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { CaseStage() }
        }
        WindowContainer(FILE_ID, Form.Planar, { defaultSize = WindowContainerSize(680.dp, 1000.dp) }) {
            PicoTheme { FileWindow() }
        }
    }
