package app.exmusic.providers.innertube.models

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

/**
 * The frame YouTube itself shows for a video, taken straight from the video id.
 *
 * What YouTube Music lists for a video is that same frame behind a crop directive of its own
 * (`hqdefault.jpg?sqp=…`), which is both smaller and cut to whatever shape Music wanted. `mqdefault`
 * is the plain 16:9 frame and, unlike `hq720` or `maxresdefault`, exists for every video, including
 * uploads that were never available above 360p.
 */
fun videoThumbnail(videoId: String) = Thumbnail(
    url = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
    width = 320,
    height = 180
)
