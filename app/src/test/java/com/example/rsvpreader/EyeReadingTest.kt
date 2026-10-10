package com.example.rsvpreader

import org.junit.Assert.*
import org.junit.Test

class EyeReadingTest {
    @Test fun sourcePositionsPreserveBlankSpaceAndDoNotRecenter() {
        val doc = TextProcessor.process("안녕하세요 만나서 반갑습니다")
        val sentence = doc.sentences.single()
        val second = eyeReadingSlices(doc.tokens[1], EyeReadingUnit.WORD).single()
        val all = doc.tokens.flatMap { eyeReadingSlices(it, EyeReadingUnit.WORD) }
        val only = eyeVisibleRange(sentence, second, all, false, 1)
        assertEquals("만나서", sentence.text.substring(only.first, only.last + 1))
        assertEquals(6, only.first)
        val kept = eyeVisibleRange(sentence, second, all, true, 1)
        assertEquals("안녕하세요 만나서", sentence.text.substring(kept.first, kept.last + 1))
        assertEquals(6, second.start)
    }

    @Test fun previewStartsWithRequestedCountAndMovesWithoutRecentering() {
        val doc = TextProcessor.process("오늘은 날씨가 좋아서 산책을 나갔다.")
        val sentence = doc.sentences.single()
        val units = doc.tokens.flatMap { eyeReadingSlices(it, EyeReadingUnit.WORD) }
        fun shown(step: Int, keep: Boolean, count: Int): String {
            val range = eyeVisibleRange(sentence, units[step], units, keep, count)
            return sentence.text.substring(range.first, range.last + 1)
        }
        assertEquals("오늘은 날씨가 좋아서", shown(0, true, 3))
        assertEquals("오늘은 날씨가 좋아서 산책을", shown(1, true, 3))
        assertEquals("오늘은 날씨가 좋아서 산책을", shown(0, false, 4))
        assertEquals("날씨가 좋아서 산책을", shown(1, false, 3))
        assertEquals("좋아서 산책을 나갔다.", shown(2, false, 3))
        assertEquals("오늘은 날씨가 좋아서 산책을 나갔다.", shown(4, true, 3))
        assertEquals("나갔다.", shown(4, false, 3))
        assertEquals("오늘은", shown(0, false, 0))
        assertEquals("오늘은 날씨가 좋아서 산책을 나갔다.", shown(0, false, 100))
    }

    @Test fun rapErasesPastAndKeepsEntireFutureVisible() {
        val doc = TextProcessor.process("오늘은 날씨가 좋아서 산책을 나갔다.")
        val sentence = doc.sentences.single()
        val words = doc.tokens.flatMap { eyeReadingSlices(it, EyeReadingUnit.WORD) }
        fun shown(step: Int, past: Int, keep: Boolean = false): String {
            val range = eyeVisibleRange(sentence, words[step], words, keep, 3, past, true)
            return sentence.text.substring(range.first, range.last + 1)
        }
        assertEquals("오늘은 날씨가 좋아서 산책을 나갔다.", shown(0, 0))
        assertEquals("날씨가 좋아서 산책을 나갔다.", shown(1, 0))
        assertEquals("좋아서 산책을 나갔다.", shown(2, 0))
        assertEquals("오늘은 날씨가 좋아서 산책을 나갔다.", shown(2, 2))
        assertEquals("오늘은 날씨가 좋아서 산책을 나갔다.", shown(2, 0, true))
        assertEquals("나갔다.", shown(words.lastIndex, 0))
    }

    @Test fun lookbackAndLookaheadCombineWithinFixedWindow() {
        val doc = TextProcessor.process("오늘은 날씨가 좋아서 산책을 나갔다.")
        val sentence = doc.sentences.single()
        val words = doc.tokens.flatMap { eyeReadingSlices(it, EyeReadingUnit.WORD) }
        val range = eyeVisibleRange(sentence, words[2], words, false, 2, 1, false)
        assertEquals("날씨가 좋아서 산책을", sentence.text.substring(range.first, range.last + 1))
    }

    @Test fun previewRespectsCharacterUnitAndLastUnit() {
        val doc = TextProcessor.process("가나다 라마")
        val sentence = doc.sentences.single()
        val units = doc.tokens.flatMap { eyeReadingSlices(it, EyeReadingUnit.CHARACTER) }
        fun shown(step: Int): String {
            val range = eyeVisibleRange(sentence, units[step], units, false, 3)
            return sentence.text.substring(range.first, range.last + 1)
        }
        assertEquals("가나다", shown(0))
        assertEquals("나다 라", shown(1))
        assertEquals("마", shown(units.lastIndex))
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
