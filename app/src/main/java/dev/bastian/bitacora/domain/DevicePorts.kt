package dev.bastian.bitacora.domain

interface PhotoStorage {
    /** Copies the picked image into internal storage and returns the new file path. */
    suspend fun import(uri: String): Result<String>

    /** Deletes a file previously returned by [import]. Unknown paths are ignored. */
    fun delete(path: String)
}

sealed interface LocationResult {
    data class Found(val latitude: Double, val longitude: Double) : LocationResult
    data object PermissionDenied : LocationResult
    data object ProviderDisabled : LocationResult
    data object Unavailable : LocationResult
}

interface LocationProvider {
    /** One location fix from the framework `LocationManager`. The caller asks for permission first. */
    suspend fun currentLocation(): LocationResult
}
