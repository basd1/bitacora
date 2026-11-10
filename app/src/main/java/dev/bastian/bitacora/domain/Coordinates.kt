package dev.bastian.bitacora.domain

import java.util.Locale

enum class CoordinateError { NOT_A_NUMBER, OUT_OF_RANGE, INCOMPLETE }

/** Result of parsing the two coordinate text fields. Both values are null when empty or invalid. */
data class CoordinatesResult(
    val latitude: Double?,
    val longitude: Double?,
    val error: CoordinateError?,
)

/** Accepts both `.` and `,` as the decimal separator. Both fields empty means "no location". */
fun parseCoordinates(latitude: String, longitude: String): CoordinatesResult {
    val latText = latitude.trim()
    val lonText = longitude.trim()
    if (latText.isEmpty() && lonText.isEmpty()) return CoordinatesResult(null, null, null)
    if (latText.isEmpty() || lonText.isEmpty()) {
        return CoordinatesResult(null, null, CoordinateError.INCOMPLETE)
    }
    val lat = latText.toDecimalOrNull()
    val lon = lonText.toDecimalOrNull()
    if (lat == null || lon == null) return CoordinatesResult(null, null, CoordinateError.NOT_A_NUMBER)
    if (lat !in -MAX_LATITUDE..MAX_LATITUDE || lon !in -MAX_LONGITUDE..MAX_LONGITUDE) {
        return CoordinatesResult(null, null, CoordinateError.OUT_OF_RANGE)
    }
    return CoordinatesResult(lat, lon, null)
}

fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

private fun String.toDecimalOrNull(): Double? =
    replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
