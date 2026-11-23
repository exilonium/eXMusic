package app.exmusic.exilonium.utils

import androidx.media3.common.MediaItem
import app.exmusic.exilonium.Database
import app.exmusic.exilonium.internal
import app.exmusic.exilonium.models.RelatedSongMap
import app.exmusic.exilonium.service.LOCAL_KEY_PREFIX
import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.bodies.NextBody
import app.exmusic.providers.innertube.requests.relatedPage

/**
 * Asks YouTube what it would play next to [seed] and keeps the songs it names, so that quick picks
 * can be built out of the library instead of a fresh request every time the home screen opens.
 *
 * Returns the whole page, shelves included, for whoever wants to show more than the songs.
 */
suspend fun storeRelatedPage(seed: MediaItem): Result<Innertube.RelatedPage>? {
    if (seed.mediaId.startsWith(LOCAL_KEY_PREFIX)) return null

    val result = Innertube.relatedPage(NextBody(videoId = seed.mediaId))
    val songs = result?.getOrNull()?.songs.orEmpty()

    if (songs.isNotEmpty()) {
        runCatching {
            Database.internal.runInTransaction {
                Database.insert(seed)
                Database.clearRelatedSongsOf(seed.mediaId)

                songs.forEach { song ->
                    Database.insert(song.asMediaItem)
                    Database.insert(
                        RelatedSongMap(
                            songId = seed.mediaId,
                            relatedSongId = song.key
                        )
                    )
                }
            }
        }
    }

    return result
}
