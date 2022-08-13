package com.raynigon.raylevation.db.tile

import com.raynigon.raylevation.infrastructure.model.GeoPoint
import kotlin.math.ceil
import kotlin.math.floor

data class TileBounds(
    val yMax: Double,
    val xMin: Double,
    val yMin: Double,
    val xMax: Double
) {
    init {
        require(xMax > xMin) { "X: $xMax is less than or equals $xMin" }
        require(yMax > yMin) { "Y: $yMin is less than or equals $yMin" }
    }

    constructor(upperLeft: GeoPoint, lowerRight: GeoPoint) : this(
        upperLeft.latitude,
        upperLeft.longitude,
        lowerRight.latitude,
        lowerRight.longitude,
    )

    val center: GeoPoint by lazy {
        GeoPoint(
            longitude = xMin + (xMax - xMin) / 2.0,
            latitude = yMin + (yMax - yMin) / 2.0
        )
    }

    fun contains(point: GeoPoint): Boolean {
        return point.longitude in xMin..xMax && point.latitude in yMin..yMax
    }

    fun contains(other: TileBounds): Boolean {
        if (other.xMin < xMin) return false
        if (other.xMax > xMax) return false
        if (other.yMin < yMin) return false
        if (other.yMax > yMax) return false
        return true
    }

    fun roundOff(factor: Double = 1000.0): TileBounds {
        val xMin = ceil(xMin * factor) / factor
        val xMax = floor(xMax * factor) / factor
        val yMin = ceil(yMin * factor) / factor
        val yMax = floor(yMax * factor) / factor
        return TileBounds(yMax, xMin, yMin, xMax)
    }
}
