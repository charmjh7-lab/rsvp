package com.example.rsvpreader

import org.junit.Assert.*
import org.junit.Test

class EyeReadingTest {
    @Test fun sourcePositionsPreserveBlankSpaceAndDoNotRecenter() {
        val doc = TextProcessor.process("안녕하세요 만나서 반갑습니다")
        val sentence = doc.sentences.single()
        val second = eyeReadingSlices(doc.tokens[1], EyeReadingUnit.WORD).single()
        val only = eyeVisibleRange(sentence, second, false)
        assertEquals("만나서", sentence.text.substring(only.first, only.last + 1))
        assertEquals(6, only.first)
        val kept = eyeVisibleRange(sentence, second, true)
        assertEquals("안녕하세요 만나서", sentence.text.substring(kept.first, kept.last + 1))
        assertEquals(6, second.start)
    }

    @Test fun characterModePreservesCombinedCharactersAndOffsets() {
        val doc = TextProcessor.process("가나다 cafe\u0301")
        val slices = doc.tokens.flatMap { eyeReadingSlices(it, EyeReadingUnit.CHARACTER) }
        assertEquals(listOf("가", "나", "다", "c", "a", "f", "e\u0301"), slices.map { it.text })
        slices.forEach { assertEquals(it.text, doc.normalizedText.substring(it.start, it.endExclusive)) }
    }

    @Test fun separateRatesAndPunctuationTimingUseDisplayedUnits() {
        val settings = AppSettings(wordRate = 100, characterRate = 120, longWordDelay = true)
        assertEquals(100, settings.eyeRate)
        assertEquals(120, settings.copy(eyeUnit = EyeReadingUnit.CHARACTER).eyeRate)
        assertEquals(100, settings.copy(eyeUnit = EyeReadingUnit.CHARACTER).withEyeRate(80).wordRate)
        val token = TextProcessor.process("가나다라마바.").tokens.single()
        assertEquals(990L, eyeReadingDelay(token, eyeReadingSlices(token, EyeReadingUnit.WORD).single(), settings))
        val chars = eyeReadingSlices(token, EyeReadingUnit.CHARACTER)
        val charSettings = settings.copy(eyeUnit = EyeReadingUnit.CHARACTER)
        assertEquals(500L, eyeReadingDelay(token, chars.first(), charSettings))
        assertEquals(825L, eyeReadingDelay(token, chars.last(), charSettings))
    }
}
