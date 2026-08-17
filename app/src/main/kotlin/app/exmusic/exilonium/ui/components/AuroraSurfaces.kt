package app.exmusic.exilonium.ui.components

import androidx.compose.ui.graphics.Color
import app.exmusic.core.ui.ColorPalette
import app.exmusic.exilonium.preferences.AppearancePreferences

/**
 * Background of a screen-level container. While aurora mode is on it is transparent, so the app-wide
 * aurora painted by [app.exmusic.exilonium.ui.modifiers.auroraGlow] shows through instead of being
 * covered by an opaque slab; otherwise it is the plain palette background.
 */
val ColorPalette.screenBackground
    get() = if (AppearancePreferences.aurora) Color.Transparent else background0
