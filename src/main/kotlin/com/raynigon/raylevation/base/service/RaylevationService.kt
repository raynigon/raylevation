package com.raynigon.raylevation.base.service

import com.raynigon.ecs.logging.async.service.AsyncService
import com.raynigon.raylevation.db.IRaylevationDB
import com.raynigon.raylevation.db.config.DatabaseConfig
import com.raynigon.raylevation.db.exception.RaylevationException
import com.raynigon.raylevation.db.exception.TileNotFoundException
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import javax.measure.Quantity
import javax.measure.quantity.Length

/**
 * The internal result for a single lookup.
 * @param point       The point which should be looked up
 * @param elevation   The elevation which was found for the [point] or 0 if an error occurred
 * @param error       Contains the error message if an error occurred, else its null
 */
data class LookupResult(val point: GeoPoint, val elevation: Quantity<Length>, val error: String? = null)

/**
 * The Raylevation Service is a Bean which provides the functionality
 * to query the elevation for a list of [GeoPoint]s.
 * The requests are executed in parallel.
 *
 * It also manages the cache of the [IRaylevationDB].
 */
interface RaylevationService {
    /**
     * Queries the elevation for a given list of [GeoPoint]s.
     * The locations will be queried in parallel.
     *
     * @param locations   the [GeoPoint]s for which the elevation should be queried
     * @return The elevation data for all given [GeoPoint]s.
     */
    fun lookup(locations: List<GeoPoint>): List<LookupResult>

    /**
     * Updates the cache of the underlying [IRaylevationDB]
     */
    fun syncCache()
}

/**
 * Implementation of the [RaylevationService]
 */
@Service
@EnableConfigurationProperties(DatabaseConfig::class)
class RaylevationServiceImpl(
    raylevationDBFactory: RaylevationDBFactory,
    private val asyncService: AsyncService
) : RaylevationService {

    companion object {
        const val PARALLEL_LIMIT = 10_000
        const val CACHE_REFRESH_INTERVAL = 1_000L
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val database: IRaylevationDB = raylevationDBFactory.create()

    override fun lookup(locations: List<GeoPoint>): List<LookupResult> {
        if (locations.size <= PARALLEL_LIMIT) {
            return locations.map(::lookupSingle)
        }
        return locations
            .chunked(PARALLEL_LIMIT)
            .map { asyncService.supplyAsync { lookup(it) } }
            .flatMap(CompletableFuture<List<LookupResult>>::get)
    }

    @Scheduled(fixedRate = CACHE_REFRESH_INTERVAL)
    override fun syncCache() {
        database.syncCache()
    }

    private fun lookupSingle(geoPoint: GeoPoint): LookupResult {
        return try {
            LookupResult(geoPoint, database.lookupElevation(geoPoint))
        } catch (exception: TileNotFoundException) {
            logger.debug("Tile was not found", exception)
            return LookupResult(geoPoint, Metre(0), "Missing elevation data for (${exception.point})")
        } catch (exception: RaylevationException) {
            logger.warn("Unexpected exception", exception)
            return LookupResult(geoPoint, Metre(0), exception.message)
        }
    }
}
