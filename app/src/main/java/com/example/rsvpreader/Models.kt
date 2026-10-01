package com.example.rsvpreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

const val WPM_MIN = 60
const val WPM_MAX = 600
const val WPM_DEFAULT = 240

enum class SourceType { PASTE, TXT, PDF, EPUB }

enum class LightBackground { WHITE, WARM }

data class ImportedTocEntry(
    val title: String,
    val level: Int,
    val charOffset: Int
)

data class DocumentTocEntry(
    val title: String,
    val level: Int,
    val globalTokenIndex: Int
)

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
    val progress: Float,
    val toc: List<DocumentTocEntry> = emptyList()
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

fun mapImportedToc(processed: ProcessedDocument, source: List<ImportedTocEntry>): List<DocumentTocEntry> {
    if (processed.tokens.isEmpty() || source.isEmpty()) return emptyList()
    return source.mapNotNull { item ->
        val title = item.title.trim()
        if (title.isBlank()) return@mapNotNull null
        val offset = item.charOffset.coerceIn(0, processed.normalizedText.length)
        val token = processed.tokens.firstOrNull { it.endExclusive > offset }
            ?: processed.tokens.lastOrNull()
            ?: return@mapNotNull null
        DocumentTocEntry(
            title = title,
            level = item.level.coerceAtLeast(0),
            globalTokenIndex = token.globalIndex
        )
    }.distinctBy { Triple(it.title, it.level, it.globalTokenIndex) }
}

data class ReaderPosition(
    val globalTokenIndex: Int,
    val sentenceIndex: Int,
    val tokenIndexInSentence: Int,
    val progress: Float
)

data class AppSettings(
    val wpm: Int = WPM_DEFAULT,
    val focusColorArgb: Long = 0xFFE65100,
    val showFocusColor: Boolean = true,
    val contextFontSp: Float = 20f,
    val rsvpFontSp: Float = 46f,
    val darkMode: Boolean = false,
    val lightBackground: LightBackground = LightBackground.WHITE,
    val punctuationDelay: Boolean = true,
    val longWordDelay: Boolean = false,
    val ttsRate: Float = 1.0f,
    val ttsEnginePackage: String? = null,
    val ttsVoiceName: String? = null,
    val lastTtsMode: Boolean = false,
    val rsvpAtTop: Boolean = false
) {
    val focusColor: Color get() = Color(focusColorArgb.toInt())
    val effectiveFocusColor: Color get() = if (darkMode) adaptFocusColorForDark(focusColor) else focusColor
}

private val darkReaderBackground = Color(0xFF121212)

private fun adaptFocusColorForDark(base: Color): Color {
    var candidate = base
    repeat(8) {
        val lighter = maxOf(candidate.luminance(), darkReaderBackground.luminance())
        val darker = minOf(candidate.luminance(), darkReaderBackground.luminance())
        if ((lighter + 0.05f) / (darker + 0.05f) >= 4.5f) return candidate
        candidate = Color(
            red = candidate.red + (1f - candidate.red) * 0.14f,
            green = candidate.green + (1f - candidate.green) * 0.14f,
            blue = candidate.blue + (1f - candidate.blue) * 0.14f,
            alpha = candidate.alpha
        )
    }
    return candidate
}

sealed interface ImportResult {
    data class Success(val entry: DocumentEntry, val duplicateOf: DocumentEntry? = null) : ImportResult
    data class Failure(val message: String) : ImportResult
}
