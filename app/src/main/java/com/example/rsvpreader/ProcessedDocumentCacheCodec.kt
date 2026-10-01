package com.example.rsvpreader

import java.io.DataInput
import java.io.DataOutput

object ProcessedDocumentCacheCodec {
    private const val MAGIC = 0x52535650
    const val VERSION = 2
    private const val MAX_SENTENCES = 2_000_000
    private const val MAX_TOKENS = 10_000_000

    fun write(
        output: DataOutput,
        processed: ProcessedDocument,
        contentHash: String,
        processingSchemaVersion: Int = TextProcessor.PROCESSING_SCHEMA_VERSION
    ) {
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeInt(processingSchemaVersion)
        output.writeUTF(contentHash)

        output.writeInt(processed.sentences.size)
        processed.sentences.forEach { sentence ->
            output.writeInt(sentence.start)
            output.writeInt(sentence.endExclusive)
            output.writeInt(sentence.tokenStartGlobal)
            output.writeInt(sentence.tokenCount)
        }

        output.writeInt(processed.tokens.size)
        processed.tokens.forEach { token ->
            output.writeInt(token.start)
            output.writeInt(token.endExclusive)
            output.writeInt(token.sentenceIndex)
            output.writeInt(token.indexInSentence)
            output.writeInt(token.globalIndex)
            output.writeInt(token.graphemeCount)
            output.writeBoolean(token.weakPause)
            output.writeBoolean(token.strongPause)
        }
    }

    fun read(
        input: DataInput,
        normalizedText: String,
        expectedContentHash: String,
        expectedProcessingSchemaVersion: Int = TextProcessor.PROCESSING_SCHEMA_VERSION
    ): ProcessedDocument? =
        runCatching {
            if (input.readInt() != MAGIC) return null
            if (input.readInt() != VERSION) return null
            if (input.readInt() != expectedProcessingSchemaVersion) return null
            if (input.readUTF() != expectedContentHash) return null

            val sentenceCount = input.readInt()
            if (sentenceCount !in 0..MAX_SENTENCES) return null
            val sentenceMetadata = ArrayList<IntArray>(sentenceCount)
            repeat(sentenceCount) {
                val start = input.readInt()
                val end = input.readInt()
                val tokenStart = input.readInt()
                val tokenCount = input.readInt()
                if (start !in 0..normalizedText.length || end !in start..normalizedText.length) return null
                if (tokenStart < 0 || tokenCount < 0) return null
                sentenceMetadata += intArrayOf(start, end, tokenStart, tokenCount)
            }

            val tokenCount = input.readInt()
            if (tokenCount !in 0..MAX_TOKENS) return null
            val tokens = ArrayList<RsvpToken>(tokenCount)
            repeat(tokenCount) { expectedGlobalIndex ->
                val start = input.readInt()
                val end = input.readInt()
                val sentenceIndex = input.readInt()
                val indexInSentence = input.readInt()
                val globalIndex = input.readInt()
                val graphemeCount = input.readInt()
                val weakPause = input.readBoolean()
                val strongPause = input.readBoolean()

                if (start !in 0..normalizedText.length || end !in start..normalizedText.length) return null
                if (sentenceIndex !in 0 until sentenceCount) return null
                if (indexInSentence < 0 || globalIndex != expectedGlobalIndex || graphemeCount < 0) return null

                tokens += RsvpToken(
                    display = normalizedText.substring(start, end),
                    start = start,
                    endExclusive = end,
                    sentenceIndex = sentenceIndex,
                    indexInSentence = indexInSentence,
                    globalIndex = globalIndex,
                    graphemeCount = graphemeCount,
                    weakPause = weakPause,
                    strongPause = strongPause
                )
            }

            val sentences = sentenceMetadata.map { metadata ->
                val start = metadata[0]
                val end = metadata[1]
                val tokenStart = metadata[2]
                val count = metadata[3]
                if (tokenStart > tokenCount || tokenStart + count > tokenCount) return null
                SentenceUnit(
                    text = normalizedText.substring(start, end),
                    start = start,
                    endExclusive = end,
                    tokenStartGlobal = tokenStart,
                    tokenCount = count
                )
            }

            ProcessedDocument(
                normalizedText = normalizedText,
                sentences = sentences,
                tokens = tokens
            )
        }.getOrNull()
}
