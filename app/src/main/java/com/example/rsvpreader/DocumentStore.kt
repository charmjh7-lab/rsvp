package com.example.rsvpreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.UUID

class DocumentStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val documentDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val processedCache = object : LinkedHashMap<String, ProcessedDocument>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ProcessedDocument>?): Boolean = size > 3
    }

    fun listDocuments(): List<DocumentEntry> {
        val ids = prefs.getStringSet("doc_ids", emptySet()).orEmpty()
        return ids.mapNotNull(::readEntry).sortedByDescending { it.updatedAt }
    }

    fun getDocument(id: String): DocumentEntry? = readEntry(id)

    fun loadText(id: String): String? {
        val file = File(documentDir, "$id.txt")
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun loadProcessed(id: String): ProcessedDocument? {
        synchronized(processedCache) {
            processedCache[id]?.let { return it }
        }

        val entry = readEntry(id) ?: return null
        val text = loadText(id) ?: return null
        val cached = readProcessedCache(id, text, entry.contentHash)
        val processed = cached ?: TextProcessor.process(text).also {
            if (it.tokens.isNotEmpty()) writeProcessedCache(id, it, entry.contentHash)
        }
        synchronized(processedCache) { processedCache[id] = processed }
        return processed
    }

    fun addDocument(
        requestedTitle: String?,
        sourceType: SourceType,
        originalName: String?,
        rawText: String,
        sourceToc: List<ImportedTocEntry> = emptyList()
    ): ImportResult {
        val processed = TextProcessor.process(rawText)
        if (processed.tokens.isEmpty()) {
            return ImportResult.Failure("읽을 수 있는 텍스트를 찾지 못했습니다.")
        }
        val hash = sha256(processed.normalizedText)
        val mappedToc = mapImportedToc(processed, sourceToc)
        val duplicate = listDocuments().firstOrNull { it.contentHash == hash }
        if (duplicate != null) {
            val resolved = if (duplicate.toc.isEmpty() && mappedToc.isNotEmpty()) {
                duplicate.copy(toc = mappedToc).also(::writeEntry)
            } else {
                duplicate
            }
            return ImportResult.Success(resolved, resolved)
        }

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
            progress = 0f,
            toc = mappedToc
        )
        val ids = prefs.getStringSet("doc_ids", emptySet()).orEmpty().toMutableSet().apply { add(id) }
        prefs.edit().putStringSet("doc_ids", ids).putString("doc_$id", entry.toJson().toString()).apply()
        synchronized(processedCache) { processedCache[id] = processed }
        writeProcessedCache(id, processed, hash)
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
        processedCacheFile(id).delete()
        synchronized(processedCache) { processedCache.remove(id) }
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

    private fun processedCacheFile(id: String) = File(documentDir, "$id.processed.bin")

    private fun readProcessedCache(id: String, text: String, contentHash: String): ProcessedDocument? {
        val file = processedCacheFile(id)
        if (!file.exists()) return null
        val result = runCatching {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                ProcessedDocumentCacheCodec.read(input, text, contentHash)
            }
        }.getOrNull()
        if (result == null) file.delete()
        return result
    }

    private fun writeProcessedCache(id: String, processed: ProcessedDocument, contentHash: String) {
        val target = processedCacheFile(id)
        val temp = File(documentDir, "$id.processed.tmp")
        runCatching {
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use { output ->
                ProcessedDocumentCacheCodec.write(output, processed, contentHash)
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }.onFailure { temp.delete() }
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
        put("toc", JSONArray().apply {
            toc.forEach { item ->
                put(JSONObject().apply {
                    put("title", item.title)
                    put("level", item.level)
                    put("globalTokenIndex", item.globalTokenIndex)
                })
            }
        })
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
        progress = optDouble("progress", 0.0).toFloat(),
        toc = optJSONArray("toc").toDocumentToc()
    )

    private fun JSONArray?.toDocumentToc(): List<DocumentTocEntry> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                if (title.isBlank()) continue
                add(
                    DocumentTocEntry(
                        title = title,
                        level = item.optInt("level", 0).coerceAtLeast(0),
                        globalTokenIndex = item.optInt("globalTokenIndex", 0).coerceAtLeast(0)
                    )
                )
            }
        }
    }
}
