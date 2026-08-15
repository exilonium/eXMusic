package app.exmusic.exilonium.ui.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.Dp
import androidx.media3.common.MediaItem
import app.exmusic.exilonium.LocalPlayerServiceBinder
import app.exmusic.exilonium.R
import app.exmusic.exilonium.ui.components.LocalMenuState
import app.exmusic.exilonium.ui.components.themed.NonQueuedMediaItemMenu
import app.exmusic.exilonium.utils.asMediaItem
import app.exmusic.exilonium.utils.forcePlay
import app.exmusic.core.ui.Dimensions
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.core.ui.utils.px
import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.NavigationEndpoint
import coil3.compose.AsyncImage

/**
 * One row of a search that hands back several kinds of result at once: songs and videos play
 * straight away, everything else opens its own screen.
 *
 * Every kind is drawn as the same row - one thumbnail size, one title line, one line under it -
 * rather than in the layout each kind uses on a screen of its own. A list that mixes an album, an
 * artist and a video is only readable if the rows line up.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InnertubeItem(
    item: Innertube.Item,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailSize: Dp = Dimensions.thumbnails.song,
    currentMediaId: String? = null,
    playing: Boolean = false
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    fun playable(
        mediaItem: () -> MediaItem,
        endpoint: () -> NavigationEndpoint.Endpoint.Watch?
    ) = modifier.combinedClickable(
        onLongClick = {
            menuState.display {
                NonQueuedMediaItemMenu(
                    onDismiss = menuState::hide,
                    mediaItem = mediaItem()
                )
            }
        },
        onClick = {
            binder?.stopRadio()
            binder?.player?.forcePlay(mediaItem())
            binder?.setupRadio(endpoint())
        }
    )

    when (item) {
        is Innertube.SongItem -> SongItem(
            song = item,
            thumbnailSize = thumbnailSize,
            modifier = playable({ item.asMediaItem }, { item.info?.endpoint }),
            isPlaying = playing && currentMediaId == item.key
        )

        is Innertube.VideoItem -> SongItem(
            title = item.info?.name,
            authors = item.authors?.joinToString("") { it.name.orEmpty() },
            // a search rarely gives a video a duration, and puts the view count there instead
            duration = item.durationText ?: item.viewsText,
            explicit = false,
            thumbnailSize = thumbnailSize,
            // a video's artwork is a 16:9 frame: cropping it to the square every other row uses
            // leaves the middle third and little else, so it is fitted instead
            thumbnailContent = {
                Box(
                    modifier = Modifier
                        .clip(LocalAppearance.current.thumbnailShape)
                        .background(LocalAppearance.current.colorPalette.background1)
                        .fillMaxSize()
                ) {
                    AsyncImage(
                        model = item.thumbnail?.url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            modifier = playable({ item.asMediaItem }, { item.info?.endpoint }),
            isPlaying = playing && currentMediaId == item.key
        )

        is Innertube.AlbumItem -> SongItem(
            thumbnailUrl = item.thumbnail?.size(thumbnailSize.px),
            title = item.info?.name,
            authors = item.authors?.joinToString("") { it.name.orEmpty() },
            duration = item.year,
            explicit = false,
            thumbnailSize = thumbnailSize,
            modifier = modifier.clickable { onAlbumClick(item.key) }
        )

        is Innertube.PlaylistItem -> SongItem(
            thumbnailUrl = item.thumbnail?.size(thumbnailSize.px),
            title = item.info?.name,
            authors = item.channel?.name,
            duration = item.songCount?.let {
                pluralStringResource(R.plurals.song_count_plural, it, it)
            },
            explicit = false,
            thumbnailSize = thumbnailSize,
            modifier = modifier.clickable { onPlaylistClick(item.key) }
        )

        is Innertube.ArtistItem -> SongItem(
            title = item.info?.name,
            authors = item.subscribersCountText,
            duration = null,
            explicit = false,
            thumbnailSize = thumbnailSize,
            // the round thumbnail is all that sets an artist apart here, as it is on YouTube Music
            thumbnailContent = {
                AsyncImage(
                    model = item.thumbnail?.size(thumbnailSize.px),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .clip(CircleShape)
                        .fillMaxSize()
                )
            },
            modifier = modifier.clickable { onArtistClick(item.key) }
        )

        else -> Unit
    }
}
