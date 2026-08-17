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

private const val HIGHLIGHT_ALPHA = 0.12f

/**
 * The pill drawn behind the song that is playing. While aurora mode is on it is a hue-neutral wash of
 * [ColorPalette.text] rather than an opaque grey, matching the player's chrome: a slab of its own
 * colour reads as pasted over the aurora instead of lit by it.
 */
val ColorPalette.songHighlight
    get() = if (AppearancePreferences.aurora) text.copy(alpha = HIGHLIGHT_ALPHA) else background2
