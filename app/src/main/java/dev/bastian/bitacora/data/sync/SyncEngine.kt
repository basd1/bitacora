package dev.bastian.bitacora.data.sync

import dev.bastian.bitacora.data.db.ObservationDao
import dev.bastian.bitacora.data.db.ObservationEntity
import dev.bastian.bitacora.data.toDto
import dev.bastian.bitacora.domain.Clock
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.shared.ChangeDto
import dev.bastian.bitacora.shared.ChangeStatus
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
 * Runs one synchronization pass. Uploads are idempotent: the server answers a replayed
 * change with the version it already stored, so a pass interrupted at any point can
 * simply be repeated.
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
                ChangeStatus.CONFLICT -> dao.markError(listOf(entity.id), CONFLICT_MESSAGE)
            }
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is ResponseException -> "HTTP ${e.response.status.value}"
        else -> e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
    }

    private companion object {
        const val BATCH_SIZE = 50
        const val MAX_PUSH_ROUNDS = 3
        const val CONFLICT_MESSAGE = "The server holds a newer version of this observation"
    }
}
