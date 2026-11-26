package app.exmusic.exilonium.ui.screens.player

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.core.ui.onOverlay
import app.exmusic.core.ui.onOverlayShimmer
import app.exmusic.core.ui.overlay
import app.exmusic.core.ui.utils.dp
import app.exmusic.exilonium.Database
import app.exmusic.exilonium.LocalPlayerServiceBinder
import app.exmusic.exilonium.R
import app.exmusic.exilonium.models.Lyrics
import app.exmusic.exilonium.preferences.PlayerPreferences
import app.exmusic.exilonium.query
import app.exmusic.exilonium.service.LOCAL_KEY_PREFIX
import app.exmusic.exilonium.transaction
import app.exmusic.exilonium.ui.components.LocalMenuState
import app.exmusic.exilonium.ui.components.themed.CircularProgressIndicator
import app.exmusic.exilonium.ui.components.themed.DefaultDialog
import app.exmusic.exilonium.ui.components.themed.Menu
import app.exmusic.exilonium.ui.components.themed.MenuEntry
import app.exmusic.exilonium.ui.components.themed.TextField
import app.exmusic.exilonium.ui.components.themed.TextFieldDialog
import app.exmusic.exilonium.ui.components.themed.TextPlaceholder
import app.exmusic.exilonium.ui.components.themed.ValueSelectorDialogBody
import app.exmusic.exilonium.ui.modifiers.verticalFadingEdge
import app.exmusic.exilonium.utils.DisposableListener
import app.exmusic.exilonium.utils.LyricSentence
import app.exmusic.exilonium.utils.LyricWord
import app.exmusic.exilonium.utils.LyricsPlus
import app.exmusic.exilonium.utils.SynchronizedLyrics
import app.exmusic.exilonium.utils.SynchronizedLyricsParser
import app.exmusic.exilonium.utils.SynchronizedLyricsState
import app.exmusic.exilonium.utils.center
import app.exmusic.exilonium.utils.color
import app.exmusic.exilonium.utils.isInPip
import app.exmusic.exilonium.utils.medium
import app.exmusic.exilonium.utils.semiBold
import app.exmusic.exilonium.utils.toast
import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.bodies.NextBody
import app.exmusic.providers.innertube.requests.lyrics
import app.exmusic.providers.kugou.KuGou
import app.exmusic.providers.lrclib.LrcLib
import app.exmusic.providers.lrclib.models.Track
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val UPDATE_DELAY = 50L
private const val MAX_UPDATE_DELAY = 500L

// How long after a drag the lyrics stay where they were put before re-centering on the playing line
private val AUTO_SCROLL_RESUME_DELAY = 4.seconds

// How much a synchronized lyrics line shrinks while it is being pressed, so a tap is visible
private const val PRESSED_LINE_SCALE = 0.94f

private data class WordCharRange(
    val word: LyricWord,
    val startOffset: Int,
    val endOffset: Int
)

private fun calculateWipeX(
    layout: TextLayoutResult,
    activeRange: WordCharRange,
    currentPos: Long,
    isWiping: Boolean
): Float {
    val trimmedLength = activeRange.word.text.trimEnd().length.coerceAtLeast(1)
    val glyphEndOffset = (activeRange.startOffset + trimmedLength).coerceAtMost(layout.layoutInput.text.length)
    val wordEndX = layout.getHorizontalPosition(glyphEndOffset, true)
    if (!isWiping) return wordEndX

    val duration = (activeRange.word.endMs - activeRange.word.startMs).coerceAtLeast(1L)
    val progress = ((currentPos - activeRange.word.startMs).toFloat() / duration).coerceIn(0f, 1f)
    val wordStartX = layout.getHorizontalPosition(activeRange.startOffset, true)
    return wordStartX + (wordEndX - wordStartX) * progress
}

