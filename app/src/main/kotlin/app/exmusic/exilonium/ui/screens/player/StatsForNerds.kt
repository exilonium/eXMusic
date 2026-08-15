package app.exmusic.exilonium.ui.screens.player

import android.content.ClipData
import android.content.ClipDescription
import android.text.format.Formatter
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.core.ui.onOverlay
import app.exmusic.core.ui.overlay
import app.exmusic.exilonium.Database
import app.exmusic.exilonium.LocalPlayerServiceBinder
import app.exmusic.exilonium.R
import app.exmusic.exilonium.models.Format
import app.exmusic.exilonium.utils.color
import app.exmusic.exilonium.utils.medium
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun StatsForNerds(
    mediaId: String,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) = AnimatedVisibility(
    visible = isDisplayed,
    enter = fadeIn(),
    exit = fadeOut()
) {
    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val clipboardManager = LocalClipboard.current

    val coroutineScope = rememberCoroutineScope()

    var cachedBytes by remember(binder, mediaId) {
        mutableLongStateOf(binder?.cache?.getCachedBytes(mediaId, 0, -1) ?: 0L)
    }

    var format by remember { mutableStateOf<Format?>(null) }

    LaunchedEffect(mediaId) {
        Database
            .format(mediaId)
            .distinctUntilChanged()
            .collectLatest { currentFormat ->
                if (currentFormat?.itag != null) format = currentFormat
            }
    }

    DisposableEffect(binder, mediaId) {
        val currentBinder = binder ?: return@DisposableEffect onDispose { }

        val listener = object : Cache.Listener {
            override fun onSpanAdded(cache: Cache, span: CacheSpan) {
                cachedBytes += span.length
            }

            override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
                cachedBytes -= span.length
            }

            override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) = Unit
        }

        currentBinder.cache.addListener(mediaId, listener)

        onDispose {
            currentBinder.cache.removeListener(mediaId, listener)
        }
    }

    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .background(colorPalette.overlay)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(all = 16.dp)
        ) {
            @Composable
            fun Text(
                text: String,
                modifier: Modifier = Modifier
            ) = BasicText(
                text = text,
                maxLines = 1,
                style = typography.xs.medium.color(colorPalette.onOverlay),
                modifier = modifier
            )

            Column(horizontalAlignment = Alignment.End) {
                Text(text = stringResource(R.string.id))
                Text(text = stringResource(R.string.itag))
                Text(text = stringResource(R.string.bitrate))
                Text(text = stringResource(R.string.size))
                Text(text = stringResource(R.string.cached))
                Text(text = stringResource(R.string.loudness))
            }

            Column {
                Text(
                    text = mediaId,
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            clipboardManager.setText(mediaId)
                        }
                    }
                )
                Text(text = format?.itag?.toString() ?: stringResource(R.string.unknown))
                Text(
                    text = when (val rate = format?.bitrate) {
                        null, 0L -> stringResource(R.string.unknown)
                        else -> stringResource(R.string.format_kbps, rate / 1000)
                    }
                )
                Text(
                    text = when (val length = format?.contentLength) {
                        null, 0L -> stringResource(R.string.unknown)
                        else -> Formatter.formatShortFileSize(context, length)
                    }
                )
                Text(
                    text = buildString {
                        append(Formatter.formatShortFileSize(context, cachedBytes))

                        format?.contentLength?.let {
                            append(" (${(cachedBytes.toFloat() / it * 100).roundToInt()}%)")
                        }
                    }
                )
                Text(
                    text = format?.loudnessDb?.let {
                        stringResource(
                            R.string.format_db,
                            "%.2f".format(it)
                        )
                    } ?: stringResource(R.string.unknown)
                )
            }
        }
    }
}

suspend fun Clipboard.setText(
    text: String,
    description: String? = null,
    mimeTypes: List<String> = listOf("text/plain")
) = setClipEntry(
    ClipEntry(
        ClipData(
            /* description = */ ClipDescription(
                /* label = */ description ?: text,
                /* mimeTypes = */ mimeTypes.toTypedArray()
            ),
            /* item = */ ClipData.Item(text)
        )
    )
)
