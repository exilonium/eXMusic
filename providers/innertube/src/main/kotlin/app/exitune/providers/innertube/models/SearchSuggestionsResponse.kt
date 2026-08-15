package app.exitune.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchSuggestionsResponse(
    val contents: List<Content>?
) {
    @Serializable
    data class Content(
        val searchSuggestionsSectionRenderer: SearchSuggestionsSectionRenderer?
    ) {
        @Serializable
        data class SearchSuggestionsSectionRenderer(
            val contents: List<Content>?
        ) {
            /**
             * The first section holds the completed queries, the second one the items YouTube
             * offers straight away, so both renderers have to be optional here.
             */
            @Serializable
            data class Content(
                val searchSuggestionRenderer: SearchSuggestionRenderer?,
                val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?
            ) {
                @Serializable
                data class SearchSuggestionRenderer(
                    val navigationEndpoint: NavigationEndpoint?
                )
            }
        }
    }
}
