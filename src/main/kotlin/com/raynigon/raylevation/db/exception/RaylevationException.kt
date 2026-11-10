package com.raynigon.raylevation.db.exception

import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import java.nio.file.Path

/**
 * If an error occurred during a Raylevation Lookup,
 * this exception is thrown.
 */
sealed class RaylevationException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * If not tile was found in which the GeoPoint is present,
 * this exception is thrown.
 */
data class TileNotFoundException(
    val point: GeoPoint,
) : RaylevationException("No Tile was found for $point")

/**
 * If a tile was used to look up a GeoPoint which is outside the tile bounds,
 * this exception is thrown.
 */
data class LookupOutOfBoundsException(
    val point: GeoPoint,
    val bounds: TileBounds,
) : RaylevationException("$point is not in bounds $bounds")

/**
 * If a GeoTiff file was loaded which has other bands/types than the defines ones,
 * this exception is thrown.
 */
class IncompatibleTileException(
    message: String,
) : RaylevationException(message) {
    constructor(path: Path, reason: String, given: Int, expected: Int) : this(
        "Tile ($path) $reason.\nGiven: $given\tExpected: $expected",
    )
}

/**
 * If a GeoTiff file can not be read or written, this exception is thrown
 */
class GeoTiffException(
    val path: Path,
    message: String,
    cause: Throwable? = null,
) : RaylevationException("Unable to read GeoTiff ($path): $message", cause) {
    constructor(path: Path, cause: Throwable) : this(path, cause.message ?: cause.javaClass.simpleName, cause)
}

/**
 * If a sub tile was requested for an area which is not covered by the tile, this exception is thrown
 */
class InvalidSubTileException(
    val path: Path,
    val bounds: TileBounds,
    val requested: TileBounds,
) : RaylevationException("Tile ($path) with bounds $bounds does not cover the requested area $requested")

/**
 * If a modification to the database is attempted while it is not locked, this exception is thrown
 */
data class DatabaseNotLockedException(
    val operation: String,
) : RaylevationException("Database modification via '$operation' is not allowed without a lock")
