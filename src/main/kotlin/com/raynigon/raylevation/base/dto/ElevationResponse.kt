package com.raynigon.raylevation.base.dto

import com.raynigon.raylevation.infrastructure.model.GeoPoint
import javax.measure.Quantity
import javax.measure.quantity.Length

/**
 * The Response for an Elevation Request.
 * For each requested [GeoPoint] an entry in the [results] property exists.
 *
 * @param errors  If errors are present in the results this is true else false
 * @param results The result for each requested [GeoPoint]
 */
data class ElevationResponse(
    val errors: Boolean,
    val results: List<ElevationResult>
) {
    constructor(results: List<ElevationResult>) : this(results.any { it.error != null }, results)
}

/**
 * The Response for a requested GeoPoint.
 *
 * @param latitude    The latitude of the requested [GeoPoint]
 * @param longitude   The longitude of the requested [GeoPoint]
 * @param elevation   The elevation for the requested [GeoPoint] or 0 if an error occurred
 * @param error       The error message or null if no error occurred
 */
data class ElevationResult(
    val latitude: Double,
    val longitude: Double,
    val elevation: Quantity<Length>,
    val error: String?
) {
    constructor(point: GeoPoint, elevation: Quantity<Length>, error: String? = null) : this(
        point.latitude,
        point.longitude,
        elevation,
        error
    )
}
