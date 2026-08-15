package app.exitune.android.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import app.exitune.android.LocalPlayerAwareWindowInsets
import app.exitune.android.R
import app.exitune.android.ui.components.themed.Header
import app.exitune.android.ui.components.themed.Scaffold
import app.exitune.android.ui.components.themed.SecondaryTextButton
import app.exitune.android.ui.screens.GlobalRoutes
import app.exitune.android.ui.screens.Route
import app.exitune.android.ui.screens.albumRoute
import app.exitune.android.ui.screens.artistRoute
import app.exitune.android.ui.screens.playlistRoute
import app.exitune.android.utils.align
import app.exitune.android.utils.medium
import app.exitune.android.utils.secondary
import app.exitune.compose.persist.PersistMapCleanup
import app.exitune.compose.routing.RouteHandler
import app.exitune.core.ui.LocalAppearance
import io.ktor.http.Url
import kotlinx.coroutines.delay

private const val ONLINE_TAB = 0

@Route
@Composable
fun SearchScreen(
    initialTextInput: String,
    onSearch: (String) -> Unit,
    onViewPlaylist: (String) -> Unit
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val (tabIndex, onTabChanged) = rememberSaveable { mutableIntStateOf(ONLINE_TAB) }

    val (textFieldValue, onTextFieldValueChanged) = rememberSaveable(
        initialTextInput,
        stateSaver = TextFieldValue.Saver
    ) {
        mutableStateOf(
            TextFieldValue(
                text = initialTextInput,
                selection = TextRange(initialTextInput.length)
            )
        )
    }

    PersistMapCleanup(prefix = "search/")

    RouteHandler {
        GlobalRoutes()

        Content {
            val (colorPalette, typography) = LocalAppearance.current
            val keyboardController = LocalSoftwareKeyboardController.current
            val windowInsets = LocalPlayerAwareWindowInsets.current

            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                delay(300)
                focusRequester.requestFocus()
            }

            val playlistId = remember(textFieldValue.text) {
                runCatching {
                    Url(textFieldValue.text).takeIf {
                        it.host.endsWith("youtube.com", ignoreCase = true) &&
                            it.segments.lastOrNull()?.equals("playlist", ignoreCase = true) == true
                    }?.parameters?.get("list")
                }.getOrNull()
            }

            Scaffold(
                key = "search",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = tabIndex,
                onTabChange = onTabChanged,
                tabColumnContent = {
                    tab(0, R.string.online, R.drawable.globe, canHide = false)
                    tab(1, R.string.library, R.drawable.library)
                },
                // one search bar for every tab, so that switching tabs neither hands the keyboard
                // over to a second text field nor closes it
                primaryContent = {
                    Header(
                        titleContent = {
                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = onTextFieldValueChanged,
                                textStyle = typography.xxl.medium.align(TextAlign.End),
                                singleLine = true,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (tabIndex == ONLINE_TAB &&
                                            textFieldValue.text.isNotEmpty()
                                        ) onSearch(textFieldValue.text)
                                        else keyboardController?.hide()
                                    }
                                ),
                                cursorBrush = SolidColor(colorPalette.text),
                                decorationBox = { innerTextField ->
                                    Box {
                                        AnimatedVisibility(
                                            visible = textFieldValue.text.isEmpty(),
                                            enter = fadeIn(tween(durationMillis = 300)),
                                            exit = fadeOut(tween(durationMillis = 300)),
                                            modifier = Modifier.align(Alignment.CenterEnd)
                                        ) {
                                            BasicText(
                                                text = stringResource(R.string.search_placeholder),
                                                maxLines = 1,
                                                style = typography.xxl.secondary
                                            )
                                        }

                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.focusRequester(focusRequester)
                            )
                        },
                        actionsContent = {
                            if (playlistId != null) {
                                val isAlbum = playlistId.startsWith("OLAK5uy_")

                                SecondaryTextButton(
                                    text = if (isAlbum) stringResource(R.string.view_album)
                                    else stringResource(R.string.view_playlist),
                                    onClick = { onViewPlaylist(textFieldValue.text) }
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (textFieldValue.text.isNotEmpty()) SecondaryTextButton(
                                text = stringResource(R.string.clear),
                                onClick = { onTextFieldValueChanged(TextFieldValue()) }
                            )
                        },
                        modifier = Modifier.padding(
                            windowInsets
                                .only(WindowInsetsSides.Top + WindowInsetsSides.End)
                                .asPaddingValues()
                        )
                    )
                }
            ) { currentTabIndex ->
                // the search bar above already took the top inset
                CompositionLocalProvider(
                    LocalPlayerAwareWindowInsets provides windowInsets
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                ) {
                    saveableStateHolder.SaveableStateProvider(currentTabIndex) {
                        when (currentTabIndex) {
                            ONLINE_TAB -> OnlineSearch(
                                textFieldValue = textFieldValue,
                                onTextFieldValueChange = onTextFieldValueChanged,
                                onSearch = onSearch,
                                onAlbumClick = { albumRoute(it) },
                                onArtistClick = { artistRoute(it) },
                                onPlaylistClick = { playlistRoute(it, null, null, false) }
                            )

                            1 -> LocalSongSearch(textFieldValue = textFieldValue)
                        }
                    }
                }
            }
        }
    }
}
