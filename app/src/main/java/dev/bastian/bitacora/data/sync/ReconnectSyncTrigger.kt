package dev.bastian.bitacora.data.sync

import dev.bastian.bitacora.domain.NetworkMonitor
import dev.bastian.bitacora.domain.SyncScheduler
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/** Requests a sync every time the device goes from offline to online. */
class ReconnectSyncTrigger(
    private val network: NetworkMonitor,
    private val scheduler: SyncScheduler,
) {
    /** Collects until cancelled. The first emission is the current state, not a reconnection. */
    suspend fun run() {
        network.isOnline
            .distinctUntilChanged()
            .drop(1)
            .filter { online -> online }
            .collect { scheduler.requestSync() }
    }
}
