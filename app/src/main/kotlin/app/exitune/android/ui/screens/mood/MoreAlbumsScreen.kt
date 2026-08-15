package app.exitune.android.ui.screens.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.exitune.android.R
import app.exitune.android.ui.components.themed.Scaffold
import app.exitune.android.ui.screens.GlobalRoutes
import app.exitune.android.ui.screens.Route
import app.exitune.android.ui.screens.albumRoute
import app.exitune.compose.persist.PersistMapCleanup
import app.exitune.compose.routing.RouteHandler

@Route
@Composable
fun MoreAlbumsScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup(prefix = "more_albums/")

    RouteHandler {
        GlobalRoutes()

        Content {
            Scaffold(
                key = "morealbums",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = 0,
                onTabChange = { },
                tabColumnContent = {
                    tab(0, R.string.albums, R.drawable.disc)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> MoreAlbumsList(
                            onAlbumClick = { albumRoute(it) }
                        )
                    }
                }
            }
        }
    }
}
