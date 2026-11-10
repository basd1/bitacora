package dev.bastian.bitacora.data

import dev.bastian.bitacora.data.db.ObservationEntity
import dev.bastian.bitacora.domain.Observation
import dev.bastian.bitacora.domain.ObservationDraft
import dev.bastian.bitacora.domain.SyncState

fun ObservationEntity.toDomain() = Observation(
    id = id,
    title = title,
    notes = notes,
    category = category,
    latitude = latitude,
    longitude = longitude,
    photoPath = photoPath,
    observedAt = observedAt,
    updatedAt = updatedAt,
    syncState = syncState,
    syncError = errorMessage,
)

fun ObservationDraft.toNewEntity(id: String, now: Long) = ObservationEntity(
    id = id,
    title = title.trim(),
    notes = notes.trim(),
    category = category,
    latitude = latitude,
    longitude = longitude,
    photoPath = photoPath,
    observedAt = observedAt ?: now,
    updatedAt = now,
    serverVersion = 0,
    syncState = SyncState.PENDING,
    deleted = false,
    baseSnapshot = null,
    remoteSnapshot = null,
    errorMessage = null,
)
