package app.exmusic.providers.innertube.utils

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.MusicCardShelfRenderer
import app.exmusic.providers.innertube.models.MusicShelfRenderer
import app.exmusic.providers.innertube.models.NavigationEndpoint
import app.exmusic.providers.innertube.models.Runs
import app.exmusic.providers.innertube.models.isExplicit
import app.exmusic.providers.innertube.models.largest
import app.exmusic.providers.innertube.models.videoThumbnail

private const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
private const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
private const val PAGE_TYPE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
private const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"

/**
 * A search that is not filtered by kind hands every kind back in one list, with the kind spelled
 * out as the first token of the subtitle ("Song • Daft Punk", "Album • Daft Punk • 2013") and
 * without a duration column. The mappers used for filtered searches count runs from the end and so
 * read the wrong ones here, hence this separate set.
 */
fun itemFromSearchResult(
    content: MusicShelfRenderer.Content,
    fallbackAuthors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>? = null
): Innertube.Item? = runCatching {
    val renderer = content.musicResponsiveListItemRenderer ?: return@runCatching null

    val (mainRuns, otherRuns) = content.runs
    val title = mainRuns.firstOrNull() ?: return@runCatching null
    val thumbnail = content.thumbnail

    when (renderer.navigationEndpoint?.browseEndpoint?.type) {
        PAGE_TYPE_ALBUM -> Innertube.AlbumItem(
            info = Innertube.Info(
                name = title.text,
                endpoint = renderer.navigationEndpoint.browseEndpoint
            ),
            authors = otherRuns.byLine?.map(Innertube::Info),
            year = otherRuns.lastToken?.takeIf { it.toIntOrNull() != null },
            thumbnail = thumbnail
        ).takeIf { it.info?.endpoint?.browseId != null }

        PAGE_TYPE_ARTIST -> Innertube.ArtistItem(
            info = Innertube.Info(
                name = title.text,
                endpoint = renderer.navigationEndpoint.browseEndpoint
            ),
            subscribersCountText = otherRuns.lastToken,
            thumbnail = thumbnail
        ).takeIf { it.info?.endpoint?.browseId != null }

        PAGE_TYPE_PLAYLIST -> Innertube.PlaylistItem(
            info = Innertube.Info(
                name = title.text,
                endpoint = renderer.navigationEndpoint.browseEndpoint
            ),
            channel = otherRuns.byLine?.firstOrNull()?.let(Innertube::Info),
            songCount = otherRuns.lastToken
                ?.split(' ')
                ?.firstOrNull()
                ?.toIntOrNull(),
            thumbnail = thumbnail
        ).takeIf { it.info?.endpoint?.browseId != null }

        else -> {
            val endpoint = title.navigationEndpoint?.watchEndpoint?.takeIf { it.videoId != null }
                ?: return@runCatching null
            val info = Innertube.Info(name = title.text, endpoint = endpoint)
            val authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>? =
                otherRuns.byLine?.map(Innertube::Info) ?: fallbackAuthors
            val durationText = renderer
                .fixedColumns
                ?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.firstOrNull()
                ?.text
                ?: otherRuns.lastToken?.takeIf { ':' in it }

            val isVideo = endpoint
                .watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType
                ?.let { it != MUSIC_VIDEO_TYPE_ATV } == true

            if (isVideo) Innertube.VideoItem(
                info = info,
                authors = authors,
                viewsText = otherRuns.lastToken?.takeIf { ':' !in it },
                durationText = durationText,
                thumbnail = endpoint.videoId?.let(::videoThumbnail) ?: thumbnail
            ) else Innertube.SongItem(
                info = info,
                authors = authors,
                album = renderer
                    .flexColumns
                    .getOrNull(2)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.takeIf { it.navigationEndpoint?.browseEndpoint?.type == PAGE_TYPE_ALBUM }
                    ?.let(Innertube::Info),
                durationText = durationText,
                explicit = renderer.badges.isExplicit,
                thumbnail = thumbnail
            )
        }
    }
}.getOrNull()

/**
 * The top result of an unfiltered search, which is laid out as a card rather than as a row.
 */
fun itemFromSearchResult(card: MusicCardShelfRenderer): Innertube.Item? = runCatching {
    val title = card.title?.runs?.firstOrNull() ?: return@runCatching null
    val subtitle = card.subtitle?.splitBySeparator().orEmpty()
    val thumbnail = card
        .thumbnail
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.largest

    val browseEndpoint = card.onTap?.browseEndpoint ?: title.navigationEndpoint?.browseEndpoint

    when (browseEndpoint?.type) {
        PAGE_TYPE_ALBUM -> Innertube.AlbumItem(
            info = Innertube.Info(name = title.text, endpoint = browseEndpoint),
            authors = subtitle.byLine?.map(Innertube::Info),
            year = subtitle.lastToken?.takeIf { it.toIntOrNull() != null },
            thumbnail = thumbnail
        ).takeIf { it.info?.endpoint?.browseId != null }

        PAGE_TYPE_ARTIST -> Innertube.ArtistItem(
            info = Innertube.Info(name = title.text, endpoint = browseEndpoint),
            subscribersCountText = subtitle.lastToken,
            thumbnail = thumbnail
        ).takeIf { it.info?.endpoint?.browseId != null }

        PAGE_TYPE_PLAYLIST -> Innertube.PlaylistItem(
            info = Innertube.Info(name = title.text, endpoint = browseEndpoint),
            channel = subtitle.byLine?.firstOrNull()?.let(Innertube::Info),
            songCount = subtitle.lastToken?.split(' ')?.firstOrNull()?.toIntOrNull(),
            thumbnail = thumbnail
        ).takeIf { it.info?.endpoint?.browseId != null }

        else -> {
            val endpoint = (card.onTap?.watchEndpoint ?: title.navigationEndpoint?.watchEndpoint)
                ?.takeIf { it.videoId != null }
                ?: return@runCatching null
            val info = Innertube.Info(name = title.text, endpoint = endpoint)
            val authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>? =
                subtitle.byLine?.map(Innertube::Info)

            val isVideo = endpoint
                .watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType
                ?.let { it != MUSIC_VIDEO_TYPE_ATV } == true

            if (isVideo) Innertube.VideoItem(
                info = info,
                authors = authors,
                viewsText = subtitle.lastToken?.takeIf { ':' !in it },
                durationText = subtitle.lastToken?.takeIf { ':' in it },
                thumbnail = endpoint.videoId?.let(::videoThumbnail) ?: thumbnail
            ) else Innertube.SongItem(
                info = info,
                authors = authors,
                album = null,
                durationText = subtitle.lastToken?.takeIf { ':' in it },
                explicit = false,
                thumbnail = thumbnail
            )
        }
    }
}.getOrNull()

/**
 * The subtitle group that names whoever made the item, i.e. the first one that links to a channel.
 * Falls back to the group right after the kind ("Song • …") for the rows that spell the by-line out
 * without linking it, but never to a duration: the items listed under a top result carry
 * "Song • 3:52" and no artist at all, since the card above them is the artist.
 */
private val List<List<Runs.Run>>.byLine
    get() = firstOrNull { runs ->
        runs.any { it.navigationEndpoint?.browseEndpoint != null }
    } ?: getOrNull(1)?.takeUnless { runs -> runs.any { ':' in it.text.orEmpty() } }

private val List<List<Runs.Run>>.lastToken
    get() = lastOrNull()?.lastOrNull()?.text
