package com.raynigon.raylevation.db.model

import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import java.lang.Double.max
import java.lang.Double.min

data class RaylevationStateBounds(
    val lon0: Double,
    val lat0: Double,
    val lon1: Double,
    val lat1: Double,
) {

    constructor(bounds: TileBounds) : this(bounds.xMin, bounds.yMax, bounds.xMax, bounds.yMin)

    fun toTileBounds(): TileBounds {
        return TileBounds(
            GeoPoint(lat0, max(lon0, -180.0)),
            GeoPoint(lat1, min(lon1, 180.0)),
        )
    }
}
