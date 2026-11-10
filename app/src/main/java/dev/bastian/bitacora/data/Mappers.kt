package dev.bastian.bitacora.data

import dev.bastian.bitacora.data.db.ObservationEntity
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.Observation
import dev.bastian.bitacora.domain.ObservationDraft
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.domain.SyncableFields
import dev.bastian.bitacora.shared.ObservationDto

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

fun ObservationEntity.toDto() = ObservationDto(
    id = id,
    title = title,
    notes = notes,
    category = category.name,
    latitude = latitude,
    longitude = longitude,
    observedAt = observedAt,
    updatedAt = updatedAt,
    deleted = deleted,
)

fun ObservationEntity.toFields() = SyncableFields(
    title = title,
    notes = notes,
    category = category,
    latitude = latitude,
    longitude = longitude,
    observedAt = observedAt,
    deleted = deleted,
)

fun ObservationEntity.withFields(fields: SyncableFields) = copy(
    title = fields.title,
    notes = fields.notes,
    category = fields.category,
    latitude = fields.latitude,
    longitude = fields.longitude,
    observedAt = fields.observedAt,
    deleted = fields.deleted,
)

fun ObservationDto.toFields() = SyncableFields(
    title = title,
    notes = notes,
    category = Category.entries.firstOrNull { it.name == category } ?: Category.OTHER,
    latitude = latitude,
    longitude = longitude,
    observedAt = observedAt,
    deleted = deleted,
)