private fun buildWipePath(
    path: Path,
    layout: TextLayoutResult,
    wordRanges: List<WordCharRange>,
    currentPos: Long,
    containerWidth: Float
) {
    val activeIndex = wordRanges.indexOfLast { currentPos >= it.word.startMs }
    if (activeIndex == -1) return

    val activeRange = wordRanges[activeIndex]
    val activeLine = layout.getLineForOffset(activeRange.startOffset)

    for (l in 0 until activeLine) {
        path.addRect(Rect(0f, layout.getLineTop(l), containerWidth, layout.getLineBottom(l)))
    }

    val isWiping = currentPos < activeRange.word.endMs
    val wipeX = calculateWipeX(layout, activeRange, currentPos, isWiping)
    val lineTop = layout.getLineTop(activeLine)
    val lineBottom = layout.getLineBottom(activeLine)
    val isRtl = layout.getParagraphDirection(activeRange.startOffset) == ResolvedTextDirection.Rtl

    if (!isRtl) {
        path.addRect(Rect(0f, lineTop, wipeX.coerceAtLeast(0f), lineBottom))
    } else {
        path.addRect(Rect(wipeX.coerceAtMost(containerWidth), lineTop, containerWidth, lineBottom))
    }
}

@Composable
private fun WordSyncedLyricLine(
    sentence: LyricSentence,
    positionProvider: () -> Long,
    isPlaying: Boolean,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    playbackSpeed: Float = 1f
) {
    val currentPositionProvider by rememberUpdatedState(positionProvider)
    val currentPlaybackSpeed by rememberUpdatedState(playbackSpeed)
    var position by remember(sentence) { mutableLongStateOf(positionProvider()) }

    LaunchedEffect(isPlaying, sentence) {
        if (!isPlaying) {
            position = currentPositionProvider()
            return@LaunchedEffect
        }

        var lastRealtime = SystemClock.elapsedRealtime()
        var lastPlayerPos = currentPositionProvider()

        while (true) {
            withFrameMillis {
                val now = SystemClock.elapsedRealtime()
                val freshPos = currentPositionProvider()

                if (freshPos != lastPlayerPos) {
                    lastPlayerPos = freshPos
                    lastRealtime = now
                    position = freshPos
                } else {
                    val dt = now - lastRealtime
                    position = lastPlayerPos + (dt * currentPlaybackSpeed).toLong()
                }
            }
        }
    }

    val wordRanges = remember(sentence) {
        val ranges = mutableListOf<WordCharRange>()
        var searchIdx = 0
        for (word in sentence.words) {
            if (word.text.isEmpty()) continue
            var start = sentence.text.indexOf(word.text, searchIdx)
            if (start == -1) {
                start = sentence.text.indexOf(word.text)
            }
            if (start != -1) {
                val end = (start + word.text.length).coerceAtMost(sentence.text.length)
                ranges.add(WordCharRange(word, start, end))
                searchIdx = end
            }
        }
        ranges
    }

    if (wordRanges.isEmpty()) {
        BasicText(
            text = sentence.text,
            style = textStyle.color(Color.White),
            modifier = modifier
        )
        return
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val clipPath = remember { Path() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = sentence.text,
            style = textStyle.color(Color.White.copy(alpha = 0.35f)),
            onTextLayout = { textLayoutResult = it }
        )

        BasicText(
            text = sentence.text,
            style = textStyle.color(Color.White),
            modifier = Modifier.drawWithContent {
                val layout = textLayoutResult
                val currentPos = position
                if (layout == null || currentPos < wordRanges.first().word.startMs) {
                    return@drawWithContent
                }

                if (currentPos >= wordRanges.last().word.endMs) {
                    drawContent()
                    return@drawWithContent
                }

                clipPath.reset()
                buildWipePath(clipPath, layout, wordRanges, currentPos, size.width)
                clipPath(clipPath) {
                    this@drawWithContent.drawContent()
                }
            }
        )
    }
}

enum class LyricsMode {
    WORD_SYNC,
    LINE_SYNC,
    UNSYNC
}

