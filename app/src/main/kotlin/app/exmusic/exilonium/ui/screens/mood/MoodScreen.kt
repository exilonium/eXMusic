package app.exmusic.exilonium.ui.screens.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.exmusic.compose.persist.PersistMapCleanup
import app.exmusic.compose.routing.RouteHandler
import app.exmusic.exilonium.R
import app.exmusic.exilonium.models.Mood
import app.exmusic.exilonium.ui.components.themed.Scaffold
import app.exmusic.exilonium.ui.screens.GlobalRoutes
import app.exmusic.exilonium.ui.screens.Route

@Route
@Composable
fun MoodScreen(mood: Mood) {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup(prefix = "playlist/mood/")

    RouteHandler {
        GlobalRoutes()

        Content {
            Scaffold(
                key = "mood",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = 0,
                onTabChange = { },
                tabColumnContent = {
                    tab(0, R.string.mood, R.drawable.disc)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> MoodList(mood = mood)
                    }
                }
            }
        }
    }
}
