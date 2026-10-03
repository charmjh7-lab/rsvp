package com.example.rsvpreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProcessorTest {
    private fun tokens(text: String) = TextProcessor.process(text).tokens.map { it.display }

    @Test
    fun koreanAndEnglishBasicTokenization() {
        assertEquals(listOf("안녕하세요", "반갑습니다."), tokens("안녕하세요 반갑습니다."))
        assertEquals(listOf("Hello", "world."), tokens("Hello world."))
    }

    @Test
    fun connectorAndHyphenRulesRemainStable() {
        assertEquals(listOf("정치ㆍ", "경제"), tokens("정치ㆍ경제"))
        assertEquals(listOf("state-", "of-", "the-", "art"), tokens("state-of-the-art"))
    }

    @Test
    fun groupingBracketsStayWithInnerWordsButSplitFromOuterWords() {
        assertEquals(
            listOf("앞단어", "(괄호안)", "뒤단어"),
            tokens("앞단어(괄호안)뒤단어")
        )
        assertEquals(
            listOf("앞단어", "(괄호", "안)", "뒤단어"),
            tokens("앞단어(괄호 안)뒤단어")
        )
        assertEquals(
            listOf("before", "[inside]", "after"),
            tokens("before[inside]after")
        )
    }

    @Test
    fun punctuationAfterClosingBracketStaysWithBracketedToken() {
        val processed = TextProcessor.process("앞(안). 다음")
        assertEquals(listOf("앞", "(안).", "다음"), processed.tokens.map { it.display })
        assertTrue(processed.tokens[1].strongPause)
    }

    @Test
    fun decimalThousandsAndNumericSlashStayWhole() {
        assertEquals(listOf("3.14", "1,000", "2026/09/28", "10/20"), tokens("3.14 1,000 2026/09/28 10/20"))
    }

    @Test
    fun ordinarySlashKeepsReadableBoundaries() {
        assertEquals(listOf("and/", "or", "input/", "output", "/", "test", "test/"), tokens("and/or input/output /test test/"))
        assertEquals(listOf("입력／", "출력"), tokens("입력／출력"))
        assertEquals(0..0, TextProcessor.focusGraphemeRange("/"))
    }

    @Test
    fun urlAndEmailStayAsMeaningfulUnits() {
        assertEquals(listOf("https://example.com/a/b"), tokens("https://example.com/a/b"))
        assertEquals(listOf("reader@example.com"), tokens("reader@example.com"))
    }

    @Test
    fun quotesAndEnglishAbbreviationsSplitSentencesCorrectly() {
        val quoted = TextProcessor.process("\"Hello world.\" Next sentence.")
        assertEquals(2, quoted.sentences.size)
        assertEquals("\"Hello world.\"", quoted.sentences[0].text)

        val abbreviations = TextProcessor.process("Mr. Smith met Dr. Jones. They left etc. Next day.")
        assertEquals(3, abbreviations.sentences.size)
        assertEquals("Mr. Smith met Dr. Jones.", abbreviations.sentences[0].text)
        assertEquals("They left etc.", abbreviations.sentences[1].text)
        assertEquals("Next day.", abbreviations.sentences[2].text)

        val midSentenceEtc = TextProcessor.process("We need pens, paper, etc. before class.")
        assertEquals(1, midSentenceEtc.sentences.size)
    }

    @Test
    fun asciiAndUnicodeEllipsisDoNotMergeUnexpectedWords() {
        val ascii = TextProcessor.process("잠깐... 다음 문장.")
        assertEquals(listOf("잠깐...", "다음", "문장."), ascii.tokens.map { it.display })
        assertEquals(2, ascii.sentences.size)

        val unicode = TextProcessor.process("첫 회담…단절됐던 대화 재개")
        assertEquals(listOf("첫", "회담…", "단절됐던", "대화", "재개"), unicode.tokens.map { it.display })
    }

    @Test
    fun punctuationDelayFlagsRemainCorrect() {
        val processed = TextProcessor.process("Hello, world!")
        assertTrue(processed.tokens[0].weakPause)
        assertFalse(processed.tokens[0].strongPause)
        assertTrue(processed.tokens[1].strongPause)
        assertFalse(processed.tokens[1].weakPause)
    }

    @Test
    fun focusRangeExistsForNumericSlashToken() {
        val range = TextProcessor.focusGraphemeRange("2026/09/28")
        assertNotNull(range)
        assertTrue(range!!.first in "2026/09/28".indices)
    }
}
