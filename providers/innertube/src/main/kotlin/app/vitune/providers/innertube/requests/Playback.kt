package app.vitune.providers.innertube.requests

import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.Context
import app.vitune.providers.innertube.models.PlayerResponse
import app.vitune.providers.innertube.models.bodies.PlayerBody
import app.vitune.providers.utils.runCatchingCancellable
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
    val cpn: String?
)

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

        result.getOrNull()?.let { return@runCatchingCancellable it }
        result.exceptionOrNull()?.let {
            lastError = it
            logger.warn("Client ${context.client.clientName} could not resolve $videoId: ${it.message}")
        }
    }

    throw lastError
}

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
        cpn = response.cpn
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
