package com.example.rsvpreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TocMappingTest {
    @Test
    fun mapsImportedOffsetsToStableGlobalTokenIndices() {
        val text = "첫 장의 시작입니다.\n\n둘째 장의 시작입니다."
        val processed = TextProcessor.process(text)
        val secondOffset = processed.normalizedText.indexOf("둘째")
        val mapped = mapImportedToc(
            processed,
            listOf(
                ImportedTocEntry("첫 장", 0, 0),
                ImportedTocEntry("둘째 장", 0, secondOffset)
            )
        )

        assertEquals(2, mapped.size)
        assertEquals(0, mapped[0].globalTokenIndex)
        val secondToken = processed.tokens.first { it.start >= secondOffset }
        assertEquals(secondToken.globalIndex, mapped[1].globalTokenIndex)
    }

    @Test
    fun emptyMetadataProducesNoFakeToc() {
        val processed = TextProcessor.process("목차처럼 보이는 제목\n\n본문입니다.")
        assertTrue(mapImportedToc(processed, emptyList()).isEmpty())
    }
}
