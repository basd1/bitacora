package dev.bastian.bitacora.shared

import kotlinx.serialization.Serializable

/** Syncable fields of an observation, as exchanged between app and server. */
@Serializable
data class ObservationDto(
    val id: String,
    val title: String,
    val notes: String,
    val category: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val observedAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** A server record: the DTO plus the global version assigned on write. */
@Serializable
data class ServerObservation(
    val observation: ObservationDto,
    val version: Long,
)

@Serializable
data class PullResponse(
    val items: List<ServerObservation>,
    val latestVersion: Long,
)

@Serializable
data class ChangeDto(
    val observation: ObservationDto,
    /** Server version the client based this change on; 0 for a brand new record. */
    val baseVersion: Long,
)

@Serializable
data class BatchRequest(val changes: List<ChangeDto>)

@Serializable
enum class ChangeStatus { ACCEPTED, CONFLICT }

@Serializable
data class ChangeResult(
    val id: String,
    val status: ChangeStatus,
    /** New version when ACCEPTED; current server version when CONFLICT. */
    val version: Long,
    /** Current server record when CONFLICT. */
    val current: ObservationDto? = null,
)

@Serializable
data class BatchResponse(val results: List<ChangeResult>)
