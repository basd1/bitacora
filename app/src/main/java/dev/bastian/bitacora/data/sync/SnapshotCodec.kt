package dev.bastian.bitacora.data.sync

import dev.bastian.bitacora.shared.ObservationDto
import dev.bastian.bitacora.shared.ServerObservation
import kotlinx.serialization.json.Json

/** JSON form of the synced snapshots stored in the database. */
internal object SnapshotCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeDto(dto: ObservationDto): String = json.encodeToString(ObservationDto.serializer(), dto)

    fun decodeDto(text: String): ObservationDto = json.decodeFromString(ObservationDto.serializer(), text)

    fun encodeServer(record: ServerObservation): String = json.encodeToString(ServerObservation.serializer(), record)

    fun decodeServer(text: String): ServerObservation = json.decodeFromString(ServerObservation.serializer(), text)
}
