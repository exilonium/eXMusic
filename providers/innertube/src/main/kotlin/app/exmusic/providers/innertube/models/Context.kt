package app.exmusic.providers.innertube.models

import app.exmusic.providers.innertube.VisitorData
import io.ktor.client.request.headers
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.parameters
import io.ktor.http.userAgent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Locale

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
    val user: User? = User()
) {
    @Serializable
    data class Client(
        @Transient
        val clientId: Int = 0,
        val clientName: String,
        val clientVersion: String,
        val platform: String? = null,
        val hl: String = "en",
        val gl: String = "US",
        @SerialName("visitorData")
        val defaultVisitorData: String = VisitorData.peek(),
        val androidSdkVersion: Int? = null,
        val userAgent: String? = null,
        val referer: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val acceptHeader: String? = null,
        val timeZone: String? = "UTC",
        val utcOffsetMinutes: Int? = 0,
        @Transient
        val apiKey: String? = null,
        @Transient
        val music: Boolean = false
    ) {
        /**
         * Names one client exactly. Two clients can share a name and differ only in version — the
         * VR ones do — and they succeed and fail independently, so the version belongs in anything
         * that remembers which client did what.
         */
        val id get() = "$clientName/$clientVersion"

        context(builder: HttpMessageBuilder)
        fun apply() = with(builder) {
            userAgent?.let { userAgent(it) }

            headers {
                referer?.let { set("Referer", it) }
                set("X-Youtube-Bootstrap-Logged-In", "false")
                set("X-YouTube-Client-Name", clientId.toString())
                set("X-YouTube-Client-Version", clientVersion)
                apiKey?.let { set("X-Goog-Api-Key", it) }
                set("X-Goog-Visitor-Id", defaultVisitorData)
            }

            parameters {
                apiKey?.let { set("key", it) }
            }
        }

        /**
         * The headers the CDN expects from whoever asks for a stream this client resolved. It ties
         * a URL to the client that asked for it and truncates the response for a mismatched caller.
         */
        val streamHeaders: Map<String, String>
            get() = buildMap {
                userAgent?.let { put("User-Agent", it) }

                val origin = if (music) MUSIC_ORIGIN else ORIGIN
                put("Origin", origin)
                put("Referer", "$origin/")
            }

        private companion object {
            const val ORIGIN = "https://www.youtube.com"
            const val MUSIC_ORIGIN = "https://music.youtube.com"
        }
    }

    @Serializable
    data class ThirdParty(
        val embedUrl: String
    )

    @Serializable
    data class User(
        val lockedSafetyMode: Boolean = false
    )

    context(_: HttpMessageBuilder)
    fun apply() = client.apply()

    companion object {
        private val Context.withLang: Context
            get() {
                val locale = Locale.getDefault()

                return copy(
                    client = client.copy(
                        hl = locale
                            .toLanguageTag()
                            .replace("-Hant", "")
                            .takeIf { it in validLanguageCodes } ?: "en",
                        gl = locale
                            .country
                            .takeIf { it in validCountryCodes } ?: "US"
                    )
                )
            }

        /**
         * The contexts below are built on every access rather than held as constants, so that each
         * one carries the visitor id currently in hand instead of the one that happened to be
         * current when this class was loaded.
         */

        val DefaultWeb get() = DefaultWebNoLang.withLang

        val DefaultWebNoLang get() = Context(
            client = Client(
                clientId = 67,
                clientName = "WEB_REMIX",
                clientVersion = "1.20260114.03.00",
                platform = "DESKTOP",
                userAgent = UserAgents.DESKTOP,
                referer = "https://music.youtube.com/",
                music = true
            )
        )

        val DefaultIOS get() = Context(
            client = Client(
                clientId = 5,
                clientName = "IOS",
                clientVersion = "21.03.1",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iPhone",
                osVersion = "18.2.22C152",
                acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                userAgent = UserAgents.IOS,
                music = false
            )
        )

        val DefaultAndroidMusic get() = Context(
            client = Client(
                clientId = 21,
                clientName = "ANDROID_MUSIC",
                clientVersion = "7.27.52",
                platform = "MOBILE",
                osVersion = "11",
                androidSdkVersion = 30,
                userAgent = UserAgents.ANDROID_MUSIC,
                music = true
            )
        )

        val DefaultTV get() = Context(
            client = Client(
                clientId = 7,
                clientName = "TVHTML5",
                clientVersion = "7.20260114.12.00",
                platform = "TV",
                userAgent = UserAgents.TV,
                referer = "https://www.youtube.com/",
                music = false
            )
        )

        /**
         * Clients below hand out plain, unciphered stream URLs, so they can be used for playback
         * without solving the JavaScript signature challenge.
         */

        val DefaultVisionOS get() = Context(
            client = Client(
                clientId = 101,
                clientName = "VISIONOS",
                clientVersion = "0.1",
                deviceMake = "Apple",
                deviceModel = "RealityDevice14,1",
                osName = "visionOS",
                osVersion = "1.3.21O771",
                userAgent = UserAgents.VISION_OS,
                music = false
            )
        )

        val DefaultAndroidVR get() = Context(
            client = Client(
                clientId = 28,
                clientName = "ANDROID_VR",
                clientVersion = "1.65.10",
                platform = "MOBILE",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "12L",
                androidSdkVersion = 32,
                userAgent = UserAgents.ANDROID_VR,
                music = false
            )
        )

        /**
         * Older VR client: constant bitrate only, which works around audio stuttering on streams
         * the newer client hands out as adaptive.
         */
        val DefaultAndroidVRLegacy get() = Context(
            client = Client(
                clientId = 28,
                clientName = "ANDROID_VR",
                clientVersion = "1.43.32",
                platform = "MOBILE",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "12",
                androidSdkVersion = 32,
                userAgent = UserAgents.ANDROID_VR_LEGACY,
                music = false
            )
        )

        /**
         * Ordered by how reliably they return a directly playable audio stream.
         *
         * [DefaultIOS] and [DefaultTV] are deliberately absent. iOS answers OK and hands out a URL
         * that the CDN serves exactly once, refusing every range after the first with a 403, so
         * picking it means about half a minute of audio and then a dead stop that re-resolving
         * cannot fix. TV no longer returns a playable URL at all. Both only ever shadow a client
         * that would have worked, or the yt-dlp fallback.
         *
         * WEB_REMIX is absent for a different reason: with a signature timestamp it answers where
         * these are refused, but only with ciphered URLs, and nothing available today can solve
         * that signature — NewPipe's own master cannot parse the current player, and yt-dlp's web
         * client returns no audio formats either.
         */
        val PlaybackContexts
            get() = listOf(
                DefaultVisionOS,
                DefaultAndroidVR,
                DefaultAndroidVRLegacy
            )
    }
}

