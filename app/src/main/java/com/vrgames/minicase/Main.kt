package com.vrgames.minicase

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.minicase.content.BoardWindow
import com.vrgames.minicase.content.CaseStage
import com.vrgames.minicase.content.FileWindow

const val BOARD_ID = "Board"
const val FILE_ID = "File"

/**
 * Three linked spaces: the miniature crime scene on the table (Stage), the 案情板 (clues, links,
 * questions asked, the accusation) and the 卷宗 (cases, people, hints, settings). They share
 * [com.vrgames.minicase.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { CaseStage() }
        }
        WindowContainer(BOARD_ID, Form.Planar, { defaultSize = WindowContainerSize(980.dp, 1000.dp) }) {
            PicoTheme { BoardWindow() }
        }
        WindowContainer(FILE_ID, Form.Planar, { defaultSize = WindowContainerSize(680.dp, 1000.dp) }) {
            PicoTheme { FileWindow() }
        }
    }
