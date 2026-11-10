package dev.bastian.bitacora.domain

import kotlinx.coroutines.flow.Flow

interface SyncScheduler {
    /** Queues a sync that runs when the network is available. A pending request is kept, not duplicated. */
    fun requestSync()

    /** Replaces any queued or backed-off sync with one that starts as soon as there is a connection. */
    fun retryNow()

    /** True while a sync is running. */
    val isSyncing: Flow<Boolean>
}
