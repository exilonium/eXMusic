package app.exmusic.providers.innertube.requests

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.Context
import app.exmusic.providers.innertube.models.PlayerResponse
import app.exmusic.providers.innertube.models.bodies.PlayerBody
import app.exmusic.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.util.generateNonce
import io.ktor.util.generateNonceSuspend
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

private suspend fun Innertube.tryContexts(
    body: PlayerBody,
    checkIsValid: Boolean,
    contexts: List<Context>
): PlayerResponse? {
    contexts.forEach { context ->
        if (!currentCoroutineContext().isActive) return null

        logger.info("Trying ${context.client.clientName} ${context.client.clientVersion} ${context.client.platform}")
        val cpn = generateNonceSuspend(16)
        runCatchingCancellable {
            withRetry {
                client.post(if (context.client.music) PLAYER_MUSIC else PLAYER) {
                    setBody(
                        body.copy(
                            context = context,
                            cpn = cpn
                        )
                    )

                    context.apply()

                    parameter("t", generateNonceSuspend(12))
                    header("X-Goog-Api-Format-Version", "2")
                    parameter("id", body.videoId)
                }.body<PlayerResponse>().also { logger.info("Got $it") }
            }
        }
            ?.getOrNull()
            ?.takeIf { !checkIsValid || it.isValid }
            ?.let {
                return it.copy(
                    cpn = cpn,
                    context = context
                )
            }
    }

    return null
}

private val PlayerResponse.isValid
    get() = playabilityStatus?.status == "OK" &&
        streamingData?.adaptiveFormats?.any { it.url != null || it.signatureCipher != null } == true

suspend fun Innertube.player(
    body: PlayerBody,
    checkIsValid: Boolean = true,
    contexts: List<Context> = listOf(
        Context.DefaultIOS,
        Context.DefaultWeb,
        Context.DefaultAndroidMusic,
        Context.DefaultTV
    )
): Result<PlayerResponse?>? = runCatchingCancellable {
    tryContexts(
        body = body,
        checkIsValid = checkIsValid,
        contexts = contexts
    )
}
