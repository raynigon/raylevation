package com.raynigon.raylevation.db.tile

import com.raynigon.raylevation.db.exception.LookupOutOfBoundsException
import com.raynigon.raylevation.db.gdal.GDALTile
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import javax.measure.Quantity
import javax.measure.quantity.Length

/**
 * The representation of one tile in the raylevation database
 */
interface IRaylevationTile {

    /**
     * The unique name of this tile
     */
    val name: String

    /**
     * The path where the GeoTiff file is stored
     */
    val path: Path

    /**
     * The bounds of the area for which this tile provides elevation information
     */
    val bounds: TileBounds

    /**
     * Cache state of this tile.
     * If set to `true` the tile data gets cached,
     * If set to `false` the tile data gets cleared
     */
    var cache: Boolean

    /**
     *  Counter for the number of lookups of this tile, which increases monotonically.
     */
    val lookups: Long

    /**
     * Lookup Elevation for a given [GeoPoint], which has to be in this tile.
     *
     * @return the elevation value for the GeoPoint
     * @throws com.raynigon.raylevation.db.exception.RaylevationException if the GeoPoint is not in the bounds of this tile
     */
    fun lookupElevation(point: GeoPoint): Quantity<Length>
}

/**
 * Implementation of [IRaylevationTile]
 */
class RaylevationTile(
    override val name: String,
    override val path: Path,
    override val bounds: TileBounds,
    registry: MeterRegistry
) : IRaylevationTile {

    private var tileCache: GDALTile? = null
    private val lookupCounter = LongAdder()
    private val metricCounter = registry.counter(
        "app.raylevation.db.tile.lookups",
        "geoHash",
        bounds.center.geoHash,
        "latitude",
        bounds.center.latitude.toString(),
        "longitude",
        bounds.center.longitude.toString()
    )
    private val metricCached = registry.gauge(
        "app.raylevation.db.tile.cached",
        setOf(
            Tag.of("geoHash", bounds.center.geoHash),
            Tag.of("latitude", bounds.center.latitude.toString()),
            Tag.of("longitude", bounds.center.longitude.toString())
        ),
        AtomicInteger(0)
    ) ?: error("Unable to initialize cached metric")

    override val lookups: Long get() = lookupCounter.sum()

    override var cache: Boolean
        get() = tileCache != null

        @Synchronized
        set(value) {
            if (value && tileCache == null) {
                tileCache = GDALTile(path)
                metricCached.set(1)
            } else if (!value && tileCache != null) {
                tileCache = null
                metricCached.set(0)
            }
            // In all other cases the tileCache already has the expected state
        }

    override fun lookupElevation(point: GeoPoint): Quantity<Length> {
        if (!bounds.contains(point)) {
            throw LookupOutOfBoundsException(point, bounds)
        }
        val result = (tileCache ?: GDALTile(path))
            .lookupElevation(point)
        incrementCounter()
        return result
    }

    private fun incrementCounter() {
        lookupCounter.increment()
        metricCounter.increment()
    }
}
