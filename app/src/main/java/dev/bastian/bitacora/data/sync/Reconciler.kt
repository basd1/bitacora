package dev.bastian.bitacora.data.sync

import dev.bastian.bitacora.data.db.ObservationEntity
import dev.bastian.bitacora.data.toFields
import dev.bastian.bitacora.data.withFields
import dev.bastian.bitacora.domain.MergeResult
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.domain.SyncableFields
import dev.bastian.bitacora.domain.mergeThreeWay
import dev.bastian.bitacora.shared.ObservationDto
import dev.bastian.bitacora.shared.ServerObservation

/** Adopts the server record as is, keeping the local-only photo. */
internal fun takeRemote(local: ObservationEntity, remote: ObservationDto, remoteVersion: Long): ObservationEntity =
    local.withFields(remote.toFields()).copy(
        updatedAt = remote.updatedAt,
        serverVersion = remoteVersion,
        syncState = SyncState.SYNCED,
        baseSnapshot = SnapshotCodec.encodeDto(remote),
        remoteSnapshot = null,
        errorMessage = null,
    )

/** The base of the three-way merge: the last version both sides agreed on. */
internal fun ObservationEntity.baseFields(): SyncableFields =
    baseSnapshot?.let { SnapshotCodec.decodeDto(it).toFields() } ?: SyncableFields.BLANK

/**
 * Brings a server record into a local one that may have unsynced edits.
 * - Untouched local record (SYNCED): the server record replaces it.
 * - Otherwise a three-way merge. A clean merge becomes the new local content on top of the server
 *   version (and is uploaded again unless it already equals the server record); a conflict keeps the
 *   local content and stores the server record for the resolution screen.
 */
internal fun reconcile(
    local: ObservationEntity,
    remote: ObservationDto,
    remoteVersion: Long,
    now: Long,
): ObservationEntity {
    if (local.syncState == SyncState.SYNCED) return takeRemote(local, remote, remoteVersion)
    val remoteFields = remote.toFields()
    return when (val result = mergeThreeWay(local.baseFields(), local.toFields(), remoteFields)) {
        is MergeResult.Conflicted -> local.copy(
            syncState = SyncState.CONFLICT,
            remoteSnapshot = SnapshotCodec.encodeServer(ServerObservation(remote, remoteVersion)),
            errorMessage = null,
        )
        is MergeResult.Clean ->
            if (result.merged == remoteFields) {
                takeRemote(local, remote, remoteVersion)
            } else {
                local.withFields(result.merged).copy(
                    updatedAt = maxOf(now, local.updatedAt + 1, remote.updatedAt + 1),
                    serverVersion = remoteVersion,
                    syncState = SyncState.PENDING,
                    baseSnapshot = SnapshotCodec.encodeDto(remote),
                    remoteSnapshot = null,
                    errorMessage = null,
                )
            }
    }
}

internal fun newEntityFromRemote(remote: ObservationDto, version: Long): ObservationEntity = ObservationEntity(
    id = remote.id,
    title = remote.title,
    notes = remote.notes,
    category = remote.toFields().category,
    latitude = remote.latitude,
    longitude = remote.longitude,
    photoPath = null,
    observedAt = remote.observedAt,
    updatedAt = remote.updatedAt,
    serverVersion = version,
    syncState = SyncState.SYNCED,
    deleted = remote.deleted,
    baseSnapshot = SnapshotCodec.encodeDto(remote),
    remoteSnapshot = null,
    errorMessage = null,
)