// @formatter:off
@Suppress("MaximumLineLength")
val validLanguageCodes =
    listOf("af", "az", "id", "ms", "ca", "cs", "da", "de", "et", "en-GB", "en", "es", "es-419", "eu", "fil", "fr", "fr-CA", "gl", "hr", "zu", "is", "it", "sw", "lt", "hu", "nl", "nl-NL", "no", "or", "uz", "pl", "pt-PT", "pt", "ro", "sq", "sk", "sl", "fi", "sv", "bo", "vi", "tr", "bg", "ky", "kk", "mk", "mn", "ru", "sr", "uk", "el", "hy", "iw", "ur", "ar", "fa", "ne", "mr", "hi", "bn", "pa", "gu", "ta", "te", "kn", "ml", "si", "th", "lo", "my", "ka", "am", "km", "zh-CN", "zh-TW", "zh-HK", "ja", "ko")

@Suppress("MaximumLineLength")
val validCountryCodes =
    listOf("DZ", "AR", "AU", "AT", "AZ", "BH", "BD", "BY", "BE", "BO", "BA", "BR", "BG", "KH", "CA", "CL", "HK", "CO", "CR", "HR", "CY", "CZ", "DK", "DO", "EC", "EG", "SV", "EE", "FI", "FR", "GE", "DE", "GH", "GR", "GT", "HN", "HU", "IS", "IN", "ID", "IQ", "IE", "IL", "IT", "JM", "JP", "JO", "KZ", "KE", "KR", "KW", "LA", "LV", "LB", "LY", "LI", "LT", "LU", "MK", "MY", "MT", "MX", "ME", "MA", "NP", "NL", "NZ", "NI", "NG", "NO", "OM", "PK", "PA", "PG", "PY", "PE", "PH", "PL", "PT", "PR", "QA", "RO", "RU", "SA", "SN", "RS", "SG", "SK", "SI", "ZA", "ES", "LK", "SE", "CH", "TW", "TZ", "TH", "TN", "TR", "UG", "UA", "AE", "GB", "US", "UY", "VE", "VN", "YE", "ZW")
// @formatter:on

@Suppress("MaximumLineLength")
object UserAgents {
    const val DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0"
    const val ANDROID_MUSIC =
        "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
    const val IOS = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)"
    const val TV =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
    const val VISION_OS =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
    const val ANDROID_VR =
        "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    const val ANDROID_VR_LEGACY =
        "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)"
}
