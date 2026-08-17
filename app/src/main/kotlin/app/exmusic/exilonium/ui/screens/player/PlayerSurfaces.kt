package app.exmusic.exilonium.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import app.exmusic.core.ui.ColorPalette
import app.exmusic.exilonium.preferences.AppearancePreferences

private const val SHEET_ALPHA = 0.2f

// The queue sits well above the rest of the glass. It is the one surface carrying a full screen of
// text, so it is mostly opaque - the aurora is a hint of colour behind it, not something read through.
private const val QUEUE_ALPHA = 0.95f

private const val CONTROL_ALPHA = 0.12f
private const val EDGE_ALPHA = 0.14f

private val auroraEnabled get() = AppearancePreferences.aurora

/**
 * Surfaces used by the expanded player. While aurora mode is on they are one frosted glass material
 * instead of a stack of unrelated greys: the sheets share a single translucent tint, the chrome that
 * can be tapped is a hue-neutral wash of [ColorPalette.text], and everything is separated by the
 * same hairline edge, so the aurora reads through all of it. With aurora off there is nothing to show
 * through, so they fall back to the plain palette backgrounds.
 */
val ColorPalette.playerPanel
    get() = if (auroraEnabled) background1.copy(alpha = QUEUE_ALPHA) else background1

val ColorPalette.playerBar
    get() = if (auroraEnabled) background1.copy(alpha = SHEET_ALPHA) else background2

/** The bar under the open queue. Matches [playerPanel] so the open queue is one pane of glass. */
val ColorPalette.playerQueueBar
    get() = if (auroraEnabled) background1.copy(alpha = QUEUE_ALPHA) else background2

val ColorPalette.playerControl
    get() = if (auroraEnabled) text.copy(alpha = CONTROL_ALPHA) else background2

/**
 * Queue rows. They sit on [playerPanel], which already carries the tint, so painting them again would
 * only stack another slab on top of it.
 */
val ColorPalette.playerItem
    get() = if (auroraEnabled) Color.Transparent else background1

/** Hairline that separates the glass surfaces from each other and from the aurora. */
val ColorPalette.playerEdge
    get() = if (auroraEnabled) text.copy(alpha = EDGE_ALPHA) else Color.Transparent

/** [fill] plus the [ColorPalette.playerEdge] hairline that ties every glass surface together. */
fun Modifier.playerGlass(colorPalette: ColorPalette, fill: Color, shape: Shape = RectangleShape) =
    background(color = fill, shape = shape)
        .border(width = Dp.Hairline, color = colorPalette.playerEdge, shape = shape)
