package com.vrgames.minicase

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.Placement
import com.pico.spatial.ui.foundation.dsl.PlacementContext
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.foundation.dsl.WorldScale
import com.pico.spatial.ui.platform.LengthUnit
import com.vrgames.minicase.content.CabinetWindow
import com.vrgames.minicase.content.CaseStage
import com.vrgames.minicase.content.FileWindow
import com.vrgames.minicase.content.PeopleWindow

const val FILE_ID = "File"
const val PEOPLE_ID = "People"
const val CABINET_ID = "Cabinet"
val ALL_WINDOWS = listOf(FILE_ID, PEOPLE_ID, CABINET_ID)

/**
 * Linked spaces sharing [com.vrgames.minicase.game.Game]: the miniature crime scene on the table (Stage)
 * with the 案情板 standing behind it; the 卷宗 window (cases, hints, settings) to its left; the 人物档案
 * window (everyone and what they said) to its right; and the 证物柜 volume (the evidence found, to
 * turn in the hand) beside the 人物档案. Several may be up at once (contest round 2026-11-15).
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { CaseStage() }
        }
        WindowContainer(FILE_ID, Form.Planar, {
            defaultSize = WindowContainerSize(680.dp, 1000.dp)
            placement = { place(this, FILE_ID, Placement.Orientation.Left, null) }
        }) {
            PicoTheme { FileWindow() }
        }
        WindowContainer(PEOPLE_ID, Form.Planar, {
            defaultSize = WindowContainerSize(660.dp, 980.dp)
            placement = { place(this, PEOPLE_ID, Placement.Orientation.Right, null) }
        }) {
            PicoTheme { PeopleWindow() }
        }
        WindowContainer(CABINET_ID, Form.Volumetric, {
            // Far away a volume keeps its apparent size (emulator 2026-11-15: at its true size it looked tiny).
            worldScale = WorldScale.Dynamic
            defaultSize = WindowContainerSize(0.82f, 0.56f, 0.34f, LengthUnit.Meters)
            placement = { place(this, CABINET_ID, Placement.Orientation.Right, PEOPLE_ID) }
        }) {
            PicoTheme { CabinetWindow() }
        }
    }

/**
 * Windows open beside the table, not over the scene: [side] of the stage, or of [prefer] when that
 * window is up.
 */
private fun place(ctx: PlacementContext, self: String, side: Placement.Orientation, prefer: String?): Placement {
    android.util.Log.i("CaseWindows", "place $self among ${ctx.containers.joinToString { "${it.id}/${it.tag}" }}")
    val anchor = prefer?.let { p -> ctx.containers.firstOrNull { it.id == p } } ?: ctx.containers.firstOrNull { it.id !in ALL_WINDOWS } ?: return Placement.none()
    return Placement.placement(anchor, side, 24.dp)
}
