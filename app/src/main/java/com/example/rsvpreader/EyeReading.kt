package com.example.rsvpreader

import kotlin.math.roundToLong

enum class EyeReadingMode(val label: String) { ALTERNATING("좌우 교대"), FIXED_POSITION("자리 채우기") }
enum class EyeReadingUnit(val label: String) { WORD("단어"), CHARACTER("글자") }

const val EYE_RATE_MIN = 30
const val EYE_RATE_MAX = 240
const val EYE_PREVIEW_MIN = 1
const val EYE_PREVIEW_MAX = 10
const val EYE_PREVIEW_DEFAULT = 3

data class EyeReadingSlice(val text: String, val start: Int, val endExclusive: Int)

fun eyeReadingSlices(token: RsvpToken, unit: EyeReadingUnit): List<EyeReadingSlice> =
    if (unit == EyeReadingUnit.WORD) listOf(EyeReadingSlice(token.display, token.start, token.endExclusive))
    else TextProcessor.graphemeRanges(token.display).map { range ->
        EyeReadingSlice(token.display.substring(range.first, range.last + 1), token.start + range.first, token.start + range.last + 1)
    }.filter { it.text.isNotBlank() }

fun eyeReadingDelay(token: RsvpToken, slice: EyeReadingSlice, settings: AppSettings): Long {
    val base = 60_000.0 / settings.eyeRate.coerceIn(EYE_RATE_MIN, EYE_RATE_MAX)
    val lastSlice = slice.endExclusive == token.endExclusive
    val punctuation = if (settings.punctuationDelay && lastSlice) {
        when { token.strongPause -> 1.65; token.weakPause -> 1.25; else -> 1.0 }
    } else 1.0
    val length = if (settings.longWordDelay && settings.eyeUnit == EyeReadingUnit.WORD) longWordDelayMultiplier(token.display) else 1.0
    return (base * maxOf(punctuation, length)).roundToLong()
}

// Preserve source offsets and layout while a preview window moves across units.
fun eyeVisibleRange(
    sentence: SentenceUnit,
    slice: EyeReadingSlice,
    sentenceSlices: List<EyeReadingSlice>,
    keepPrevious: Boolean,
    previewCount: Int,
    previousCount: Int = 0,
    showAllFollowing: Boolean = false
): IntRange {
    if (sentenceSlices.isEmpty()) return 0 until 0
    val cursor = sentenceSlices.indexOfFirst { it.start == slice.start && it.endExclusive == slice.endExclusive }
    if (cursor < 0) return 0 until 0
    val lastVisible = if (showAllFollowing) sentenceSlices.lastIndex else
        (cursor + previewCount.coerceIn(EYE_PREVIEW_MIN, EYE_PREVIEW_MAX) - 1)
            .coerceAtMost(sentenceSlices.lastIndex)
    val firstVisible = if (keepPrevious) 0 else
        (cursor - previousCount.coerceIn(0, EYE_PREVIEW_MAX)).coerceAtLeast(0)
    val start = (sentenceSlices[firstVisible].start - sentence.start).coerceIn(0, sentence.text.length)
    val end = (sentenceSlices[lastVisible].endExclusive - sentence.start).coerceIn(start, sentence.text.length)
    return start until end
}
