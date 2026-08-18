package app.exmusic.providers.innertube.requests

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.BrowseResponse
import app.exmusic.providers.innertube.models.ContinuationResponse
import app.exmusic.providers.innertube.models.MusicCarouselShelfRenderer
import app.exmusic.providers.innertube.models.MusicTwoRowItemRenderer
import app.exmusic.providers.innertube.models.SectionListRenderer
import app.exmusic.providers.innertube.models.bodies.BrowseBody
import app.exmusic.providers.innertube.models.bodies.ContinuationBody
import app.exmusic.providers.innertube.utils.from
import app.exmusic.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * YouTube Music's own front page: the shelves it would show on opening the app, in its order.
 *
 * This is what the app has been missing. Its quick picks are built from what happens to be in the
 * local database — one song's related page — which cannot suggest anything the listener has not
 * already played. The home feed is YouTube's recommendation, so it reaches past that.
 */
suspend fun Innertube.homePage() = runCatchingCancellable {
    val response = client.post(BROWSE) {
        setBody(BrowseBody(browseId = HOME_BROWSE_ID))
        mask(FEED_MASK)
    }.body<BrowseResponse>()

    response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.toFeedPage()
}

/**
 * The next screenful of [homePage]. Home is long and YouTube hands it over a shelf or two at a
 * time, so without this only the first few are ever seen.
 */
suspend fun Innertube.homePage(body: ContinuationBody) = runCatchingCancellable {
    val response = client.post(BROWSE) {
        setBody(body)
        mask(FEED_CONTINUATION_MASK)
    }.body<ContinuationResponse>()

    response
        .continuationContents
        ?.sectionListContinuation
        ?.toFeedPage()
}

/**
 * What is popular right now, by country: YouTube Music's charts, which the app had no way to show
 * at all.
 */
suspend fun Innertube.chartsPage() = runCatchingCancellable {
    val response = client.post(BROWSE) {
        setBody(BrowseBody(browseId = CHARTS_BROWSE_ID))
        mask(FEED_MASK)
    }.body<BrowseResponse>()

    response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.toFeedPage()
}

private const val HOME_BROWSE_ID = "FEmusic_home"
private const val CHARTS_BROWSE_ID = "FEmusic_charts"

/**
 * A feed shelf comes in three shapes, and leaving any of them out drops whole rows: the charts hand
 * their top songs over as a list, and the moods and genres come as a grid.
 */
@Suppress("MaximumLineLength")
private val SHELF_MASK =
    "contents(musicCarouselShelfRenderer(header.musicCarouselShelfBasicHeaderRenderer(title,strapline),contents(musicTwoRowItemRenderer,musicResponsiveListItemRenderer)),musicShelfRenderer(title,contents.${Innertube.MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK}),gridRenderer(header,items.musicTwoRowItemRenderer))"

private val FEED_MASK =
    "contents.singleColumnBrowseResultsRenderer.tabs.tabRenderer.content" +
        ".sectionListRenderer(continuations,$SHELF_MASK)"

private val FEED_CONTINUATION_MASK =
    "continuationContents.sectionListContinuation(continuations,$SHELF_MASK)"

private fun SectionListRenderer.toFeedPage() = Innertube.FeedPage(
    sections = contents
        ?.mapNotNull { it.toShelf() }
        ?.filter { it.items.isNotEmpty() }
        .orEmpty(),
    continuation = continuations
        ?.firstOrNull()
        ?.nextContinuationData
        ?.continuation
)

private fun SectionListRenderer.Content.toShelf() = when {
    musicCarouselShelfRenderer != null -> musicCarouselShelfRenderer.toShelf()

    musicShelfRenderer != null -> Innertube.FeedPage.Shelf(
        title = musicShelfRenderer.title?.runs?.firstOrNull()?.text,
        strapline = null,
        items = musicShelfRenderer
            .contents
            ?.mapNotNull { it.musicResponsiveListItemRenderer?.let(Innertube.SongItem::from) }
            .orEmpty()
    )

    gridRenderer != null -> Innertube.FeedPage.Shelf(
        title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text,
        strapline = null,
        items = gridRenderer.items?.mapNotNull { it.musicTwoRowItemRenderer?.toFeedItem() }.orEmpty()
    )

    else -> null
}

private fun MusicCarouselShelfRenderer.toShelf(): Innertube.FeedPage.Shelf {
    val header = header?.musicCarouselShelfBasicHeaderRenderer

    return Innertube.FeedPage.Shelf(
        title = header?.title?.runs?.firstOrNull()?.text,
        // "Listen again", "Recommended for you" and the like sit above the title on the real page
        strapline = header?.strapline?.runs?.firstOrNull()?.text,
        items = contents?.mapNotNull { it.toItem() }.orEmpty()
    )
}

/**
 * A shelf mixes shapes: tiles for songs, albums, playlists and artists, rows for the quick picks.
 */
private fun MusicCarouselShelfRenderer.Content.toItem(): Innertube.Item? {
    musicResponsiveListItemRenderer?.let { return Innertube.SongItem.from(it) }

    return musicTwoRowItemRenderer?.toFeedItem()
}

private fun MusicTwoRowItemRenderer.toFeedItem(): Innertube.Item? = when {
    isSong -> Innertube.SongItem.from(this)
    isAlbum -> Innertube.AlbumItem.from(this)
    isPlaylist -> Innertube.PlaylistItem.from(this)
    isArtist -> Innertube.ArtistItem.from(this)
    else -> null
}
