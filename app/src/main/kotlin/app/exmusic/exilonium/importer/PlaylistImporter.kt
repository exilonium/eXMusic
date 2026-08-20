package app.exmusic.exilonium.importer

import android.annotation.SuppressLint
import androidx.media3.common.MediaItem
import app.exmusic.exilonium.Database
import app.exmusic.exilonium.internal
import app.exmusic.exilonium.models.Playlist
import app.exmusic.exilonium.models.SongPlaylistMap
import app.exmusic.exilonium.utils.asMediaItem
import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.bodies.SearchBody
import app.exmusic.providers.innertube.requests.searchPage
import app.exmusic.providers.innertube.utils.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class ImportResult(
    val imported: Int,
    val failed: List<ImportedSong>
)

/**
 * Turns songs read out of a CSV into a playlist, by looking each one up on YouTube Music. Songs
 * that no result matches confidently are handed back instead of being guessed at, so an import
 * never quietly fills a playlist with the wrong recordings.
 */
object PlaylistImporter {
    /** Looked up at once: enough to stay quick, few enough to not read as a scraper. */
    private const val BATCH_SIZE = 8

    suspend fun import(
        songs: List<ImportedSong>,
        name: String,
        onProgress: (processed: Int) -> Unit
    ): ImportResult = coroutineScope {
        val matched = mutableListOf<MediaItem>()
        val failed = mutableListOf<ImportedSong>()
        var processed = 0

        songs.chunked(BATCH_SIZE).forEach { batch ->
            batch
                .map { song -> async(Dispatchers.IO) { song to findMatch(song) } }
                .awaitAll()
                .forEach { (song, match) ->
                    if (match == null) failed += song else matched += match.asMediaItem
                }

            processed += batch.size
            onProgress(processed)
        }

        val items = matched.distinctBy { it.mediaId }
        if (items.isNotEmpty()) persist(name = name, items = items)

        ImportResult(imported = items.size, failed = failed)
    }

    @SuppressLint("RestrictedApi")
    private suspend fun persist(name: String, items: List<MediaItem>) =
        withContext(Dispatchers.IO) {
            Database.internal.runInTransaction(
                Runnable {
                    val playlistId = Database.insert(Playlist(name = name))
                    if (playlistId == -1L) return@Runnable

                    items.forEachIndexed { position, item ->
                        Database.insert(item)
                        Database.insert(
                            SongPlaylistMap(
                                songId = item.mediaId,
                                playlistId = playlistId,
                                position = position
                            )
                        )
                    }
                }
            )
        }

    private suspend fun findMatch(song: ImportedSong): Innertube.SongItem? {
        val query = listOfNotNull(song.title, song.artist, song.album).joinToString(" ")

        // The album narrows the search, but a wrong or unreleased one can empty it out entirely
        val candidates = search(query).ifEmpty {
            if (song.album == null) emptyList() else search("${song.title} ${song.artist}")
        }

        return candidates
            .map { it to SongMatcher.score(song = song, candidate = it) }
            .filter { (_, score) -> score >= SongMatcher.MINIMUM_SCORE }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private suspend fun search(query: String) = Innertube.searchPage(
        body = SearchBody(query = query, params = Innertube.SearchFilter.Song.value),
        fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
    )
        ?.getOrNull()
        ?.items
        ?.filter { it.info?.endpoint?.videoId != null }
        .orEmpty()
}
