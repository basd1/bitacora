package dev.bastian.bitacora.domain

import kotlinx.coroutines.flow.Flow

data class SyncErrorItem(
    val id: String,
    val title: String,
    val message: String,
)

data class SyncStatus(
    /** Records waiting to be uploaded. */
    val pending: Int = 0,
    val conflicts: Int = 0,
    /** Records the server rejected, with the cause. */
    val errors: List<SyncErrorItem> = emptyList(),
    val lastSyncAt: Long? = null,
    /** Cause of the last failed pass, cleared by the next successful one. */
    val lastError: String? = null,
)

interface SyncStatusRepository {
    val status: Flow<SyncStatus>

    /** Puts rejected records back in the queue and starts a sync as soon as there is a connection. */
    suspend fun retry()
}

interface NetworkMonitor {
    /** Emits the current connectivity first, then every change. */
    val isOnline: Flow<Boolean>
}
