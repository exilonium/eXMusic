package app.exitune.providers.innertube.models

import kotlinx.serialization.Serializable

/**
 * The largest variant listed.
 *
 * Innertube lists thumbnails smallest first, and for the i.ytimg urls the size is part of the file
 * name rather than a directive, so taking the first one pins that artwork to 120x90 however large
 * it is drawn.
 */
val List<Thumbnail>.largest get() = maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int?,
    val width: Int?
) {
    /**
     * Requests this thumbnail at [size], replacing the size the url already carries: these arrive
     * tagged with the variant they were listed at, and the CDN honours the first directive it sees.
     */
    fun size(size: Int) = when {
        url.startsWith("https://lh3.googleusercontent.com") ->
            "${url.substringBefore('=')}=w$size-h$size-l90-rj"

        url.startsWith("https://yt3.ggpht.com") -> "${url.substringBefore('=')}=s$size"

        else -> url
    }
}
