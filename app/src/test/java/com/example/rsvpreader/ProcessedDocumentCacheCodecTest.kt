package com.example.rsvpreader

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessedDocumentCacheCodecTest {
    @Test
    fun roundTripPreservesProcessedDocument() {
        val processed = TextProcessor.process("첫 문장입니다.\n\nSecond sentence with state-of-the-art text.")
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                ProcessedDocumentCacheCodec.write(output, processed, "hash-123")
            }
        }.toByteArray()

        val restored = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            ProcessedDocumentCacheCodec.read(input, processed.normalizedText, "hash-123")
        }

        assertEquals(processed, restored)
    }

    @Test
    fun mismatchedHashRejectsStaleCache() {
        val processed = TextProcessor.process("캐시 테스트 문장입니다.")
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                ProcessedDocumentCacheCodec.write(output, processed, "original")
            }
        }.toByteArray()

        val restored = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            ProcessedDocumentCacheCodec.read(input, processed.normalizedText, "changed")
        }

        assertNull(restored)
    }
}
