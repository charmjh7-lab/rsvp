package com.example.rsvpreader

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class TextProcessorNormalizationTest {
    private fun legacyNormalize(rawText: String): String {
        var text = rawText
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')

        text = text.replace(Regex("[\\t ]+"), " ")
        text = text.replace(Regex(" *\\n *\\n+ *"), "\n\n")
        text = text.replace(Regex("(?<!\\n)\\n(?!\\n)"), " ")
        text = text.replace(Regex(" {2,}"), " ")
        text = text.replace(Regex(" *\\n\\n *"), "\n\n")
        return text.trim()
    }

    @Test
    fun optimizedNormalizeMatchesLegacyRules() {
        val fixed = listOf(
            "",
            "  hello   world  ",
            "첫 줄\n둘째 줄",
            "첫 문단 \n \n 둘째 문단",
            "a\r\nb\r\n\r\nc",
            "\uFEFF본문\u00A0내용",
            "\n\n 시작 \n 중간 \n\n 끝 \n\n",
            "a\t \t b",
            "a\n   \n   \n b"
        )
        fixed.forEach { input ->
            assertEquals(legacyNormalize(input), TextProcessor.normalize(input))
        }

        val alphabet = charArrayOf(
            '가', '나', 'A', 'z', '0', '.', ',', ' ', '\t', '\n', '\r',
            '\u00A0', '\uFEFF', '\u000B', 'ㆍ', '/', '-'
        )
        val random = Random(20261001)
        repeat(500) {
            val input = buildString {
                repeat(random.nextInt(0, 240)) {
                    append(alphabet[random.nextInt(alphabet.size)])
                }
            }
            assertEquals(
                "Mismatch for randomized input #$it",
                legacyNormalize(input),
                TextProcessor.normalize(input)
            )
        }
    }
}
