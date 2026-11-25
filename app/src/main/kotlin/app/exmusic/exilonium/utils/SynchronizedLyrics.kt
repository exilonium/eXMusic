package app.exmusic.exilonium.utils

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import java.util.Locale

@Immutable
@Parcelize
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
) : Parcelable

@Immutable
@Parcelize
data class LyricSentence(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<LyricWord> = emptyList()
) : Parcelable {
    val isWordSynced: Boolean get() = words.isNotEmpty()
}

@Parcelize
@Immutable
data class SynchronizedLyricsState(
    val sentences: List<LyricSentence>?,
    val offset: Long
) : Parcelable

@Stable
class SynchronizedLyrics(
    val sentences: ImmutableList<LyricSentence>,
    val positionProvider: () -> Long
) {
    var index by mutableIntStateOf(currentIndex)
        private set

    private val currentIndex: Int
        get() {
            val position = positionProvider()
            var idx = -1
            for (i in sentences.indices) {
                if (sentences[i].startMs > position) break
                idx = i
            }
            return if (idx == -1) 0 else idx
        }

    fun update(): Boolean {
        val newIndex = currentIndex
        return if (newIndex != index) {
            index = newIndex
            true
        } else false
    }

    fun millisUntilNextSentence(): Long? {
        val position = positionProvider()
        for (sentence in sentences) {
            if (sentence.startMs > position) return sentence.startMs - position
        }
        return null
    }
}

object SynchronizedLyricsParser {
    private val lyricRegex = "^\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{2,3}))?](.*)$".toRegex()
    private val enhancedWordRegex = "<(\\d{1,2}):(\\d{2})(?:[.:](\\d{2,3}))?>".toRegex()

