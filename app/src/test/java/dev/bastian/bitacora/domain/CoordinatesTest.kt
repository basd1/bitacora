package dev.bastian.bitacora.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinatesTest {

    @Test
    fun bothEmptyMeansNoLocation() {
        val result = parseCoordinates("", "  ")
        assertNull(result.latitude)
        assertNull(result.longitude)
        assertNull(result.error)
    }

    @Test
    fun validPairIsParsed() {
        val result = parseCoordinates("40.4168", "-3.7038")
        assertEquals(40.4168, result.latitude!!, 0.0)
        assertEquals(-3.7038, result.longitude!!, 0.0)
        assertNull(result.error)
    }

    @Test
    fun commaIsAcceptedAsDecimalSeparator() {
        val result = parseCoordinates("40,5", "-3,25")
        assertEquals(40.5, result.latitude!!, 0.0)
        assertEquals(-3.25, result.longitude!!, 0.0)
    }

    @Test
    fun oneEmptyFieldIsIncomplete() {
        assertEquals(CoordinateError.INCOMPLETE, parseCoordinates("40.0", "").error)
        assertEquals(CoordinateError.INCOMPLETE, parseCoordinates("", "10.0").error)
    }

    @Test
    fun nonNumericTextIsRejected() {
        assertEquals(CoordinateError.NOT_A_NUMBER, parseCoordinates("north", "10").error)
        assertEquals(CoordinateError.NOT_A_NUMBER, parseCoordinates("NaN", "10").error)
    }

    @Test
    fun outOfRangeValuesAreRejected() {
        assertEquals(CoordinateError.OUT_OF_RANGE, parseCoordinates("90.5", "0").error)
        assertEquals(CoordinateError.OUT_OF_RANGE, parseCoordinates("0", "-180.1").error)
    }

    @Test
    fun boundariesAreAccepted() {
        assertNull(parseCoordinates("-90", "180").error)
        assertNull(parseCoordinates("90", "-180").error)
    }

    @Test
    fun formattingUsesSixDecimalsWithADot() {
        assertEquals("40.416800", formatCoordinate(40.4168))
        assertEquals("-3.703800", formatCoordinate(-3.7038))
    }
}
