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

    @Test
    fun mismatchedProcessorVersionRejectsStaleCache() {
        val processed = TextProcessor.process("프로세서 버전 테스트입니다.")
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                ProcessedDocumentCacheCodec.write(
                    output,
                    processed,
                    "hash",
                    processingSchemaVersion = TextProcessor.PROCESSING_SCHEMA_VERSION - 1
                )
            }
        }.toByteArray()

        val restored = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            ProcessedDocumentCacheCodec.read(input, processed.normalizedText, "hash")
        }

        assertNull(restored)
    }

    @Test
    fun corruptedCacheReturnsNullWithoutTouchingSourceText() {
        val originalText = "원문은 그대로 유지되어야 합니다."
        val corrupted = byteArrayOf(0x52, 0x53, 0x01, 0x02, 0x03)

        val restored = DataInputStream(ByteArrayInputStream(corrupted)).use { input ->
            ProcessedDocumentCacheCodec.read(input, originalText, "hash")
        }

        assertNull(restored)
        assertEquals("원문은 그대로 유지되어야 합니다.", originalText)
    }
}
