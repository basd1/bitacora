package dev.bastian.bitacora.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ObservationEntity::class], version = 1, exportSchema = true)
abstract class BitacoraDatabase : RoomDatabase() {
    abstract fun observationDao(): ObservationDao
}
