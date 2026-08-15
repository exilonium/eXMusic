package app.exitune.android.ui.screens.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.exitune.android.Database
import app.exitune.android.LocalPlayerAwareWindowInsets
import app.exitune.android.LocalPlayerServiceBinder
import app.exitune.android.R
import app.exitune.android.models.SearchQuery
import app.exitune.android.preferences.DataPreferences
import app.exitune.android.query
import app.exitune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.exitune.android.ui.items.InnertubeItem
import app.exitune.android.utils.center
import app.exitune.android.utils.disabled
import app.exitune.android.utils.playingSong
import app.exitune.android.utils.secondary
import app.exitune.compose.persist.persist
import app.exitune.compose.persist.persistList
import app.exitune.core.ui.LocalAppearance
import app.exitune.providers.innertube.Innertube
import app.exitune.providers.innertube.models.bodies.SearchSuggestionsBody
import app.exitune.providers.innertube.requests.searchSuggestions
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun OnlineSearch(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) = Box(modifier = modifier) {
    val (_, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    var history by persistList<SearchQuery>("search/online/history")
    var suggestionsResult by persist<Result<Innertube.SearchSuggestions>?>(
        tag = "search/online/suggestionsResult"
    )

    LaunchedEffect(textFieldValue.text) {
        if (DataPreferences.pauseSearchHistory) return@LaunchedEffect

        Database.queries("%${textFieldValue.text}%")
            .distinctUntilChanged { old, new -> old.size == new.size }
            .collect { history = it.toImmutableList() }
    }

    LaunchedEffect(textFieldValue.text) {
        // an empty box has nothing to suggest, and leaving the last results up makes clearing it
        // look like it did nothing
        if (textFieldValue.text.isEmpty()) {
            suggestionsResult = null
            return@LaunchedEffect
        }

        delay(500)
        suggestionsResult = Innertube.searchSuggestions(
            body = SearchSuggestionsBody(input = textFieldValue.text)
        )
    }

    val lazyListState = rememberLazyListState()
    val (currentMediaId, playing) = playingSong(binder)

    val suggestions = suggestionsResult?.getOrNull()

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .asPaddingValues(),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = history,
            key = SearchQuery::id
        ) { searchQuery ->
            QueryRow(
                query = searchQuery.query,
                icon = R.drawable.time,
                onClick = { onSearch(searchQuery.query) },
                onFill = {
                    onTextFieldValueChange(
                        TextFieldValue(
                            text = searchQuery.query,
                            selection = TextRange(searchQuery.query.length)
                        )
                    )
                },
                onDelete = {
                    query {
                        Database.delete(searchQuery)
                    }
                },
                modifier = Modifier.animateItem()
            )
        }

        items(
            items = suggestions?.queries.orEmpty(),
            key = { "suggestion/$it" }
        ) { suggestion ->
            QueryRow(
                query = suggestion,
                icon = R.drawable.search,
                onClick = { onSearch(suggestion) },
                onFill = {
                    onTextFieldValueChange(
                        TextFieldValue(
                            text = suggestion,
                            selection = TextRange(suggestion.length)
                        )
                    )
                }
            )
        }

        items(
            items = suggestions?.items.orEmpty(),
            key = { "item/${it.key}" }
        ) { item ->
            InnertubeItem(
                item = item,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                currentMediaId = currentMediaId,
                playing = playing
            )
        }

        suggestionsResult?.exceptionOrNull()?.let {
            item(key = "error") {
                Box(modifier = Modifier.fillMaxSize()) {
                    BasicText(
                        text = stringResource(R.string.error_message),
                        style = typography.s.secondary.center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
}

@Composable
private fun QueryRow(
    query: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    val (_, typography) = LocalAppearance.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(all = 16.dp)
    ) {
        Spacer(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(20.dp)
                .paint(
                    painter = painterResource(icon),
                    colorFilter = ColorFilter.disabled
                )
        )

        BasicText(
            text = query,
            style = typography.s.secondary,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .weight(1f)
        )

        onDelete?.let {
            Image(
                painter = painterResource(R.drawable.close),
                contentDescription = null,
                colorFilter = ColorFilter.disabled,
                modifier = Modifier
                    .clickable(
                        indication = ripple(bounded = false),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = it
                    )
                    .padding(horizontal = 8.dp)
                    .size(20.dp)
            )
        }

        Image(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            colorFilter = ColorFilter.disabled,
            modifier = Modifier
                .clickable(
                    indication = ripple(bounded = false),
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onFill
                )
                .rotate(225f)
                .padding(horizontal = 8.dp)
                .size(22.dp)
        )
    }
}
