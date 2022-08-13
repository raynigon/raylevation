package com.raynigon.raylevation.db.tile

import com.raynigon.raylevation.infrastructure.model.GeoPoint
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Define the area which is covered by a tile.
 *
 * @param yMax    Latitude of the upper left corner
 * @param xMin    Longitude of the upper left corner
 * @param yMin    Latitude of the lower right corner
 * @param xMax    Longitude of the lower right corner
 */
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

    /**
     * Checks if a given [GeoPoint] is in the area of this bounds
     *
     * @param point   The [GeoPoint] which should be checked
     * @return True if the bounds contain the given [GeoPoint], false if not
     */
    fun contains(point: GeoPoint): Boolean {
        return point.longitude in xMin..xMax && point.latitude in yMin..yMax
    }

    /**
     * Checks if another [TileBounds] is fully included the area of this bounds
     *
     * @param other   The [TileBounds] which should be checked
     * @return True if the other bounds are fully included the area of the bounds, false if not
     */
    fun contains(other: TileBounds): Boolean {
        if (other.xMin < xMin) return false
        if (other.xMax > xMax) return false
        if (other.yMin < yMin) return false
        if (other.yMax > yMax) return false
        return true
    }

    /**
     * Round the tile bounds by a given factor,
     * to make it a little smaller and correct rounding error
     *
     * @param factor  The factor on which the [TileBounds] should be rounded to
     * @return [TileBounds] which contain a smaller area, defined by the given factor
     */
    fun roundOff(factor: Double = 1000.0): TileBounds {
        val xMin = ceil(xMin * factor) / factor
        val xMax = floor(xMax * factor) / factor
        val yMin = ceil(yMin * factor) / factor
        val yMax = floor(yMax * factor) / factor
        return TileBounds(yMax, xMin, yMin, xMax)
    }
}
