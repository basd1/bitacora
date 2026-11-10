package dev.bastian.bitacora.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.bitacora.domain.NetworkMonitor
import dev.bastian.bitacora.domain.SyncScheduler
import dev.bastian.bitacora.domain.SyncStatus
import dev.bastian.bitacora.domain.SyncStatusRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncStatusUiState(
    val status: SyncStatus = SyncStatus(),
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
)

class SyncStatusViewModel(
    private val repository: SyncStatusRepository,
    networkMonitor: NetworkMonitor,
    scheduler: SyncScheduler,
) : ViewModel() {

    val uiState: StateFlow<SyncStatusUiState> = combine(
        repository.status,
        networkMonitor.isOnline,
        scheduler.isSyncing,
    ) { status, online, syncing -> SyncStatusUiState(status, online, syncing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncStatusUiState())

    fun retry() {
        viewModelScope.launch { repository.retry() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
