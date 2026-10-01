package com.example.rsvpreader

import kotlin.math.min

enum class RsvpTokenLanguage {
    KOREAN,
    LATIN,
    MIXED
}

private val neutralTokenPunctuation = setOf(
    ',', '.', '!', '?', '…', '\'', '"', '“', '”', '‘', '’',
    '(', ')', '[', ']', '{', '}', '-', '‐', '‑', '‒', '–', '—'
)

fun classifyRsvpTokenLanguage(text: String): RsvpTokenLanguage {
    if (text.isBlank()) return RsvpTokenLanguage.MIXED
    val meaningful = text.filterNot { it in neutralTokenPunctuation }
    if (meaningful.isBlank() || meaningful.any { !it.isLetter() }) return RsvpTokenLanguage.MIXED
    return when {
        meaningful.all(::isHangulChar) -> RsvpTokenLanguage.KOREAN
        meaningful.all { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN } -> RsvpTokenLanguage.LATIN
        else -> RsvpTokenLanguage.MIXED
    }
}

private fun isHangulChar(char: Char): Boolean = char.code in 0xAC00..0xD7A3 ||
    char.code in 0x1100..0x11FF ||
    char.code in 0x3130..0x318F ||
    char.code in 0xA960..0xA97F ||
    char.code in 0xD7B0..0xD7FF

fun proactiveRsvpScale(
    text: String,
    graphemeCount: Int,
    latinEquivalentCharacters: Float? = null
): Float = when (classifyRsvpTokenLanguage(text)) {
    RsvpTokenLanguage.KOREAN -> {
        if (graphemeCount <= 4) 1f
        else (1f - 0.04f * (graphemeCount - 4)).coerceAtLeast(0.72f)
    }
    RsvpTokenLanguage.LATIN -> {
        val effectiveLength = (latinEquivalentCharacters ?: graphemeCount.toFloat()).coerceAtLeast(0f)
        if (effectiveLength < 9f) 1f
        else (1f - 0.03f * (effectiveLength - 8f)).coerceAtLeast(0.72f)
    }
    RsvpTokenLanguage.MIXED -> 1f
}

fun widthFitRsvpScale(requiredHalfWidthPx: Float, usableHalfWidthPx: Float): Float {
    if (requiredHalfWidthPx <= 0f || usableHalfWidthPx <= 0f) return 1f
    return (usableHalfWidthPx / requiredHalfWidthPx).coerceIn(0f, 1f)
}

fun effectiveRsvpFontSp(
    baseFontSp: Float,
    proactiveScale: Float,
    widthFitScale: Float,
    minimumFontSp: Float = 12f
): Float {
    val finalScale = min(proactiveScale.coerceIn(0f, 1f), widthFitScale.coerceIn(0f, 1f))
    return (baseFontSp * finalScale)
        .coerceAtLeast(minimumFontSp.coerceAtMost(baseFontSp))
        .coerceAtMost(baseFontSp)
}
