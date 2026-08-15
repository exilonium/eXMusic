package app.exmusic.exilonium.ui.screens.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.exmusic.compose.persist.PersistMapCleanup
import app.exmusic.compose.routing.RouteHandler
import app.exmusic.exilonium.R
import app.exmusic.exilonium.models.toUiMood
import app.exmusic.exilonium.ui.components.themed.Scaffold
import app.exmusic.exilonium.ui.screens.GlobalRoutes
import app.exmusic.exilonium.ui.screens.Route
import app.exmusic.exilonium.ui.screens.moodRoute

@Route
@Composable
fun MoreMoodsScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup(prefix = "more_moods/")

    RouteHandler {
        GlobalRoutes()

        moodRoute { mood ->
            MoodScreen(mood = mood)
        }

        Content {
            Scaffold(
                key = "moremoods",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = 0,
                onTabChange = { },
                tabColumnContent = {
                    tab(0, R.string.moods_and_genres, R.drawable.playlist)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> MoreMoodsList(
                            onMoodClick = { mood -> moodRoute(mood.toUiMood()) }
                        )
                    }
                }
            }
        }
    }
}
