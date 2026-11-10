package dev.bastian.bitacora.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Sync bookkeeping that is not part of any observation. */
interface SyncStore {
    val lastSyncAt: Flow<Long?>
    val lastError: Flow<String?>

    /** Highest server version applied locally; 0 before the first pull. */
    suspend fun lastPulledVersion(): Long
    suspend fun setLastPulledVersion(version: Long)
    suspend fun recordSuccess(at: Long)
    suspend fun recordError(message: String)
}

class DataStoreSyncStore(private val dataStore: DataStore<Preferences>) : SyncStore {

    override val lastSyncAt: Flow<Long?> = dataStore.data.map { it[LAST_SYNC_AT] }
    override val lastError: Flow<String?> = dataStore.data.map { it[LAST_ERROR] }

    override suspend fun lastPulledVersion(): Long = dataStore.data.first()[LAST_PULLED_VERSION] ?: 0L

    override suspend fun setLastPulledVersion(version: Long) {
        dataStore.edit { it[LAST_PULLED_VERSION] = version }
    }

    override suspend fun recordSuccess(at: Long) {
        dataStore.edit {
            it[LAST_SYNC_AT] = at
            it.remove(LAST_ERROR)
        }
    }

    override suspend fun recordError(message: String) {
        dataStore.edit { it[LAST_ERROR] = message }
    }

    private companion object {
        val LAST_PULLED_VERSION = longPreferencesKey("last_pulled_version")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }
}