@Composable
fun Lyrics(
    mediaId: String,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    mediaMetadataProvider: () -> MediaMetadata,
    durationProvider: () -> Long,
    ensureSongInserted: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuLaunch: () -> Unit = { },
    onOpenDialog: (() -> Unit)? = null,
    onClose: () -> Unit = onDismiss,
    shouldShowSynchronizedLyrics: Boolean = PlayerPreferences.isShowingSynchronizedLyrics,
    setShouldShowSynchronizedLyrics: (Boolean) -> Unit = {
        PlayerPreferences.isShowingSynchronizedLyrics = it
    },
    isWordSyncedEnabled: Boolean = PlayerPreferences.wordSyncedLyrics,
    setIsWordSyncedEnabled: (Boolean) -> Unit = {
        PlayerPreferences.wordSyncedLyrics = it
    },
    shouldKeepScreenAwake: Boolean = PlayerPreferences.lyricsKeepScreenAwake,
    shouldUpdateLyrics: Boolean = true,
    showControls: Boolean = true
) = AnimatedVisibility(
    visible = isDisplayed,
    enter = fadeIn(),
    exit = fadeOut()
) {
    val currentEnsureSongInserted by rememberUpdatedState(ensureSongInserted)
    val currentMediaMetadataProvider by rememberUpdatedState(mediaMetadataProvider)
    val currentDurationProvider by rememberUpdatedState(durationProvider)

    val (colorPalette, typography, _, thumbnailShape) = LocalAppearance.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val density = LocalDensity.current
    val view = LocalView.current

    val pip = isInPip()

    var isPlaying by remember { mutableStateOf(binder?.player?.isPlaying == true) }
    binder?.player.DisposableListener {
        object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
    }

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }

    val showSynchronizedLyrics = remember(shouldShowSynchronizedLyrics, lyrics) {
        shouldShowSynchronizedLyrics && lyrics?.synced?.isBlank() != true
    }

    var editing by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var picking by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var error by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }

    val text = remember(lyrics, showSynchronizedLyrics) {
        if (showSynchronizedLyrics) lyrics?.synced else lyrics?.fixed
    }
    var invalidLrc by remember(text) { mutableStateOf(false) }

    DisposableEffect(shouldKeepScreenAwake) {
        view.keepScreenOn = shouldKeepScreenAwake

        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(mediaId, shouldShowSynchronizedLyrics, isWordSyncedEnabled) {
        runCatching {
            withContext(Dispatchers.IO) {
                Database
                    .lyrics(mediaId)
                    .distinctUntilChanged()
                    .cancellable()
                    .collect { currentLyrics ->
                        val hasSynced = !currentLyrics?.synced.isNullOrBlank()
                        val hasFixed = !currentLyrics?.fixed.isNullOrBlank()
                        val isWordSynced = hasSynced &&
                            SynchronizedLyricsParser.hasWordSync(currentLyrics.synced.orEmpty())

                        if (currentLyrics != null) {
                            withContext(Dispatchers.Main) {
                                lyrics = currentLyrics
                                error = (shouldShowSynchronizedLyrics && !hasSynced) ||
                                    (!shouldShowSynchronizedLyrics && !hasFixed)
                            }
                        }

                        val missingLyrics = !hasFixed || !hasSynced
                        val missingWordSync = shouldShowSynchronizedLyrics &&
                            isWordSyncedEnabled &&
                            !isWordSynced
                        val needsFetch = (!shouldUpdateLyrics && currentLyrics == null) ||
                            (shouldUpdateLyrics && (missingLyrics || missingWordSync))

                        if (!needsFetch) return@collect

                        val mediaMetadata = currentMediaMetadataProvider()
                        var duration = withContext(Dispatchers.Main) { currentDurationProvider() }
                        var attempts = 0
                        while (duration == C.TIME_UNSET && attempts < 10) {
                            delay(100.milliseconds)
                            duration = withContext(Dispatchers.Main) { currentDurationProvider() }
                            attempts++
                        }
                        val durationSeconds = if (duration != C.TIME_UNSET) duration / 1000 else 0L

                        val album = mediaMetadata.albumTitle?.toString()
                        val artist = mediaMetadata.artist?.toString().orEmpty()
                        val title = mediaMetadata.title?.toString().orEmpty().let {
                            if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                                .substringBeforeLast('.')
                                .trim()
                            else it
                        }

                        val youLySynced = if (!isWordSynced && isWordSyncedEnabled) {
                            LyricsPlus.fetchLyrics(
                                title = title,
                                artist = artist,
                                durationSeconds = durationSeconds
                            )
                        } else null

                        val currentFixed = currentLyrics?.fixed?.takeIf { it.isNotBlank() }
                        val fixed = currentFixed
                            ?: Innertube
                                .lyrics(NextBody(videoId = mediaId))
                                ?.getOrNull()
                            ?: youLySynced?.let {
                                SynchronizedLyricsParser.extractPlainText(it)
                            }
                            ?: LrcLib.bestLyrics(
                                artist = artist,
                                title = title,
                                duration = duration.milliseconds,
                                album = album,
                                synced = false
                            )?.map { it?.text }?.getOrNull()

                        val currentSynced = currentLyrics?.synced?.takeIf { it.isNotBlank() }
                        val synced = youLySynced
                            ?: currentSynced
                            ?: LrcLib.bestLyrics(
                                artist = artist,
                                title = title,
                                duration = duration.milliseconds,
                                album = album
                            )?.map { it?.text }?.getOrNull()
                            ?: LrcLib.bestLyrics(
                                artist = artist,
                                title = title.split("(")[0].trim(),
                                duration = duration.milliseconds,
                                album = album
                            )?.map { it?.text }?.getOrNull()
                            ?: KuGou.lyrics(
                                artist = artist,
                                title = title,
                                duration = durationSeconds
                            )?.map { it?.value }?.getOrNull()

                        val updated = Lyrics(
                            songId = mediaId,
                            fixed = fixed.orEmpty(),
                            synced = synced.orEmpty()
                        )

                        withContext(Dispatchers.Main) {
                            lyrics = updated
                            error = (shouldShowSynchronizedLyrics && updated.synced?.isBlank() == true) ||
                                (!shouldShowSynchronizedLyrics && updated.fixed?.isBlank() == true)
                        }

                        this@withContext.ensureActive()
                        transaction {
                            runCatching {
                                currentEnsureSongInserted()
                                Database.upsert(updated)
                            }
                        }
                    }
            }
        }.exceptionOrNull()?.let {
            if (it is CancellationException) throw it
            else it.printStackTrace()
        }
    }

    if (editing) TextFieldDialog(
        hintText = stringResource(R.string.enter_lyrics),
        initialTextInput = (if (shouldShowSynchronizedLyrics) {
            lyrics?.synced?.let { SynchronizedLyricsParser.toLrcString(it) }
        } else lyrics?.fixed).orEmpty(),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 10),
        isTextInputValid = { true },
        onDismiss = { editing = false },
        onAccept = {
            transaction {
                runCatching {
                    currentEnsureSongInserted()

                    Database.upsert(
                        if (shouldShowSynchronizedLyrics) Lyrics(
                            songId = mediaId,
                            fixed = lyrics?.fixed,
                            synced = it
                        ) else Lyrics(
                            songId = mediaId,
                            fixed = it,
                            synced = lyrics?.synced
                        )
                    )
                }
            }
        }
    )

    if (picking && shouldShowSynchronizedLyrics) {
        var query by rememberSaveable {
            mutableStateOf(
                currentMediaMetadataProvider().title?.toString().orEmpty().let {
                    if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                        .substringBeforeLast('.')
                        .trim()
                    else it
                }
            )
        }

        LrcLibSearchDialog(
            query = query,
            setQuery = { query = it },
            onDismiss = { picking = false },
            onPick = {
                runCatching {
                    transaction {
                        Database.upsert(
                            Lyrics(
                                songId = mediaId,
                                fixed = lyrics?.fixed,
                                synced = it.syncedLyrics
                            )
                        )
                    }
                }
            }
        )
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .fillMaxSize()
            .background(colorPalette.overlay)
    ) {
        val animatedHeight by animateDpAsState(
            targetValue = this.maxHeight, // `this` needed, thanks Android Lint!
            label = ""
        )

        AnimatedVisibility(
            visible = error,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(
                    if (shouldShowSynchronizedLyrics) R.string.synchronized_lyrics_not_available
                    else R.string.lyrics_not_available
                ),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = !text.isNullOrBlank() && !error && invalidLrc && shouldShowSynchronizedLyrics,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(R.string.invalid_synchronized_lyrics),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        val lyricsState = rememberSaveable(text) {
            text?.takeIf { it.isNotBlank() }?.let {
                SynchronizedLyricsParser.parse(it)
            } ?: SynchronizedLyricsState(sentences = null, offset = 0L)
        }

        val synchronizedLyrics = remember(lyricsState) {
            invalidLrc = lyricsState.sentences == null
            lyricsState.sentences?.let {
                SynchronizedLyrics(it.toImmutableList()) {
                    binder?.player?.let { player ->
                        player.currentPosition + UPDATE_DELAY + lyricsState.offset -
                            (lyrics?.startTime ?: 0L)
                    } ?: 0L
                }
            }
        }

        AnimatedContent(
            targetState = showSynchronizedLyrics,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { synchronized ->
            val lazyListState = rememberLazyListState()
            if (synchronized) {
                // Bumped whenever a line is tapped, so the scroll loop restarts and re-centers on
                // the line we just seeked to instead of waiting for the next poll
                var seekTrigger by remember { mutableIntStateOf(0) }

                // Reading ahead means scrolling away from the playing line, so the automatic
                // scrolling stands down while a drag is in progress and for a moment after it, then
                // re-centers. Without the pause it would yank the list back mid-gesture.
                val dragged by lazyListState.interactionSource.collectIsDraggedAsState()
                var autoScroll by remember { mutableStateOf(true) }

                LaunchedEffect(dragged) {
                    if (dragged) autoScroll = false
                    else if (!autoScroll) {
                        delay(AUTO_SCROLL_RESUME_DELAY)
                        autoScroll = true
                    }
                }

                LaunchedEffect(synchronizedLyrics, density, animatedHeight, seekTrigger, autoScroll) {
                    if (!autoScroll) return@LaunchedEffect
                    val currentSynchronizedLyrics = synchronizedLyrics ?: return@LaunchedEffect
                    val centerOffset = with(density) { (-animatedHeight / 3).roundToPx() }

                    lazyListState.animateScrollToItem(
                        index = currentSynchronizedLyrics.index + 1,
                        scrollOffset = centerOffset
                    )

                    while (true) {
                        // Sleep until the next sentence is due instead of polling at 20 Hz; the
                        // cap keeps seeks and speed changes from stalling the scroll for long
                        val speed = binder?.player?.playbackParameters?.speed
                            ?.takeIf { it > 0f } ?: 1f

                        delay(
                            currentSynchronizedLyrics.millisUntilNextSentence()
                                ?.let { (it / speed).toLong() }
                                ?.coerceIn(UPDATE_DELAY, MAX_UPDATE_DELAY)
                                ?: MAX_UPDATE_DELAY
                        )
                        if (!currentSynchronizedLyrics.update()) continue

                        lazyListState.animateScrollToItem(
                            index = currentSynchronizedLyrics.index + 1,
                            scrollOffset = centerOffset
                        )
                    }
                }

                val lyricStyle = typography.xs.center.medium

                if (synchronizedLyrics != null) LazyColumn(
                    state = lazyListState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .verticalFadingEdge()
                        .fillMaxWidth()
                ) {
                    item(key = "header", contentType = 0) {
                        Spacer(modifier = Modifier.height(maxHeight))
                    }
                    itemsIndexed(
                        items = synchronizedLyrics.sentences
                    ) { index, sentence ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val isCurrentLine = index == synchronizedLyrics.index

                        val color by animateColorAsState(
                            if (pressed || isCurrentLine) Color.White
                            else colorPalette.textDisabled
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (pressed) PRESSED_LINE_SCALE else 1f,
                            label = ""
                        )

                        val lineModifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                enabled = binder?.player != null
                            ) {
                                binder?.player?.seekTo(
                                    (sentence.startMs - lyricsState.offset + (lyrics?.startTime ?: 0L))
                                        .coerceAtLeast(0L)
                                )
                                synchronizedLyrics.update()
                                seekTrigger++
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .padding(vertical = 4.dp)

                        if (sentence.text.isBlank()) Image(
                            painter = painterResource(R.drawable.musical_notes),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(color),
                            modifier = lineModifier.height(typography.xs.fontSize.dp)
                        ) else if (isCurrentLine && sentence.isWordSynced && isWordSyncedEnabled) WordSyncedLyricLine(
                            sentence = sentence,
                            positionProvider = synchronizedLyrics.positionProvider,
                            isPlaying = isPlaying,
                            textStyle = lyricStyle,
                            modifier = lineModifier,
                            playbackSpeed = binder?.player?.playbackParameters?.speed ?: 1f
                        ) else BasicText(
                            text = sentence.text,
                            style = lyricStyle.color(color),
                            modifier = lineModifier
                        )
                    }
                    item(key = "footer", contentType = 0) {
                        Spacer(modifier = Modifier.height(maxHeight))
                    }
                }
            } else BasicText(
                text = lyrics?.fixed.orEmpty(),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .verticalFadingEdge()
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(vertical = maxHeight / 4, horizontal = 28.dp)
            )
        }

        if (text == null && !error) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.shimmer()
        ) {
            repeat(4) {
                TextPlaceholder(
                    color = colorPalette.onOverlayShimmer,
                    modifier = Modifier.alpha(1f - it * 0.2f)
                )
            }
        }

        if (showControls) {
            // With no lyrics the overlay is a bare scrim, so a tap anywhere already dismisses it and
            // an explicit close button is only clutter. It exists because tapping a synchronized
            // line seeks instead of dismissing, which cannot happen when there are no lines.
            if (!text.isNullOrBlank() && !error) Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(56.dp)
                    .clickable(
                        indication = ripple(bounded = false, radius = 24.dp),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClose
                    )
            ) {
                Image(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (onOpenDialog != null) Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(56.dp)
                    .clickable(
                        indication = ripple(bounded = false, radius = 24.dp),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onOpenDialog()
                        }
                    )
            ) {
                Image(
                    painter = painterResource(R.drawable.expand),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(56.dp)
                    .clickable(
                        indication = ripple(bounded = false, radius = 24.dp),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onMenuLaunch()
                            menuState.display {
                                Menu {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 6.dp)
                                    ) {
                                        val currentMode = when {
                                            !shouldShowSynchronizedLyrics -> LyricsMode.UNSYNC
                                            isWordSyncedEnabled -> LyricsMode.WORD_SYNC
                                            else -> LyricsMode.LINE_SYNC
                                        }

                                        val pills = listOf(
                                            Triple(LyricsMode.WORD_SYNC, "Word-sync", R.drawable.sparkles),
                                            Triple(LyricsMode.LINE_SYNC, "Sync", R.drawable.time),
                                            Triple(LyricsMode.UNSYNC, "Unsync", R.drawable.text)
                                        )

                                        pills.forEach { (mode, label, icon) ->
                                            val isSelected = currentMode == mode

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(thumbnailShape)
                                                    .clickable {
                                                        when (mode) {
                                                            LyricsMode.WORD_SYNC -> {
                                                                setShouldShowSynchronizedLyrics(true)
                                                                setIsWordSyncedEnabled(true)
                                                            }

                                                            LyricsMode.LINE_SYNC -> {
                                                                setShouldShowSynchronizedLyrics(true)
                                                                setIsWordSyncedEnabled(false)
                                                            }

                                                            LyricsMode.UNSYNC -> {
                                                                setShouldShowSynchronizedLyrics(false)
                                                            }
                                                        }
                                                        menuState.hide()
                                                    }
                                                    .background(
                                                        if (isSelected) colorPalette.accent
                                                        else colorPalette.background0,
                                                        thumbnailShape
                                                    )
                                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                            ) {
                                                Image(
                                                    painter = painterResource(icon),
                                                    contentDescription = null,
                                                    colorFilter = ColorFilter.tint(
                                                        if (isSelected) colorPalette.onAccent
                                                        else colorPalette.textSecondary
                                                    ),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                BasicText(
                                                    text = label,
                                                    style = typography.xxs.medium.center.copy(
                                                        color = if (isSelected) colorPalette.onAccent
                                                        else colorPalette.textSecondary
                                                    ),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    MenuEntry(
                                        icon = R.drawable.pencil,
                                        text = stringResource(R.string.edit_lyrics),
                                        onClick = {
                                            menuState.hide()
                                            editing = true
                                        }
                                    )

                                    val errorMsg = stringResource(R.string.no_browser_installed)
                                    MenuEntry(
                                        icon = R.drawable.search,
                                        text = stringResource(R.string.search_lyrics_online),
                                        onClick = {
                                            menuState.hide()
                                            val mediaMetadata = currentMediaMetadataProvider()

                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                                                        putExtra(
                                                            SearchManager.QUERY,
                                                            "${mediaMetadata.title} ${mediaMetadata.artist} lyrics"
                                                        )
                                                    }
                                                )
                                            } catch (_: ActivityNotFoundException) {
                                                context.toast(errorMsg)
                                            }
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.sync,
                                        text = stringResource(R.string.refetch_lyrics),
                                        enabled = lyrics != null,
                                        onClick = {
                                            menuState.hide()

                                            transaction {
                                                runCatching {
                                                    currentEnsureSongInserted()

                                                    Database.upsert(
                                                        if (shouldShowSynchronizedLyrics) Lyrics(
                                                            songId = mediaId,
                                                            fixed = lyrics?.fixed,
                                                            synced = null
                                                        ) else Lyrics(
                                                            songId = mediaId,
                                                            fixed = null,
                                                            synced = lyrics?.synced
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    if (shouldShowSynchronizedLyrics) {
                                        MenuEntry(
                                            icon = R.drawable.download,
                                            text = stringResource(R.string.pick_from_lrclib),
                                            onClick = {
                                                menuState.hide()
                                                picking = true
                                            }
                                        )
                                        MenuEntry(
                                            icon = R.drawable.play_skip_forward,
                                            text = stringResource(R.string.set_lyrics_start_offset),
                                            secondaryText = stringResource(
                                                R.string.set_lyrics_start_offset_description
                                            ),
                                            onClick = {
                                                menuState.hide()
                                                lyrics?.let {
                                                    val startTime = binder?.player?.currentPosition
                                                    query {
                                                        Database.upsert(it.copy(startTime = startTime))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
            ) {
                Image(
                    painter = painterResource(R.drawable.ellipsis_horizontal),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LrcLibSearchDialog(
    query: String,
    setQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Track) -> Unit,
    modifier: Modifier = Modifier
) = DefaultDialog(
    onDismiss = onDismiss,
    horizontalPadding = 0.dp,
    modifier = modifier
) {
    val (_, typography) = LocalAppearance.current

    val tracks = remember { mutableStateListOf<Track>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        loading = true
        error = false

        delay(1000.milliseconds)

        LrcLib.lyrics(
            query = query,
            synced = true
        )?.onSuccess { newTracks ->
            tracks.clear()
            tracks.addAll(newTracks.filter { !it.syncedLyrics.isNullOrBlank() })
            loading = false
            error = false
        }?.onFailure {
            loading = false
            error = true
            it.printStackTrace()
        } ?: run { loading = false }
    }

    TextField(
        initialText = query,
        onValueChange = setQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        lineLimits = TextFieldLineLimits.SingleLine
    )
    Spacer(modifier = Modifier.height(8.dp))

    when {
        loading -> CircularProgressIndicator(
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        error || tracks.isEmpty() -> BasicText(
            text = stringResource(R.string.no_lyrics_found),
            style = typography.s.semiBold.center,
            modifier = Modifier
                .padding(all = 24.dp)
                .align(Alignment.CenterHorizontally)
        )

        else -> ValueSelectorDialogBody(
            onDismiss = onDismiss,
            title = stringResource(R.string.choose_lyric_track),
            selectedValue = null,
            values = tracks.toImmutableList(),
            onValueSelect = {
                transaction {
                    onPick(it)
                    onDismiss()
                }
            },
            valueText = {
                "${it.artistName} - ${it.trackName} (${
                    it.duration.seconds.toComponents { minutes, seconds, _ ->
                        "$minutes:${seconds.toString().padStart(2, '0')}"
                    }
                })"
            }
        )
    }
}
