package app.exmusic.providers.innertube

import app.exmusic.providers.innertube.models.MusicNavigationButtonRenderer
import app.exmusic.providers.innertube.models.NavigationEndpoint
import app.exmusic.providers.innertube.models.Runs
import app.exmusic.providers.innertube.models.Thumbnail
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.brotli
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.host
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

object Innertube {
    private val OriginInterceptor = createClientPlugin("OriginInterceptor") {
        client.sendPipeline.intercept(HttpSendPipeline.State) {
            context.headers {
                val host =
                    if (context.host == "youtubei.googleapis.com") "www.youtube.com" else context.host
                val origin = "${context.url.protocol.name}://$host"
                set("host", host)
                set("x-origin", origin)
                set("origin", origin)
            }
        }
    }

    /**
     * The visitor id decides whether YouTube treats the caller as a bot, and the header wins over
     * whatever the request body carries, so it is set here rather than per-request.
     */
    private val VisitorDataInterceptor = createClientPlugin("VisitorDataInterceptor") {
        client.sendPipeline.intercept(HttpSendPipeline.State) {
            val visitorData = VisitorData.get()

            context.headers { set("X-Goog-Visitor-Id", visitorData) }
        }
    }

    private val REQUEST_TIMEOUT = 30.seconds
    private val CONNECT_TIMEOUT = 15.seconds
    private val SOCKET_TIMEOUT = 30.seconds

    private const val IDLE_CONNECTIONS = 10
    private val KEEP_ALIVE = 5.minutes

    /**
     * Short on purpose: the probe stands between the user and the first note, so a CDN that will
     * not answer quickly is better treated as unknown than waited on.
     */
    private val PROBE_TIMEOUT = 5.seconds

