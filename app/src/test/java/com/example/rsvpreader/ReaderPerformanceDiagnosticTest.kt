package com.example.rsvpreader

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.system.measureNanoTime
import org.json.JSONObject
import org.junit.Test

class ReaderPerformanceDiagnosticTest {
    @Test
    fun printLongDocumentPipelineTimings() {
        val sample = "대한민국의 경제ㆍ사회 변화는 state-of-the-art 기술과 함께 진행됩니다. " +
            "Mr. Kim은 2026/09/28에 https://example.com/a/b를 확인했고, " +
            "input/output 값은 1,000.25입니다… 다음 문장입니다.\n\n"
        val text = buildString(sample.length * 15_000) {
            repeat(15_000) { append(sample) }
        }

        val metadata = """{"id":"perf","title":"긴 문서","sourceType":"TXT","contentHash":"perf-hash","createdAt":1,"updatedAt":1,"globalTokenIndex":0,"sentenceIndex":0,"tokenIndexInSentence":0,"tokenCount":0,"progress":0.0,"toc":[]}"""

        var metadataParsed: JSONObject? = null
        val metadataNs = measureNanoTime { metadataParsed = JSONObject(metadata) }
        check(metadataParsed?.getString("id") == "perf")

        val temp = File.createTempFile("rsvp-perf-", ".txt")
        temp.writeText(text, Charsets.UTF_8)
        var readText = ""
        val fileReadNs = measureNanoTime { readText = temp.readText(Charsets.UTF_8) }
        check(readText.length == text.length)

        var normalized = ""
        val normalizeNs = measureNanoTime { normalized = TextProcessor.normalize(readText) }
        check(normalized.isNotBlank())

        lateinit var coldProcessed: ProcessedDocument
        val coldProcessNs = measureNanoTime { coldProcessed = TextProcessor.process(readText) }

        lateinit var warmProcessed: ProcessedDocument
        val warmProcessNs = measureNanoTime { warmProcessed = TextProcessor.process(readText) }
        check(coldProcessed.tokens.size == warmProcessed.tokens.size)

        lateinit var encoded: ByteArray
        val encodeNs = measureNanoTime {
            encoded = ByteArrayOutputStream().also { buffer ->
                DataOutputStream(buffer).use { output ->
                    ProcessedDocumentCacheCodec.write(output, coldProcessed, "perf-hash")
                }
            }.toByteArray()
        }

        lateinit var decoded: ProcessedDocument
        val decodeNs = measureNanoTime {
            decoded = DataInputStream(ByteArrayInputStream(encoded)).use { input ->
                requireNotNull(
                    ProcessedDocumentCacheCodec.read(
                        input,
                        coldProcessed.normalizedText,
                        "perf-hash"
                    )
                )
            }
        }
        check(decoded.tokens.size == coldProcessed.tokens.size)

        val memory = hashMapOf("perf" to coldProcessed)
        var hit: ProcessedDocument? = null
        val memoryHitNs = measureNanoTime { repeat(10_000) { hit = memory["perf"] } }
        check(hit === coldProcessed)

        val session = ReaderSessionHolder().apply {
            update("perf", coldProcessed, coldProcessed.tokens.size / 2)
        }
        var sessionHit: ReaderSessionSnapshot? = null
        val sessionHitNs = measureNanoTime {
            repeat(10_000) { sessionHit = session.snapshot("perf") }
        }
        check(sessionHit?.processed === coldProcessed)

        temp.delete()

        fun ms(ns: Long) = ns / 1_000_000.0
        println(
            "RSVP_PERF_OPTIMIZED " +
                "chars=${text.length} tokens=${coldProcessed.tokens.size} sentences=${coldProcessed.sentences.size} " +
                "metadataMs=${"%.3f".format(ms(metadataNs))} " +
                "fileReadMs=${"%.3f".format(ms(fileReadNs))} " +
                "normalizeMs=${"%.3f".format(ms(normalizeNs))} " +
                "coldProcessMs=${"%.3f".format(ms(coldProcessNs))} " +
                "warmProcessMs=${"%.3f".format(ms(warmProcessNs))} " +
                "cacheEncodeMs=${"%.3f".format(ms(encodeNs))} " +
                "cacheDecodeMs=${"%.3f".format(ms(decodeNs))} " +
                "memoryHit10kMs=${"%.3f".format(ms(memoryHitNs))} " +
                "sessionHit10kMs=${"%.3f".format(ms(sessionHitNs))} " +
                "cacheBytes=${encoded.size}"
        )
    }
}
