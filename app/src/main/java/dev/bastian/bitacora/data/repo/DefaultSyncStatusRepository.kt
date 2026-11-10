package dev.bastian.bitacora.data.repo

import dev.bastian.bitacora.data.db.ObservationDao
import dev.bastian.bitacora.data.sync.SyncStore
import dev.bastian.bitacora.domain.SyncErrorItem
import dev.bastian.bitacora.domain.SyncScheduler
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.domain.SyncStatus
import dev.bastian.bitacora.domain.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DefaultSyncStatusRepository(
    private val dao: ObservationDao,
    private val store: SyncStore,
    private val scheduler: SyncScheduler,
) : SyncStatusRepository {

    override val status: Flow<SyncStatus> = combine(
        dao.observeCount(SyncState.PENDING),
        dao.observeCount(SyncState.CONFLICT),
        dao.observeErrors(),
        store.lastSyncAt,
        store.lastError,
    ) { pending, conflicts, errors, lastSyncAt, lastError ->
        SyncStatus(
            pending = pending,
            conflicts = conflicts,
            errors = errors.map { SyncErrorItem(it.id, it.title, it.errorMessage.orEmpty()) },
            lastSyncAt = lastSyncAt,
            lastError = lastError,
        )
    }

    override suspend fun retry() {
        dao.resetErrors()
        scheduler.retryNow()
    }
}