    val logger: Logger = LoggerFactory.getLogger(Innertube::class.java)
    val baseClient = HttpClient(OkHttp) {
        expectSuccess = true

        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                val ex = cause as? ResponseException ?: return@handleResponseExceptionWithRequest
                val code = ex.response.status.value
                if (code !in (100..<600)) throw InvalidHttpCodeException(code)
            }
        }

        // Without a deadline a request that stalls stalls playback with it: the resolver waits on
        // the socket rather than moving on to the next client
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds
            connectTimeoutMillis = CONNECT_TIMEOUT.inWholeMilliseconds
            socketTimeoutMillis = SOCKET_TIMEOUT.inWholeMilliseconds
        }

        engine {
            config {
                retryOnConnectionFailure(true)

                // Resolving a song is several requests to the same two hosts in a row, and holding
                // the sockets open across them saves a handshake each time
                connectionPool(ConnectionPool(IDLE_CONNECTIONS, KEEP_ALIVE.inWholeMinutes, TimeUnit.MINUTES))
            }
        }

        install(ContentNegotiation) {
            json(json)
        }

        install(ContentEncoding) {
            brotli(1.0f)
            gzip(0.9f)
            deflate(0.8f)
        }

        install(Logging) {
            level = LogLevel.INFO
        }

        install(OriginInterceptor)
    }
    val client = baseClient.config {
        install(VisitorDataInterceptor)

        defaultRequest {
            url(scheme = "https", host = "music.youtube.com") {
                contentType(ContentType.Application.Json)
                headers {
                    set("X-Goog-Api-Key", API_KEY)
                }
                parameters {
                    set("prettyPrint", "false")
                    set("key", API_KEY)
                }
            }
        }
    }

    /**
     * Asks the CDN, rather than YouTube, whether a resolved stream URL actually serves bytes. It
     * carries none of the API plumbing: the googlevideo hosts want the headers of the client the
     * URL was minted for, and nothing else.
     */
    internal val probeClient = HttpClient(OkHttp) {
        expectSuccess = false

        install(HttpTimeout) {
            requestTimeoutMillis = PROBE_TIMEOUT.inWholeMilliseconds
            connectTimeoutMillis = PROBE_TIMEOUT.inWholeMilliseconds
            socketTimeoutMillis = PROBE_TIMEOUT.inWholeMilliseconds
        }
    }

    /**
     * Runs [block], asking again when it fails in a way that asking again could fix: a dropped
     * connection, a timeout, or a server error. A refusal YouTube meant — anything in the 4xx range
     * — is passed straight on, as is a cancellation.
     */
    internal suspend fun <T> withRetry(
        attempts: Int = 3,
        initialDelay: Duration = 300.milliseconds,
        factor: Int = 2,
        block: suspend () -> T
    ): T {
        var delay = initialDelay

        repeat(attempts - 1) {
            val attempt = runCatching { block() }
            val ex = attempt.exceptionOrNull() ?: return attempt.getOrThrow()

            if (!ex.isTransient) throw ex

            logger.info("Retrying after a transient failure: ${ex.message}")
            delay(delay)
            delay *= factor
        }

        return block()
    }

    private val Throwable.isTransient
        get() = this is IOException ||
            this is HttpRequestTimeoutException ||
            this is ServerResponseException

    private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    private const val BASE = "/youtubei/v1"
    internal const val BROWSE = "$BASE/browse"
    internal const val NEXT = "$BASE/next"
    internal const val PLAYER = "https://youtubei.googleapis.com/youtubei/v1/player"
    internal const val PLAYER_MUSIC = "$BASE/player"
    internal const val QUEUE = "$BASE/music/get_queue"
    internal const val SEARCH = "$BASE/search"
    internal const val SEARCH_SUGGESTIONS = "$BASE/music/get_search_suggestions"
    internal const val MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK =
        "musicResponsiveListItemRenderer(flexColumns,fixedColumns,thumbnail,navigationEndpoint,badges)"
    internal const val MUSIC_TWO_ROW_ITEM_RENDERER_MASK =
        "musicTwoRowItemRenderer(thumbnailRenderer,title,subtitle,navigationEndpoint)"

    @Suppress("MaximumLineLength")
    internal const val PLAYLIST_PANEL_VIDEO_RENDERER_MASK =
        "playlistPanelVideoRenderer(title,navigationEndpoint,longBylineText,shortBylineText,thumbnail,lengthText,badges)"

    internal fun HttpRequestBuilder.mask(value: String = "*") =
        header("X-Goog-FieldMask", value)

    @Serializable
    data class Info<T : NavigationEndpoint.Endpoint>(
        val name: String?,
        val endpoint: T?
    ) {
        @Suppress("UNCHECKED_CAST")
        constructor(run: Runs.Run) : this(
            name = run.text,
            endpoint = run.navigationEndpoint?.endpoint as T?
        )
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val Song = SearchFilter("EgWKAQIIAWoOEAMQBBAJEAoQBRAQEBU%3D")
            val Video = SearchFilter("EgWKAQIQAWoOEAMQBBAJEAoQBRAQEBU%3D")
            val Album = SearchFilter("EgWKAQIYAWoOEAMQBBAJEAoQBRAQEBU%3D")
            val Artist = SearchFilter("EgWKAQIgAWoOEAMQBBAJEAoQBRAQEBU%3D")
            val CommunityPlaylist = SearchFilter("EgeKAQQoAEABag4QAxAEEAkQChAFEBAQFQ%3D%3D")
        }
    }

    sealed class Item {
        abstract val thumbnail: Thumbnail?
        abstract val key: String
    }

    @Serializable
    data class SongItem(
        val info: Info<NavigationEndpoint.Endpoint.Watch>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val album: Info<NavigationEndpoint.Endpoint.Browse>?,
        val durationText: String?,
        val explicit: Boolean,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.videoId!!

        companion object
    }

    data class VideoItem(
        val info: Info<NavigationEndpoint.Endpoint.Watch>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val viewsText: String?,
        val durationText: String?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.videoId!!

        val isOfficialMusicVideo: Boolean
            get() = info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType == "MUSIC_VIDEO_TYPE_OMV"

        companion object
    }

    @Serializable
    data class AlbumItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val year: String?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!

        companion object
    }

    @Serializable
    data class ArtistItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val subscribersCountText: String?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!

        companion object
    }

    @Serializable
    data class PlaylistItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val channel: Info<NavigationEndpoint.Endpoint.Browse>?,
        val songCount: Int?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!

        companion object
    }

    data class ArtistPage(
        val name: String?,
        val description: String?,
        val thumbnail: Thumbnail?,
        val shuffleEndpoint: NavigationEndpoint.Endpoint.Watch?,
        val radioEndpoint: NavigationEndpoint.Endpoint.Watch?,
        val songs: List<SongItem>?,
        val songsEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val albums: List<AlbumItem>?,
        val albumsEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val singles: List<AlbumItem>?,
        val singlesEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val subscribersCountText: String?
    )

    data class PlaylistOrAlbumPage(
        val title: String?,
        val description: String?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val year: String?,
        val thumbnail: Thumbnail?,
        val url: String?,
        val songsPage: ItemsPage<SongItem>?,
        val otherVersions: List<AlbumItem>?,
        val otherInfo: String?
    )

    data class NextPage(
        val itemsPage: ItemsPage<SongItem>?,
        val playlistId: String?,
        val params: String? = null,
        val playlistSetVideoId: String? = null
    )

    @Serializable
    data class RelatedPage(
        val songs: List<SongItem>? = null,
        val playlists: List<PlaylistItem>? = null,
        val albums: List<AlbumItem>? = null,
        val artists: List<ArtistItem>? = null
    )

    data class DiscoverPage(
        val newReleaseAlbums: List<AlbumItem>,
        val moods: List<Mood.Item>,
        val trending: Trending
    ) {
        data class Trending(
            val songs: List<SongItem>,
            val endpoint: NavigationEndpoint.Endpoint.Browse?
        )
    }

    data class Mood(
        val title: String,
        val items: List<Item>
    ) {
        data class Item(
            val title: String,
            val stripeColor: Long,
            val endpoint: NavigationEndpoint.Endpoint.Browse
        ) : Innertube.Item() {
            override val thumbnail get() = null
            override val key
                get() = "${endpoint.browseId.orEmpty()}${endpoint.params?.let { "/$it" }.orEmpty()}"

            companion object
        }
    }

    fun MusicNavigationButtonRenderer.toMood(): Mood.Item? {
        return Mood.Item(
            title = buttonText.runs.firstOrNull()?.text ?: return null,
            stripeColor = solid?.leftStripeColor ?: return null,
            endpoint = clickCommand.browseEndpoint ?: return null
        )
    }

    data class ItemsPage<T : Item>(
        val items: List<T>?,
        val continuation: String?
    )

    /**
     * What the search box offers while typing: the queries YouTube would complete the input to,
     * plus the handful of songs, albums and artists it is confident enough about to offer straight
     * away.
     */
    data class SearchSuggestions(
        val queries: List<String>,
        val items: List<Item>
    )

    /**
     * An unfiltered search: everything YouTube considers relevant, in one page, grouped by kind.
     */
    data class SearchSummaryPage(
        val sections: List<Section>
    ) {
        data class Section(
            val type: Type,
            val items: List<Item>
        )

        enum class Type {
            TopResult,
            Songs,
            Videos,
            Albums,
            Artists,
            Playlists
        }
    }
}

data class InvalidHttpCodeException(val code: Int) :
    IllegalStateException("Invalid http code received: $code")
