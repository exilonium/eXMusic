package app.exmusic.providers.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SectionListRenderer(
    val contents: List<Content>?,
    val continuations: List<Continuation>?
) {
    @Serializable
    data class Content(
        @JsonNames("musicImmersiveCarouselShelfRenderer")
        val musicCarouselShelfRenderer: MusicCarouselShelfRenderer?,
        @JsonNames("musicPlaylistShelfRenderer")
        val musicShelfRenderer: MusicShelfRenderer?,
        val gridRenderer: GridRenderer?,
        val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer?,
        val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer?,
        val musicCardShelfRenderer: MusicCardShelfRenderer? = null,
        val itemSectionRenderer: ItemSectionRenderer? = null
    ) {
        @Serializable
        data class MusicDescriptionShelfRenderer(
            val description: Runs?
        )

        /**
         * How an unfiltered search hands back its results: one section per item, without a title.
         */
        @Serializable
        data class ItemSectionRenderer(
            val contents: List<MusicShelfRenderer.Content>?
        )

        @Serializable
        data class MusicResponsiveHeaderRenderer(
            val title: Runs?,
            val description: MusicDescriptionShelfRenderer?,
            val subtitle: Runs?,
            val secondSubtitle: Runs?,
            val thumbnail: ThumbnailRenderer?,
            val straplineTextOne: Runs?
        )
    }
}
