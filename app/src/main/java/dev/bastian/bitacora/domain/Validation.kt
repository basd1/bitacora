package dev.bastian.bitacora.domain

const val MAX_TITLE_LENGTH = 120
const val MAX_NOTES_LENGTH = 2000

enum class TitleError { EMPTY, TOO_LONG }

fun validateTitle(title: String): TitleError? {
    val trimmed = title.trim()
    return when {
        trimmed.isEmpty() -> TitleError.EMPTY
        trimmed.length > MAX_TITLE_LENGTH -> TitleError.TOO_LONG
        else -> null
    }
}

/** Returns true when the notes are too long. */
fun notesTooLong(notes: String): Boolean = notes.trim().length > MAX_NOTES_LENGTH
