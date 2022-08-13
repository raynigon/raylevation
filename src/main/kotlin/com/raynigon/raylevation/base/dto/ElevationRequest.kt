package com.raynigon.raylevation.base.dto

import com.raynigon.raylevation.infrastructure.model.GeoPoint

data class ElevationRequest(
    val locations: List<GeoPoint>
)
