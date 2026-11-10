package dev.bastian.bitacora.data.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import dev.bastian.bitacora.domain.LocationProvider
import dev.bastian.bitacora.domain.LocationResult
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Uses the framework [LocationManager] only; no Play Services. */
class FrameworkLocationProvider(private val context: Context) : LocationProvider {

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationResult {
        val fine = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return LocationResult.PermissionDenied
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return LocationResult.Unavailable
        val candidates = if (fine) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER)
        }
        val provider = candidates.firstOrNull { manager.isProviderEnabled(it) }
            ?: return LocationResult.ProviderDisabled
        return try {
            val location = withTimeoutOrNull(FIX_TIMEOUT_MS) { requestFix(manager, provider) }
                ?: manager.getLastKnownLocation(provider)
            if (location == null) {
                LocationResult.Unavailable
            } else {
                LocationResult.Found(location.latitude, location.longitude)
            }
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the request.
            LocationResult.PermissionDenied
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun requestFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                signal,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }

    private companion object {
        const val FIX_TIMEOUT_MS = 15_000L
    }
}
