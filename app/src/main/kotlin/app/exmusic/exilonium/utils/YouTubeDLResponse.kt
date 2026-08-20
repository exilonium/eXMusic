package app.exmusic.exilonium.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class YouTubeDLResponse(
    val id: String,
    @SerialName("format_id")
    val formatId: String? = null,
    val url: String? = null,
    val formats: List<Format>? = null,
    /**
     * Absent whenever yt-dlp only knows the size from the bitrate, so it cannot be required: a
     * missing size has to read as "unknown length", not as a song that refused to play.
     */
    @SerialName("filesize")
    val fileSize: Long? = null,
    @SerialName("filesize_approx")
    val fileSizeApprox: Long? = null
) {
    /** Exact where yt-dlp knows it, estimated where it does not. */
    val contentLength get() = fileSize ?: fileSizeApprox

    companion object {
        val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
        fun fromString(str: String) = json.decodeFromString<YouTubeDLResponse>(str)
    }

    @Serializable
    data class Format(
        @SerialName("format_id")
        val formatId: String,
        val url: String? = null,
        val abr: Double? = null
    )
}
