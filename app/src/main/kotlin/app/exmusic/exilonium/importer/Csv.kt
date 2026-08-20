package app.exmusic.exilonium.importer

private const val BOM = '\uFEFF'
private const val QUOTE = '"'
private const val SNIFF_LIMIT = 4096

private val DELIMITERS = listOf(',', ';', '\t')

/**
 * Splits [text] into records the way [RFC 4180](https://www.rfc-editor.org/rfc/rfc4180) describes:
 * a quoted field may contain the delimiter, a line break, or a doubled quote standing for a literal
 * one. A leading byte order mark, CRLF line endings, blank lines and exports delimited by a
 * semicolon or a tab instead of a comma are all tolerated, because playlist exporters emit all of
 * them.
 */
fun parseCsv(text: String): List<List<String>> {
    val body = text.removePrefix(BOM.toString())

    return CsvParser(
        text = body,
        delimiter = sniffDelimiter(body)
    ).parse()
}

/**
 * Picks the delimiter that occurs most often in the first record, ignoring anything inside quotes.
 */
private fun sniffDelimiter(text: String): Char {
    val counts = mutableMapOf<Char, Int>()
    var quoted = false

    for (char in text.take(SNIFF_LIMIT)) {
        if (char == QUOTE) quoted = !quoted
        if (!quoted && char == '\n') break
        if (!quoted && char in DELIMITERS) counts[char] = (counts[char] ?: 0) + 1
    }

    return counts.maxByOrNull { it.value }?.key ?: ','
}

private class CsvParser(
    private val text: String,
    private val delimiter: Char
) {
    private val records = mutableListOf<List<String>>()
    private val field = StringBuilder()

    private var record = mutableListOf<String>()
    private var quoted = false
    private var quotedField = false
    private var index = 0

    fun parse(): List<List<String>> {
        while (index < text.length) {
            val char = text[index]

            if (quoted) readQuoted(char) else read(char)
            index++
        }

        if (field.isNotEmpty() || record.isNotEmpty()) endRecord()

        return records
    }

    private fun readQuoted(char: Char) {
        when {
            char != QUOTE -> field.append(char)

            // A doubled quote inside a quoted field is an escaped quote, not the end of it
            text.getOrNull(index + 1) == QUOTE -> {
                field.append(QUOTE)
                index++
            }

            else -> quoted = false
        }
    }

    private fun read(char: Char) {
        when {
            // Only a quote opening the field starts a quoted field; elsewhere it is just a quote
            char == QUOTE && field.isBlank() -> {
                field.clear()
                quoted = true
                quotedField = true
            }

            char == delimiter -> endField()

            char == '\r' -> {
                if (text.getOrNull(index + 1) == '\n') index++
                endRecord()
            }

            char == '\n' -> endRecord()

            else -> field.append(char)
        }
    }

    private fun endField() {
        record += if (quotedField) field.toString() else field.toString().trim()
        field.clear()
        quotedField = false
    }

    private fun endRecord() {
        endField()
        if (record.any { it.isNotEmpty() }) records += record
        record = mutableListOf()
    }
}
