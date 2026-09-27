package com.example.rsvpreader

import androidx.compose.ui.graphics.Color

enum class SourceType { PASTE, TXT, PDF, EPUB }

data class DocumentEntry(
    val id: String,
    val title: String,
    val sourceType: SourceType,
    val originalName: String?,
    val contentHash: String,
    val createdAt: Long,
    val updatedAt: Long,
    val globalTokenIndex: Int,
    val sentenceIndex: Int,
    val tokenIndexInSentence: Int,
    val tokenCount: Int,
    val progress: Float
)

data class RsvpToken(
    val display: String,
    val start: Int,
    val endExclusive: Int,
    val sentenceIndex: Int,
    val indexInSentence: Int,
    val globalIndex: Int,
    val graphemeCount: Int,
    val weakPause: Boolean,
    val strongPause: Boolean
)

data class SentenceUnit(
    val text: String,
    val start: Int,
    val endExclusive: Int,
    val tokenStartGlobal: Int,
    val tokenCount: Int
)

data class ProcessedDocument(
    val normalizedText: String,
    val sentences: List<SentenceUnit>,
    val tokens: List<RsvpToken>
)

data class ReaderPosition(
    val globalTokenIndex: Int,
    val sentenceIndex: Int,
    val tokenIndexInSentence: Int,
    val progress: Float
)

data class AppSettings(
    val wpm: Int = 240,
    val focusColorArgb: Long = 0xFFE65100,
    val showFocusColor: Boolean = true,
    val contextFontSp: Float = 20f,
    val rsvpFontSp: Float = 46f,
    val darkMode: Boolean = false,
    val punctuationDelay: Boolean = true,
    val longWordDelay: Boolean = false,
    val ttsRate: Float = 1.0f,
    val ttsEnginePackage: String? = null,
    val ttsVoiceName: String? = null,
    val lastTtsMode: Boolean = false,
    val rsvpAtTop: Boolean = false
) {
    val focusColor: Color get() = Color(focusColorArgb.toInt())
}

sealed interface ImportResult {
    data class Success(val entry: DocumentEntry, val duplicateOf: DocumentEntry? = null) : ImportResult
    data class Failure(val message: String) : ImportResult
}
