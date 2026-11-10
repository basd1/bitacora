package dev.bastian.bitacora.server

import dev.bastian.bitacora.shared.ChangeDto
import dev.bastian.bitacora.shared.ChangeResult
import dev.bastian.bitacora.shared.ChangeStatus
import dev.bastian.bitacora.shared.PullResponse
import dev.bastian.bitacora.shared.ServerObservation

/**
 * In-memory store with a global monotonically increasing version.
 * Every accepted write receives the next version.
 */
class ObservationStore {
    private val records = LinkedHashMap<String, ServerObservation>()
    private var version = 0L

    @Synchronized
    fun pull(since: Long): PullResponse = PullResponse(
        items = records.values.filter { it.version > since }.sortedBy { it.version },
        latestVersion = version,
    )

    @Synchronized
    fun apply(change: ChangeDto): ChangeResult {
        val incoming = change.observation
        val existing = records[incoming.id]
        if (existing == null || existing.version <= change.baseVersion) {
            version += 1
            records[incoming.id] = ServerObservation(incoming, version)
            return ChangeResult(incoming.id, ChangeStatus.ACCEPTED, version)
        }
        // Replay of an already applied change: same content, answer with the stored version.
        if (existing.observation == incoming) {
            return ChangeResult(incoming.id, ChangeStatus.ACCEPTED, existing.version)
        }
        return ChangeResult(incoming.id, ChangeStatus.CONFLICT, existing.version, existing.observation)
    }
}
