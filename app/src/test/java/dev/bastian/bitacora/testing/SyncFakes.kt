package dev.bastian.bitacora.testing

import dev.bastian.bitacora.data.sync.SyncRemote
import dev.bastian.bitacora.data.sync.SyncStore
import dev.bastian.bitacora.domain.SyncScheduler
import dev.bastian.bitacora.shared.BatchResponse
import dev.bastian.bitacora.shared.ChangeDto
import dev.bastian.bitacora.shared.ChangeResult
import dev.bastian.bitacora.shared.ChangeStatus
import dev.bastian.bitacora.shared.PullResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSyncScheduler : SyncScheduler {
    var requests = 0
        private set
    var retries = 0
        private set
    val syncing = MutableStateFlow(false)

    override fun requestSync() {
        requests++
    }

    override fun retryNow() {
        retries++
    }

    override val isSyncing: Flow<Boolean> = syncing
}

class InMemorySyncStore : SyncStore {
    private val syncAt = MutableStateFlow<Long?>(null)
    private val error = MutableStateFlow<String?>(null)
    private var pulledVersion = 0L

    override val lastSyncAt: Flow<Long?> = syncAt
    override val lastError: Flow<String?> = error

    override suspend fun lastPulledVersion(): Long = pulledVersion

    override suspend fun setLastPulledVersion(version: Long) {
        pulledVersion = version
    }

    override suspend fun recordSuccess(at: Long) {
        syncAt.value = at
        error.value = null
    }

    override suspend fun recordError(message: String) {
        error.value = message
    }
}

/** Scriptable remote: every push is accepted with the next version unless a handler or failure is set. */
class FakeRemote : SyncRemote {
    val pushes = mutableListOf<List<ChangeDto>>()
    var pullCalls = 0
        private set
    var failure: Exception? = null
    var pullResponse = PullResponse(emptyList(), 0)
    var pushHandler: suspend (List<ChangeDto>) -> BatchResponse = { changes ->
        BatchResponse(changes.map { ChangeResult(it.observation.id, ChangeStatus.ACCEPTED, 1L) })
    }

    override suspend fun pull(since: Long): PullResponse {
        pullCalls++
        failure?.let { throw it }
        return pullResponse
    }

    override suspend fun push(changes: List<ChangeDto>): BatchResponse {
        failure?.let { throw it }
        pushes += changes
        return pushHandler(changes)
    }
}
