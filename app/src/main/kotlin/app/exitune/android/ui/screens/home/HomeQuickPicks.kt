package app.exitune.android.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.exitune.android.Database
import app.exitune.android.LocalPlayerAwareWindowInsets
import app.exitune.android.LocalPlayerServiceBinder
import app.exitune.android.R
import app.exitune.android.models.Song
import app.exitune.android.preferences.DataPreferences
import app.exitune.android.query
import app.exitune.android.ui.components.LocalMenuState
import app.exitune.android.ui.components.ShimmerHost
import app.exitune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.exitune.android.ui.components.themed.Header
import app.exitune.android.ui.components.themed.NonQueuedMediaItemMenu
import app.exitune.android.ui.components.themed.TextPlaceholder
import app.exitune.android.ui.items.AlbumItem
import app.exitune.android.ui.items.AlbumItemPlaceholder
import app.exitune.android.ui.items.ArtistItem
import app.exitune.android.ui.items.ArtistItemPlaceholder
import app.exitune.android.ui.items.PlaylistItem
import app.exitune.android.ui.items.PlaylistItemPlaceholder
import app.exitune.android.ui.items.SongItem
import app.exitune.android.ui.items.SongItemPlaceholder
import app.exitune.android.ui.screens.Route
import app.exitune.android.utils.asMediaItem
import app.exitune.android.utils.center
import app.exitune.android.utils.forcePlay
import app.exitune.android.utils.playingSong
import app.exitune.android.utils.rememberSnapLayoutInfo
import app.exitune.android.utils.secondary
import app.exitune.android.utils.semiBold
import app.exitune.compose.persist.persist
import app.exitune.core.ui.Dimensions
import app.exitune.core.ui.LocalAppearance
import app.exitune.core.ui.utils.isLandscape
import app.exitune.providers.innertube.Innertube
import app.exitune.providers.innertube.models.NavigationEndpoint
import app.exitune.providers.innertube.models.bodies.NextBody
import app.exitune.providers.innertube.requests.relatedPage
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Route
@Composable
fun QuickPicks(
    onAlbumClick: (Innertube.AlbumItem) -> Unit,
    onArtistClick: (Innertube.ArtistItem) -> Unit,
    onPlaylistClick: (Innertube.PlaylistItem) -> Unit,
    onSearchClick: () -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var trending by persist<Song?>("home/trending")

    var relatedPageResult by persist<Result<Innertube.RelatedPage?>?>(tag = "home/relatedPageResult")

    LaunchedEffect(relatedPageResult, DataPreferences.shouldCacheQuickPicks) {
        if (DataPreferences.shouldCacheQuickPicks)
            relatedPageResult?.getOrNull()?.let { DataPreferences.cachedQuickPicks = it }
        else DataPreferences.cachedQuickPicks = Innertube.RelatedPage()
    }

    LaunchedEffect(DataPreferences.quickPicksSource) {
        if (
            DataPreferences.shouldCacheQuickPicks && !DataPreferences.cachedQuickPicks.let {
                it.albums.isNullOrEmpty() &&
                    it.artists.isNullOrEmpty() &&
                    it.playlists.isNullOrEmpty() &&
                    it.songs.isNullOrEmpty()
            }
        ) relatedPageResult = Result.success(DataPreferences.cachedQuickPicks)

        suspend fun handleSong(song: Song?) {
            if (relatedPageResult == null || trending?.id != song?.id) relatedPageResult =
                Innertube.relatedPage(
                    body = NextBody(videoId = (song?.id ?: "J7p4bzqLvCw"))
                )
            trending = song
        }

        when (DataPreferences.quickPicksSource) {
            DataPreferences.QuickPicksSource.Trending ->
                Database
                    .trending()
                    .distinctUntilChanged()
                    .collect { handleSong(it.firstOrNull()) }

            DataPreferences.QuickPicksSource.LastInteraction ->
                Database
                    .events()
                    .distinctUntilChanged()
                    .collect { handleSong(it.firstOrNull()?.song) }
        }
    }

    val scrollState = rememberScrollState()
    val quickPicksLazyGridState = rememberLazyGridState()

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    val (currentMediaId, playing) = playingSong(binder)

    BoxWithConstraints {
        val quickPicksLazyGridItemWidthFactor =
            if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.475f else 0.75f

        val snapLayoutInfoProvider = rememberSnapLayoutInfo(
            lazyGridState = quickPicksLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize * quickPicksLazyGridItemWidthFactor / 2f - itemSize / 2f)
            }
        )

        val itemInHorizontalGridWidth = maxWidth * quickPicksLazyGridItemWidthFactor

        Column(
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    windowInsets
                        .only(WindowInsetsSides.Vertical)
                        .asPaddingValues()
                )
        ) {
            Header(
                title = stringResource(R.string.quick_picks),
                modifier = Modifier.padding(endPaddingValues)
            )

            relatedPageResult?.getOrNull()?.let { related ->
                LazyHorizontalGrid(
                    state = quickPicksLazyGridState,
                    rows = GridCells.Fixed(4),
                    flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                    contentPadding = endPaddingValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((Dimensions.thumbnails.song + Dimensions.items.verticalPadding * 2) * 4)
                ) {
                    trending?.let { song ->
                        item {
                            SongItem(
                                modifier = Modifier
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    onDismiss = menuState::hide,
                                                    mediaItem = song.asMediaItem,
                                                    onRemoveFromQuickPicks = {
                                                        query {
                                                            Database.clearEventsFor(song.id)
                                                        }
                                                    }
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
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                    .width(itemInHorizontalGridWidth),
                                song = song,
                                thumbnailSize = Dimensions.thumbnails.song,
                                trailingContent = {
                                    Image(
                                        painter = painterResource(R.drawable.star),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette.accent),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                showDuration = false,
                                isPlaying = playing && currentMediaId == song.id
                            )
                        }
                    }

                    items(
                        items = related.songs?.dropLast(if (trending == null) 0 else 1)
                            ?: emptyList(),
                        key = Innertube.SongItem::key
                    ) { song ->
                        SongItem(
                            song = song,
                            thumbnailSize = Dimensions.thumbnails.song,
                            modifier = Modifier
                                .combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                onDismiss = menuState::hide,
                                                mediaItem = song.asMediaItem
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
                                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                .width(itemInHorizontalGridWidth),
                            showDuration = false,
                            isPlaying = playing && currentMediaId == song.key
                        )
                    }
                }

                related.albums?.let { albums ->
                    BasicText(
                        text = stringResource(R.string.related_albums),
                        style = typography.m.semiBold,
                        modifier = sectionTextModifier
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = albums,
                            key = Innertube.AlbumItem::key
                        ) { album ->
                            AlbumItem(
                                album = album,
                                thumbnailSize = Dimensions.thumbnails.album,
                                alternative = true,
                                modifier = Modifier.clickable { onAlbumClick(album) }
                            )
                        }
                    }
                }

                related.artists?.let { artists ->
                    BasicText(
                        text = stringResource(R.string.similar_artists),
                        style = typography.m.semiBold,
                        modifier = sectionTextModifier
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = artists,
                            key = Innertube.ArtistItem::key
                        ) { artist ->
                            ArtistItem(
                                artist = artist,
                                thumbnailSize = Dimensions.thumbnails.artist,
                                alternative = true,
                                modifier = Modifier.clickable { onArtistClick(artist) }
                            )
                        }
                    }
                }

                related.playlists?.let { playlists ->
                    BasicText(
                        text = stringResource(R.string.recommended_playlists),
                        style = typography.m.semiBold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 8.dp)
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = playlists,
                            key = Innertube.PlaylistItem::key
                        ) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSize = Dimensions.thumbnails.playlist,
                                alternative = true,
                                modifier = Modifier.clickable { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }

                Unit
            } ?: relatedPageResult?.exceptionOrNull()?.let {
                BasicText(
                    text = stringResource(R.string.error_message),
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(all = 16.dp)
                )
            } ?: ShimmerHost {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        AlbumItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        ArtistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        PlaylistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(
            scrollState = scrollState,
            icon = R.drawable.search,
            onClick = onSearchClick
        )
    }
}
