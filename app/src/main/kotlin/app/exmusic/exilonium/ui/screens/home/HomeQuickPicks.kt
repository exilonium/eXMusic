package app.exmusic.exilonium.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.exmusic.compose.persist.persist
import app.exmusic.core.ui.Dimensions
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.core.ui.utils.isLandscape
import app.exmusic.exilonium.Database
import app.exmusic.exilonium.LocalPlayerAwareWindowInsets
import app.exmusic.exilonium.LocalPlayerServiceBinder
import app.exmusic.exilonium.R
import app.exmusic.exilonium.models.Song
import app.exmusic.exilonium.preferences.AppearancePreferences
import app.exmusic.exilonium.preferences.DataPreferences
import app.exmusic.exilonium.query
import app.exmusic.exilonium.ui.components.LocalMenuState
import app.exmusic.exilonium.ui.components.ShimmerHost
import app.exmusic.exilonium.ui.components.screenBackground
import app.exmusic.exilonium.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.exmusic.exilonium.ui.components.themed.Header
import app.exmusic.exilonium.ui.components.themed.NonQueuedMediaItemMenu
import app.exmusic.exilonium.ui.components.themed.SecondaryTextButton
import app.exmusic.exilonium.ui.components.themed.TextPlaceholder
import app.exmusic.exilonium.ui.items.AlbumItem
import app.exmusic.exilonium.ui.items.AlbumItemPlaceholder
import app.exmusic.exilonium.ui.items.ArtistItem
import app.exmusic.exilonium.ui.items.ArtistItemPlaceholder
import app.exmusic.exilonium.ui.items.PlaylistItem
import app.exmusic.exilonium.ui.items.PlaylistItemPlaceholder
import app.exmusic.exilonium.ui.items.SongItem
import app.exmusic.exilonium.ui.items.SongItemPlaceholder
import app.exmusic.exilonium.ui.screens.Route
import app.exmusic.exilonium.utils.asMediaItem
import app.exmusic.exilonium.utils.center
import app.exmusic.exilonium.utils.forcePlayAtIndex
import app.exmusic.exilonium.utils.forcePlayFromBeginning
import app.exmusic.exilonium.utils.playingSong
import app.exmusic.exilonium.utils.rememberSnapLayoutInfo
import app.exmusic.exilonium.utils.secondary
import app.exmusic.exilonium.utils.semiBold
import app.exmusic.exilonium.utils.storeRelatedPage
import app.exmusic.providers.innertube.Innertube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val QUICK_PICKS_SIZE = 24
private const val DEFAULT_SEED_ID = "J7p4bzqLvCw"

private val defaultSeed = Song(
    id = DEFAULT_SEED_ID,
    title = "Blinding Lights",
    artistsText = "The Weeknd",
    durationText = null,
    thumbnailUrl = null
)

private val Innertube.RelatedPage.isEmpty
    get() = songs.isNullOrEmpty() &&
        albums.isNullOrEmpty() &&
        artists.isNullOrEmpty() &&
        playlists.isNullOrEmpty()

