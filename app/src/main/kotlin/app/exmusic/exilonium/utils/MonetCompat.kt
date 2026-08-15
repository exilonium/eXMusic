package app.exmusic.exilonium.utils

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.exmusic.core.ui.ColorPalette
import app.exmusic.core.ui.defaultLightPalette
import com.kieronquinn.monetcompat.core.MonetCompat
import kotlinx.coroutines.launch

val LocalMonetCompat = staticCompositionLocalOf { MonetCompat.getInstance() }

context(owner: LifecycleOwner)
inline fun MonetCompat.invokeOnReady(
    state: Lifecycle.State = Lifecycle.State.CREATED,
    crossinline block: () -> Unit
) = with(owner) {
    lifecycleScope.launch {
        repeatOnLifecycle(state) {
            awaitMonetReady()
            block()
        }
    }
}

fun MonetCompat.setDefaultPalette(palette: ColorPalette = defaultLightPalette) {
    defaultAccentColor = palette.accent.toArgb()
    defaultBackgroundColor = palette.background0.toArgb()
    defaultPrimaryColor = palette.background1.toArgb()
    defaultSecondaryColor = palette.background2.toArgb()
}
