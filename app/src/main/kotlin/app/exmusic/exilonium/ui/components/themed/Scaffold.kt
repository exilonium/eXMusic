package app.exmusic.exilonium.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Down
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Up
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import app.exmusic.exilonium.R
import app.exmusic.exilonium.preferences.UIStatePreferences
import app.exmusic.core.ui.LocalAppearance
import kotlinx.collections.immutable.toImmutableList

@Composable
fun Scaffold(
    key: String,
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabChange: (Int) -> Unit,
    tabColumnContent: TabsBuilder.() -> Unit,
    modifier: Modifier = Modifier,
    tabsEditingTitle: String = stringResource(R.string.tabs),
    /**
     * Shown above the tab content and kept across tab changes, so that whatever it holds does not
     * lose its state - a text field does not lose focus, and the keyboard stays up.
     */
    primaryContent: (@Composable () -> Unit)? = null,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val (colorPalette) = LocalAppearance.current
    var hiddenTabs by UIStatePreferences.mutableTabStateOf(key)

    Row(
        modifier = modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        NavigationRail(
            topIconButtonId = topIconButtonId,
            onTopIconButtonClick = onTopIconButtonClick,
            tabIndex = tabIndex,
            onTabIndexChange = onTabChange,
            hiddenTabs = hiddenTabs,
            setHiddenTabs = { hiddenTabs = it.toImmutableList() },
            tabsEditingTitle = tabsEditingTitle,
            content = tabColumnContent
        )

        Column(modifier = Modifier.fillMaxSize()) {
            primaryContent?.invoke()

            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = {
                    val slideDirection = if (targetState > initialState) Up else Down
                    val animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = Spring.StiffnessLow,
                        visibilityThreshold = IntOffset.VisibilityThreshold
                    )

                    ContentTransform(
                        targetContentEnter = slideIntoContainer(slideDirection, animationSpec),
                        initialContentExit = slideOutOfContainer(slideDirection, animationSpec),
                        sizeTransform = null
                    )
                },
                content = content,
                label = ""
            )
        }
    }
}
