package com.raynigon.raylevation.db.exception

import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import org.gdal.gdal.gdal
import java.nio.file.Path

sealed class RaylevationException(
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)

data class TileNotFoundException(val point: GeoPoint) :
    RaylevationException("No Tile was found for $point")

data class LookupOutOfBoundsException(val point: GeoPoint, val bounds: TileBounds) :
    RaylevationException("$point is not in bounds $bounds")

class IncompatibleTileException(message: String) : RaylevationException(message) {

    constructor(path: Path, reason: String, given: Int, expected: Int) : this(
        "Tile ($path) $reason.\nGiven: $given\tExpected: $expected"
    )
}

data class GDALException(
    val errorNumber: Int,
    val errorMessage: String
) : RaylevationException("GDAL error occured with ErrorNumber=$errorNumber and Message=$errorMessage") {

    constructor() : this(gdal.GetLastErrorNo(), gdal.GetLastErrorMsg())
}
