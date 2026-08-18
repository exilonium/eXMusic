package app.exmusic.providers.innertube.utils

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.MusicTwoRowItemRenderer
import app.exmusic.providers.innertube.models.largest
import app.exmusic.providers.innertube.models.oddElements
import app.exmusic.providers.innertube.models.splitBySeparator

fun Innertube.AlbumItem.Companion.from(renderer: MusicTwoRowItemRenderer) = Innertube.AlbumItem(
    info = renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(Innertube::Info),
    authors = null,
    year = renderer
        .subtitle
        ?.runs
        ?.lastOrNull()
        ?.text,
    thumbnail = renderer
        .thumbnailRenderer
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.largest
).takeIf { it.info?.endpoint?.browseId != null }

fun Innertube.ArtistItem.Companion.from(renderer: MusicTwoRowItemRenderer) = Innertube.ArtistItem(
    info = renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(Innertube::Info),
    subscribersCountText = renderer
        .subtitle
        ?.runs
        ?.firstOrNull()
        ?.text,
    thumbnail = renderer
        .thumbnailRenderer
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.largest
).takeIf { it.info?.endpoint?.browseId != null }

/**
 * A song as a carousel tile rather than a list row. The subtitle is the artist line, and there is
 * no duration on a tile, so the two are read from what is there rather than assumed.
 */
fun Innertube.SongItem.Companion.from(renderer: MusicTwoRowItemRenderer) = Innertube.SongItem(
    info = renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(Innertube::Info),
    authors = renderer
        .subtitle
        ?.runs
        ?.splitBySeparator()
        ?.firstOrNull()
        ?.oddElements()
        ?.map(Innertube::Info),
    album = null,
    durationText = null,
    explicit = false,
    thumbnail = renderer
        .thumbnailRenderer
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.largest
).takeIf { it.info?.endpoint?.videoId != null }

fun Innertube.PlaylistItem.Companion.from(renderer: MusicTwoRowItemRenderer) =
    Innertube.PlaylistItem(
        info = renderer
            .title
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        channel = renderer
            .subtitle
            ?.runs
            ?.getOrNull(2)
            ?.let(Innertube::Info),
        songCount = renderer
            .subtitle
            ?.runs
            ?.getOrNull(4)
            ?.text
            ?.split(' ')
            ?.firstOrNull()
            ?.toIntOrNull(),
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.largest
    ).takeIf { it.info?.endpoint?.browseId != null }
