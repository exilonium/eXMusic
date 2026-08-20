package app.exmusic.exilonium.importer

import app.exmusic.providers.innertube.Innertube
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Decides whether a search result is the song the CSV asked for. Everything is scored rather than
 * matched exactly, because an export and YouTube Music rarely spell a song the same way.
 */
object SongMatcher {
    /** Below this a result is treated as a different song, and the import reports it as missing. */
    const val MINIMUM_SCORE = 70

    private const val PRIMARY_ARTIST_SCORE = 40
    private const val OTHER_ARTIST_SCORE = 10
    private const val TITLE_SCORE = 50
    private const val ALBUM_SCORE = 20
    private const val MODIFIER_SCORE = 25
    private const val MODIFIER_PENALTY = 40
    private const val DURATION_SCORE = 20
    private const val DURATION_PENALTY = 30

    private val DURATION_TOLERANCE = 5.seconds
    private val DURATION_MISMATCH = 30.seconds

    /**
     * Words that change which recording a title refers to. A live take and a studio take share a
     * name, so matching one against the other is worse than not matching at all.
     */
    private val MODIFIERS = setOf(
        "remix", "edit", "mix", "live", "cover", "instrumental", "karaoke", "acoustic",
        "unplugged", "remaster", "remastered", "demo", "reprise", "radio", "extended",
        "slowed", "sped up"
    )

    private val ARTIST_SEPARATORS =
        Regex(",|&|;|/|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bwith\\b|\\bvs\\.?\\b")
    private val TITLE_EXTRAS = Regex("""[(\[].*?[)\]]|\s-\s.*""")
    private val PUNCTUATION = Regex("[^\\p{L}\\p{N}]+")

    private data class Normalized(
        val title: String,
        val artists: List<String>,
        val modifiers: Set<String>
    )

    fun score(song: ImportedSong, candidate: Innertube.SongItem): Int {
        val wanted = normalize(title = song.title, artists = song.artist)
        val found = normalize(
            title = candidate.info?.name.orEmpty(),
            artists = candidate.authors?.joinToString(", ") { it.name.orEmpty() }.orEmpty()
        )

        val artistScore = artistScore(wanted = wanted, found = found)
        // The right title by the wrong artist is a different song, so nothing else can rescue it
        if (artistScore == 0) return 0

        return artistScore +
            titleScore(wanted = wanted.title, found = found.title) +
            albumScore(wanted = song.album, found = candidate.album?.name) +
            modifierScore(wanted = wanted.modifiers, found = found.modifiers) +
            durationScore(
                wanted = song.duration,
                found = parseDuration(candidate.durationText.orEmpty())
            )
    }

    private fun artistScore(wanted: Normalized, found: Normalized): Int {
        val primary = wanted.artists.firstOrNull() ?: return 0
        val matchesPrimary = found.artists.any { it.contains(primary) || primary.contains(it) }
        if (!matchesPrimary) return 0

        val others = wanted.artists.drop(1).count { artist ->
            found.artists.any { it.contains(artist) }
        }

        return PRIMARY_ARTIST_SCORE + others * OTHER_ARTIST_SCORE
    }

    private fun titleScore(wanted: String, found: String): Int {
        val length = max(wanted.length, found.length)
        if (length == 0) return 0

        val similarity = 1.0 - levenshtein(wanted, found).toDouble() / length

        return (similarity * TITLE_SCORE).toInt()
    }

    private fun albumScore(wanted: String?, found: String?): Int {
        val album = wanted?.lowercase()?.trim()
        if (album.isNullOrEmpty() || found == null) return 0

        return if (found.lowercase().contains(album)) ALBUM_SCORE else 0
    }

    private fun modifierScore(wanted: Set<String>, found: Set<String>) = when {
        wanted.isEmpty() && found.isEmpty() -> 0
        wanted == found -> MODIFIER_SCORE * wanted.size
        wanted.isEmpty() -> -MODIFIER_PENALTY
        found.isEmpty() -> -MODIFIER_PENALTY / 2
        else -> MODIFIER_SCORE * wanted.count { it in found } - MODIFIER_PENALTY / 2
    }

    private fun durationScore(wanted: Duration?, found: Duration?): Int {
        if (wanted == null || found == null) return 0
        val difference = (wanted - found).absoluteValue

        return when {
            difference <= DURATION_TOLERANCE -> DURATION_SCORE
            difference >= DURATION_MISMATCH -> -DURATION_PENALTY
            else -> 0
        }
    }

    private fun normalize(title: String, artists: String): Normalized {
        val lowercase = title.lowercase()

        return Normalized(
            title = TITLE_EXTRAS.replace(lowercase, "").replace(PUNCTUATION, " ").trim(),
            artists = artists
                .lowercase()
                .split(ARTIST_SEPARATORS)
                .map { it.replace(PUNCTUATION, " ").trim() }
                .filter { it.isNotEmpty() },
            modifiers = TITLE_EXTRAS
                .findAll(lowercase)
                .flatMap { it.value.split(PUNCTUATION) }
                .map { it.trim() }
                .filter { it in MODIFIERS }
                .toSet()
        )
    }

    private fun levenshtein(lhs: String, rhs: String): Int {
        var previous = IntArray(lhs.length + 1) { it }
        var current = IntArray(lhs.length + 1)

        for (i in 1..rhs.length) {
            current[0] = i

            for (j in 1..lhs.length) {
                val substitution = previous[j - 1] + if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), substitution)
            }

            val swap = previous
            previous = current
            current = swap
        }

        return previous[lhs.length]
    }
}
