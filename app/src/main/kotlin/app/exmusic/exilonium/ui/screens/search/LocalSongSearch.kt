package app.exmusic.exilonium.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import app.exmusic.exilonium.Database
import app.exmusic.exilonium.LocalPlayerAwareWindowInsets
import app.exmusic.exilonium.LocalPlayerServiceBinder
import app.exmusic.exilonium.models.Song
import app.exmusic.exilonium.ui.components.LocalMenuState
import app.exmusic.exilonium.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.exmusic.exilonium.ui.components.themed.InHistoryMediaItemMenu
import app.exmusic.exilonium.ui.items.SongItem
import app.exmusic.exilonium.utils.asMediaItem
import app.exmusic.exilonium.utils.forcePlay
import app.exmusic.exilonium.utils.playingSong
import app.exmusic.compose.persist.persistList
import app.exmusic.core.ui.Dimensions
import app.exmusic.providers.innertube.models.NavigationEndpoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Below this the query matches too much of the library to be worth showing. */
private const val MINIMUM_QUERY_LENGTH = 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalSongSearch(
    textFieldValue: TextFieldValue,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    var items by persistList<Song>("search/local/songs")

    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text.length < MINIMUM_QUERY_LENGTH) {
            // otherwise clearing the box leaves the results of the query that was just erased up
            items = persistentListOf()
            return@LaunchedEffect
        }

        Database
            .search("%${textFieldValue.text}%")
            .collect { items = it.toImmutableList() }
    }

    val lazyListState = rememberLazyListState()

    val (currentMediaId, playing) = playingSong(binder)

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = items,
                key = Song::id
            ) { song ->
                SongItem(
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                menuState.display {
                                    InHistoryMediaItemMenu(
                                        song = song,
                                        onDismiss = menuState::hide
                                    )
                                }
                            },
                            onClick = {
                                val mediaItem = song.asMediaItem
                                binder?.stopRadio()
                                binder?.player?.forcePlay(mediaItem)
                                binder?.setupRadio(
                                    NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                )
                            }
                        )
                        .animateItem(),
                    song = song,
                    thumbnailSize = Dimensions.thumbnails.song,
                    // TODO: refactor out a simple 'song list' in order to fix this kind of repetition
                    isPlaying = playing && currentMediaId == song.id
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}
