package com.raynigon.raylevation.base.dto

import com.raynigon.raylevation.infrastructure.model.GeoPoint

/**
 * Data transfer object for elevation requests.
 * The JSON document is deserialized into the object structure.
 */
data class ElevationRequest(
    val locations: List<GeoPoint>
)
