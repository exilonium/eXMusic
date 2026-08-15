package app.exitune.android.ui.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.exitune.android.LocalPlayerServiceBinder
import app.exitune.android.ui.components.LocalMenuState
import app.exitune.android.ui.components.themed.NonQueuedMediaItemMenu
import app.exitune.android.utils.asMediaItem
import app.exitune.android.utils.forcePlay
import app.exitune.core.ui.Dimensions
import app.exitune.providers.innertube.Innertube

/**
 * One row of a search that hands back several kinds of result at once: songs and videos play
 * straight away, everything else opens its own screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InnertubeItem(
    item: Innertube.Item,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentMediaId: String? = null,
    playing: Boolean = false
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    when (item) {
        is Innertube.SongItem -> SongItem(
            song = item,
            thumbnailSize = Dimensions.thumbnails.song,
            modifier = modifier.combinedClickable(
                onLongClick = {
                    menuState.display {
                        NonQueuedMediaItemMenu(
                            onDismiss = menuState::hide,
                            mediaItem = item.asMediaItem
                        )
                    }
                },
                onClick = {
                    binder?.stopRadio()
                    binder?.player?.forcePlay(item.asMediaItem)
                    binder?.setupRadio(item.info?.endpoint)
                }
            ),
            isPlaying = playing && currentMediaId == item.key
        )

        is Innertube.VideoItem -> VideoItem(
            video = item,
            thumbnailWidth = 128.dp,
            thumbnailHeight = 72.dp,
            modifier = modifier.combinedClickable(
                onLongClick = {
                    menuState.display {
                        NonQueuedMediaItemMenu(
                            onDismiss = menuState::hide,
                            mediaItem = item.asMediaItem
                        )
                    }
                },
                onClick = {
                    binder?.stopRadio()
                    binder?.player?.forcePlay(item.asMediaItem)
                    binder?.setupRadio(item.info?.endpoint)
                }
            )
        )

        is Innertube.AlbumItem -> AlbumItem(
            album = item,
            thumbnailSize = Dimensions.thumbnails.album,
            modifier = modifier.clickable { onAlbumClick(item.key) }
        )

        is Innertube.ArtistItem -> ArtistItem(
            artist = item,
            thumbnailSize = 64.dp,
            modifier = modifier.clickable { onArtistClick(item.key) }
        )

        is Innertube.PlaylistItem -> PlaylistItem(
            playlist = item,
            thumbnailSize = Dimensions.thumbnails.playlist,
            modifier = modifier.clickable { onPlaylistClick(item.key) }
        )

        else -> Unit
    }
}
