package app.exmusic.providers.innertube.requests

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.SearchResponse
import app.exmusic.providers.innertube.models.bodies.SearchBody
import app.exmusic.providers.innertube.utils.itemFromSearchResult
import app.exmusic.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Searches without a filter, the way the YouTube Music app does when the search key is pressed:
 * one page holding the top result and then every kind of match, rather than one list per kind.
 *
 * YouTube hands these back as one section per item, without a title, so the items are grouped by
 * kind here instead. There is no continuation to follow: this page is all there is, and the
 * filtered searches are what carry on past it.
 */
suspend fun Innertube.searchSummaryPage(body: SearchBody) = runCatchingCancellable {
    val response = client.post(SEARCH) {
        setBody(body)
        @Suppress("all")
        mask(
            "contents.tabbedSearchResultsRenderer.tabs.tabRenderer.content.sectionListRenderer" +
                ".contents(" +
                "musicShelfRenderer.contents.$MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK," +
                "itemSectionRenderer.contents.$MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK," +
                "musicCardShelfRenderer(title,subtitle,thumbnail,onTap," +
                "contents.$MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK))"
        )
    }.body<SearchResponse>()

    val topResult = mutableListOf<Innertube.Item>()
    val items = mutableListOf<Innertube.Item>()

    response
        .contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.forEach { section ->
            section.musicCardShelfRenderer?.let { card ->
                val item = itemFromSearchResult(card)

                // the songs listed under an artist card do not name the artist again
                val authors = (item as? Innertube.ArtistItem)
                    ?.info
                    ?.takeIf { it.endpoint != null }
                    ?.let { listOf(it) }

                topResult += listOfNotNull(item)
                topResult += card
                    .contents
                    ?.mapNotNull { itemFromSearchResult(it, fallbackAuthors = authors) }
                    .orEmpty()
            }

            items += section.musicShelfRenderer?.contents?.mapNotNull(::itemFromSearchResult)
                .orEmpty()
            items += section.itemSectionRenderer?.contents?.mapNotNull(::itemFromSearchResult)
                .orEmpty()
        }

    val seen = topResult.mapTo(mutableSetOf()) { it.key }

    val sections = buildList {
        if (topResult.isNotEmpty()) add(
            Innertube.SearchSummaryPage.Section(
                type = Innertube.SearchSummaryPage.Type.TopResult,
                items = topResult.distinctBy { it.key }
            )
        )

        items
            .filter { seen.add(it.key) }
            .groupBy { item ->
                when (item) {
                    is Innertube.SongItem -> Innertube.SearchSummaryPage.Type.Songs
                    is Innertube.VideoItem -> Innertube.SearchSummaryPage.Type.Videos
                    is Innertube.AlbumItem -> Innertube.SearchSummaryPage.Type.Albums
                    is Innertube.ArtistItem -> Innertube.SearchSummaryPage.Type.Artists
                    is Innertube.PlaylistItem -> Innertube.SearchSummaryPage.Type.Playlists
                    else -> null
                }
            }
            .entries
            .sortedBy { (type, _) -> type?.ordinal ?: Int.MAX_VALUE }
            .forEach { (type, sectionItems) ->
                if (type != null) add(
                    Innertube.SearchSummaryPage.Section(
                        type = type,
                        items = sectionItems
                    )
                )
            }
    }

    Innertube.SearchSummaryPage(sections = sections)
}
