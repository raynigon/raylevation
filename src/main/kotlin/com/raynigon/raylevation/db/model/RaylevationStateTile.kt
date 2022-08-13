package com.raynigon.raylevation.db.model

import com.raynigon.raylevation.db.RaylevationDB
import com.raynigon.raylevation.db.tile.IRaylevationTile
import com.raynigon.raylevation.db.tile.RaylevationTile
import io.micrometer.core.instrument.MeterRegistry
import java.nio.file.Path

data class RaylevationStateTile(
    val name: String,
    val bounds: RaylevationStateBounds
) {

    constructor(tile: IRaylevationTile) : this(tile.name, RaylevationStateBounds(tile.bounds))

    fun toRaylevationTile(tilesFolder: Path, registry: MeterRegistry): IRaylevationTile {
        return RaylevationTile(
            name,
            tilesFolder.resolve("$name${RaylevationDB.GEOTIFF_FILE_SUFFIX}"),
            bounds.toTileBounds(),
            registry
        )
    }
}
