package app.exmusic.exilonium.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Down
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Up
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.exilonium.R
import app.exmusic.exilonium.preferences.UIStatePreferences
import kotlinx.collections.immutable.toImmutableList

/** Enough to make a screen full of text illegible the moment it starts moving. */
private val TAB_TRANSITION_BLUR_RADIUS = 32.dp

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
                        // the fade covers the devices below Android 12, where blur does nothing
                        targetContentEnter = slideIntoContainer(slideDirection, animationSpec) +
                            fadeIn(),
                        initialContentExit = slideOutOfContainer(slideDirection, animationSpec) +
                            fadeOut(),
                        sizeTransform = null
                    )
                },
                content = { currentTabIndex ->
                    // the outgoing tab used to stay perfectly readable while it slid away; blurring
                    // it out hides what it showed the moment the switch starts
                    val blurRadius by transition.animateDp(label = "") { state ->
                        if (state == EnterExitState.Visible) 0.dp else TAB_TRANSITION_BLUR_RADIUS
                    }

                    Box(modifier = Modifier.blur(blurRadius)) {
                        content(currentTabIndex)
                    }
                },
                label = ""
            )
        }
    }
}
