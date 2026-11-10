package dev.bastian.bitacora.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.Observation
import dev.bastian.bitacora.domain.ObservationDraft
import dev.bastian.bitacora.domain.ObservationRepository
import dev.bastian.bitacora.domain.TitleError
import dev.bastian.bitacora.domain.notesTooLong
import dev.bastian.bitacora.domain.validateTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val notes: String = "",
    val category: Category = Category.OTHER,
    val titleError: TitleError? = null,
    val notesError: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val deletedId: String? = null,
)

class EditViewModel(
    private val repository: ObservationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var loadedId: String? = null
    private var original: Observation? = null

    /** Loads the observation to edit, or an empty form when [id] is null. Idempotent for the same id. */
    fun load(id: String?) {
        if (loaded && loadedId == id) return
        loaded = true
        loadedId = id
        original = null
        if (id == null) {
            _uiState.value = EditUiState(isLoading = false, isNew = true)
            return
        }
        _uiState.value = EditUiState(isLoading = true, isNew = false)
        viewModelScope.launch {
            val observation = repository.get(id)
            if (loadedId != id) return@launch
            original = observation
            _uiState.value = if (observation == null) {
                EditUiState(isLoading = false, isNew = false, notFound = true)
            } else {
                EditUiState(
                    isLoading = false,
                    isNew = false,
                    title = observation.title,
                    notes = observation.notes,
                    category = observation.category,
                )
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, titleError = null) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value, notesError = false) }

    fun onCategoryChange(value: Category) = _uiState.update { it.copy(category = value) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.saved) return
        val titleError = validateTitle(state.title)
        val notesError = notesTooLong(state.notes)
        if (titleError != null || notesError) {
            _uiState.update { it.copy(titleError = titleError, notesError = notesError) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val base = original
            repository.save(
                ObservationDraft(
                    id = loadedId,
                    title = state.title,
                    notes = state.notes,
                    category = state.category,
                    latitude = base?.latitude,
                    longitude = base?.longitude,
                    photoPath = base?.photoPath,
                    observedAt = base?.observedAt,
                ),
            )
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun delete() {
        val id = loadedId ?: return
        viewModelScope.launch {
            repository.delete(id)
            _uiState.update { it.copy(deletedId = id) }
        }
    }

    /** The screen reacted to [EditUiState.saved] or [EditUiState.deletedId]; the next load starts clean. */
    fun consumeResult() {
        loaded = false
    }

    /** The user left the form without saving. */
    fun cancel() {
        loaded = false
    }
}
