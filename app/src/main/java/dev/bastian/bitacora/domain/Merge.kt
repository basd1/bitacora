package dev.bastian.bitacora.domain

/** The fields of an observation that travel to the server and take part in the merge. */
data class SyncableFields(
    val title: String,
    val notes: String,
    val category: Category,
    val latitude: Double?,
    val longitude: Double?,
    val observedAt: Long,
    val deleted: Boolean,
) {
    fun withFieldFrom(field: Field, source: SyncableFields): SyncableFields = when (field) {
        Field.TITLE -> copy(title = source.title)
        Field.NOTES -> copy(notes = source.notes)
        Field.CATEGORY -> copy(category = source.category)
        Field.LATITUDE -> copy(latitude = source.latitude)
        Field.LONGITUDE -> copy(longitude = source.longitude)
        Field.OBSERVED_AT -> copy(observedAt = source.observedAt)
    }

    fun hasContentChangesFrom(other: SyncableFields): Boolean = Field.entries.any { field ->
        withFieldFrom(field, other) != this
    }

    companion object {
        /** Stand-in base for a record that was never synced: any field edited on both sides conflicts. */
        val BLANK = SyncableFields(
            title = "",
            notes = "",
            category = Category.OTHER,
            latitude = null,
            longitude = null,
            observedAt = 0L,
            deleted = false,
        )
    }
}

/** Fields that can be in conflict. `deleted` never is: see [mergeThreeWay]. */
enum class Field { TITLE, NOTES, CATEGORY, LATITUDE, LONGITUDE, OBSERVED_AT }

enum class Side { LOCAL, REMOTE }

sealed interface MergeResult {
    data class Clean(val merged: SyncableFields) : MergeResult

    /**
     * [partial] holds the merged non-conflicting fields; each field in [conflicts]
     * carries the local value until the user chooses a side.
     */
    data class Conflicted(val partial: SyncableFields, val conflicts: Set<Field>) : MergeResult {
        fun resolve(remote: SyncableFields, choices: Map<Field, Side>): SyncableFields =
            conflicts.fold(partial) { resolved, field ->
                if (choices[field] == Side.REMOTE) resolved.withFieldFrom(field, remote) else resolved
            }
    }
}

/** What the conflict screen needs about one record in conflict. */
data class ConflictDetails(
    val id: String,
    val base: SyncableFields,
    val local: SyncableFields,
    val remote: SyncableFields,
    val remoteVersion: Long,
)

/**
 * Field-by-field three-way merge of [local] and [remote] against their common ancestor [base]:
 * - equal on both sides: that value;
 * - changed on one side only: the changed side wins;
 * - changed on both sides to different values: a conflict.
 *
 * Deletion against edit is not a conflict: the edit wins and the record is restored.
 */
fun mergeThreeWay(base: SyncableFields, local: SyncableFields, remote: SyncableFields): MergeResult {
    val conflicts = linkedSetOf<Field>()

    fun <T> pick(field: Field, baseValue: T, localValue: T, remoteValue: T): T = when {
        localValue == remoteValue -> localValue
        localValue == baseValue -> remoteValue
        remoteValue == baseValue -> localValue
        else -> {
            conflicts.add(field)
            localValue
        }
    }

    val merged = SyncableFields(
        title = pick(Field.TITLE, base.title, local.title, remote.title),
        notes = pick(Field.NOTES, base.notes, local.notes, remote.notes),
        category = pick(Field.CATEGORY, base.category, local.category, remote.category),
        latitude = pick(Field.LATITUDE, base.latitude, local.latitude, remote.latitude),
        longitude = pick(Field.LONGITUDE, base.longitude, local.longitude, remote.longitude),
        observedAt = pick(Field.OBSERVED_AT, base.observedAt, local.observedAt, remote.observedAt),
        deleted = mergeDeleted(base, local, remote),
    )
    return if (conflicts.isEmpty()) MergeResult.Clean(merged) else MergeResult.Conflicted(merged, conflicts)
}

private fun mergeDeleted(base: SyncableFields, local: SyncableFields, remote: SyncableFields): Boolean {
    if (local.deleted == remote.deleted) return local.deleted
    // Exactly one side changed the flag; that side's value is the standard outcome.
    val changed = if (local.deleted == base.deleted) remote.deleted else local.deleted
    if (!changed) return false
    // A deletion against an edit: if the side that kept the record also edited it, the edit wins.
    val survivor = if (local.deleted) remote else local
    return !survivor.hasContentChangesFrom(base)
}
