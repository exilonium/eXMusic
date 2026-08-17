package app.exmusic.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

object Dimensions {
    object Thumbnails {
        val album = 108.dp
        val artist = 92.dp
        val song = 54.dp
        val playlist = album

        val player = Player

        object Player {
            /**
             * This size ends up baked into the artwork url (and thereby Coil's cache key), so it
             * must not change with orientation. `screenWidthDp`/`screenHeightDp` exclude whichever
             * system bars the current orientation has, so their min shifts by a few dp on rotation
             * — enough for a different url, a cold cache and a full refetch. `smallestScreenWidthDp`
             * is orientation-invariant by definition.
             */
            val song
                @Composable get() = LocalConfiguration.current.smallestScreenWidthDp.dp
        }
    }

    val thumbnails = Thumbnails

    object Items {
        val moodHeight = 64.dp
        val headerHeight = 140.dp
        val collapsedPlayerHeight = 64.dp

        val verticalPadding = 8.dp
        val horizontalPadding = 8.dp
        val alternativePadding = 12.dp

        val gap = 4.dp
    }

    val items = Items

    object NavigationRail {
        val width = 60.dp
        val widthLandscape = 120.dp
        val iconOffset = 6.dp
    }

    val navigationRail = NavigationRail
}
