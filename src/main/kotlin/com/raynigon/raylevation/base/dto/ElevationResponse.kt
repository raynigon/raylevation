package com.raynigon.raylevation.base.dto

import com.raynigon.raylevation.infrastructure.model.GeoPoint
import javax.measure.Quantity
import javax.measure.quantity.Length

data class ElevationResponse(
    val errors: Boolean,
    val results: List<ElevationResult>
) {
    constructor(results: List<ElevationResult>) : this(results.any { it.error != null }, results)
}

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
