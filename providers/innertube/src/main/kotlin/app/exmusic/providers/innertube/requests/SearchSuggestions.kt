package app.exmusic.providers.innertube.requests

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.MusicShelfRenderer
import app.exmusic.providers.innertube.models.SearchSuggestionsResponse
import app.exmusic.providers.innertube.models.bodies.SearchSuggestionsBody
import app.exmusic.providers.innertube.utils.itemFromSearchResult
import app.exmusic.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

suspend fun Innertube.searchSuggestions(body: SearchSuggestionsBody) = runCatchingCancellable {
    val response = client.post(SEARCH_SUGGESTIONS) {
        setBody(body)
        @Suppress("all")
        mask(
            "contents.searchSuggestionsSectionRenderer.contents(" +
                "searchSuggestionRenderer.navigationEndpoint.searchEndpoint.query," +
                "$MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK)"
        )
    }.body<SearchSuggestionsResponse>()

    val contents = response
        .contents
        ?.mapNotNull { it.searchSuggestionsSectionRenderer?.contents }
        ?.flatten()
        .orEmpty()

    Innertube.SearchSuggestions(
        queries = contents.mapNotNull { content ->
            content
                .searchSuggestionRenderer
                ?.navigationEndpoint
                ?.searchEndpoint
                ?.query
        },
        items = contents.mapNotNull { content ->
            content.musicResponsiveListItemRenderer?.let { renderer ->
                itemFromSearchResult(
                    MusicShelfRenderer.Content(musicResponsiveListItemRenderer = renderer)
                )
            }
        }
    )
}
