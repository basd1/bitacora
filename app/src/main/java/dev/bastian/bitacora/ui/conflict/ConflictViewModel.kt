package dev.bastian.bitacora.ui.conflict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.ConflictDetails
import dev.bastian.bitacora.domain.Field
import dev.bastian.bitacora.domain.MergeResult
import dev.bastian.bitacora.domain.ObservationRepository
import dev.bastian.bitacora.domain.Side
import dev.bastian.bitacora.domain.SyncableFields
import dev.bastian.bitacora.domain.formatCoordinate
import dev.bastian.bitacora.domain.mergeThreeWay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A field value ready to display. */
sealed interface ConflictValue {
    data object Empty : ConflictValue
    data class Text(val value: String) : ConflictValue
    data class CategoryValue(val category: Category) : ConflictValue
    data class Moment(val millis: Long) : ConflictValue
}

data class ConflictFieldUi(
    val field: Field,
    val local: ConflictValue,
    val remote: ConflictValue,
    val choice: Side,
)

data class ConflictUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val fields: List<ConflictFieldUi> = emptyList(),
    val isResolving: Boolean = false,
    val resolved: Boolean = false,
)

class ConflictViewModel(
    private val repository: ObservationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConflictUiState())
    val uiState: StateFlow<ConflictUiState> = _uiState.asStateFlow()

    private var loadedId: String? = null
    private var details: ConflictDetails? = null
    private var conflicted: MergeResult.Conflicted? = null

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        viewModelScope.launch {
            val found = repository.getConflict(id)
            if (found == null) {
                _uiState.value = ConflictUiState(isLoading = false, notFound = true)
                return@launch
            }
            when (val merge = mergeThreeWay(found.base, found.local, found.remote)) {
                is MergeResult.Clean -> {
                    // Nothing is left to decide (for example the server record changed again): apply the merge.
                    repository.resolveConflict(id, merge.merged)
                    _uiState.value = ConflictUiState(isLoading = false, resolved = true)
                }
                is MergeResult.Conflicted -> {
                    details = found
                    conflicted = merge
                    _uiState.value = ConflictUiState(
                        isLoading = false,
                        fields = merge.conflicts.map { field ->
                            ConflictFieldUi(
                                field = field,
                                local = valueOf(found.local, field),
                                remote = valueOf(found.remote, field),
                                choice = Side.LOCAL,
                            )
                        },
                    )
                }
            }
        }
    }

    fun choose(field: Field, side: Side) {
        _uiState.update { state ->
            state.copy(fields = state.fields.map { if (it.field == field) it.copy(choice = side) else it })
        }
    }

    fun resolve() {
        val found = details ?: return
        val merge = conflicted ?: return
        val state = _uiState.value
        if (state.isResolving || state.resolved) return
        _uiState.update { it.copy(isResolving = true) }
        val choices = state.fields.associate { it.field to it.choice }
        viewModelScope.launch {
            repository.resolveConflict(found.id, merge.resolve(found.remote, choices))
            _uiState.update { it.copy(isResolving = false, resolved = true) }
        }
    }

    private fun valueOf(fields: SyncableFields, field: Field): ConflictValue = when (field) {
        Field.TITLE -> textOrEmpty(fields.title)
        Field.NOTES -> textOrEmpty(fields.notes)
        Field.CATEGORY -> ConflictValue.CategoryValue(fields.category)
        Field.LATITUDE -> fields.latitude?.let { ConflictValue.Text(formatCoordinate(it)) } ?: ConflictValue.Empty
        Field.LONGITUDE -> fields.longitude?.let { ConflictValue.Text(formatCoordinate(it)) } ?: ConflictValue.Empty
        Field.OBSERVED_AT -> ConflictValue.Moment(fields.observedAt)
    }

    private fun textOrEmpty(text: String): ConflictValue =
        if (text.isBlank()) ConflictValue.Empty else ConflictValue.Text(text)
}
