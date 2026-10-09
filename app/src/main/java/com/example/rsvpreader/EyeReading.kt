package com.example.rsvpreader

import kotlin.math.roundToLong

enum class EyeReadingMode(val label: String) { ALTERNATING("좌우 교대"), FIXED_POSITION("자리 채우기") }
enum class EyeReadingUnit(val label: String) { WORD("단어"), CHARACTER("글자") }

const val EYE_RATE_MIN = 30
const val EYE_RATE_MAX = 240

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

// Use source offsets rather than spaces or recentered strings: the complete sentence
// is laid out once, and only visibility changes as the cursor advances.
fun eyeVisibleRange(sentence: SentenceUnit, slice: EyeReadingSlice, keepPrevious: Boolean): IntRange {
    val start = if (keepPrevious) 0 else (slice.start - sentence.start).coerceAtLeast(0)
    val end = (slice.endExclusive - sentence.start).coerceIn(start, sentence.text.length)
    return start until end
}
