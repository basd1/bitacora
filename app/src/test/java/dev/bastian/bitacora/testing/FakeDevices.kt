package dev.bastian.bitacora.testing

import dev.bastian.bitacora.domain.LocationProvider
import dev.bastian.bitacora.domain.LocationResult
import dev.bastian.bitacora.domain.PhotoStorage
import java.io.IOException

class FakePhotoStorage : PhotoStorage {
    var failImports = false
    val existing = mutableSetOf<String>()
    private var counter = 0

    override suspend fun import(uri: String): Result<String> {
        if (failImports) return Result.failure(IOException("import failed"))
        val path = "/photos/${++counter}.jpg"
        existing += path
        return Result.success(path)
    }

    override fun delete(path: String) {
        existing -= path
    }
}

class FakeLocationProvider(var result: LocationResult = LocationResult.Unavailable) : LocationProvider {
    var calls = 0
        private set

    override suspend fun currentLocation(): LocationResult {
        calls++
        return result
    }
}
