package dev.bastian.bitacora.data.repo

import dev.bastian.bitacora.data.db.ObservationDao
import dev.bastian.bitacora.data.db.ObservationEntity
import dev.bastian.bitacora.data.toDomain
import dev.bastian.bitacora.data.toNewEntity
import dev.bastian.bitacora.domain.Clock
import dev.bastian.bitacora.domain.IdGenerator
import dev.bastian.bitacora.domain.ListFilter
import dev.bastian.bitacora.domain.Observation
import dev.bastian.bitacora.domain.ObservationDraft
import dev.bastian.bitacora.domain.ObservationRepository
import dev.bastian.bitacora.domain.SyncScheduler
import dev.bastian.bitacora.domain.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultObservationRepository(
    private val dao: ObservationDao,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val scheduler: SyncScheduler,
) : ObservationRepository {

    override fun observeAll(filter: ListFilter): Flow<List<Observation>> =
        dao.observeFiltered(filter.category?.name, filter.query.trim())
            .map { rows -> rows.map { it.toDomain() } }

    override fun observe(id: String): Flow<Observation?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun get(id: String): Observation? = dao.getById(id)?.toDomain()

    override suspend fun save(draft: ObservationDraft): String {
        val now = clock.now()
        val id = draft.id ?: ids.newId()
        dao.update(id) { existing ->
            if (existing == null) draft.toNewEntity(id, now) else existing.applyDraft(draft, now)
        }
        scheduler.requestSync()
        return id
    }

    override suspend fun delete(id: String) {
        val now = clock.now()
        dao.update(id) { existing ->
            if (existing == null || existing.deleted) existing else existing.markChanged(now, deleted = true)
        }
        scheduler.requestSync()
    }

    override suspend fun restore(id: String) {
        val now = clock.now()
        dao.update(id) { existing ->
            if (existing == null || !existing.deleted) existing else existing.markChanged(now, deleted = false)
        }
        scheduler.requestSync()
    }
}

private fun ObservationEntity.applyDraft(draft: ObservationDraft, now: Long): ObservationEntity {
    val edited = copy(
        title = draft.title.trim(),
        notes = draft.notes.trim(),
        category = draft.category,
        latitude = draft.latitude,
        longitude = draft.longitude,
        observedAt = draft.observedAt ?: observedAt,
        photoPath = draft.photoPath,
    )
    // A photo-only change is local (photos are not synced), so it must not queue an upload.
    val syncedFieldsChanged = edited.copy(photoPath = photoPath) != this
    return if (syncedFieldsChanged) edited.markChanged(now, deleted = edited.deleted) else edited
}

/** Flags the record as locally modified. A record in conflict stays in conflict until resolved. */
private fun ObservationEntity.markChanged(now: Long, deleted: Boolean) = copy(
    deleted = deleted,
    updatedAt = maxOf(now, updatedAt + 1),
    syncState = if (syncState == SyncState.CONFLICT) SyncState.CONFLICT else SyncState.PENDING,
    errorMessage = null,
)
