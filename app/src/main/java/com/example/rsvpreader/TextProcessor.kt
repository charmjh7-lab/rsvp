package com.example.rsvpreader

import java.text.BreakIterator
import java.util.Locale

object TextProcessor {
    private val commonAbbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
        "e.g", "i.e", "u.s", "u.k", "no", "fig", "dept", "inc", "ltd", "co"
    )

    private val connectorChars = setOf(
        ',', '，', '、', 'ㆍ', '·', '-', '‐', '‑', '‒', '–', '—', '~', '～', ':', ';', '：', '；'
    )
    private val weakPauseChars = setOf(',', '，', '、', 'ㆍ', '·', ':', ';', '：', '；')
    private val strongPauseChars = setOf('.', '。', '?', '？', '!', '！')
    private val openingChars = setOf('(', '[', '{', '〈', '《', '「', '『', '【', '〔', '“', '‘', '"', '\'', '<')
    private val closingChars = setOf(')', ']', '}', '〉', '》', '」', '』', '】', '〕', '”', '’', '"', '\'', '>')

    fun process(rawText: String): ProcessedDocument {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return ProcessedDocument("", emptyList(), emptyList())

        val sentenceRanges = splitSentences(normalized)
        val tokens = mutableListOf<RsvpToken>()
        val sentences = mutableListOf<SentenceUnit>()

        sentenceRanges.forEachIndexed { sentenceIndex, range ->
            val sentenceText = normalized.substring(range.first, range.second)
            val sentenceTokenStart = tokens.size
            val localTokens = tokenizeSentence(sentenceText, range.first, sentenceIndex, sentenceTokenStart)
            tokens += localTokens
            sentences += SentenceUnit(
                text = sentenceText,
                start = range.first,
                endExclusive = range.second,
                tokenStartGlobal = sentenceTokenStart,
                tokenCount = localTokens.size
            )
        }

        return ProcessedDocument(normalized, sentences, tokens)
    }

    fun normalize(rawText: String): String {
        var text = rawText
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')

        // EPUB/TXT paragraphs are preserved; single hard wraps (common in PDF extraction) become spaces.
        text = text.replace(Regex("[\\t ]+"), " ")
        text = text.replace(Regex(" *\\n *\\n+ *"), "\n\n")
        text = text.replace(Regex("(?<!\\n)\\n(?!\\n)"), " ")
        text = text.replace(Regex(" {2,}"), " ")
        text = text.replace(Regex(" *\\n\\n *"), "\n\n")
        return text.trim()
    }

    private fun splitSentences(text: String): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = 0
        var i = 0

        fun addRange(endExclusive: Int) {
            var s = start
            var e = endExclusive
            while (s < e && text[s].isWhitespace()) s++
            while (e > s && text[e - 1].isWhitespace()) e--
            if (e > s) ranges += s to e
            start = endExclusive
        }

        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '\n' && text[i + 1] == '\n') {
                addRange(i)
                i += 2
                start = i
                continue
            }

            val ch = text[i]
            if (strongPauseChars.contains(ch)) {
                val isPeriodLike = ch == '.' || ch == '。'
                val shouldSplit = !isPeriodLike || !isInternalPeriod(text, i)
                if (shouldSplit) {
                    var end = i + 1
                    while (end < text.length && closingChars.contains(text[end])) end++
                    val after = nextNonWhitespace(text, end)
                    if (after == -1 || end < text.length && text.substring(end, after).isNotEmpty() || end == text.length) {
                        addRange(end)
                        i = end
                        start = end
                        continue
                    }
                }
            }
            i++
        }
        if (start < text.length) addRange(text.length)
        return ranges.ifEmpty { listOf(0 to text.length) }
    }

    private fun isInternalPeriod(text: String, index: Int): Boolean {
        val prev = text.getOrNull(index - 1)
        val next = text.getOrNull(index + 1)
        if (prev?.isDigit() == true && next?.isDigit() == true) return true

        var s = index - 1
        while (s >= 0 && (text[s].isLetter() || text[s] == '.')) s--
        val beforeWithDots = text.substring(s + 1, index)
        val normalized = beforeWithDots.lowercase(Locale.ROOT).trim('.')
        if (normalized in commonAbbreviations) return true

        val includingCurrentDot = text.substring(s + 1, index + 1)
        if (Regex("(?i)(?:[a-z]\\.){2,}").matches(includingCurrentDot)) return true

        // A single-letter initial in a name, e.g. "A. Smith".
        if (beforeWithDots.length == 1 && beforeWithDots[0].isUpperCase()) {
            val nextNonSpace = nextNonWhitespace(text, index + 1)
            if (nextNonSpace != -1 && text[nextNonSpace].isLetter()) return true
        }
        return false
    }

    private fun nextNonWhitespace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return if (i < text.length) i else -1
    }

    private fun tokenizeSentence(
        sentence: String,
        globalStart: Int,
        sentenceIndex: Int,
        globalTokenStart: Int
    ): List<RsvpToken> {
        val out = mutableListOf<RsvpToken>()
        var i = 0

        while (i < sentence.length) {
            while (i < sentence.length && sentence[i].isWhitespace()) i++
            if (i >= sentence.length) break

            val tokenStart = i
            val urlEnd = matchSpecial(sentence, i)
            if (urlEnd != null) {
                addToken(out, sentence, tokenStart, urlEnd, globalStart, sentenceIndex, globalTokenStart)
                i = urlEnd
                continue
            }

            while (i < sentence.length && openingChars.contains(sentence[i])) i++
            if (i >= sentence.length) {
                addToken(out, sentence, tokenStart, i, globalStart, sentenceIndex, globalTokenStart)
                break
            }

            // Number expressions keep decimal/thousands separators inside one token.
            if (sentence[i].isDigit()) {
                var j = i + 1
                while (j < sentence.length) {
                    val c = sentence[j]
                    val p = sentence.getOrNull(j - 1)
                    val n = sentence.getOrNull(j + 1)
                    if (c.isDigit() || (c == ',' && p?.isDigit() == true && n?.isDigit() == true) ||
                        (c == '.' && p?.isDigit() == true && n?.isDigit() == true)) {
                        j++
                    } else break
                }
                while (j < sentence.length && (sentence[j] == '%' || sentence[j].isLetter())) j++
                while (j < sentence.length && closingChars.contains(sentence[j])) j++
                if (j < sentence.length && (weakPauseChars.contains(sentence[j]) || strongPauseChars.contains(sentence[j]))) j++
                addToken(out, sentence, tokenStart, j, globalStart, sentenceIndex, globalTokenStart)
                i = j
                continue
            }

            var j = i
            while (j < sentence.length) {
                val c = sentence[j]
                if (c.isWhitespace()) break

                if (connectorChars.contains(c)) {
                    val prev = sentence.getOrNull(j - 1)
                    val next = sentence.getOrNull(j + 1)
                    val numericInternal = (c == ',' || c == '.') && prev?.isDigit() == true && next?.isDigit() == true
                    if (numericInternal) {
                        j++
                        continue
                    }
                    j++
                    while (j < sentence.length && closingChars.contains(sentence[j])) j++
                    break
                }

                if (strongPauseChars.contains(c)) {
                    if (c == '.' && isInternalPeriod(sentence, j)) {
                        j++
                        continue
                    }
                    j++
                    while (j < sentence.length && closingChars.contains(sentence[j])) j++
                    break
                }
                j++
            }

            if (j == tokenStart) j++
            addToken(out, sentence, tokenStart, j, globalStart, sentenceIndex, globalTokenStart)
            i = j
        }
        return out
    }

    private fun matchSpecial(text: String, start: Int): Int? {
        val rest = text.substring(start)
        val url = Regex("(?i)^(?:https?://|www\\.)[^\\s]+") .find(rest)?.value
        val email = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(rest)?.value
        val value = when {
            url != null -> url
            email != null -> email
            else -> return null
        }
        var end = start + value.length
        if (email != null) {
            while (end < text.length && (connectorChars.contains(text[end]) || strongPauseChars.contains(text[end]) || closingChars.contains(text[end]))) end++
        }
        return end
    }

    private fun addToken(
        out: MutableList<RsvpToken>,
        sentence: String,
        localStart: Int,
        localEnd: Int,
        globalStart: Int,
        sentenceIndex: Int,
        globalTokenStart: Int
    ) {
        if (localEnd <= localStart) return
        val display = sentence.substring(localStart, localEnd)
        if (display.isBlank()) return
        val indexInSentence = out.size
        out += RsvpToken(
            display = display,
            start = globalStart + localStart,
            endExclusive = globalStart + localEnd,
            sentenceIndex = sentenceIndex,
            indexInSentence = indexInSentence,
            globalIndex = globalTokenStart + indexInSentence,
            graphemeCount = graphemeCount(display),
            weakPause = terminalPunctuation(display)?.let { weakPauseChars.contains(it) } == true,
            strongPause = terminalPunctuation(display)?.let { strongPauseChars.contains(it) } == true
        )
    }

    private fun terminalPunctuation(display: String): Char? {
        var i = display.lastIndex
        while (i >= 0 && closingChars.contains(display[i])) i--
        return display.getOrNull(i)
    }

    fun focusGraphemeRange(text: String): IntRange? {
        val ranges = graphemeRanges(text)
        if (ranges.isEmpty()) return null
        val letterRanges = ranges.filter { range ->
            text.substring(range.first, range.last + 1).any { it.isLetterOrDigit() }
        }
        val candidates = letterRanges.ifEmpty { ranges }
        val index = if (candidates.size % 2 == 0) candidates.size / 2 - 1 else candidates.size / 2
        return candidates[index.coerceAtLeast(0)]
    }

    fun graphemeRanges(text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val bi = BreakIterator.getCharacterInstance(Locale.getDefault())
        bi.setText(text)
        val ranges = mutableListOf<IntRange>()
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            ranges += start until end
            start = end
            end = bi.next()
        }
        return ranges
    }

    private fun graphemeCount(text: String): Int = graphemeRanges(text).size
}