    fun parse(raw: String): SynchronizedLyricsState {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            return parseJson(trimmed)
        }
        return parseLrc(trimmed)
    }

    fun hasWordSync(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("{") || enhancedWordRegex.containsMatchIn(trimmed)
    }

    fun extractPlainText(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            val state = parseJson(trimmed)
            val lines = state.sentences
                ?.filter { it.text.isNotBlank() }
                ?.map { it.text }
                ?: return null
            return lines.joinToString("\n").takeIf { it.isNotBlank() }
        }
        return null
    }

    fun toLrcString(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return raw
        val state = parseJson(trimmed)
        val sentences = state.sentences ?: return raw
        val sb = StringBuilder()
        for (s in sentences) {
            if (s.text.isBlank()) continue
            val m = s.startMs / 60000
            val sec = (s.startMs % 60000) / 1000
            val ms = (s.startMs % 1000) / 10
            val tag = String.format(Locale.ROOT, "[%02d:%02d.%02d]", m, sec, ms)
            sb.append(tag).append(s.text).append("\n")
        }
        return sb.toString().trimEnd()
    }

    private fun parseJson(jsonStr: String): SynchronizedLyricsState = runCatching {
        val root = JSONObject(jsonStr)
        val lyricsArray = root.optJSONArray("lyrics") ?: return SynchronizedLyricsState(null, 0L)
        val sentences = mutableListOf<LyricSentence>()
        var lastEndMs = 0L

        for (i in 0 until lyricsArray.length()) {
            val lineObj = lyricsArray.getJSONObject(i)
            val text = lineObj.optString("text", "")
            val startMs = (lineObj.optDouble("start_time", 0.0) * 1000).toLong()
            val endMs = (lineObj.optDouble("end_time", (startMs / 1000.0) + 3.0) * 1000).toLong()

            if (startMs - lastEndMs >= 4000L) {
                sentences.add(
                    LyricSentence(
                        text = "",
                        startMs = lastEndMs,
                        endMs = startMs,
                        words = emptyList()
                    )
                )
            }
            lastEndMs = endMs

            val syllabusArray = lineObj.optJSONArray("syllabus")
            val words = mutableListOf<LyricWord>()
            if (syllabusArray != null && syllabusArray.length() > 0) {
                for (j in 0 until syllabusArray.length()) {
                    val sylObj = syllabusArray.getJSONObject(j)
                    val wText = sylObj.optString("text", "")
                    val wStart = (sylObj.optDouble("start_time", 0.0) * 1000).toLong()
                    val wEnd = (sylObj.optDouble("end_time", wStart / 1000.0) * 1000).toLong()
                    words.add(LyricWord(text = wText, startMs = wStart, endMs = wEnd))
                }
            }
            sentences.add(
                LyricSentence(
                    text = text,
                    startMs = startMs,
                    endMs = endMs,
                    words = words
                )
            )
        }

        if (sentences.isEmpty()) SynchronizedLyricsState(null, 0L)
        else SynchronizedLyricsState(sentences, 0L)
    }.getOrDefault(SynchronizedLyricsState(null, 0L))

    private fun parseTimestamp(min: String, sec: String, msPart: String): Long {
        val minutes = min.toLongOrNull() ?: 0L
        val seconds = sec.toLongOrNull() ?: 0L
        val millis = when (msPart.length) {
            1 -> (msPart.toLongOrNull() ?: 0L) * 100
            2 -> (msPart.toLongOrNull() ?: 0L) * 10
            else -> msPart.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60000L + seconds * 1000L + millis
    }

    private fun parseOffset(line: String): Long? {
        if (!line.startsWith("[offset:", ignoreCase = true)) return null
        return line.substringAfter(":").substringBefore("]").trim().toLongOrNull()
    }

    private fun parseEnhancedWords(content: String, lineTimestamp: Long, lineEndMs: Long): List<LyricWord> {
        val wordMatches = enhancedWordRegex.findAll(content).toList()
        if (wordMatches.isEmpty()) return emptyList()

        val parsedWords = mutableListOf<LyricWord>()
        var lastIdx = 0
        var currentTagTime = lineTimestamp

        for (wm in wordMatches) {
            val preText = content.substring(lastIdx, wm.range.first)
            if (preText.isNotEmpty()) {
                parsedWords.add(LyricWord(text = preText, startMs = currentTagTime, endMs = currentTagTime))
            }
            val (wMin, wSec, wMsPart) = wm.destructured
            currentTagTime = parseTimestamp(wMin, wSec, wMsPart)
            lastIdx = wm.range.last + 1
        }
        val tailText = content.substring(lastIdx)
        if (tailText.isNotEmpty()) {
            parsedWords.add(LyricWord(text = tailText, startMs = currentTagTime, endMs = currentTagTime))
        }

        for (wIdx in 0 until parsedWords.size - 1) {
            val nextStart = parsedWords[wIdx + 1].startMs
            if (nextStart > parsedWords[wIdx].startMs) {
                parsedWords[wIdx] = parsedWords[wIdx].copy(endMs = nextStart)
            }
        }
        if (parsedWords.isNotEmpty() && parsedWords.last().endMs <= parsedWords.last().startMs) {
            parsedWords[parsedWords.lastIndex] = parsedWords.last().copy(endMs = lineEndMs)
        }
        return parsedWords
    }

    private fun parseLrc(lrcText: String): SynchronizedLyricsState {
        var offset = 0L
        val rawSentences = mutableListOf<Pair<Long, String>>()

        for (line in lrcText.lines()) {
            val trimmed = line.trim()
            val parsedOffset = parseOffset(trimmed)
            if (parsedOffset != null) {
                offset = parsedOffset
            } else {
                val match = lyricRegex.find(trimmed)
                if (match != null) {
                    val (min, sec, msPart, content) = match.destructured
                    val timestamp = parseTimestamp(min, sec, msPart)
                    rawSentences.add(timestamp to content.trim())
                }
            }
        }

        if (rawSentences.isEmpty()) return SynchronizedLyricsState(null, 0L)
        rawSentences.sortBy { it.first }

        val sentences = mutableListOf<LyricSentence>()
        var lastEndMs = 0L
        for (index in rawSentences.indices) {
            val (timestamp, content) = rawSentences[index]
            if (timestamp - lastEndMs >= 4000L) {
                sentences.add(
                    LyricSentence(
                        text = "",
                        startMs = lastEndMs,
                        endMs = timestamp,
                        words = emptyList()
                    )
                )
            }
            val nextTime = rawSentences.getOrNull(index + 1)?.first ?: (timestamp + 4000L)
            val endMs = if (nextTime > timestamp) nextTime else timestamp + 4000L
            val words = parseEnhancedWords(content, timestamp, endMs)
            val cleanText = if (words.isNotEmpty()) content.replace(enhancedWordRegex, "").trim() else content

            sentences.add(
                LyricSentence(
                    text = cleanText,
                    startMs = timestamp,
                    endMs = endMs,
                    words = words
                )
            )
            lastEndMs = endMs
        }

        return SynchronizedLyricsState(sentences = sentences, offset = offset)
    }
}
