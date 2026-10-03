package com.example.rsvpreader

import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpTimingTest {
    @Test
    fun koreanDelayStartsClearlyAtSixAndExceedsSentencePauseForVeryLongWords() {
        assertEquals(1.0, longWordDelayMultiplier("가".repeat(5)), 0.001)
        assertEquals(1.50, longWordDelayMultiplier("가".repeat(6)), 0.001)
        assertEquals(1.60, longWordDelayMultiplier("가".repeat(7)), 0.001)
        assertEquals(1.70, longWordDelayMultiplier("가".repeat(8)), 0.001)
        assertEquals(1.80, longWordDelayMultiplier("가".repeat(9)), 0.001)
    }

    @Test
    fun latinDelayStartsClearlyAtElevenAndReachesSentencePauseForVeryLongWords() {
        assertEquals(1.0, longWordDelayMultiplier("a".repeat(10)), 0.001)
        assertEquals(1.45, longWordDelayMultiplier("a".repeat(11)), 0.001)
        assertEquals(1.50, longWordDelayMultiplier("a".repeat(12)), 0.001)
        assertEquals(1.55, longWordDelayMultiplier("a".repeat(13)), 0.001)
        assertEquals(1.60, longWordDelayMultiplier("a".repeat(14)), 0.001)
        assertEquals(1.65, longWordDelayMultiplier("a".repeat(15)), 0.001)
        assertEquals(1.75, longWordDelayMultiplier("a".repeat(16)), 0.001)
    }

    @Test
    fun punctuationDoesNotInflateWordLength() {
        assertEquals(1.50, longWordDelayMultiplier("가".repeat(6) + "."), 0.001)
        assertEquals(1.45, longWordDelayMultiplier("a".repeat(11) + "!"), 0.001)
    }
}
