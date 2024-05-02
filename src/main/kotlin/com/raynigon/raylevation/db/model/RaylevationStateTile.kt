package com.raynigon.raylevation.db.model

import com.raynigon.raylevation.db.RaylevationDB
import com.raynigon.raylevation.db.tile.IRaylevationTile
import com.raynigon.raylevation.db.tile.RaylevationTile
import io.micrometer.core.instrument.MeterRegistry
import java.nio.file.Path

/**
 * Data transfer object to store the tile information in the raylevation database state
 *
 * @param name    The name of this tile
 * @param bounds  The area covered by this tile
 */
data class RaylevationStateTile(
    val name: String,
    val bounds: RaylevationStateBounds,
) {
    constructor(tile: IRaylevationTile) : this(tile.name, RaylevationStateBounds(tile.bounds))

    /**
     * Converts the tile information into an instance of a [IRaylevationTile].
     *
     * @param tilesFolder The path to the folder where all tiles are stored
     * @param registry    The meter registry used by the [IRaylevationTile] to generate metrics
     * @return A [IRaylevationTile] instance which can be used to look up the elevation for a GeoPoint
     */
    fun toRaylevationTile(
        tilesFolder: Path,
        registry: MeterRegistry,
    ): IRaylevationTile {
        return RaylevationTile(
            name,
            tilesFolder.resolve("$name${RaylevationDB.GEOTIFF_FILE_SUFFIX}"),
            bounds.toTileBounds(),
            registry,
        )
    }
}
