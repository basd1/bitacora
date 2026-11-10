package dev.bastian.bitacora.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.ListFilter
import dev.bastian.bitacora.domain.NetworkMonitor
import dev.bastian.bitacora.domain.Observation
import dev.bastian.bitacora.domain.ObservationRepository
import dev.bastian.bitacora.domain.SyncScheduler
import dev.bastian.bitacora.domain.SyncStatus
import dev.bastian.bitacora.domain.SyncStatusRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListUiState(
    val items: List<Observation> = emptyList(),
    val query: String = "",
    val category: Category? = null,
    val isLoaded: Boolean = false,
    /** Id of the observation just deleted, while its undo prompt is pending. */
    val lastDeletedId: String? = null,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val errorCount: Int = 0,
)

class ListViewModel(
    private val repository: ObservationRepository,
    private val syncStatusRepository: SyncStatusRepository,
    networkMonitor: NetworkMonitor,
    scheduler: SyncScheduler,
) : ViewModel() {

    private val filter = MutableStateFlow(ListFilter())
    private val lastDeletedId = MutableStateFlow<String?>(null)

    private class SyncInfo(val online: Boolean, val status: SyncStatus, val syncing: Boolean)

    private val syncInfo = combine(
        networkMonitor.isOnline,
        syncStatusRepository.status,
        scheduler.isSyncing,
    ) { online, status, syncing -> SyncInfo(online, status, syncing) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ListUiState> = combine(
        filter.flatMapLatest { f -> repository.observeAll(f).map { items -> f to items } },
        lastDeletedId,
        syncInfo,
    ) { filtered, deleted, info ->
        ListUiState(
            items = filtered.second,
            query = filtered.first.query,
            category = filtered.first.category,
            isLoaded = true,
            lastDeletedId = deleted,
            isOnline = info.online,
            isSyncing = info.syncing,
            pendingCount = info.status.pending,
            conflictCount = info.status.conflicts,
            errorCount = info.status.errors.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ListUiState())

    fun onQueryChange(query: String) = filter.update { it.copy(query = query) }

    fun onCategoryChange(category: Category?) = filter.update { it.copy(category = category) }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            lastDeletedId.value = id
        }
    }

    /** Called when a deletion was made elsewhere, for example from the detail screen. */
    fun announceDeleted(id: String) {
        lastDeletedId.value = id
    }

    fun undoDelete() {
        val id = lastDeletedId.value ?: return
        lastDeletedId.value = null
        viewModelScope.launch { repository.restore(id) }
    }

    fun onDeletedPromptShown() {
        lastDeletedId.value = null
    }

    /** The manual sync button. */
    fun syncNow() {
        viewModelScope.launch { syncStatusRepository.retry() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
