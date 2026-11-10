package dev.bastian.bitacora.data.sync

import dev.bastian.bitacora.data.db.ObservationDao
import dev.bastian.bitacora.data.db.ObservationEntity
import dev.bastian.bitacora.data.toDto
import dev.bastian.bitacora.domain.Clock
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.shared.ChangeDto
import dev.bastian.bitacora.shared.ChangeStatus
import dev.bastian.bitacora.shared.ObservationDto
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncResult {
    data object Success : SyncResult
    data class Failure(val message: String) : SyncResult
}

/**
 * Runs one synchronization pass: upload pending changes (merging on conflict), then download
 * what changed on the server. Uploads are idempotent: the server answers a replayed change with
 * the version it already stored, so a pass interrupted at any point can simply be repeated.
 * The pull cursor only advances after every downloaded change was applied.
 */
class SyncEngine(
    private val dao: ObservationDao,
    private val remote: SyncRemote,
    private val store: SyncStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun run(): SyncResult = mutex.withLock {
        try {
            push()
            pull()
            store.recordSuccess(clock.now())
            SyncResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = describe(e)
            store.recordError(message)
            SyncResult.Failure(message)
        }
    }

    private suspend fun push() {
        repeat(MAX_PUSH_ROUNDS) {
            val pending = dao.getByState(SyncState.PENDING)
            if (pending.isEmpty()) return
            pending.chunked(BATCH_SIZE).forEach { chunk -> pushChunk(chunk) }
        }
    }

    private suspend fun pushChunk(chunk: List<ObservationEntity>) {
        val response = try {
            remote.push(chunk.map { ChangeDto(it.toDto(), it.serverVersion) })
        } catch (e: ClientRequestException) {
            // The server rejected this request outright; retrying it unchanged cannot help.
            dao.markError(chunk.map { it.id }, describe(e))
            return
        }
        val resultsById = response.results.associateBy { it.id }
        for (entity in chunk) {
            val result = resultsById[entity.id] ?: continue
            when (result.status) {
                ChangeStatus.ACCEPTED -> dao.markPushed(
                    id = entity.id,
                    version = result.version,
                    snapshot = SnapshotCodec.encodeDto(entity.toDto()),
                    pushedUpdatedAt = entity.updatedAt,
                )
                ChangeStatus.CONFLICT -> resolveRejected(entity.id, result.current, result.version)
            }
        }
    }

    /**
     * The server holds a newer version than the one the change was based on. Merge it: a clean merge
     * is queued again on top of the server version; a real conflict waits for the user.
     */
    private suspend fun resolveRejected(id: String, current: ObservationDto?, version: Long) {
        if (current == null) {
            dao.markError(listOf(id), MISSING_CURRENT_MESSAGE)
            return
        }
        val now = clock.now()
        dao.update(id) { latest -> latest?.let { reconcile(it, current, version, now) } }
    }

    private suspend fun pull() {
        val since = store.lastPulledVersion()
        val response = remote.pull(since)
        val now = clock.now()
        for (item in response.items) {
            dao.update(item.observation.id) { local ->
                when {
                    local == null -> newEntityFromRemote(item.observation, item.version)
                    // Already have this version (for example our own upload coming back).
                    local.syncState != SyncState.CONFLICT && local.serverVersion >= item.version -> local
                    else -> reconcile(local, item.observation, item.version, now)
                }
            }
        }
        store.setLastPulledVersion(response.latestVersion)
    }

    private fun describe(e: Exception): String = when (e) {
        is ResponseException -> "HTTP ${e.response.status.value}"
        else -> e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
    }

    private companion object {
        const val BATCH_SIZE = 50
        const val MAX_PUSH_ROUNDS = 3
        const val MISSING_CURRENT_MESSAGE = "The server reported a conflict without the current record"
    }
}
