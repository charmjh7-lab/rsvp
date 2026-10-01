package com.example.rsvpreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpSizingTest {
    private val base = 46f

    @Test
    fun koreanScaleStartsAtFiveAndFallsGradually() {
        val expected = listOf(
            1 to 1.00f,
            4 to 1.00f,
            5 to 0.96f,
            6 to 0.92f,
            7 to 0.88f,
            8 to 0.84f,
            9 to 0.80f,
            10 to 0.76f,
            11 to 0.72f,
            12 to 0.72f
        )
        expected.forEach { (count, scale) ->
            val text = "가".repeat(count)
            assertEquals(scale, proactiveRsvpScale(text, count), 0.001f)
        }
    }

    @Test
    fun latinScaleUsesMeasuredEquivalentWidth() {
        assertEquals(1f, proactiveRsvpScale("iiiiiiiiiiii", 12, 7.5f), 0.001f)
        assertEquals(0.97f, proactiveRsvpScale("abcdefghi", 9, 9f), 0.001f)
        assertEquals(0.82f, proactiveRsvpScale("WWWWWWWWW", 9, 14f), 0.001f)
        assertEquals(0.72f, proactiveRsvpScale("abcdefghijklmnopqrst", 20, 20f), 0.001f)
    }

    @Test
    fun mixedTokensRelyOnMeasuredWidthFit() {
        val url = "https://example.com/a/very/long/path"
        assertEquals(RsvpTokenLanguage.MIXED, classifyRsvpTokenLanguage(url))
        assertEquals(1f, proactiveRsvpScale(url, url.length, 30f), 0.001f)

        val widthScale = widthFitRsvpScale(requiredHalfWidthPx = 400f, usableHalfWidthPx = 180f)
        val font = effectiveRsvpFontSp(base, 1f, widthScale)
        assertEquals(20.7f, font, 0.05f)
        assertTrue(400f * (font / base) <= 180.5f)
    }

    @Test
    fun proactiveAndWidthFitUseTheSmallerScale() {
        val proactive = 0.80f
        val widthFit = 0.65f
        assertEquals(base * 0.65f, effectiveRsvpFontSp(base, proactive, widthFit), 0.001f)
    }

    @Test
    fun extremelyLongTokenKeepsReadableMinimum() {
        assertEquals(12f, effectiveRsvpFontSp(base, 1f, 0.1f), 0.001f)
    }
}
