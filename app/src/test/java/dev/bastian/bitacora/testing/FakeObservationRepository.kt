package dev.bastian.bitacora.testing

import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.ConflictDetails
import dev.bastian.bitacora.domain.ListFilter
import dev.bastian.bitacora.domain.Observation
import dev.bastian.bitacora.domain.ObservationDraft
import dev.bastian.bitacora.domain.ObservationRepository
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.domain.SyncableFields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class FakeObservationRepository : ObservationRepository {
    private val items = MutableStateFlow<List<Observation>>(emptyList())
    private val deletedIds = MutableStateFlow<Set<String>>(emptySet())

    val savedDrafts = mutableListOf<ObservationDraft>()
    var conflict: ConflictDetails? = null
    val resolutions = mutableListOf<Pair<String, SyncableFields>>()
    private var counter = 0

    fun seed(vararg observations: Observation) {
        items.value = observations.toList()
    }

    override fun observeAll(filter: ListFilter): Flow<List<Observation>> =
        combine(items, deletedIds) { all, deleted ->
            all.filter { it.id !in deleted }
                .filter { filter.category == null || it.category == filter.category }
                .filter {
                    filter.query.isBlank() ||
                        it.title.contains(filter.query, ignoreCase = true) ||
                        it.notes.contains(filter.query, ignoreCase = true)
                }
        }

    override fun observe(id: String): Flow<Observation?> = items.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun get(id: String): Observation? = items.value.firstOrNull { it.id == id }

    override suspend fun save(draft: ObservationDraft): String {
        savedDrafts += draft
        val id = draft.id ?: "new-${++counter}"
        val saved = Observation(
            id = id,
            title = draft.title.trim(),
            notes = draft.notes.trim(),
            category = draft.category,
            latitude = draft.latitude,
            longitude = draft.longitude,
            photoPath = draft.photoPath,
            observedAt = draft.observedAt ?: 0L,
            updatedAt = 0L,
            syncState = SyncState.PENDING,
            syncError = null,
        )
        items.value = items.value.filter { it.id != id } + saved
        return id
    }

    override suspend fun delete(id: String) {
        deletedIds.value = deletedIds.value + id
    }

    override suspend fun restore(id: String) {
        deletedIds.value = deletedIds.value - id
    }

    override suspend fun getConflict(id: String): ConflictDetails? = conflict?.takeIf { it.id == id }

    override suspend fun resolveConflict(id: String, resolved: SyncableFields) {
        resolutions += id to resolved
    }
}

fun observation(
    id: String = "a",
    title: String = "Title",
    notes: String = "",
    category: Category = Category.OTHER,
    syncState: SyncState = SyncState.SYNCED,
    observedAt: Long = 100L,
) = Observation(
    id = id,
    title = title,
    notes = notes,
    category = category,
    latitude = null,
    longitude = null,
    photoPath = null,
    observedAt = observedAt,
    updatedAt = observedAt,
    syncState = syncState,
    syncError = null,
)
