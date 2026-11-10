package dev.bastian.bitacora.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.SyncState

@Entity(
    tableName = "observations",
    indices = [Index("syncState"), Index("observedAt")],
)
data class ObservationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val category: Category,
    val latitude: Double?,
    val longitude: Double?,
    /** Path in internal storage. Photos are never synced. */
    val photoPath: String?,
    val observedAt: Long,
    val updatedAt: Long,
    /** 0 when the record was never uploaded. */
    val serverVersion: Long,
    val syncState: SyncState,
    /** Soft delete, so the deletion can be synced. */
    val deleted: Boolean,
    /** JSON of the syncable fields as of the last synced version; the base of the three-way merge. */
    val baseSnapshot: String?,
    /** JSON of the server record that conflicts with the local edit; set only while in CONFLICT. */
    val remoteSnapshot: String?,
    /** Cause of the last failure; set only while in ERROR. */
    val errorMessage: String?,
)
