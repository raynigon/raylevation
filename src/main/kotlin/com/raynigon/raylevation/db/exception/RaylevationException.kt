package com.raynigon.raylevation.db.exception

import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import org.gdal.gdal.gdal
import java.nio.file.Path

/**
 * If an error occurred during a Raylevation Lookup,
 * this exception is thrown.
 */
sealed class RaylevationException(
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * If not tile was found in which the GeoPoint is present,
 * this exception is thrown.
 */
data class TileNotFoundException(val point: GeoPoint) :
    RaylevationException("No Tile was found for $point")

/**
 * If a tile was used to look up a GeoPoint which is outside the tile bounds,
 * this exception is thrown.
 */
data class LookupOutOfBoundsException(val point: GeoPoint, val bounds: TileBounds) :
    RaylevationException("$point is not in bounds $bounds")

/**
 * If a GeoTiff file was loaded which has other bands/types than the defines ones,
 * this exception is thrown.
 */
class IncompatibleTileException(message: String) : RaylevationException(message) {

    constructor(path: Path, reason: String, given: Int, expected: Int) : this(
        "Tile ($path) $reason.\nGiven: $given\tExpected: $expected"
    )
}

/**
 * If a GDAL error occurs, this exception is thrown
 */
data class GDALException(
    val errorNumber: Int,
    val errorMessage: String
) : RaylevationException("GDAL error occured with ErrorNumber=$errorNumber and Message=$errorMessage") {

    constructor() : this(gdal.GetLastErrorNo(), gdal.GetLastErrorMsg())
}
