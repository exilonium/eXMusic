package app.exmusic.providers.innertube.requests

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.BrowseResponse
import app.exmusic.providers.innertube.models.Context
import app.exmusic.providers.innertube.models.NextResponse
import app.exmusic.providers.innertube.models.bodies.BrowseBody
import app.exmusic.providers.innertube.models.bodies.NextBody
import app.exmusic.providers.innertube.utils.from
import app.exmusic.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

private const val RELATED_BROWSE_PREFIX = "MPTRt"

suspend fun Innertube.relatedPage(body: NextBody) = runCatchingCancellable {
    val nextResponse = client.post(NEXT) {
        setBody(body.copy(context = Context.DefaultWebNoLang))
        Context.DefaultWebNoLang.apply()
        @Suppress("all")
        mask(
            "contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)"
        )
    }.body<NextResponse>()

    val tabs = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        .orEmpty()
        .mapNotNull { it.tabRenderer }

    // The related tab used to be the third one, but YouTube varies how many tabs it sends, so
    // identify it by its browse id instead of its position
    val browseId = tabs
        .firstOrNull { it.endpoint?.browseEndpoint?.browseId?.startsWith(RELATED_BROWSE_PREFIX) == true }
        ?.endpoint
        ?.browseEndpoint
        ?.browseId
        ?: tabs
            .lastOrNull { it.endpoint?.browseEndpoint?.browseId != null }
            ?.endpoint
            ?.browseEndpoint
            ?.browseId
        // Reporting this as a failure rather than an empty page: callers cannot tell a null
        // payload from "still loading", and would wait on it forever
        ?: error("No related tab in the watch next response for ${body.videoId}")

    val response = client.post(BROWSE) {
        setBody(
            BrowseBody(
                browseId = browseId,
                context = Context.DefaultWebNoLang
            )
        )
        Context.DefaultWebNoLang.apply()
        @Suppress("all")
        mask(
            "contents.sectionListRenderer.contents.musicCarouselShelfRenderer.contents($MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK,$MUSIC_TWO_ROW_ITEM_RENDERER_MASK)"
        )
    }.body<BrowseResponse>()

    val shelves = response
        .contents
        ?.sectionListRenderer
        ?.contents
        ?.mapNotNull { it.musicCarouselShelfRenderer }
        .orEmpty()

    val songs = mutableListOf<Innertube.SongItem>()
    val albums = mutableListOf<Innertube.AlbumItem>()
    val artists = mutableListOf<Innertube.ArtistItem>()
    val playlists = mutableListOf<Innertube.PlaylistItem>()

    // Shelves are picked apart by what their items are, not by their headers: YouTube translates
    // the headers per region, so matching on "You might also like" empties the page for anyone
    // outside an English locale
    shelves.forEach { shelf ->
        shelf.contents?.forEach { content ->
            content.musicResponsiveListItemRenderer?.let { row ->
                Innertube.SongItem.from(row)?.let(songs::add)
            }

            content.musicTwoRowItemRenderer?.let { tile ->
                when {
                    tile.isAlbum -> Innertube.AlbumItem.from(tile)?.let(albums::add)
                    tile.isArtist -> Innertube.ArtistItem.from(tile)?.let(artists::add)
                    tile.isPlaylist -> Innertube.PlaylistItem.from(tile)?.let(playlists::add)
                    tile.isSong -> Innertube.SongItem.from(tile)?.let(songs::add)
                }
            }
        }
    }

    Innertube.RelatedPage(
        songs = songs.distinctBy { it.key }.takeIf { it.isNotEmpty() },
        albums = albums.distinctBy { it.key }.takeIf { it.isNotEmpty() },
        artists = artists.distinctBy { it.key }.takeIf { it.isNotEmpty() },
        playlists = playlists
            .distinctBy { it.key }
            .sortedByDescending { it.channel?.name == "YouTube Music" }
            .takeIf { it.isNotEmpty() }
    )
}