private fun Innertube.SongItem.asSong(): Song = Song(
    id = key,
    title = info?.name.orEmpty(),
    artistsText = authors?.joinToString("") { it.name.orEmpty() },
    durationText = durationText,
    thumbnailUrl = thumbnail?.url,
    explicit = explicit
)

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

    var seed by persist<Song?>("home/quickPicksSeed")
    var picks by persist<List<Song>?>("home/quickPicks")
    var relatedPage by persist<Innertube.RelatedPage?>("home/relatedPage")
    var failed by persist("home/quickPicksFailed", false)
    var settled by persist("home/quickPicksSettled", false)
    var removedSongIds by rememberSaveable { mutableStateOf(emptySet<String>()) }

    // The shelves are asked for once per seed rather than once per launch: what YouTube recommends
    // off a song does not change between two openings of the home screen, and the songs themselves
    // are read from the library, which the player keeps filling in the background
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val cached = DataPreferences.cachedQuickPicks
                if (relatedPage == null && !cached.isEmpty) relatedPage = cached

                val seedSong = Database.events().first().firstOrNull()?.song ?: defaultSeed
                val seedChanged = seedSong.id != seed?.id
                seed = seedSong

                val seedItem = seedSong.asMediaItem

                val known = runCatching { Database.relatedSongsCount(seedItem.mediaId) }.getOrDefault(0)
                if (known > 0 && !seedChanged && relatedPage != null && !relatedPage!!.isEmpty) return@withContext

                val result = storeRelatedPage(seedItem)
                result?.getOrNull()?.let { page ->
                    if (page.isEmpty) return@let

                    relatedPage = page
                    DataPreferences.cachedQuickPicks = page
                    failed = false
                } ?: run { failed = relatedPage == null }
            } finally {
                settled = true
            }
        }
    }

    // Whatever the network did, the grid is fed from the library: recommendations harvested off
    // recent plays and heavy rotation, topped up with favourites that have gone quiet
    LaunchedEffect(Unit) {
        combine(
            Database.quickPicks(),
            Database.forgottenFavorites()
        ) { related, forgotten -> (related + forgotten).distinctBy { it.id } }.collect { songs ->
            picks = songs
                .filterNot { AppearancePreferences.hideExplicit && it.explicit }
                .take(QUICK_PICKS_SIZE)
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

        val songs = remember(picks, relatedPage, AppearancePreferences.hideExplicit, removedSongIds) {
            val local = picks.orEmpty().filterNot { it.id in removedSongIds }
            val remote = relatedPage?.songs.orEmpty()
                .map { it.asSong() }
                .filterNot { AppearancePreferences.hideExplicit && it.explicit }
                .filterNot { it.id in removedSongIds }

            if (local.isEmpty()) {
                remote.take(QUICK_PICKS_SIZE)
            } else {
                val localIds = local.map { it.id }.toSet()
                val fill = remote.filterNot { it.id in localIds }
                (local + fill).take(QUICK_PICKS_SIZE)
            }
        }

        LaunchedEffect(songs) {
            if (songs.isNotEmpty()) {
                quickPicksLazyGridState.scrollToItem(0)
            }
        }
        val related = relatedPage
        val loading = !settled && songs.isEmpty() && related == null

        Column(
            modifier = Modifier
                .background(colorPalette.screenBackground)
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
            ) {
                if (songs.isNotEmpty()) {
                    SecondaryTextButton(
                        text = stringResource(R.string.play),
                        onClick = {
                            binder?.stopRadio()
                            binder?.player?.forcePlayFromBeginning(songs.map(Song::asMediaItem))
                        }
                    )
                }
            }

            if (loading) {
                ShimmerHost {
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
                                thumbnailSize = Dimensions.thumbnails.artist,
                                alternative = true
                            )
                        }
                    }

                    TextPlaceholder(modifier = sectionTextModifier)

                    Row {
                        repeat(2) {
                            PlaylistItemPlaceholder(
                                thumbnailSize = Dimensions.thumbnails.playlist,
                                alternative = true
                            )
                        }
                    }
                }
            } else if (songs.isNotEmpty()) {
                LazyHorizontalGrid(
                    state = quickPicksLazyGridState,
                    rows = GridCells.Fixed(4),
                    flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                    contentPadding = endPaddingValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((Dimensions.thumbnails.song + Dimensions.items.verticalPadding * 2) * 4)
                ) {
                    items(
                        items = songs,
                        key = Song::id
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
                                                mediaItem = song.asMediaItem,
                                                onRemoveFromQuickPicks = {
                                                    query {
                                                        Database.clearEventsFor(song.id)
                                                        Database.clearRelatedSongsFor(song.id)
                                                    }
                                                    picks = picks?.filterNot { it.id == song.id }
                                                    removedSongIds = removedSongIds + song.id
                                                }
                                            )
                                        }
                                    },
                                    onClick = {
                                        val index = songs.indexOfFirst { it.id == song.id }
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayAtIndex(
                                            items = songs.map(Song::asMediaItem),
                                            index = if (index >= 0) index else 0
                                        )
                                    }
                                )
                                .width(itemInHorizontalGridWidth),
                            showDuration = false,
                            isPlaying = playing && currentMediaId == song.id
                        )
                    }
                }
            }

            related?.albums?.let { albums ->
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

            related?.artists?.let { artists ->
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

            related?.playlists?.let { playlists ->
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

            if (!loading && songs.isEmpty() && related == null) {
                BasicText(
                    text = stringResource(
                        if (failed) R.string.error_message else R.string.quick_picks_nothing_yet
                    ),
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(all = 16.dp)
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(
            scrollState = scrollState,
            icon = R.drawable.search,
            onClick = onSearchClick
        )
    }
}
