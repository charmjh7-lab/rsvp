package com.example.rsvpreader

fun longWordDelayMultiplier(text: String): Double {
    val letters = text.filter(Char::isLetter)
    if (letters.isEmpty()) return 1.0

    return when (classifyRsvpTokenLanguage(letters)) {
        RsvpTokenLanguage.KOREAN -> when (letters.length) {
            in 0..5 -> 1.0
            6 -> 1.50
            7 -> 1.60
            8 -> 1.70
            else -> 1.80
        }
        RsvpTokenLanguage.LATIN -> when (letters.length) {
            in 0..10 -> 1.0
            11 -> 1.45
            12 -> 1.50
            13 -> 1.55
            14 -> 1.60
            15 -> 1.65
            else -> 1.75
        }
        RsvpTokenLanguage.MIXED -> 1.0
    }
}
