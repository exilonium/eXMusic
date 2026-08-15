package app.exmusic.providers.innertube.requests

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.BrowseResponse
import app.exmusic.providers.innertube.models.Context
import app.exmusic.providers.innertube.models.MusicCarouselShelfRenderer
import app.exmusic.providers.innertube.models.NextResponse
import app.exmusic.providers.innertube.models.bodies.BrowseBody
import app.exmusic.providers.innertube.models.bodies.NextBody
import app.exmusic.providers.innertube.utils.findSectionByStrapline
import app.exmusic.providers.innertube.utils.findSectionByTitle
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
            "contents.sectionListRenderer.contents.musicCarouselShelfRenderer(header.musicCarouselShelfBasicHeaderRenderer(title,strapline),contents($MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK,$MUSIC_TWO_ROW_ITEM_RENDERER_MASK))"
        )
    }.body<BrowseResponse>()

    val sectionListRenderer = response
        .contents
        ?.sectionListRenderer

    Innertube.RelatedPage(
        songs = sectionListRenderer
            ?.findSectionByTitle("You might also like")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        playlists = sectionListRenderer
            ?.findSectionByTitle("Recommended playlists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.PlaylistItem::from)
            ?.sortedByDescending { it.channel?.name == "YouTube Music" },
        albums = sectionListRenderer
            ?.findSectionByStrapline("MORE FROM")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from),
        artists = sectionListRenderer
            ?.findSectionByTitle("Similar artists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.ArtistItem::from)
    )
}
