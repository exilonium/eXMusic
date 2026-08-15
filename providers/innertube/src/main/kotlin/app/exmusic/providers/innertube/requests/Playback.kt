package app.exmusic.providers.innertube.requests

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.VisitorData
import app.exmusic.providers.innertube.models.Context
import app.exmusic.providers.innertube.models.PlayerResponse
import app.exmusic.providers.innertube.models.bodies.PlayerBody
import app.exmusic.providers.utils.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * A directly playable stream, resolved straight from the YouTube Music API.
 */
data class PlaybackData(
    val format: PlayerResponse.StreamingData.AdaptiveFormat,
    val url: String,
    val expiresInSeconds: Long?,
    val loudnessDb: Float?,
    val clientName: String,
    val cpn: String?,
    /**
     * Must be sent when fetching [url]: the CDN ties a stream to the client that asked for it, and
     * serves a mismatched caller a truncated response.
     */
    val streamHeaders: Map<String, String>
)

private val Context.streamHeaders: Map<String, String>
    get() = buildMap {
        client.userAgent?.let { put("User-Agent", it) }

        val origin =
            if (client.music) "https://music.youtube.com" else "https://www.youtube.com"
        put("Origin", origin)
        put("Referer", "$origin/")
    }

/**
 * Resolves a playable audio stream by asking the player endpoint as a series of clients that hand
 * out unciphered URLs, stopping at the first one that answers with a usable format.
 *
 * Unlike [player], this never returns a response that would still need the JavaScript signature
 * challenge solved, so its result can be handed to the player as-is.
 */
suspend fun Innertube.playback(
    videoId: String,
    playlistId: String? = null,
    contexts: List<Context> = Context.PlaybackContexts
): Result<PlaybackData>? = runCatchingCancellable {
    tryContexts(videoId, playlistId, contexts)
        .recoverCatching { error ->
            // Every client refusing the same way means YouTube rejected the visitor id rather than
            // the video, and a new one is all that stands between here and a playable stream
            if (!error.isBotCheck) throw error

            logger.info("Visitor id rejected as a bot, minting a new one and retrying $videoId")
            VisitorData.invalidate()

            tryContexts(videoId, playlistId, contexts).getOrThrow()
        }
        .getOrThrow()
}

/**
 * Asks each client in turn, giving up on the first one that resolves. Fails with the last client's
 * error.
 */
private suspend fun Innertube.tryContexts(
    videoId: String,
    playlistId: String?,
    contexts: List<Context>
): Result<PlaybackData> {
    var lastError: Throwable = NoPlayableFormatException(videoId)

    contexts.forEach { context ->
        currentCoroutineContext().ensureActive()

        val result = runCatchingCancellable {
            resolve(
                videoId = videoId,
                playlistId = playlistId,
                context = context
            )
        } ?: throw CancellationException("Cancelled while resolving $videoId")

        result.onSuccess { return result }.onFailure {
            lastError = it
            logger.warn("Client ${context.client.clientName} could not resolve $videoId: ${it.message}")
        }
    }

    return Result.failure(lastError)
}

/**
 * A bot check surfaces as either status, and its reason is localised, so a sign-in demand from a
 * client that never signs in is taken as one too.
 */
private val Throwable.isBotCheck
    get() = this is LoginRequiredPlaybackException ||
        (this is PlaybackResolutionException && message?.contains("not a bot", true) == true)

private suspend fun Innertube.resolve(
    videoId: String,
    playlistId: String?,
    context: Context
): PlaybackData {
    logger.info("Resolving $videoId as ${context.client.clientName} ${context.client.clientVersion}")

    val response = player(
        body = PlayerBody(
            context = context,
            videoId = videoId,
            playlistId = playlistId
        ),
        checkIsValid = false,
        contexts = listOf(context)
    )?.getOrThrow() ?: throw NoPlayerResponseException(videoId)

    when (val status = response.playabilityStatus?.status) {
        "OK" -> Unit
        "LOGIN_REQUIRED" -> throw LoginRequiredPlaybackException(response.reason)
        "UNPLAYABLE" -> throw UnplayablePlaybackException(response.reason)
        else -> throw PlaybackStatusException(status, response.reason)
    }

    // A mismatch means YouTube substituted another video, which would silently play the wrong song
    response.videoDetails?.videoId?.let {
        if (it != videoId) throw VideoIdMismatchException(expected = videoId, actual = it)
    }

    val streamingData = response.streamingData ?: throw NoPlayableFormatException(videoId)
    val format = streamingData.highestQualityPlayableFormat
        ?: throw NoPlayableFormatException(videoId)

    return PlaybackData(
        format = format,
        url = format.url ?: throw NoPlayableFormatException(videoId),
        expiresInSeconds = streamingData.expiresInSeconds,
        loudnessDb = response.playerConfig?.audioConfig?.normalizedLoudnessDb,
        clientName = context.client.clientName,
        cpn = response.cpn,
        streamHeaders = context.streamHeaders
    )
}

private val PlayerResponse.reason
    get() = playabilityStatus?.reason
        ?: playabilityStatus?.errorScreen?.playerErrorMessageRenderer?.subreason?.text

sealed class PlaybackResolutionException(message: String) : RuntimeException(message)

class NoPlayerResponseException(videoId: String) :
    PlaybackResolutionException("No player response for $videoId")

class NoPlayableFormatException(videoId: String) :
    PlaybackResolutionException("No directly playable audio format for $videoId")

class VideoIdMismatchException(expected: String, actual: String) :
    PlaybackResolutionException("Expected video $expected, but got $actual")

class LoginRequiredPlaybackException(reason: String?) :
    PlaybackResolutionException(reason ?: "Login required")

class UnplayablePlaybackException(reason: String?) :
    PlaybackResolutionException(reason ?: "Unplayable")

class PlaybackStatusException(status: String?, reason: String?) :
    PlaybackResolutionException(listOfNotNull(status, reason).joinToString(": "))
