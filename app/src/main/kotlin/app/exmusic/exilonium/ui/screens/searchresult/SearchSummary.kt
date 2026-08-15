package app.exmusic.exilonium.ui.screens.searchresult

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.exmusic.exilonium.LocalPlayerAwareWindowInsets
import app.exmusic.exilonium.LocalPlayerServiceBinder
import app.exmusic.exilonium.R
import app.exmusic.exilonium.ui.components.ShimmerHost
import app.exmusic.exilonium.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.exmusic.exilonium.ui.items.InnertubeItem
import app.exmusic.exilonium.ui.items.SongItemPlaceholder
import app.exmusic.exilonium.utils.center
import app.exmusic.exilonium.utils.playingSong
import app.exmusic.exilonium.utils.secondary
import app.exmusic.exilonium.utils.semiBold
import app.exmusic.compose.persist.persist
import app.exmusic.core.ui.Dimensions
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.bodies.SearchBody
import app.exmusic.providers.innertube.requests.searchSummaryPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PLACEHOLDER_COUNT = 8

/** Lines the rows up with the section titles, which sit at the list's own 16dp. */
private val ROW_PADDING = 8.dp

/**
 * Everything YouTube found for the query on one page - the top result first, then the songs, then
 * the rest - rather than one kind per tab. Songs play from here, the others open their own screen.
 */
@Composable
fun SearchSummary(
    query: String,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val (_, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    var summary by persist<Result<Innertube.SearchSummaryPage>?>("searchResults/$query/summary")

    LaunchedEffect(query) {
        if (summary?.getOrNull() != null) return@LaunchedEffect

        summary = withContext(Dispatchers.IO) {
            Innertube.searchSummaryPage(body = SearchBody(query = query))
        }
    }

    val lazyListState = rememberLazyListState()
    val (currentMediaId, playing) = playingSong(binder)

    val sections = summary?.getOrNull()?.sections.orEmpty()

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = "header"
            ) {
                header()
            }

            sections.forEach { section ->
                item(
                    key = "title/${section.type}",
                    contentType = "title"
                ) {
                    BasicText(
                        text = stringResource(section.type.titleId),
                        style = typography.m.semiBold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 12.dp)
                    )
                }

                items(
                    items = section.items,
                    key = { "${section.type}/${it.key}" }
                ) { item ->
                    InnertubeItem(
                        item = item,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        onPlaylistClick = onPlaylistClick,
                        modifier = Modifier.padding(horizontal = ROW_PADDING),
                        currentMediaId = currentMediaId,
                        playing = playing
                    )
                }
            }

            if (summary != null && sections.isEmpty()) item(key = "empty") {
                BasicText(
                    text = stringResource(
                        if (summary?.isFailure == true) R.string.error_message
                        else R.string.no_search_results
                    ),
                    style = typography.xs.secondary.center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 32.dp)
                        .fillMaxWidth()
                )
            }

            if (summary == null) item(key = "loading") {
                ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                    repeat(PLACEHOLDER_COUNT) {
                        SongItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.song,
                            modifier = Modifier.padding(horizontal = ROW_PADDING)
                        )
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

private val Innertube.SearchSummaryPage.Type.titleId
    get() = when (this) {
        Innertube.SearchSummaryPage.Type.TopResult -> R.string.top_result
        Innertube.SearchSummaryPage.Type.Songs -> R.string.songs
        Innertube.SearchSummaryPage.Type.Videos -> R.string.videos
        Innertube.SearchSummaryPage.Type.Albums -> R.string.albums
        Innertube.SearchSummaryPage.Type.Artists -> R.string.artists
        Innertube.SearchSummaryPage.Type.Playlists -> R.string.playlists
    }
