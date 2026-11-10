package dev.bastian.bitacora.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun blankTitleIsEmpty() {
        assertEquals(TitleError.EMPTY, validateTitle(""))
        assertEquals(TitleError.EMPTY, validateTitle("   "))
    }

    @Test
    fun titleAtLimitIsValidAndOneOverIsTooLong() {
        assertNull(validateTitle("a".repeat(MAX_TITLE_LENGTH)))
        assertEquals(TitleError.TOO_LONG, validateTitle("a".repeat(MAX_TITLE_LENGTH + 1)))
    }

    @Test
    fun surroundingWhitespaceDoesNotCountTowardsTheLimit() {
        assertNull(validateTitle("  " + "a".repeat(MAX_TITLE_LENGTH) + "  "))
    }

    @Test
    fun notesAtLimitAreValidAndOneOverIsTooLong() {
        assertFalse(notesTooLong("n".repeat(MAX_NOTES_LENGTH)))
        assertTrue(notesTooLong("n".repeat(MAX_NOTES_LENGTH + 1)))
    }
}
