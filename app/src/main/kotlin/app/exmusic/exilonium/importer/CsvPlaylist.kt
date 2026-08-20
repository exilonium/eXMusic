package app.exmusic.exilonium.importer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/** Above this, a bare number is milliseconds rather than seconds: no song is three hours long. */
private const val MILLISECONDS_CUTOFF = 10_000

/**
 * Header names used by the CSV exporters people actually run, most specific first. Spotify itself
 * has no export, so these come from Exportify, TuneMyMusic, Spotlistr and the playlist analysers.
 */
private val TITLE_HEADERS = listOf(
    "track name",
    "song name",
    "track title",
    "song",
    "title",
    "track",
    "name"
)

private val ARTIST_HEADERS = listOf(
    "artist name(s)",
    "artist name",
    "artist(s)",
    "artist",
    "artists",
    "album artist"
)

private val ALBUM_HEADERS = listOf(
    "album name",
    "album title",
    "album"
)

private val DURATION_HEADERS = listOf(
    "track duration (ms)",
    "duration (ms)",
    "duration ms",
    "duration",
    "length",
    "time"
)

/** A song as it was written in the CSV, before anything tries to find it on YouTube Music. */
data class ImportedSong(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Duration? = null
)

/** Which column holds what. [album] and [duration] only sharpen the search, so both may be absent. */
data class CsvColumns(
    val title: Int,
    val artist: Int,
    val album: Int?,
    val duration: Int?
)

data class CsvPlaylist(
    val header: List<String>,
    val rows: List<List<String>>,
    val columns: CsvColumns
) {
    val columnCount = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)

    fun songs(columns: CsvColumns = this.columns) = rows.mapNotNull { row ->
        val title = row.getOrNull(columns.title).orEmpty().trim()
        val artist = row.getOrNull(columns.artist).orEmpty().trim()

        if (title.isEmpty() || artist.isEmpty()) return@mapNotNull null

        ImportedSong(
            title = title,
            artist = artist,
            album = columns.album
                ?.let { row.getOrNull(it) }
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            duration = columns.duration?.let {
                parseDuration(
                    value = row.getOrNull(it).orEmpty(),
                    header = header.getOrNull(it).orEmpty()
                )
            }
        )
    }
}

/**
 * Reads [text] as a playlist export. A first row that names none of the columns we know is treated
 * as data rather than as a header, so a bare "title,artist" dump does not silently lose its first
 * song.
 */
fun readCsvPlaylist(text: String): CsvPlaylist? {
    // The picker cannot filter by type reliably, so a binary file has to be rejected here
    if (text.contains('\u0000')) return null

    val records = parseCsv(text)
    val first = records.firstOrNull() ?: return null
    val detected = detectColumns(first)

    return if (detected == null) CsvPlaylist(
        header = emptyList(),
        rows = records,
        columns = CsvColumns(title = 0, artist = 1, album = null, duration = null)
    ) else CsvPlaylist(
        header = first,
        rows = records.drop(1),
        columns = detected
    )
}

/** Null when [header] names neither a title nor an artist column, meaning it is not a header. */
private fun detectColumns(header: List<String>): CsvColumns? {
    val title = header.findColumn(TITLE_HEADERS)
    val artist = header.findColumn(ARTIST_HEADERS, setOfNotNull(title))

    if (title == null && artist == null) return null

    val taken = setOfNotNull(title, artist)
    val album = header.findColumn(ALBUM_HEADERS, taken)
    val duration = header.findColumn(DURATION_HEADERS, taken + setOfNotNull(album))

    return CsvColumns(
        title = title ?: if (artist == 0) 1 else 0,
        artist = artist ?: if (title == 0) 1 else 0,
        album = album,
        duration = duration
    )
}

/**
 * Exact matches beat partial ones, so "Artist Name(s)" wins over "Artist URI(s)" no matter which
 * column comes first.
 */
private fun List<String>.findColumn(aliases: List<String>, taken: Set<Int> = emptySet()): Int? {
    val candidates = mapIndexed { index, name -> index to name.trim().lowercase() }
        .filter { (index, name) -> index !in taken && name.isNotEmpty() }

    return aliases.firstNotNullOfOrNull { alias ->
        candidates.firstOrNull { (_, name) -> name == alias }?.first
    } ?: aliases.firstNotNullOfOrNull { alias ->
        candidates.firstOrNull { (_, name) -> name.contains(alias) }?.first
    }
}

/** Handles both the "3:41" of a human-readable export and the raw millisecond count of Exportify. */
internal fun parseDuration(value: String, header: String = ""): Duration? {
    val text = value.trim()
    if (text.isEmpty()) return null

    if (':' in text) {
        val parts = text.split(':').map { it.trim().toIntOrNull() ?: return null }

        return when (parts.size) {
            2 -> (parts[0] * SECONDS_PER_MINUTE + parts[1]).seconds
            3 -> (parts[0] * SECONDS_PER_HOUR + parts[1] * SECONDS_PER_MINUTE + parts[2]).seconds
            else -> null
        }
    }

    val number = text.toDoubleOrNull()?.toLong() ?: return null
    val isMilliseconds = header.contains("ms", ignoreCase = true) || number > MILLISECONDS_CUTOFF

    return if (isMilliseconds) number.milliseconds else number.seconds
}
