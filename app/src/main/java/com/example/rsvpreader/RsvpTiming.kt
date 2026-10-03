package com.example.rsvpreader

private const val MAX_LONG_WORD_DELAY_MULTIPLIER = 3.0

fun longWordDelayMultiplier(text: String): Double {
    val letters = text.filter(Char::isLetter)
    if (letters.isEmpty()) return 1.0

    return when (classifyRsvpTokenLanguage(letters)) {
        RsvpTokenLanguage.KOREAN -> {
            if (letters.length <= 5) 1.0
            else (1.50 + 0.10 * (letters.length - 6)).coerceAtMost(MAX_LONG_WORD_DELAY_MULTIPLIER)
        }
        RsvpTokenLanguage.LATIN -> {
            if (letters.length <= 10) 1.0
            else (1.45 + 0.05 * (letters.length - 11)).coerceAtMost(MAX_LONG_WORD_DELAY_MULTIPLIER)
        }
        RsvpTokenLanguage.MIXED -> 1.0
    }
}
