package app.exmusic.exilonium.ui.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.exmusic.core.ui.ColorPalette
import app.exmusic.exilonium.preferences.AppearancePreferences

private const val DARK_GLOW_ALPHA = 0.25f
private const val LIGHT_GLOW_ALPHA = 0.12f
private const val GLOW_RADIUS_FACTOR = 0.85f
private const val SECONDARY_GLOW_RADIUS_FACTOR = 0.7f
private const val SECONDARY_GLOW_ALPHA_FACTOR = 0.6f

/**
 * Paints [ColorPalette.background0] and, while aurora mode is on, bleeds two soft accent glows in
 * from the top-right and bottom-left corners. This is the static, app-wide counterpart of the
 * player's animated [app.exmusic.exilonium.ui.components.AuroraBackground]: it is meant to sit
 * behind the whole UI, so it never animates and never touches the artwork.
 *
 * Screens have to leave their own background transparent for this to show through, which
 * [app.exmusic.exilonium.ui.components.screenBackground] takes care of.
 */
fun Modifier.auroraGlow(colorPalette: ColorPalette) = drawBehind {
    drawRect(color = colorPalette.background0)

    if (!AppearancePreferences.aurora) return@drawBehind

    val alpha = if (colorPalette.isDark) DARK_GLOW_ALPHA else LIGHT_GLOW_ALPHA
    val radius = size.maxDimension * GLOW_RADIUS_FACTOR

    drawGlow(
        color = colorPalette.accent,
        center = Offset(x = size.width, y = 0f),
        radius = radius,
        alpha = alpha
    )
    drawGlow(
        color = colorPalette.accent,
        center = Offset(x = 0f, y = size.height),
        radius = radius * SECONDARY_GLOW_RADIUS_FACTOR,
        alpha = alpha * SECONDARY_GLOW_ALPHA_FACTOR
    )
}

private fun DrawScope.drawGlow(
    color: Color,
    center: Offset,
    radius: Float,
    alpha: Float
) = drawCircle(
    brush = Brush.radialGradient(
        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
        center = center,
        radius = radius
    ),
    radius = radius,
    center = center
)
