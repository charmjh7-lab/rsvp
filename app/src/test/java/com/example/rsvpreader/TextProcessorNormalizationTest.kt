package com.example.rsvpreader

import org.junit.Assert.assertEquals
import org.junit.Test

class TextProcessorNormalizationTest {
    @Test
    fun processNormalizedMatchesNormalProcessForStoredText() {
        val rawInputs = listOf(
            "  hello   world  ",
            "첫 줄\n둘째 줄",
            "첫 문단 \n \n 둘째 문단",
            "a\r\nb\r\n\r\nc",
            "\uFEFF본문\u00A0내용",
            "\n\n 시작 \n 중간 \n\n 끝 \n\n",
            "a\t \t b",
            "a\n   \n   \n b",
            "Mr. Smith met Dr. Jones. 2026/09/28 state-of-the-art 정치ㆍ경제."
        )

        rawInputs.forEach { raw ->
            val normalized = TextProcessor.normalize(raw)
            assertEquals(
                TextProcessor.process(raw),
                TextProcessor.processNormalized(normalized)
            )
        }
    }
}
