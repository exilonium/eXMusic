package app.exmusic.exilonium.ui.screens.player

import app.exmusic.core.ui.ColorPalette
import app.exmusic.exilonium.preferences.AppearancePreferences

private const val PANEL_ALPHA = 0.6f
private const val BAR_ALPHA = 0.7f
private const val CONTROL_ALPHA = 0.15f

/**
 * Surfaces used by the expanded player. While aurora mode is on they are deliberately translucent so
 * the aurora behind them stays visible instead of being cut into flat, opaque slabs; with aurora off
 * there is nothing to show through, so they fall back to the plain palette backgrounds.
 */
val ColorPalette.playerPanel
    get() = if (AppearancePreferences.aurora) background1.copy(alpha = PANEL_ALPHA) else background1

val ColorPalette.playerBar
    get() = if (AppearancePreferences.aurora) background2.copy(alpha = BAR_ALPHA) else background2

val ColorPalette.playerControl
    get() = if (AppearancePreferences.aurora) text.copy(alpha = CONTROL_ALPHA) else background2
