package app.exmusic.providers.innertube

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.minutes

/**
 * The visitor id that identifies this install to YouTube.
 *
 * A visitor id shared by every copy of the app is treated as a bot: the player endpoint answers
 * "Sign in to confirm you're not a bot" for most videos, which strands playback on clients that
 * hand out streams dying after the first range request. Minting one per install, and again
 * whenever YouTube rejects the current one, keeps the unciphered clients usable.
 */
object VisitorData {
    /**
     * Used until the first mint succeeds. Requests still go out with it, they are just more likely
     * to be refused.
     */
    const val FALLBACK = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"

    private const val SW_JS_DATA = "https://music.youtube.com/sw.js_data"

    /** The response is XSSI-guarded with `)]}'\n`, which is not valid JSON. */
    private const val XSSI_PREFIX_LENGTH = 5

    /** Shortest a real visitor id could be; the ones YouTube hands out run to hundreds of chars. */
    private const val MIN_LENGTH = 20

    /**
     * How long a visitor id has to be in hand before it may be thrown away.
     *
     * A freshly minted id carries no history, so YouTube trusts it least; minting another one the
     * moment it is refused reads as a caller rotating identities to get around a block, which is
     * the behaviour the bot check is looking for. Riding out a refusal with the same id recovers,
     * where churning through ids does not.
     */
    private val MIN_ROTATION_INTERVAL = 30.minutes

    private val mutex = Mutex()

    @Volatile
    private var current: String? = null

    @Volatile
    private var lastRotation: Long? = null

    /**
     * Handed every newly minted visitor id so the host app can persist it. An id that survives
     * restarts reads as a returning listener, where one minted per launch reads as a new stranger
     * every time.
     */
    var onMinted: ((String) -> Unit)? = null

    /**
     * The visitor id to send, minting one if this is the first request or the last one was
     * [invalidate]d. Never throws: a failed mint falls back to the shared [FALLBACK] and leaves the
     * next call free to try again.
     */
    suspend fun get(): String {
        current?.let { return it }

        return mutex.withLock {
            current ?: mint()?.also {
                current = it
                Innertube.logger.info("Minted a visitor id")
                onMinted?.invoke(it)
            } ?: FALLBACK
        }
    }

    /** The visitor id already in hand, without minting one. Safe to call off a coroutine. */
    fun peek() = current ?: FALLBACK

    /** Seeds a visitor id kept from a previous run, so startup does not have to mint one. */
    fun restore(visitorData: String?) {
        if (!visitorData.isNullOrBlank()) current = visitorData
    }

    /**
     * Drops the current visitor id so the next [get] mints a new one, unless the one in hand is too
     * young to be worth replacing. Call when YouTube refuses a request as coming from a bot.
     *
     * Returns whether the id was actually dropped, so a caller can tell a retry that might now work
     * from one that would repeat the same request with the same identity.
     */
    fun invalidate(): Boolean {
        val last = lastRotation

        if (last != null && System.nanoTime() - last < MIN_ROTATION_INTERVAL.inWholeNanoseconds) {
            Innertube.logger.info("Keeping the visitor id: rotating again this soon reads as a bot")
            return false
        }

        lastRotation = System.nanoTime()
        current = null
        return true
    }

    private suspend fun mint(): String? = runCatching {
        val body = Innertube.baseClient.get(SW_JS_DATA).bodyAsText()

        json.parseToJsonElement(body.drop(XSSI_PREFIX_LENGTH)).findVisitorData()
    }.getOrElse {
        Innertube.logger.warn("Could not mint a visitor id: ${it.message}")
        null
    }

    /**
     * The payload is a deeply nested array of untagged values, so the visitor id is found by shape
     * rather than by position: it is a long protobuf blob that always starts with `Cg`.
     */
    private fun JsonElement.findVisitorData(): String? = when (this) {
        is JsonArray -> firstNotNullOfOrNull { it.findVisitorData() }

        is JsonPrimitive -> content.takeIf {
            isString && it.startsWith("Cg") && it.length > MIN_LENGTH
        }

        else -> null
    }
}
