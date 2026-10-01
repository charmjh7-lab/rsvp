package com.example.rsvpreader

import java.text.BreakIterator
import java.util.Locale

object TextProcessor {
    const val PROCESSING_SCHEMA_VERSION = 2

    private val horizontalWhitespaceRegex = Regex("[\\t ]+")
    private val paragraphBreakRegex = Regex(" *\\n *\\n+ *")
    private val singleLineBreakRegex = Regex("(?<!\\n)\\n(?!\\n)")
    private val repeatedSpaceRegex = Regex(" {2,}")
    private val paragraphTrimRegex = Regex(" *\\n\\n *")
    private val urlRegex = Regex("(?i)(?:https?://|www\\.)[^\\s]+")
    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    private val commonAbbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
        "e.g", "i.e", "u.s", "u.k", "no", "fig", "dept", "inc", "ltd", "co"
    )

    private val connectorChars = setOf(
        ',', '，', '、', 'ㆍ', '·', '-', '‐', '‑', '‒', '–', '—', '~', '～', ':', ';', '：', '；',
        '…', '/', '／'
    )
    private val weakPauseChars = setOf(',', '，', '、', 'ㆍ', '·', ':', ';', '：', '；', '…')
    private val strongPauseChars = setOf('.', '。', '?', '？', '!', '！')
    private val openingChars = setOf('(', '[', '{', '〈', '《', '「', '『', '【', '〔', '“', '‘', '"', '\'', '<')
    private val closingChars = setOf(')', ']', '}', '〉', '》', '」', '』', '】', '〕', '”', '’', '"', '\'', '>')

    fun process(rawText: String): ProcessedDocument =
        processNormalized(normalize(rawText))

    fun processNormalized(normalizedText: String): ProcessedDocument {
        if (normalizedText.isBlank()) return ProcessedDocument("", emptyList(), emptyList())

        val sentenceRanges = splitSentences(normalizedText)
        val tokens = mutableListOf<RsvpToken>()
        val sentences = mutableListOf<SentenceUnit>()
        val graphemeIterator = BreakIterator.getCharacterInstance(Locale.getDefault())

        sentenceRanges.forEachIndexed { sentenceIndex, range ->
            val sentenceText = normalizedText.substring(range.first, range.second)
            val sentenceTokenStart = tokens.size
            val localTokens = tokenizeSentence(
                sentenceText,
                range.first,
                sentenceIndex,
                sentenceTokenStart,
                graphemeIterator
            )
            tokens += localTokens
            sentences += SentenceUnit(
                text = sentenceText,
                start = range.first,
                endExclusive = range.second,
                tokenStartGlobal = sentenceTokenStart,
                tokenCount = localTokens.size
            )
        }

        return ProcessedDocument(normalizedText, sentences, tokens)
    }

    fun normalize(rawText: String): String {
        var text = rawText
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')

        // EPUB/TXT paragraphs are preserved; single hard wraps (common in PDF extraction) become spaces.
        text = text.replace(horizontalWhitespaceRegex, " ")
        text = text.replace(paragraphBreakRegex, "\n\n")
        text = text.replace(singleLineBreakRegex, " ")
        text = text.replace(repeatedSpaceRegex, " ")
        text = text.replace(paragraphTrimRegex, "\n\n")
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

        fun hasBoundaryAfter(endExclusive: Int): Boolean {
            if (endExclusive >= text.length) return true
            val after = nextNonWhitespace(text, endExclusive)
            return after == -1 || after > endExclusive
        }

        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '\n' && text[i + 1] == '\n') {
                addRange(i)
                i += 2
                start = i
                continue
            }

            val ch = text[i]
            if (ch == '.' && isAsciiEllipsisAt(text, i)) {
                var sentenceEnd = i
                while (sentenceEnd < text.length && text[sentenceEnd] == '.') sentenceEnd++
                while (sentenceEnd < text.length && closingChars.contains(text[sentenceEnd])) sentenceEnd++
                if (hasBoundaryAfter(sentenceEnd)) {
                    addRange(sentenceEnd)
                    i = sentenceEnd
                    start = sentenceEnd
                    continue
                }
                i = sentenceEnd
                continue
            }

            if (strongPauseChars.contains(ch)) {
                val shouldSplit = (ch != '.' && ch != '。') || !isInternalPeriod(text, i)
                if (shouldSplit) {
                    var sentenceEnd = i + 1
                    while (sentenceEnd < text.length && closingChars.contains(text[sentenceEnd])) sentenceEnd++
                    if (hasBoundaryAfter(sentenceEnd)) {
                        addRange(sentenceEnd)
                        i = sentenceEnd
                        start = sentenceEnd
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

        var tokenStart = index - 1
        while (tokenStart >= 0 && (text[tokenStart].isLetter() || text[tokenStart] == '.')) tokenStart--
        tokenStart++
        var tokenEnd = index + 1
        while (tokenEnd < text.length && (text[tokenEnd].isLetter() || text[tokenEnd] == '.')) tokenEnd++
        val dottedCandidate = text.substring(tokenStart, tokenEnd).trim('.').lowercase(Locale.ROOT)
        if (dottedCandidate in commonAbbreviations && dottedCandidate.contains('.')) {
            val hasMoreOfAbbreviation = (index + 1 until tokenEnd).any { text[it].isLetter() || text[it] == '.' }
            if (hasMoreOfAbbreviation) return true
            return abbreviationContinuesSentence(dottedCandidate, text, index)
        }

        val before = text.substring(tokenStart, index).lowercase(Locale.ROOT).trim('.')
        if (before in commonAbbreviations) return abbreviationContinuesSentence(before, text, index)

        if (before.length == 1 && text[tokenStart].isUpperCase()) {
            val nextNonSpace = nextNonWhitespace(text, index + 1)
            if (nextNonSpace != -1 && text[nextNonSpace].isLetter()) return true
        }
        return false
    }

    private fun abbreviationContinuesSentence(abbreviation: String, text: String, periodIndex: Int): Boolean {
        val nextIndex = nextNonWhitespace(text, periodIndex + 1)
        if (nextIndex == -1) return false
        val next = text[nextIndex]
        return when (abbreviation) {
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st" -> next.isLetterOrDigit()
            "e.g", "i.e", "u.s", "u.k" -> true
            "etc" -> next.isLowerCase() || next.isDigit()
            else -> next.isLowerCase() || next.isDigit()
        }
    }

    private fun isAsciiEllipsisAt(text: String, index: Int): Boolean =
        index + 2 < text.length && text[index] == '.' && text[index + 1] == '.' && text[index + 2] == '.'

    private fun nextNonWhitespace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return if (i < text.length) i else -1
    }

    private fun tokenizeSentence(
        sentence: String,
        globalStart: Int,
        sentenceIndex: Int,
        globalTokenStart: Int,
        graphemeIterator: BreakIterator
    ): List<RsvpToken> {
        val out = mutableListOf<RsvpToken>()
        var i = 0

        while (i < sentence.length) {
            while (i < sentence.length && sentence[i].isWhitespace()) i++
            if (i >= sentence.length) break

            val tokenStart = i
            val urlEnd = matchSpecial(sentence, i)
            if (urlEnd != null) {
                addToken(out, sentence, tokenStart, urlEnd, globalStart, sentenceIndex, globalTokenStart, graphemeIterator)
                i = urlEnd
                continue
            }

            while (i < sentence.length && openingChars.contains(sentence[i])) i++
            if (i >= sentence.length) {
                addToken(out, sentence, tokenStart, i, globalStart, sentenceIndex, globalTokenStart, graphemeIterator)
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
                        (c == '.' && p?.isDigit() == true && n?.isDigit() == true) ||
                        ((c == '/' || c == '／') && p?.isDigit() == true && n?.isDigit() == true)) {
                        j++
                    } else break
                }
                while (j < sentence.length && (sentence[j] == '%' || sentence[j].isLetter())) j++
                while (j < sentence.length && closingChars.contains(sentence[j])) j++
                if (j < sentence.length && (weakPauseChars.contains(sentence[j]) || strongPauseChars.contains(sentence[j]))) j++
                addToken(out, sentence, tokenStart, j, globalStart, sentenceIndex, globalTokenStart, graphemeIterator)
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
                    if (c == '.' && isAsciiEllipsisAt(sentence, j)) {
                        while (j < sentence.length && sentence[j] == '.') j++
                        while (j < sentence.length && closingChars.contains(sentence[j])) j++
                        break
                    }
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
            addToken(out, sentence, tokenStart, j, globalStart, sentenceIndex, globalTokenStart, graphemeIterator)
            i = j
        }
        return out
    }

    private fun matchSpecial(text: String, start: Int): Int? {
        val urlMatch = urlRegex.matchAt(text, start)
        val emailMatch = if (urlMatch == null) emailRegex.matchAt(text, start) else null
        val match = urlMatch ?: emailMatch ?: return null
        var end = match.range.last + 1
        if (emailMatch != null) {
            while (
                end < text.length &&
                (connectorChars.contains(text[end]) ||
                    strongPauseChars.contains(text[end]) ||
                    closingChars.contains(text[end]))
            ) {
                end++
            }
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
        globalTokenStart: Int,
        graphemeIterator: BreakIterator
    ) {
        if (localEnd <= localStart) return
        val display = sentence.substring(localStart, localEnd)
        if (display.isBlank()) return
        val indexInSentence = out.size
        val punctuation = terminalPunctuation(display)
        out += RsvpToken(
            display = display,
            start = globalStart + localStart,
            endExclusive = globalStart + localEnd,
            sentenceIndex = sentenceIndex,
            indexInSentence = indexInSentence,
            globalIndex = globalTokenStart + indexInSentence,
            graphemeCount = graphemeCount(display, graphemeIterator),
            weakPause = punctuation?.let { weakPauseChars.contains(it) } == true,
            strongPause = punctuation?.let { strongPauseChars.contains(it) } == true
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

    private fun graphemeCount(text: String, iterator: BreakIterator): Int {
        if (text.isEmpty()) return 0
        if (text.all { char -> char.code <= 0x7F || char.code in 0xAC00..0xD7A3 }) {
            return text.length
        }
        iterator.setText(text)
        var count = 0
        iterator.first()
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }
}
