package com.example.rsvpreader

import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpFocusTest {
    @Test
    fun originalModeKeepsCenterIncludingTwoAndOneCharacters() {
        for ((word, center) in listOf("안녕하세요" to 2..2, "안녕" to 0..0, "안" to 0..0)) {
            assertEquals(RsvpFocus(word, center, center), rsvpFocus(word, false))
        }
    }

    @Test
    fun precedingModeSeparatesAlignmentFromHighlight() {
        assertEquals(RsvpFocus("안녕하세요", 2..2, 1..1), rsvpFocus("안녕하세요", true))
        assertEquals(RsvpFocus("안녕", 1..1, 0..0), rsvpFocus("안녕", true))
        assertEquals(RsvpFocus("안\u3000", 1..1, 0..0), rsvpFocus("안", true))
    }

    @Test
    fun punctuationDoesNotReplaceTheVirtualAnchor() {
        assertEquals(RsvpFocus("(안\u3000).", 2..2, 1..1), rsvpFocus("(안).", true))
        assertEquals(RsvpFocus("안녕!", 1..1, 0..0), rsvpFocus("안녕!", true))
        assertEquals(RsvpFocus("", null, null), rsvpFocus("", true))
    }
}
