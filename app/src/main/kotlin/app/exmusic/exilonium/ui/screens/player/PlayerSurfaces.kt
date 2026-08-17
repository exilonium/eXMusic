package app.exmusic.exilonium.ui.screens.player

import app.exmusic.core.ui.ColorPalette

private const val PANEL_ALPHA = 0.6f
private const val BAR_ALPHA = 0.7f
private const val CONTROL_ALPHA = 0.15f

/**
 * Surfaces used by the expanded player. They are deliberately translucent so the aurora behind them
 * stays visible instead of being cut into flat, opaque slabs.
 */
val ColorPalette.playerPanel get() = background1.copy(alpha = PANEL_ALPHA)

val ColorPalette.playerBar get() = background2.copy(alpha = BAR_ALPHA)

val ColorPalette.playerControl get() = text.copy(alpha = CONTROL_ALPHA)
