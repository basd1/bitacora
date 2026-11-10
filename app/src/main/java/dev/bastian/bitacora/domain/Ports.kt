package dev.bastian.bitacora.domain

import kotlinx.coroutines.flow.Flow

/** Source of time. Logic never reads the system clock directly. */
fun interface Clock {
    fun now(): Long
}

fun interface IdGenerator {
    fun newId(): String
}

interface ObservationRepository {
    fun observeAll(filter: ListFilter): Flow<List<Observation>>
    fun observe(id: String): Flow<Observation?>
    suspend fun get(id: String): Observation?

    /** Creates or updates an observation and returns its id. */
    suspend fun save(draft: ObservationDraft): String

    /** Soft delete: the record stays so the deletion can be synced and undone. */
    suspend fun delete(id: String)
    suspend fun restore(id: String)
}
