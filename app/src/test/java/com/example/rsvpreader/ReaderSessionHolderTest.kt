package com.example.rsvpreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSessionHolderTest {
    @Test
    fun settingsRoundTripKeepsSameProcessedObjectAndIndex() {
        val processed = TextProcessor.process("첫 문장입니다. 두 번째 문장입니다.")
        val holder = ReaderSessionHolder()
        holder.update("doc", processed, 1)

        val restored = holder.snapshot("doc")

        assertSame(processed, restored?.processed)
        assertEquals(1, restored?.globalTokenIndex)
    }

    @Test
    fun clearingReaderSessionReleasesDocument() {
        val processed = TextProcessor.process("문장입니다.")
        val holder = ReaderSessionHolder()
        holder.update("doc", processed, 0)
        holder.clear("doc")

        assertNull(holder.snapshot("doc"))
    }
}
