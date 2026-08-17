package app.exmusic.exilonium.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.exmusic.core.ui.Dimensions
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.core.ui.utils.isLandscape
import app.exmusic.exilonium.LocalPlayerAwareWindowInsets
import app.exmusic.exilonium.LocalPlayerServiceBinder
import app.exmusic.exilonium.R
import app.exmusic.exilonium.ui.components.LocalMenuState
import app.exmusic.exilonium.ui.components.ShimmerHost
import app.exmusic.exilonium.ui.components.screenBackground
import app.exmusic.exilonium.ui.components.themed.Attribution
import app.exmusic.exilonium.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.exmusic.exilonium.ui.components.themed.LayoutWithAdaptiveThumbnail
import app.exmusic.exilonium.ui.components.themed.NonQueuedMediaItemMenu
import app.exmusic.exilonium.ui.components.themed.SecondaryTextButton
import app.exmusic.exilonium.ui.components.themed.TextPlaceholder
import app.exmusic.exilonium.ui.items.AlbumItem
import app.exmusic.exilonium.ui.items.AlbumItemPlaceholder
import app.exmusic.exilonium.ui.items.SongItem
import app.exmusic.exilonium.ui.items.SongItemPlaceholder
import app.exmusic.exilonium.utils.asMediaItem
import app.exmusic.exilonium.utils.forcePlay
import app.exmusic.exilonium.utils.medium
import app.exmusic.exilonium.utils.playingSong
import app.exmusic.exilonium.utils.secondary
import app.exmusic.exilonium.utils.semiBold
import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.NavigationEndpoint

private val sectionTextModifier = Modifier
    .padding(horizontal = 16.dp)
    .padding(top = 24.dp, bottom = 8.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistOverview(
    youtubeArtistPage: Innertube.ArtistPage?,
    onViewAllSongsClick: () -> Unit,
    onViewAllAlbumsClick: () -> Unit,
    onViewAllSinglesClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    thumbnailContent: @Composable () -> Unit,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    modifier: Modifier = Modifier
) = LayoutWithAdaptiveThumbnail(
    thumbnailContent = thumbnailContent,
    modifier = modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val scrollState = rememberScrollState()

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
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
            Box(modifier = Modifier.padding(endPaddingValues)) {
                headerContent {
                    youtubeArtistPage?.shuffleEndpoint?.let { endpoint ->
                        SecondaryTextButton(
                            text = stringResource(R.string.shuffle),
                            onClick = {
                                binder?.stopRadio()
                                binder?.playRadio(endpoint)
                            }
                        )
                    }
                    youtubeArtistPage?.subscribersCountText?.let { subscribers ->
                        BasicText(
                            text = stringResource(R.string.format_subscribers, subscribers),
                            style = typography.xxs.medium
                        )
                    }
                }
            }

            if (!isLandscape) thumbnailContent()

            youtubeArtistPage?.let { artist ->
                artist.songs?.let { songs ->
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(endPaddingValues)
                    ) {
                        BasicText(
                            text = stringResource(R.string.songs),
                            style = typography.m.semiBold,
                            modifier = sectionTextModifier
                        )

                        artist.songsEndpoint?.let {
                            BasicText(
                                text = stringResource(R.string.view_all),
                                style = typography.xs.secondary,
                                modifier = sectionTextModifier.clickable(onClick = onViewAllSongsClick)
                            )
                        }
                    }

                    val (currentMediaId, playing) = playingSong(binder)

                    songs.forEach { song ->
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
                                .padding(endPaddingValues),
                            isPlaying = playing && currentMediaId == song.key
                        )
                    }
                }

                artist.albums?.let { albums ->
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(endPaddingValues)
                    ) {
                        BasicText(
                            text = stringResource(R.string.albums),
                            style = typography.m.semiBold,
                            modifier = sectionTextModifier
                        )

                        artist.albumsEndpoint?.let {
                            BasicText(
                                text = stringResource(R.string.view_all),
                                style = typography.xs.secondary,
                                modifier = sectionTextModifier.clickable(onClick = onViewAllAlbumsClick)
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = endPaddingValues,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = albums,
                            key = Innertube.AlbumItem::key
                        ) { album ->
                            AlbumItem(
                                album = album,
                                thumbnailSize = Dimensions.thumbnails.album,
                                alternative = true,
                                modifier = Modifier.clickable {
                                    onAlbumClick(album.key)
                                }
                            )
                        }
                    }
                }

                artist.singles?.let { singles ->
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(endPaddingValues)
                    ) {
                        BasicText(
                            text = stringResource(R.string.singles),
                            style = typography.m.semiBold,
                            modifier = sectionTextModifier
                        )

                        artist.singlesEndpoint?.let {
                            BasicText(
                                text = stringResource(R.string.view_all),
                                style = typography.xs.secondary,
                                modifier = sectionTextModifier.clickable(onClick = onViewAllSinglesClick)
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = endPaddingValues,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = singles,
                            key = Innertube.AlbumItem::key
                        ) { album ->
                            AlbumItem(
                                album = album,
                                thumbnailSize = Dimensions.thumbnails.album,
                                alternative = true,
                                modifier = Modifier.clickable(onClick = { onAlbumClick(album.key) })
                            )
                        }
                    }
                }

                artist.description?.let { description ->
                    Attribution(
                        text = description,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .padding(vertical = 16.dp, horizontal = 8.dp)
                    )
                }

                Unit
            } ?: ArtistOverviewBodyPlaceholder()
        }

        youtubeArtistPage?.radioEndpoint?.let { endpoint ->
            FloatingActionsContainerWithScrollToTop(
                scrollState = scrollState,
                icon = R.drawable.radio,
                onClick = {
                    binder?.stopRadio()
                    binder?.playRadio(endpoint)
                }
            )
        }
    }
}

@Composable
fun ArtistOverviewBodyPlaceholder(modifier: Modifier = Modifier) = ShimmerHost(
    modifier = modifier
) {
    TextPlaceholder(modifier = sectionTextModifier)

    repeat(5) {
        SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
    }

    repeat(2) {
        TextPlaceholder(modifier = sectionTextModifier)

        Row {
            repeat(2) {
                AlbumItemPlaceholder(
                    thumbnailSize = Dimensions.thumbnails.album,
                    alternative = true
                )
            }
        }
    }
}
