package app.exmusic.providers.innertube.models

import kotlinx.serialization.Serializable

/**
 * The card an unfiltered search puts above everything else, i.e. the "top result". [contents]
 * holds the items YouTube lists underneath it, such as the first few songs of the artist that was
 * matched.
 */
@Serializable
data class MusicCardShelfRenderer(
    val thumbnail: ThumbnailRenderer?,
    val title: Runs?,
    val subtitle: Runs?,
    val contents: List<MusicShelfRenderer.Content>?,
    val onTap: NavigationEndpoint?
)
