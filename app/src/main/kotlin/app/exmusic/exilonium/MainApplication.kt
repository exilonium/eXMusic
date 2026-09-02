package app.exmusic.exilonium

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.work.Configuration
import app.exmusic.compose.persist.LocalPersistMap
import app.exmusic.compose.persist.PersistMap
import app.exmusic.compose.preferences.PreferencesHolder
import app.exmusic.core.ui.Darkness
import app.exmusic.core.ui.Dimensions
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.core.ui.SystemBarAppearance
import app.exmusic.core.ui.amoled
import app.exmusic.core.ui.appearance
import app.exmusic.core.ui.rippleConfiguration
import app.exmusic.core.ui.shimmerTheme
import app.exmusic.core.ui.utils.activityIntentBundle
import app.exmusic.core.ui.utils.isAtLeastAndroid12
import app.exmusic.core.ui.utils.isAtLeastAndroid17
import app.exmusic.core.ui.utils.songBundle
import app.exmusic.exilonium.preferences.AppearancePreferences
import app.exmusic.exilonium.preferences.DataPreferences
import app.exmusic.exilonium.service.PlayerService
import app.exmusic.exilonium.service.ServiceNotifications
import app.exmusic.exilonium.service.downloadState
import app.exmusic.exilonium.ui.components.BottomSheetMenu
import app.exmusic.exilonium.ui.components.rememberBottomSheetState
import app.exmusic.exilonium.ui.components.themed.LinearProgressIndicator
import app.exmusic.exilonium.ui.modifiers.auroraGlow
import app.exmusic.exilonium.ui.screens.albumRoute
import app.exmusic.exilonium.ui.screens.artistRoute
import app.exmusic.exilonium.ui.screens.home.HomeScreen
import app.exmusic.exilonium.ui.screens.player.Player
import app.exmusic.exilonium.ui.screens.player.Thumbnail
import app.exmusic.exilonium.ui.screens.playlistRoute
import app.exmusic.exilonium.ui.screens.searchResultRoute
import app.exmusic.exilonium.ui.screens.settingsRoute
import app.exmusic.exilonium.utils.DisposableListener
import app.exmusic.exilonium.utils.KeyedCrossfade
import app.exmusic.exilonium.utils.LocalMonetCompat
import app.exmusic.exilonium.utils.asMediaItem
import app.exmusic.exilonium.utils.collectProvidedBitmapAsState
import app.exmusic.exilonium.utils.forcePlay
import app.exmusic.exilonium.utils.intent
import app.exmusic.exilonium.utils.invokeOnReady
import app.exmusic.exilonium.utils.isInPip
import app.exmusic.exilonium.utils.maybeEnterPip
import app.exmusic.exilonium.utils.maybeExitPip
import app.exmusic.exilonium.utils.setDefaultPalette
import app.exmusic.exilonium.utils.shouldBePlaying
import app.exmusic.exilonium.utils.toast
import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.VisitorData
import app.exmusic.providers.innertube.models.bodies.BrowseBody
import app.exmusic.providers.innertube.requests.playlistPage
import app.exmusic.providers.innertube.requests.song
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.bitmapFactoryExifOrientationStrategy
import coil3.decode.ExifOrientationStrategy
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.CacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import com.valentinilk.shimmer.LocalShimmerTheme
import dev.kdrag0n.monet.theme.ColorScheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MainActivity"
private val coroutineScope = CoroutineScope(Dispatchers.IO)

// Viewmodel in order to avoid recreating the entire Player state (WORKAROUND)
class MainViewModel : ViewModel() {
    var binder: PlayerService.Binder? by mutableStateOf(null)

    suspend fun awaitBinder(): PlayerService.Binder =
        binder ?: snapshotFlow { binder }.filterNotNull().first()
}

