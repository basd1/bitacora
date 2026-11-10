package dev.bastian.bitacora.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.bastian.bitacora.domain.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ObservationDao {

    @Query(
        "SELECT * FROM observations WHERE deleted = 0 " +
            "AND (:category IS NULL OR category = :category) " +
            "AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%') " +
            "ORDER BY observedAt DESC, id",
    )
    abstract fun observeFiltered(category: String?, query: String): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE id = :id")
    abstract fun observeById(id: String): Flow<ObservationEntity?>

    @Query("SELECT * FROM observations WHERE id = :id")
    abstract suspend fun getById(id: String): ObservationEntity?

    @Query("SELECT * FROM observations WHERE syncState = :state ORDER BY updatedAt, id")
    abstract suspend fun getByState(state: SyncState): List<ObservationEntity>

    @Upsert
    abstract suspend fun upsert(entity: ObservationEntity)

    /**
     * Records that the server accepted the pushed content. The record becomes SYNCED only if it was
     * not edited again while the request was in flight; otherwise it stays PENDING on top of the new base.
     */
    @Query(
        "UPDATE observations SET serverVersion = :version, baseSnapshot = :snapshot, errorMessage = NULL, " +
            "syncState = CASE WHEN updatedAt = :pushedUpdatedAt THEN 'SYNCED' ELSE syncState END " +
            "WHERE id = :id",
    )
    abstract suspend fun markPushed(id: String, version: Long, snapshot: String, pushedUpdatedAt: Long)

    @Query("UPDATE observations SET syncState = 'ERROR', errorMessage = :message WHERE id IN (:ids)")
    abstract suspend fun markError(ids: List<String>, message: String)

    @Query("SELECT COUNT(*) FROM observations WHERE syncState = :state")
    abstract fun observeCount(state: SyncState): Flow<Int>

    @Query("SELECT * FROM observations WHERE syncState = 'ERROR' ORDER BY updatedAt DESC, id")
    abstract fun observeErrors(): Flow<List<ObservationEntity>>

    /** Manual retry: rejected records go back in the upload queue. */
    @Query("UPDATE observations SET syncState = 'PENDING', errorMessage = NULL WHERE syncState = 'ERROR'")
    abstract suspend fun resetErrors()

    /**
     * Reads, transforms and writes one record atomically, so a concurrent edit
     * and a sync step cannot overwrite each other. Returning null from
     * [transform] leaves the row untouched.
     */
    @Transaction
    open suspend fun update(id: String, transform: (ObservationEntity?) -> ObservationEntity?) {
        val current = getById(id)
        val next = transform(current)
        if (next != null && next != current) upsert(next)
    }
}
