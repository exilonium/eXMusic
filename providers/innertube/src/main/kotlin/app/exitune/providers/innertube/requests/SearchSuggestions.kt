package app.exitune.providers.innertube.requests

import app.exitune.providers.innertube.Innertube
import app.exitune.providers.innertube.models.MusicShelfRenderer
import app.exitune.providers.innertube.models.SearchSuggestionsResponse
import app.exitune.providers.innertube.models.bodies.SearchSuggestionsBody
import app.exitune.providers.innertube.utils.itemFromSearchResult
import app.exitune.providers.utils.runCatchingCancellable
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
