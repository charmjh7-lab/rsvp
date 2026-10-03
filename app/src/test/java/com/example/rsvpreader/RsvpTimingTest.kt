package com.example.rsvpreader

import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpTimingTest {
    @Test
    fun koreanDelayUsesContinuousLengthFormulaFromSixCharacters() {
        assertEquals(1.0, longWordDelayMultiplier("가".repeat(5)), 0.001)
        assertEquals(1.50, longWordDelayMultiplier("가".repeat(6)), 0.001)
        assertEquals(1.60, longWordDelayMultiplier("가".repeat(7)), 0.001)
        assertEquals(1.70, longWordDelayMultiplier("가".repeat(8)), 0.001)
        assertEquals(1.80, longWordDelayMultiplier("가".repeat(9)), 0.001)
        assertEquals(2.50, longWordDelayMultiplier("가".repeat(16)), 0.001)
        assertEquals(3.00, longWordDelayMultiplier("가".repeat(21)), 0.001)
        assertEquals(3.00, longWordDelayMultiplier("가".repeat(40)), 0.001)
    }

    @Test
    fun latinDelayUsesContinuousLengthFormulaFromElevenCharacters() {
        assertEquals(1.0, longWordDelayMultiplier("a".repeat(10)), 0.001)
        assertEquals(1.45, longWordDelayMultiplier("a".repeat(11)), 0.001)
        assertEquals(1.50, longWordDelayMultiplier("a".repeat(12)), 0.001)
        assertEquals(1.55, longWordDelayMultiplier("a".repeat(13)), 0.001)
        assertEquals(1.65, longWordDelayMultiplier("a".repeat(15)), 0.001)
        assertEquals(1.70, longWordDelayMultiplier("a".repeat(16)), 0.001)
        assertEquals(1.90, longWordDelayMultiplier("a".repeat(20)), 0.001)
        assertEquals(3.00, longWordDelayMultiplier("a".repeat(42)), 0.001)
        assertEquals(3.00, longWordDelayMultiplier("a".repeat(80)), 0.001)
    }

    @Test
    fun punctuationDoesNotInflateWordLength() {
        assertEquals(1.50, longWordDelayMultiplier("가".repeat(6) + "."), 0.001)
        assertEquals(1.45, longWordDelayMultiplier("a".repeat(11) + "!"), 0.001)
    }
}
