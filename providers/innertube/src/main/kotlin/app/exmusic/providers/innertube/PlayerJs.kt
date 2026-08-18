package app.exmusic.providers.innertube

import app.exmusic.providers.innertube.models.UserAgents
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.hours

/**
 * YouTube's player script, read for its signature timestamp.
 *
 * The timestamp tells the player endpoint which generation of the script the caller is running.
 * Every client that YouTube trusts sends one; a request without it looks like something that never
 * loaded a player, which is one of the things the bot check counts against a caller.
 *
 * The script itself is not kept. It is a couple of megabytes, and the only other thing to read out
 * of it is the signature challenge, which none of the clients we play through set.
 */
object PlayerJs {
    private const val IFRAME_API = "https://www.youtube.com/iframe_api"
    private const val BASE_JS = "https://www.youtube.com/s/player/%s/player_ias.vflset/en_US/base.js"

    /** YouTube ships a new player every few days; a stale timestamp is refused. */
    private val TTL = 6.hours

    private val HASH_REGEX = """/s/player/([a-zA-Z0-9_-]+)/""".toRegex()
    private val TIMESTAMP_REGEX = """signatureTimestamp[:=](\d+)""".toRegex()

    private val mutex = Mutex()

    private data class Script(
        val hash: String,
        val signatureTimestamp: String?,
        val fetchedAt: Long
    )

    @Volatile
    private var cached: Script? = null

    private val Script.isFresh get() = System.nanoTime() - fetchedAt < TTL.inWholeNanoseconds

    /**
     * The timestamp to send with a player request, or null if the script could not be read. Never
     * throws: a request without a timestamp is worse than one with, but better than none at all.
     */
    suspend fun signatureTimestamp() = script()?.signatureTimestamp

    private suspend fun script(): Script? {
        cached?.takeIf { it.isFresh }?.let { return it }

        return mutex.withLock {
            cached?.takeIf { it.isFresh } ?: fetch()?.also {
                cached = it
                Innertube.logger.info("Read player ${it.hash}, sts ${it.signatureTimestamp}")
            }
        }
    }

    private suspend fun fetch(): Script? = runCatching {
        val hash = HASH_REGEX
            .find(page(IFRAME_API).replace("\\/", "/"))
            ?.groupValues
            ?.get(1)
            ?: return@runCatching null

        Script(
            hash = hash,
            signatureTimestamp = TIMESTAMP_REGEX
                .find(page(BASE_JS.format(hash)))
                ?.groupValues
                ?.get(1),
            fetchedAt = System.nanoTime()
        )
    }.getOrElse {
        Innertube.logger.warn("Could not read the player script: ${it.message}")
        null
    }

    private suspend fun page(url: String) = Innertube.baseClient.get(url) {
        header("User-Agent", UserAgents.DESKTOP)
    }.bodyAsText()
}
