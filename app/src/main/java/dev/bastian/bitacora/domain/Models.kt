package dev.bastian.bitacora.domain

/** Closed set of observation categories. */
enum class Category { FAUNA, FLORA, TERRAIN, WEATHER, OTHER }

enum class SyncState { SYNCED, PENDING, CONFLICT, ERROR }

data class Observation(
    val id: String,
    val title: String,
    val notes: String,
    val category: Category,
    val latitude: Double?,
    val longitude: Double?,
    val photoPath: String?,
    val observedAt: Long,
    val updatedAt: Long,
    val syncState: SyncState,
    val syncError: String?,
)

/** What the edit form hands to the repository. A null [id] creates a new observation. */
data class ObservationDraft(
    val id: String?,
    val title: String,
    val notes: String,
    val category: Category,
    val latitude: Double?,
    val longitude: Double?,
    val photoPath: String?,
    val observedAt: Long?,
)

data class ListFilter(
    val query: String = "",
    val category: Category? = null,
)
