package app.exmusic.exilonium.ui.screens.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.exmusic.core.ui.Dimensions
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.core.ui.utils.isLandscape
import app.exmusic.exilonium.LocalPlayerAwareWindowInsets
import app.exmusic.exilonium.LocalPlayerServiceBinder
import app.exmusic.exilonium.R
import app.exmusic.exilonium.models.Song
import app.exmusic.exilonium.ui.components.LocalMenuState
import app.exmusic.exilonium.ui.components.ShimmerHost
import app.exmusic.exilonium.ui.components.screenBackground
import app.exmusic.exilonium.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.exmusic.exilonium.ui.components.themed.LayoutWithAdaptiveThumbnail
import app.exmusic.exilonium.ui.components.themed.NonQueuedMediaItemMenu
import app.exmusic.exilonium.ui.components.themed.SecondaryTextButton
import app.exmusic.exilonium.ui.items.SongItem
import app.exmusic.exilonium.ui.items.SongItemPlaceholder
import app.exmusic.exilonium.utils.PlaylistDownloadIcon
import app.exmusic.exilonium.utils.asMediaItem
import app.exmusic.exilonium.utils.enqueue
import app.exmusic.exilonium.utils.forcePlayAtIndex
import app.exmusic.exilonium.utils.forcePlayFromBeginning
import app.exmusic.exilonium.utils.playingSong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// TODO: migrate to single impl for all 'song lists'
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumSongs(
    songs: ImmutableList<Song>,
    headerContent: @Composable (
        beforeContent: (@Composable () -> Unit)?,
        afterContent: (@Composable () -> Unit)?
    ) -> Unit,
    thumbnailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    afterHeaderContent: (@Composable () -> Unit)? = null
) = LayoutWithAdaptiveThumbnail(
    thumbnailContent = thumbnailContent,
    modifier = modifier
) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val lazyListState = rememberLazyListState()

    val (currentMediaId, playing) = playingSong(binder)

    Box {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier
                .background(colorPalette.screenBackground)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    headerContent(
                        {
                            SecondaryTextButton(
                                text = stringResource(R.string.enqueue),
                                enabled = songs.isNotEmpty(),
                                onClick = {
                                    binder?.player?.enqueue(songs.map(Song::asMediaItem))
                                }
                            )
                        },
                        {
                            PlaylistDownloadIcon(
                                songs = songs.map(Song::asMediaItem).toImmutableList()
                            )
                        }
                    )

                    if (!isLandscape) thumbnailContent()
                    afterHeaderContent?.invoke()
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, song -> song.id }
            ) { index, song ->
                SongItem(
                    song = song,
                    index = index,
                    thumbnailSize = Dimensions.thumbnails.song,
                    modifier = Modifier.combinedClickable(
                        onLongClick = {
                            menuState.display {
                                NonQueuedMediaItemMenu(
                                    onDismiss = menuState::hide,
                                    mediaItem = song.asMediaItem
                                )
                            }
                        },
                        onClick = {
                            binder?.stopRadio()
                            binder?.player?.forcePlayAtIndex(
                                items = songs.map(Song::asMediaItem),
                                index = index
                            )
                        }
                    ),
                    isPlaying = playing && currentMediaId == song.id
                )
            }

            if (songs.isEmpty()) item(key = "loading") {
                ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                    repeat(4) {
                        SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                if (songs.isNotEmpty()) {
                    binder?.stopRadio()
                    binder?.player?.forcePlayFromBeginning(
                        songs.shuffled().map(Song::asMediaItem)
                    )
                }
            }
        )
    }
}
