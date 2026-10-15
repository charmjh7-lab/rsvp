package com.example.rsvpreader

/** Display-only alignment; source tokens and reading positions remain unchanged. */
data class RsvpFocus(
    val display: String,
    val anchorRange: IntRange?,
    val highlightRange: IntRange?
)

fun rsvpFocus(text: String, beforeCenter: Boolean): RsvpFocus {
    val center = TextProcessor.focusGraphemeRange(text)
    if (!beforeCenter || center == null) return RsvpFocus(text, center, center)
    val ranges = TextProcessor.graphemeRanges(text)
    val candidates = ranges.filter { range ->
        text.substring(range.first, range.last + 1).any { it.isLetterOrDigit() }
    }.ifEmpty { ranges }
    return when (candidates.size) {
        1 -> {
            // A full-width blank gives the lone character a stable virtual next cell.
            val insertion = candidates[0].last + 1
            RsvpFocus(
                text.substring(0, insertion) + "\u3000" + text.substring(insertion),
                insertion..insertion,
                candidates[0]
            )
        }
        2 -> RsvpFocus(text, candidates[1], candidates[0])
        else -> RsvpFocus(text, center, candidates[candidates.indexOf(center) - 1])
    }
}
