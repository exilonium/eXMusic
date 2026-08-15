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

/**
 * Requests this artwork at [size], replacing the size directive the url already carries: these
 * arrive tagged with the variant they were listed at, and the CDN honours the first one it sees.
 *
 * A host that is not listed here is handed back untouched, so leaving one out is silent — every
 * image on it keeps the size it was listed at, however large it ends up drawn. YouTube Music now
 * serves nearly all of its artwork from yt3.googleusercontent.com and lists it at 60x60.
 */
fun String.resizedThumbnail(size: Int) = when {
    startsWith("https://lh3.googleusercontent.com") ||
        startsWith("https://yt3.googleusercontent.com") ->
        "${substringBefore('=')}=w$size-h$size-l90-rj"

    startsWith("https://yt3.ggpht.com") -> "${substringBefore('=')}=s$size"

    else -> this
}

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int?,
    val width: Int?
) {
    fun size(size: Int) = url.resizedThumbnail(size)
}
