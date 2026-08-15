@file:OptIn(UnstableApi::class)

package app.exmusic.exilonium.utils

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.exmusic.core.ui.utils.SongBundleAccessor
import app.exmusic.exilonium.R
import app.exmusic.exilonium.models.Song
import app.exmusic.exilonium.preferences.AppearancePreferences
import app.exmusic.exilonium.service.LOCAL_KEY_PREFIX
import app.exmusic.exilonium.service.isLocal
import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.bodies.ContinuationBody
import app.exmusic.providers.innertube.models.resizedThumbnail
import app.exmusic.providers.innertube.requests.playlistPage
import app.exmusic.providers.piped.models.Playlist
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlin.time.Duration

val Innertube.SongItem.asMediaItem: MediaItem
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info?.name)
                .setArtist(authors?.joinToString("") { it.name.orEmpty() })
                .setAlbumTitle(album?.name)
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    SongBundleAccessor.bundle {
                        albumId = album?.endpoint?.browseId
                        durationText = this@asMediaItem.durationText
                        artistNames = authors
                            ?.filter { it.endpoint != null }
                            ?.mapNotNull { it.name }
                        artistIds = authors?.mapNotNull { it.endpoint?.browseId }
                        explicit = this@asMediaItem.explicit
                    }
                )
                .build()
        )
        .build()

val Innertube.VideoItem.asMediaItem: MediaItem
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info?.name)
                .setArtist(authors?.joinToString("") { it.name.orEmpty() })
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    SongBundleAccessor.bundle {
                        durationText = this@asMediaItem.durationText
                        artistNames = if (isOfficialMusicVideo) authors
                            ?.filter { it.endpoint != null }
                            ?.mapNotNull { it.name }
                        else null
                        artistIds = if (isOfficialMusicVideo) authors
                            ?.mapNotNull { it.endpoint?.browseId }
                        else null
                    }
                )
                .build()
        )
        .build()

val Playlist.Video.asMediaItem: MediaItem?
    get() {
        val key = id ?: return null

        return MediaItem.Builder()
            .setMediaId(key)
            .setUri(key)
            .setCustomCacheKey(key)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(uploaderName)
                    .also {
                        runCatching { thumbnailUrl.toString().toUri() }.getOrNull()
                            ?.let { uri -> it.setArtworkUri(uri) }
                    }
                    .setExtras(
                        SongBundleAccessor.bundle {
                            durationText = duration.toComponents { minutes, seconds, _ ->
                                "$minutes:${seconds.toString().padStart(2, '0')}"
                            }
                            artistNames = listOf(uploaderName)
                            artistIds = uploaderId?.let { listOf(it) }
                        }
                    )
                    .build()
            )
            .build()
    }

val Song.asMediaItem: MediaItem
    get() = MediaItem.Builder()
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistsText)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setExtras(
                    SongBundleAccessor.bundle {
                        durationText = this@asMediaItem.durationText
                        explicit = this@asMediaItem.explicit
                    }
                )
                .build()
        )
        .setMediaId(id)
        .setUri(
            if (isLocal) ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id.substringAfter(LOCAL_KEY_PREFIX).toLong()
            ) else id.toUri()
        )
        .setCustomCacheKey(id)
        .build()

val Duration.formatted
    @Composable get() = toComponents { hours, minutes, _, _ ->
        when {
            hours == 0L -> stringResource(id = R.string.format_minutes, minutes)
            hours < 24L -> stringResource(id = R.string.format_hours, hours)
            else -> stringResource(id = R.string.format_days, hours / 24)
        }
    }

/**
 * Requests this Google image at [size].
 *
 * The url usually arrives carrying the size it was listed at — thumbnails come out of Innertube as
 * the smallest variant, tagged `=w60-h60-l90-rj` — so the directive has to replace that one rather
 * than follow it, or the CDN keeps serving the size it was first asked for.
 */
fun String?.thumbnail(
    size: Int,
    maxSize: Int = AppearancePreferences.maxThumbnailSize
) = this?.resizedThumbnail(size.coerceAtMost(maxSize))

fun Uri?.thumbnail(size: Int) = toString().thumbnail(size)?.toUri()

fun formatAsDuration(millis: Long) = DateUtils.formatElapsedTime(millis / 1000).removePrefix("0")

@Suppress("LoopWithTooManyJumpStatements")
suspend fun Result<Innertube.PlaylistOrAlbumPage>.completed(
    maxDepth: Int = Int.MAX_VALUE,
    shouldDedup: Boolean = false
) = runCatching {
    val page = getOrThrow()
    val songs = page.songsPage?.items.orEmpty().toMutableList()

    if (songs.isEmpty()) return@runCatching page

    var continuation = page.songsPage?.continuation
    var depth = 0

    val context = currentCoroutineContext()

    while (continuation != null && depth++ < maxDepth && context.isActive) {
        val newSongs = Innertube
            .playlistPage(
                body = ContinuationBody(continuation = continuation)
            )
            ?.getOrNull()
            ?.takeUnless { it.items.isNullOrEmpty() } ?: break

        if (shouldDedup && newSongs.items?.any { it in songs } != false) break

        newSongs.items?.let { songs += it }
        continuation = newSongs.continuation
    }

    page.copy(
        songsPage = Innertube.ItemsPage(
            items = songs,
            continuation = null
        )
    )
}.also { it.exceptionOrNull()?.printStackTrace() }

fun <T> Flow<T>.onFirst(block: suspend (T) -> Unit): Flow<T> {
    var isFirst = true

    return onEach {
        if (!isFirst) return@onEach

        block(it)
        isFirst = false
    }
}

inline fun <reified T : Throwable> Throwable.findCause(): T? {
    if (this is T) return this

    var th = cause
    while (th != null) {
        if (th is T) return th
        th = th.cause
    }

    return null
}
