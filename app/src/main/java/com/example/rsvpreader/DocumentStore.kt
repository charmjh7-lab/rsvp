package com.example.rsvpreader

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class DocumentStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val documentDir = File(context.filesDir, "documents").apply { mkdirs() }

    fun listDocuments(): List<DocumentEntry> {
        val ids = prefs.getStringSet("doc_ids", emptySet()).orEmpty()
        return ids.mapNotNull(::readEntry).sortedByDescending { it.updatedAt }
    }

    fun getDocument(id: String): DocumentEntry? = readEntry(id)

    fun loadText(id: String): String? {
        val file = File(documentDir, "$id.txt")
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun addDocument(
        requestedTitle: String?,
        sourceType: SourceType,
        originalName: String?,
        rawText: String
    ): ImportResult {
        val processed = TextProcessor.process(rawText)
        if (processed.tokens.isEmpty()) {
            return ImportResult.Failure("읽을 수 있는 텍스트를 찾지 못했습니다.")
        }
        val hash = sha256(processed.normalizedText)
        val duplicate = listDocuments().firstOrNull { it.contentHash == hash }
        if (duplicate != null) return ImportResult.Success(duplicate, duplicate)

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val title = requestedTitle?.trim().takeUnless { it.isNullOrBlank() }
            ?: deriveTitle(processed)

        File(documentDir, "$id.txt").writeText(processed.normalizedText, Charsets.UTF_8)
        val entry = DocumentEntry(
            id = id,
            title = title,
            sourceType = sourceType,
            originalName = originalName,
            contentHash = hash,
            createdAt = now,
            updatedAt = now,
            globalTokenIndex = 0,
            sentenceIndex = 0,
            tokenIndexInSentence = 0,
            tokenCount = processed.tokens.size,
            progress = 0f
        )
        val ids = prefs.getStringSet("doc_ids", emptySet()).orEmpty().toMutableSet().apply { add(id) }
        prefs.edit().putStringSet("doc_ids", ids).putString("doc_$id", entry.toJson().toString()).apply()
        return ImportResult.Success(entry)
    }

    fun rename(id: String, newTitle: String): Boolean {
        val old = readEntry(id) ?: return false
        val clean = newTitle.trim()
        if (clean.isBlank()) return false
        writeEntry(old.copy(title = clean, updatedAt = System.currentTimeMillis()))
        return true
    }

    fun delete(id: String) {
        File(documentDir, "$id.txt").delete()
        val ids = prefs.getStringSet("doc_ids", emptySet()).orEmpty().toMutableSet().apply { remove(id) }
        prefs.edit().putStringSet("doc_ids", ids).remove("doc_$id").apply()
    }

    fun updatePosition(id: String, processed: ProcessedDocument, globalIndex: Int, sync: Boolean = false) {
        val old = readEntry(id) ?: return
        if (processed.tokens.isEmpty()) return
        val safe = globalIndex.coerceIn(0, processed.tokens.lastIndex)
        val token = processed.tokens[safe]
        val progress = if (processed.tokens.size <= 1) 1f else safe.toFloat() / processed.tokens.lastIndex.toFloat()
        writeEntry(
            old.copy(
                updatedAt = System.currentTimeMillis(),
                globalTokenIndex = safe,
                sentenceIndex = token.sentenceIndex,
                tokenIndexInSentence = token.indexInSentence,
                tokenCount = processed.tokens.size,
                progress = progress
            ),
            sync = sync
        )
    }

    private fun writeEntry(entry: DocumentEntry, sync: Boolean = false) {
        val editor = prefs.edit().putString("doc_${entry.id}", entry.toJson().toString())
        if (sync) editor.commit() else editor.apply()
    }

    private fun readEntry(id: String): DocumentEntry? = runCatching {
        val raw = prefs.getString("doc_$id", null) ?: return null
        JSONObject(raw).toEntry()
    }.getOrNull()

    private fun deriveTitle(processed: ProcessedDocument): String {
        val base = processed.sentences.firstOrNull()?.text ?: processed.normalizedText
        return base.replace('\n', ' ').trim().take(40).ifBlank { "제목 없는 문서" }
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun DocumentEntry.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("sourceType", sourceType.name)
        put("originalName", originalName)
        put("contentHash", contentHash)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("globalTokenIndex", globalTokenIndex)
        put("sentenceIndex", sentenceIndex)
        put("tokenIndexInSentence", tokenIndexInSentence)
        put("tokenCount", tokenCount)
        put("progress", progress.toDouble())
    }

    private fun JSONObject.toEntry() = DocumentEntry(
        id = getString("id"),
        title = getString("title"),
        sourceType = runCatching { SourceType.valueOf(getString("sourceType")) }.getOrDefault(SourceType.TXT),
        originalName = optString("originalName").takeIf { it.isNotBlank() && it != "null" },
        contentHash = getString("contentHash"),
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt"),
        globalTokenIndex = optInt("globalTokenIndex", 0),
        sentenceIndex = optInt("sentenceIndex", 0),
        tokenIndexInSentence = optInt("tokenIndexInSentence", 0),
        tokenCount = optInt("tokenCount", 0),
        progress = optDouble("progress", 0.0).toFloat()
    )
}
