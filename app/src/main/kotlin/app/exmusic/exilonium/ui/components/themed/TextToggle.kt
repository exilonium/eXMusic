package app.exmusic.exilonium.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.core.ui.utils.roundedShape
import app.exmusic.exilonium.R
import app.exmusic.exilonium.utils.medium

@Composable
fun TextToggle(
    state: Boolean,
    toggleState: () -> Unit,
    name: String,
    modifier: Modifier = Modifier,
    onLabel: String = stringResource(R.string.on_label),
    offLabel: String = stringResource(R.string.off_label),
    backgroundColor: Color = LocalAppearance.current.colorPalette.background1,
    borderColor: Color = Color.Transparent
) {
    val (_, typography) = LocalAppearance.current

    val shape = 16.dp.roundedShape

    Row(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = toggleState)
            .background(color = backgroundColor, shape = shape)
            .border(width = Dp.Hairline, color = borderColor, shape = shape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        BasicText(
            text = "$name ",
            style = typography.xxs.medium
        )

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                val slideDirection =
                    if (targetState) AnimatedContentTransitionScope.SlideDirection.Up
                    else AnimatedContentTransitionScope.SlideDirection.Down

                ContentTransform(
                    targetContentEnter = slideIntoContainer(slideDirection) + fadeIn(),
                    initialContentExit = slideOutOfContainer(slideDirection) + fadeOut()
                )
            },
            label = ""
        ) {
            BasicText(
                text = if (it) onLabel else offLabel,
                style = typography.xxs.medium
            )
        }
    }
}
