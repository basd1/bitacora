package dev.bastian.bitacora.data.sync

import dev.bastian.bitacora.shared.ObservationDto
import kotlinx.serialization.json.Json

/** JSON form of the synced snapshots stored in the database. */
internal object SnapshotCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeDto(dto: ObservationDto): String = json.encodeToString(ObservationDto.serializer(), dto)

    fun decodeDto(text: String): ObservationDto = json.decodeFromString(ObservationDto.serializer(), text)
}
