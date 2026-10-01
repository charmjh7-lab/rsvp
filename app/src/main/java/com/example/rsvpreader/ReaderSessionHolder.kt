package com.example.rsvpreader

data class ReaderSessionSnapshot(
    val processed: ProcessedDocument,
    val globalTokenIndex: Int
)

class ReaderSessionHolder {
    private var documentId: String? = null
    private var processed: ProcessedDocument? = null
    private var globalTokenIndex: Int = 0

    fun snapshot(id: String): ReaderSessionSnapshot? {
        val current = processed ?: return null
        if (documentId != id) return null
        return ReaderSessionSnapshot(current, globalTokenIndex)
    }

    fun update(id: String, processed: ProcessedDocument, globalTokenIndex: Int) {
        documentId = id
        this.processed = processed
        this.globalTokenIndex = globalTokenIndex.coerceIn(
            0,
            (processed.tokens.size - 1).coerceAtLeast(0)
        )
    }

    fun updateIndex(id: String, globalTokenIndex: Int) {
        val current = processed ?: return
        if (documentId != id) return
        this.globalTokenIndex = globalTokenIndex.coerceIn(
            0,
            (current.tokens.size - 1).coerceAtLeast(0)
        )
    }

    fun clear(id: String? = null) {
        if (id != null && documentId != id) return
        documentId = null
        processed = null
        globalTokenIndex = 0
    }
}
