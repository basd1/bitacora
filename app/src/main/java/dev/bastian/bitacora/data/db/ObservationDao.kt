package dev.bastian.bitacora.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

    @Upsert
    abstract suspend fun upsert(entity: ObservationEntity)

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
