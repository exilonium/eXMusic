package app.exmusic.providers.innertube.requests

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.PlayerJs
import app.exmusic.providers.innertube.VisitorData
import app.exmusic.providers.innertube.models.Context
import app.exmusic.providers.innertube.models.PlayerResponse
import app.exmusic.providers.innertube.models.bodies.PlayerBody
import app.exmusic.providers.utils.runCatchingCancellable
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

/**
 * A directly playable stream, resolved straight from the YouTube Music API.
 */
data class PlaybackData(
    val format: PlayerResponse.StreamingData.AdaptiveFormat,
    val url: String,
    val expiresInSeconds: Long?,
    val loudnessDb: Float?,
    val clientName: String,
    /**
     * Names the client exactly, version included. Hand it back to [markPlaybackFailure] if the
     * stream turns out not to play.
     */
    val clientKey: String,
    val cpn: String?,
    /**
     * Must be sent when fetching [url]: the CDN ties a stream to the client that asked for it, and
     * serves a mismatched caller a truncated response.
     */
    val streamHeaders: Map<String, String>
)

/**
 * Which client last handed out a URL that turned out not to play, per video.
 *
 * A stream can be refused only once the player asks the CDN for it, by which time the client that
 * resolved it is out of scope. Remembering the pairing for a while means the retry that follows
 * starts at the next client instead of resolving the same dead URL again.
 */
private object RecentFailures {
    private val TTL = 5.minutes

    private val failedAt = ConcurrentHashMap<String, Long>()

    private fun key(videoId: String, clientKey: String) = "$videoId/$clientKey"

    fun mark(videoId: String, clientKey: String) {
        failedAt[key(videoId, clientKey)] = System.nanoTime()
    }

    fun isRecent(videoId: String, clientKey: String): Boolean {
        val key = key(videoId, clientKey)
        val at = failedAt[key] ?: return false

        if (System.nanoTime() - at > TTL.inWholeNanoseconds) {
            failedAt.remove(key, at)
            return false
        }

        return true
    }
}

/**
 * Records that the stream the client named by [clientKey] resolved for [videoId] would not play, so
 * the next resolution skips that client rather than handing back the same URL.
 */
fun Innertube.markPlaybackFailure(videoId: String, clientKey: String) {
    logger.info("Client $clientKey produced an unplayable stream for $videoId")
    RecentFailures.mark(videoId, clientKey)
}

/**
 * Asks the CDN for the first two bytes of [url]. A client can answer with a URL the CDN then
 * refuses, and finding that out here costs one short request, where finding it out in the player
 * costs the user a song that will not start.
 *
 * Unknown counts as playable: a probe that times out says more about the network than the URL.
 */
private suspend fun Innertube.servesBytes(url: String, headers: Map<String, String>): Boolean =
    runCatchingCancellable {
        val response: HttpResponse = probeClient.get(url) {
            headers.forEach { (name, value) -> header(name, value) }
            header("Range", "bytes=0-1")
        }

        response.status == HttpStatusCode.PartialContent || response.status == HttpStatusCode.OK
    }?.getOrElse {
        logger.info("Could not probe a stream URL, assuming it plays: ${it.message}")
        true
    } ?: true

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
        .onSuccess { BotGate.clear() }
        .recoverCatching { error ->
            if (!error.isBotCheck) throw error

            BotGate.trip()

            // Rotating the visitor id is the only thing that changes the answer, so retrying
            // without a new one just spends another round of requests on the same refusal
            if (!VisitorData.invalidate()) throw error

            logger.info("Visitor id rejected as a bot, minting a new one and retrying $videoId")

            tryContexts(videoId, playlistId, contexts)
                .onSuccess { BotGate.clear() }
                .getOrThrow()
        }
        .getOrThrow()
}

/**
 * Whether YouTube is currently answering "confirm you're not a bot".
 *
 * The refusal is about the caller, not the song, so once one song has hit it the next few will too.
 * Holding on to that for a short while keeps a queue of songs from turning one refusal into dozens
 * of requests, which is what makes the check stay up rather than lift.
 */
object BotGate {
    private val COOL_DOWN = 2.minutes

    @Volatile
    private var trippedAt: Long? = null

    val isUp: Boolean
        get() {
            val at = trippedAt ?: return false

            if (System.nanoTime() - at > COOL_DOWN.inWholeNanoseconds) {
                trippedAt = null
                return false
            }

            return true
        }

    internal fun trip() {
        trippedAt = System.nanoTime()
    }

    internal fun clear() {
        trippedAt = null
    }
}

/**
 * Asks each client in turn, giving up on the first one whose stream the CDN will serve. Fails with
 * the last client's error.
 *
 * The last client is taken on trust: there is nothing left to fall back to, so a URL that might
 * play beats none at all.
 */
private suspend fun Innertube.tryContexts(
    videoId: String,
    playlistId: String?,
    contexts: List<Context>
): Result<PlaybackData> {
    var lastError: Throwable = NoPlayableFormatException(videoId)

    // Every client having failed recently says nothing about which one to pick, so the memo is
    // dropped rather than allowed to rule out playback altogether
    val worthTrying = contexts
        .filterNot { RecentFailures.isRecent(videoId, it.client.id) }
        .ifEmpty { contexts }
        // While the bot check is up every client refuses, so asking all of them only deepens the
        // hole. One is enough to find out whether it has lifted.
        .let { if (BotGate.isUp) it.take(1) else it }

    worthTrying.forEachIndexed { index, context ->
        currentCoroutineContext().ensureActive()

        val result = runCatchingCancellable {
            resolve(
                videoId = videoId,
                playlistId = playlistId,
                context = context
            )
        } ?: throw CancellationException("Cancelled while resolving $videoId")

        result.onSuccess { playback ->
            val isLast = index == worthTrying.lastIndex

            if (isLast || servesBytes(playback.url, playback.streamHeaders)) return result

            logger.warn("Client ${context.client.id} resolved $videoId to a URL the CDN refused")
            RecentFailures.mark(videoId, context.client.id)
            lastError = UnservableStreamException(videoId, context.client.id)
        }.onFailure {
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
val Throwable.isBotCheck: Boolean
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
            playlistId = playlistId,
            playbackContext = PlayerJs.signatureTimestamp()?.let {
                PlayerBody.PlaybackContext(
                    PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp = it)
                )
            }
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
        clientKey = context.client.id,
        cpn = response.cpn,
        streamHeaders = context.client.streamHeaders
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

class UnservableStreamException(videoId: String, clientKey: String) :
    PlaybackResolutionException("$clientKey resolved $videoId to a URL the CDN would not serve")

class VideoIdMismatchException(expected: String, actual: String) :
    PlaybackResolutionException("Expected video $expected, but got $actual")

class LoginRequiredPlaybackException(reason: String?) :
    PlaybackResolutionException(reason ?: "Login required")

class UnplayablePlaybackException(reason: String?) :
    PlaybackResolutionException(reason ?: "Unplayable")

class PlaybackStatusException(status: String?, reason: String?) :
    PlaybackResolutionException(listOfNotNull(status, reason).joinToString(": "))