class MainActivity : ComponentActivity(), MonetColorsChangedListener {
    private val vm: MainViewModel by viewModels()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is PlayerService.Binder) vm.binder = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vm.binder = null
            // Try to rebind, otherwise fail
            unbindService(this)
            bindService(intent<PlayerService>(), this, BIND_AUTO_CREATE)
        }
    }

    private var _monet: MonetCompat? by mutableStateOf(null)
    val monet get() = _monet ?: throw MonetActivityAccessException()

    override fun onStart() {
        super.onStart()
        bindService(intent<PlayerService>(), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        MonetCompat.setup(this)
        _monet = MonetCompat.getInstance()
        monet.setDefaultPalette()
        monet.addMonetColorsChangedListener(
            listener = this,
            notifySelf = false
        )
        monet.updateMonetColors()
        monet.invokeOnReady {
            setContent()
        }

        intent?.let { handleIntent(it) }
        addOnNewIntentListener(::handleIntent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppWrapper(
        modifier: Modifier = Modifier,
        content: @Composable BoxWithConstraintsScope.() -> Unit
    ) = with(AppearancePreferences) {
        val sampleBitmap = vm.binder.collectProvidedBitmapAsState()
        val appearance = appearance(
            source = colorSource,
            mode = colorMode,
            darkness = darkness,
            fontFamily = fontFamily,
            materialAccentColor = Color(monet.getAccentColor(this@MainActivity)),
            sampleBitmap = sampleBitmap,
            applyFontPadding = applyFontPadding,
            thumbnailRoundness = thumbnailRoundness.dp
        )

        SystemBarAppearance(palette = appearance.colorPalette)

        BoxWithConstraints(
            modifier = Modifier.auroraGlow(appearance.colorPalette) then modifier.fillMaxSize()
        ) {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerServiceBinder provides vm.binder,
                LocalCredentialManager provides Dependencies.credentialManager,
                LocalIndication provides ripple(),
                LocalRippleConfiguration provides rippleConfiguration(appearance = appearance),
                LocalShimmerTheme provides shimmerTheme(),
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalPersistMap provides Dependencies.application.persistMap,
                LocalMonetCompat provides monet
            ) {
                content()
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    @OptIn(ExperimentalLayoutApi::class)
    fun setContent() = setContent {
        val windowInsets = WindowInsets.systemBars

        AppWrapper(
            modifier = Modifier.padding(
                WindowInsets
                    .displayCutout
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )
        ) {
            val density = LocalDensity.current
            val bottomDp = with(density) { windowInsets.getBottom(density).toDp() }

            val imeVisible = WindowInsets.isImeVisible
            val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
            val animatedBottomDp by animateDpAsState(
                targetValue = if (imeVisible) 0.dp else bottomDp,
                label = ""
            )

            val playerBottomSheetState = rememberBottomSheetState(
                key = vm.binder,
                dismissedBound = 0.dp,
                collapsedBound = Dimensions.items.collapsedPlayerHeight + bottomDp,
                expandedBound = maxHeight
            )

            val playerAwareWindowInsets = remember(
                bottomDp,
                animatedBottomDp,
                playerBottomSheetState.value,
                imeVisible,
                imeBottomDp
            ) {
                val bottom =
                    if (imeVisible) imeBottomDp.coerceAtLeast(playerBottomSheetState.value)
                    else playerBottomSheetState.value.coerceIn(
                        animatedBottomDp..playerBottomSheetState.collapsedBound
                    )

                windowInsets
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    .add(WindowInsets(bottom = bottom))
            }

            val pip = isInPip(
                onChange = {
                    if (!it || vm.binder?.player?.shouldBePlaying != true) return@isInPip
                    playerBottomSheetState.expandSoft()
                }
            )

            KeyedCrossfade(state = pip) { currentPip ->
                if (currentPip) Thumbnail(
                    isShowingLyrics = true,
                    onShowLyrics = { },
                    isShowingStatsForNerds = false,
                    onShowStatsForNerds = { },
                    onOpenDialog = { },
                    likedAt = null,
                    setLikedAt = { },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    shouldShowSynchronizedLyrics = true,
                    setShouldShowSynchronizedLyrics = { },
                    showLyricsControls = false
                ) else CompositionLocalProvider(
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets
                ) {
                    val isDownloading by downloadState.collectAsState()

                    Box {
                        HomeScreen()
                    }

                    AnimatedVisibility(
                        visible = isDownloading,
                        modifier = Modifier.padding(playerAwareWindowInsets.asPaddingValues())
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }

                    CompositionLocalProvider(
                        LocalAppearance provides LocalAppearance.current.let {
                            if (it.colorPalette.isDark && AppearancePreferences.darkness == Darkness.AMOLED) {
                                it.copy(colorPalette = it.colorPalette.amoled())
                            } else it
                        }
                    ) {
                        Player(
                            layoutState = playerBottomSheetState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    BottomSheetMenu(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            vm.binder?.player.DisposableListener {
                object : Player.Listener {
                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) = when {
                        mediaItem == null -> {
                            maybeExitPip()
                            playerBottomSheetState.dismissSoft()
                        }

                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                            mediaItem.mediaMetadata.extras?.songBundle?.isFromPersistentQueue != true -> {
                            if (AppearancePreferences.openPlayer) playerBottomSheetState.expandSoft()
                            else Unit
                        }

                        playerBottomSheetState.dismissed -> playerBottomSheetState.collapseSoft()

                        else -> Unit
                    }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handleIntent(intent: Intent) = lifecycleScope.launch(Dispatchers.IO) {
        val extras = intent.extras?.activityIntentBundle

        when (intent.action) {
            Intent.ACTION_SEARCH -> {
                val query = extras?.query ?: return@launch
                extras.query = null

                searchResultRoute.ensureGlobal(query)
            }

            Intent.ACTION_APPLICATION_PREFERENCES -> settingsRoute.ensureGlobal()

            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                val uri = intent.data
                    ?: runCatching { extras?.text?.toUri() }.getOrNull()
                    ?: return@launch

                intent.data = null
                extras?.text = null

                handleUrl(uri, vm.awaitBinder())
            }

            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                val query = when (extras?.mediaFocus) {
                    null, "vnd.android.cursor.item/*" -> extras?.query ?: extras?.text

                    MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> extras.genre

                    MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> extras.artist

                    MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> extras.album

                    "vnd.android.cursor.item/audio" -> listOfNotNull(
                        extras.album,
                        extras.artist,
                        extras.genre,
                        extras.title
                    ).joinToString(separator = " ")

                    @Suppress("deprecation")
                    MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> extras.playlist

                    else -> null
                }

                if (!query.isNullOrBlank()) vm.awaitBinder().playFromSearch(query)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monet.removeMonetColorsChangedListener(this)
        _monet = null

        removeOnNewIntentListener(::handleIntent)
    }

    override fun onStop() {
        unbindService(serviceConnection)
        super.onStop()
    }

    override fun onMonetColorsChanged(
        monet: MonetCompat,
        monetColors: ColorScheme,
        isInitialChange: Boolean
    ) {
        if (!isInitialChange) recreate()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (AppearancePreferences.autoPip && vm.binder?.player?.shouldBePlaying == true) maybeEnterPip()
    }
}

@Suppress("CyclomaticComplexMethod")
context(context: Context)
fun handleUrl(
    uri: Uri,
    binder: PlayerService.Binder?
) {
    val path = uri.pathSegments.firstOrNull()
    Log.d(TAG, "Opening url: $uri ($path)")

    coroutineScope.launch {
        when (path) {
            "search" -> uri.getQueryParameter("q")?.let { query ->
                searchResultRoute.ensureGlobal(query)
            }

            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                val browseId = "VL$playlistId"

                if (playlistId.startsWith("OLAK5uy_")) Innertube.playlistPage(
                    body = BrowseBody(browseId = browseId)
                )
                    ?.getOrNull()
                    ?.let { page ->
                        page.songsPage?.items?.firstOrNull()?.album?.endpoint?.browseId
                            ?.let { albumRoute.ensureGlobal(it) }
                    } ?: withContext(Dispatchers.Main) {
                    context.toast(context.getString(R.string.error_url, uri))
                }
                else playlistRoute.ensureGlobal(
                    p0 = browseId,
                    p1 = uri.getQueryParameter("params"),
                    p2 = null,
                    p3 = playlistId.startsWith("RDCLAK5uy_")
                )
            }

            "channel", "c" -> uri.lastPathSegment?.let { channelId ->
                artistRoute.ensureGlobal(channelId)
            }

            else -> when {
                path == "watch" -> uri.getQueryParameter("v")

                uri.host == "youtu.be" -> path

                else -> {
                    withContext(Dispatchers.Main) {
                        context.toast(context.getString(R.string.error_url, uri))
                    }
                    null
                }
            }?.let { videoId ->
                Innertube.song(videoId)?.getOrNull()?.let { song ->
                    withContext(Dispatchers.Main) {
                        binder?.player?.forcePlay(song.asMediaItem)
                    }
                }
            }
        }
    }
}

val LocalPlayerServiceBinder = staticCompositionLocalOf<PlayerService.Binder?> { null }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No player insets provided") }
val LocalCredentialManager = staticCompositionLocalOf { Dependencies.credentialManager }

class MainApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    override fun onCreate() {
        StrictMode.setVmPolicy(
            VmPolicy.Builder()
                .let {
                    if (isAtLeastAndroid12) it.detectUnsafeIntentLaunch()
                    else it
                }
                .let {
                    if (isAtLeastAndroid17) it.detectImplicitUriPermissionGrant()
                    else it
                }
                .penaltyLog()
                .penaltyDeath()
                .build()
        )
        Dependencies.init(this)

        MonetCompat.debugLog = BuildConfig.DEBUG
        super.onCreate()

        VisitorData.restore(DataPreferences.visitorData)
        VisitorData.onMinted = { DataPreferences.visitorData = it }

        MonetCompat.enablePaletteCompat()
        ServiceNotifications.createAll()
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(this)
        .crossfade(true)
        .components {
            // Google's image CDN can take >10s to derive an uncached artwork variant, which
            // outlives the engine's default socket timeout and permanently blanks the thumbnail
            add(
                // cacheStrategy restates its default: a second argument is needed to pick the
                // four-parameter overload over the deprecated single-parameter one
                KtorNetworkFetcherFactory(
                    httpClient = {
                        HttpClient(OkHttp) {
                            install(HttpTimeout) {
                                connectTimeoutMillis = 10_000
                                socketTimeoutMillis = 30_000
                                requestTimeoutMillis = 60_000
                            }
                        }
                    },
                    cacheStrategy = { CacheStrategy.DEFAULT }
                )
            )
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.1)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("coil"))
                .maxSizeBytes(DataPreferences.coilDiskCacheMaxSize.bytes)
                .build()
        }
        .bitmapFactoryExifOrientationStrategy(ExifOrientationStrategy.IGNORE)
        .let { if (BuildConfig.DEBUG) it.logger(DebugLogger()) else it }
        .build()

    val persistMap = PersistMap()

    override val workManagerConfiguration = Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
        .build()
}

object Dependencies {
    lateinit var application: MainApplication
        private set

    val py by lazy {
        if (!Python.isStarted()) Python.start(AndroidPlatform(application))
        Python.getInstance()
    }

    private val module by lazy { py.getModule("download") }

    val quickjsPath by lazy {
        File(application.applicationInfo.nativeLibraryDir, "libqjs.so")
            .also { if (!it.canExecute()) it.setExecutable(true) }
    }

    /**
     * The visitor id goes along so yt-dlp asks YouTube as the same listener the rest of the app
     * does, rather than as an unknown one that the bot check is quicker to refuse.
     */
    fun runDownload(id: String): String = module
        .callAttr("download", quickjsPath.absolutePath, id, VisitorData.peek())
        .toString()

    /**
     * Starting the Python runtime and importing yt-dlp takes seconds, and both happen on first use
     * — which is the player's loading thread, where the wait reads as a song that refused to play
     * until it is asked two or three more times. Touching them ahead of time keeps that cost off
     * the first stream.
     */
    fun warmUpYouTubeDl() {
        runCatching { module }.exceptionOrNull()?.printStackTrace()
    }

    val credentialManager by lazy { CredentialManager.create(application) }

    internal fun init(application: MainApplication) {
        this.application = application
    }
}

open class GlobalPreferencesHolder : PreferencesHolder(Dependencies.application, "preferences")
