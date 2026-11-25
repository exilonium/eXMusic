package app.exmusic.exilonium.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LyricsPlus {
    private val pRegex = """<p\s+begin="([^"]+)"\s+end="([^"]+)"[^>]*>(.*?)</p>"""
        .toRegex(RegexOption.DOT_MATCHES_ALL)
    private val spanRegex = """<span\s+begin="([^"]+)"\s+end="([^"]+)"[^>]*>([^<]*)</span>([^<]*)"""
        .toRegex()

    private fun httpGet(urlStr: String, timeoutMs: Int = 5000): String? = runCatching {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 3) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            val code = conn.responseCode
            if (code in 301..308) {
                val loc = conn.getHeaderField("Location") ?: return null
                currentUrl = if (loc.startsWith("http")) loc else URL(URL(currentUrl), loc).toString()
                redirects++
                continue
            }
            if (code == 200) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
            return null
        }
        null
    }.getOrNull()

    private fun parseTimeMs(timeStr: String): Long {
        val parts = timeStr.trim().split(":")
        return when (parts.size) {
            3 -> ((parts[0].toDoubleOrNull() ?: 0.0) * 3600000 +
                (parts[1].toDoubleOrNull() ?: 0.0) * 60000 +
                (parts[2].toDoubleOrNull() ?: 0.0) * 1000).toLong()

            2 -> ((parts[0].toDoubleOrNull() ?: 0.0) * 60000 +
                (parts[1].toDoubleOrNull() ?: 0.0) * 1000).toLong()

            else -> ((parts[0].toDoubleOrNull() ?: 0.0) * 1000).toLong()
        }
    }

    private fun parseKpoeLines(lyrics: JSONArray): JSONArray {
        val outLines = JSONArray()
        for (i in 0 until lyrics.length()) {
            val item = lyrics.optJSONObject(i) ?: continue
            val time = item.optLong("time", 0L)
            val dur = item.optLong("duration", 0L)
            val text = item.optString("text", "")
            val sylArr = item.optJSONArray("syllabus")
            val outSyl = JSONArray()
            if (sylArr != null) {
                for (j in 0 until sylArr.length()) {
                    val s = sylArr.optJSONObject(j) ?: continue
                    val sTime = s.optLong("time", 0L)
                    val sDur = s.optLong("duration", 0L)
                    outSyl.put(
                        JSONObject().apply {
                            put("text", s.optString("text", ""))
                            put("start_time", sTime / 1000.0)
                            put("end_time", (sTime + sDur) / 1000.0)
                        }
                    )
                }
            }
            outLines.put(
                JSONObject().apply {
                    put("text", text)
                    put("start_time", time / 1000.0)
                    put("end_time", (time + dur) / 1000.0)
                    put("syllabus", outSyl)
                }
            )
        }
        return outLines
    }

    fun cleanTitle(title: String): String = title
        .replace(
            Regex(
                """\s*[\(\[](?:official|music|video|audio|lyric|lyrics|hd|4k|visualizer|remastered)[^\)\]]*[\)\]]""",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(
            Regex("""\s*[\(\[](?:feat\.|ft\.)[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE),
            ""
        )
        .replace(
            Regex("""\s*-\s*(?:official|music|video|audio|lyric|lyrics|hd|4k).*$""", RegexOption.IGNORE_CASE),
            ""
        )
        .trim()
        .ifEmpty { title.trim() }

    fun cleanArtist(artist: String): String = artist
        .replace(Regex("""\s*-\s*Topic""", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifEmpty { artist.trim() }

    private fun scoreMatch(title: String, target: String, duration: Long, targetDuration: Long): Int {
        val exact = title == target
        val contains = title.contains(target) || target.contains(title)
        val durMatch = targetDuration > 0 && kotlin.math.abs(duration - targetDuration) <= 4
        return when {
            exact && durMatch -> 100
            exact -> 80
            contains && durMatch -> 60
            contains -> 40
            else -> 0
        }
    }

    private fun findBestHit(
        hits: JSONArray,
        targetTitle: String,
        targetDurationSec: Long
    ): JSONObject? {
        val cleanTarget = cleanTitle(targetTitle).lowercase()
        var bestHit: JSONObject? = null
        var bestScore = 0

        for (i in 0 until hits.length()) {
            val hit = hits.optJSONObject(i)
            if (hit != null) {
                val hitTitle = cleanTitle(hit.optString("title", "")).lowercase()
                val hitDur = hit.optDouble("duration", 0.0).toLong()
                val score = scoreMatch(hitTitle, cleanTarget, hitDur, targetDurationSec)
                if (score > bestScore) {
                    bestScore = score
                    bestHit = hit
                }
            }
        }

        return if (bestScore >= 40) bestHit else null
    }

    private fun findBestResult(
        results: JSONArray,
        targetTitle: String,
        targetDurationSec: Long
    ): JSONObject? {
        val cleanTarget = cleanTitle(targetTitle).lowercase()
        var bestResult: JSONObject? = null
        var bestScore = 0

        for (i in 0 until results.length()) {
            val res = results.optJSONObject(i)
            if (res != null && res.optString("timing_type") == "word") {
                val trackName = cleanTitle(res.optString("track_name", "")).lowercase()
                val dur = res.optLong("duration", 0L)
                val score = scoreMatch(trackName, cleanTarget, dur, targetDurationSec)
                if (score > bestScore) {
                    bestScore = score
                    bestResult = res
                }
            }
        }

        return if (bestScore >= 40) bestResult else null
    }

    private fun fetchLrcRedMatch(title: String, artist: String, durationSec: Long): String? {
        val cTitle = cleanTitle(title)
        val cArtist = cleanArtist(artist)
        val qTitle = URLEncoder.encode(cTitle, "UTF-8")
        val qArtist = URLEncoder.encode(cArtist, "UTF-8")
        val url = "https://lrc.red/match.json?title=$qTitle&artist=$qArtist"
        val raw = httpGet(url) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val hits = root.optJSONArray("hits") ?: return null
        if (hits.length() == 0) return null

        val hit = findBestHit(hits, cTitle, durationSec) ?: return null
        val isrc = hit.optString("isrc", "")
        if (isrc.isBlank()) return null

        val songRaw = httpGet("https://lrc.red/s/$isrc.song.json") ?: return null
        val songObj = runCatching { JSONObject(songRaw) }.getOrNull() ?: return null
        val ttml = songObj.optString("source", "")
        if (ttml.isNotBlank()) {
            return parseTtml(ttml)
        }
        return null
    }

    private fun fetchKpoe(title: String, artist: String, durationSec: Long): String? {
        val cTitle = cleanTitle(title)
        val cArtist = cleanArtist(artist)
        val qTitle = URLEncoder.encode(cTitle, "UTF-8")
        val qArtist = URLEncoder.encode(cArtist, "UTF-8")
        val durParam = if (durationSec > 0) "&duration=$durationSec" else ""
        val endpoints = listOf(
            "https://lyricsplus.prjktla.my.id/v2/lyrics/get?title=$qTitle&artist=$qArtist$durParam",
            "https://lyricsplus.binimum.org/v2/lyrics/get?title=$qTitle&artist=$qArtist$durParam"
        )
        return endpoints.firstNotNullOfOrNull { url ->
            val raw = httpGet(url) ?: return@firstNotNullOfOrNull null
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@firstNotNullOfOrNull null
            val lyrics = root.optJSONArray("lyrics") ?: return@firstNotNullOfOrNull null
            if (lyrics.length() == 0) return@firstNotNullOfOrNull null

            val outLines = parseKpoeLines(lyrics)
            JSONObject().apply {
                put("type", root.optString("type", "Word"))
                put("lyrics", outLines)
            }.toString()
        }
    }

    private fun parseTtml(ttml: String): String? {
        val pMatches = pRegex.findAll(ttml).toList()
        if (pMatches.isEmpty()) return null

        val outLines = JSONArray()
        for (pm in pMatches) {
            val (beginStr, endStr, inner) = pm.destructured
            val pBegin = parseTimeMs(beginStr) / 1000.0
            val pEnd = parseTimeMs(endStr) / 1000.0
            val spanMatches = spanRegex.findAll(inner).toList()
            val outSyl = JSONArray()
            val textBuilder = StringBuilder()

            for (sm in spanMatches) {
                val (sBeginStr, sEndStr, sText, sTail) = sm.destructured
                val word = sText + sTail
                textBuilder.append(word)
                outSyl.put(
                    JSONObject().apply {
                        put("text", word)
                        put("start_time", parseTimeMs(sBeginStr) / 1000.0)
                        put("end_time", parseTimeMs(sEndStr) / 1000.0)
                    }
                )
            }

            val lineText = if (outSyl.length() > 0) {
                textBuilder.toString()
            } else {
                inner.replace("<[^>]*>".toRegex(), "").trim()
            }

            if (lineText.isNotBlank()) {
                outLines.put(
                    JSONObject().apply {
                        put("text", lineText)
                        put("start_time", pBegin)
                        put("end_time", if (pEnd > pBegin) pEnd else pBegin + 3.0)
                        put("syllabus", outSyl)
                    }
                )
            }
        }

        if (outLines.length() == 0) return null
        return JSONObject().apply {
            put("type", "Word")
            put("lyrics", outLines)
        }.toString()
    }

    private fun fetchLrcred(title: String, artist: String, durationSec: Long): String? {
        val cTitle = cleanTitle(title)
        val cArtist = cleanArtist(artist)
        val q = URLEncoder.encode("$cTitle $cArtist".trim(), "UTF-8")
        val url = "https://lrc.red/api/v1?q=$q"
        val raw = httpGet(url) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val results = root.optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        val best = findBestResult(results, cTitle, durationSec) ?: return null
        val lyricsUrl = best.optString("lyricsUrl", "")
        if (lyricsUrl.isBlank()) return null

        val ttml = httpGet(lyricsUrl) ?: return null
        return parseTtml(ttml)
    }

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationSeconds: Long = 0L
    ): String? = withContext(Dispatchers.IO) {
        coroutineScope {
            val channel = Channel<String?>(3)
            val jobs = listOf(
                launch { channel.send(fetchKpoe(title, artist, durationSeconds)) },
                launch { channel.send(fetchLrcRedMatch(title, artist, durationSeconds)) },
                launch { channel.send(fetchLrcred(title, artist, durationSeconds)) }
            )
            var result: String? = null
            repeat(jobs.size) {
                val res = channel.receive()
                if (res != null && result == null) {
                    result = res
                    jobs.forEach { it.cancel() }
                }
            }
            result
        }
    }
}
