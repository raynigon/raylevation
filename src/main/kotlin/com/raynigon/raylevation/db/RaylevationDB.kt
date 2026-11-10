package com.raynigon.raylevation.db

import com.github.davidmoten.rtree.RTree
import com.github.davidmoten.rtree.geometry.Rectangle
import com.raynigon.raylevation.db.config.DatabaseConfig
import com.raynigon.raylevation.db.exception.DatabaseNotLockedException
import com.raynigon.raylevation.db.exception.TileNotFoundException
import com.raynigon.raylevation.db.geotiff.GeoTiffTile
import com.raynigon.raylevation.db.lock.IRaylevationDBLock
import com.raynigon.raylevation.db.lock.RaylevationDBLock
import com.raynigon.raylevation.db.lock.isLocked
import com.raynigon.raylevation.db.model.IRaylevationState
import com.raynigon.raylevation.db.model.RaylevationState1d0d0
import com.raynigon.raylevation.db.model.RaylevationStateTile
import com.raynigon.raylevation.db.model.emptyRaylevationState
import com.raynigon.raylevation.db.tile.IRaylevationTile
import com.raynigon.raylevation.db.tile.RaylevationTile
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.infrastructure.kotlin.toPointDouble
import com.raynigon.raylevation.infrastructure.kotlin.toRectangle
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.io.Closeable
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import javax.measure.Quantity
import javax.measure.quantity.Length
import kotlin.math.round

/**
 * The IRaylevationDB defines the interface to interact with the [RaylevationDB].
 */
interface IRaylevationDB : Closeable {
    /**
     * Open the Raylevation Database.
     * This loads the metadata of all existing tiles into memory
     * and fills up the spatial index.
     *
     * @param locked    locks the database for writes
     */
    fun open(locked: Boolean = false)

    override fun close()

    /**
     * Lookup Elevation for a given [GeoPoint]
     *
     * @return the elevation value for the GeoPoint
     * @throws com.raynigon.raylevation.db.exception.RaylevationException if no tile is found
     */
    fun lookupElevation(point: GeoPoint): Quantity<Length>

    /**
     * Update the cache, unloads tiles which should no longer be cached and
     * loads tiles which should be cached.
     * This operation is expensive and should be triggered from a different thread
     * than the thread used for lookups.
     */
    fun syncCache()

    /**
     * Add [GeoTiffTile] to the database
     *
     * @param tile    the tile which should be added
     * @return the name under which the tile was added to the database
     */
    fun addTile(tile: GeoTiffTile): String

    /**
     * Remove the tile with the given name from the database.
     * Does nothing if no tile with the given name exists.
     *
     * @param name    the name of the tile which should be removed
     */
    fun removeTile(name: String)

    /**
     * Find the names of all tiles in the database whose bounds are fully contained in the given bounds.
     *
     * @param bounds    the bounds in which tiles should be looked up
     * @return the names of the matching tiles
     */
    fun findTileNames(bounds: TileBounds): List<String>

    /**
     *  Stores the current state of the database on disk.
     *
     *  @exception Exception   when the database was not locked
     */
    fun save()

    /**
     */
    fun <T> getMetadata(
        name: String,
        type: Class<T>,
    ): T?

    /**
     */
    fun <T> setMetadata(
        name: String,
        value: T,
    )
}

/**
 * Implementation for the [IRaylevationDB]
 */
class RaylevationDB(
    private val config: DatabaseConfig,
    private val registry: MeterRegistry,
    private val objectMapper: ObjectMapper,
) : IRaylevationDB {
    companion object {
        const val STATE_FILE_NAME = "index.json"
        const val LOCK_FILE_NAME = "lock.json"
        const val TILES_FOLDER_NAME = "tiles"
        const val GEOTIFF_FILE_SUFFIX = ".tif"
        val LOCK_TIMEOUT: Duration = Duration.ofMinutes(30)
    }

    // Observability
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val lookupTimer = registry.timer("app.raylevation.db.lookups")
    private val cacheSizeGauge = registry.gauge("app.raylevation.db.cache.size", AtomicInteger())
    private val totalTilesGauge = registry.gauge("app.raylevation.db.tiles.total", AtomicInteger())
    private val cachedTilesGauge = registry.gauge("app.raylevation.db.tiles.cached", AtomicInteger())

    // Functional
    private var index: RTree<IRaylevationTile, Rectangle> = RTree.create()
    private var tiles: Set<IRaylevationTile> = setOf()
    private var lock: IRaylevationDBLock? = null
    private val metadata: MutableMap<String, Any> = mutableMapOf()

    init {
        cacheSizeGauge.set(config.cacheTileCount)
    }

    @Synchronized
    override fun open(locked: Boolean) {
        val indexPath = config.path.resolve(STATE_FILE_NAME)
        val tilesPath = config.path.resolve(TILES_FOLDER_NAME)
        val lockPath = config.path.resolve(LOCK_FILE_NAME)
        // Init database
        initDatabase(indexPath, locked)
        // Lock database if needed
        if (locked) {
            val lock = RaylevationDBLock(lockPath, objectMapper)
            lock.lock(LOCK_TIMEOUT)
            this.lock = lock
        }
        // Read database state
        val state = objectMapper.readValue(indexPath.toFile(), IRaylevationState::class.java)
        // Load Metadata
        metadata.putAll(state.metadata)
        // Load Tiles
        state.tiles
            .map { it.toRaylevationTile(tilesPath, registry) }
            .forEach { addTile(it) }
        logger.info("Opened RaylevationDB '${config.path}' and loaded ${tiles.size} tiles (locked=$locked)")
    }

    @Synchronized
    override fun close() {
        val locked = lock.isLocked()
        if (locked) {
            lock!!.unlock()
        }
        logger.info("Closed RaylevationDB '${config.path}' and loaded ${tiles.size} tiles (locked=$locked)")
    }

    override fun lookupElevation(point: GeoPoint): Quantity<Length> {
        val sample = Timer.start()
        val tile =
            index
                .nearest(point.toPointDouble(), 360.0, 1)
                .toBlocking()
                .singleOrDefault(null)
                ?.value()
                ?: throw TileNotFoundException(point)
        return tile.lookupElevation(point).also {
            // This is thread safe, because of internal synchronization
            sample.stop(lookupTimer)
        }
    }

    @Synchronized
    override fun syncCache() {
        // Find all tiles currently in cache
        val tilesInCache =
            tiles
                .toList() // Create list as local copy
                .filter(IRaylevationTile::cache)
                .toSet()
        // Find all tiles which should be in the cache
        val tilesToCache =
            tiles
                .toList() // Create list as local copy
                .asSequence()
                .filter { it.lookups > 0 }
                .sortedByDescending { it.lookups }
                .take(config.cacheTileCount)
                .toSet()

        // No change in cache, exit early
        if (tilesInCache == tilesToCache) {
            return
        }

        // Update cache for tiles
        for (tile in tiles) {
            tile.cache = (tile in tilesToCache)
        }

        // Observability
        cachedTilesGauge.set(tilesToCache.size)
        val totalTiles = tiles.size
        val cachedTiles = tilesToCache.size
        val cachedPercent = if (totalTiles > 0) round(cachedTiles / totalTiles * 100.0) else 0
        logger.debug("Cache Status: $cachedTiles/$totalTiles Tiles   $cachedPercent%")
    }

    override fun addTile(tile: GeoTiffTile): String {
        if (!lock.isLocked()) {
            throw DatabaseNotLockedException("addTile")
        }
        lock!!.update()
        val tilesFolder = config.path.resolve(TILES_FOLDER_NAME)
        if (!Files.exists(tilesFolder)) {
            Files.createDirectories(tilesFolder)
        }
        val bounds = tile.bounds.roundOff()
        val name =
            String.format(
                "%+2.2f_%+2.2f_%+2.2f_%+2.2f",
                bounds.yMax,
                bounds.xMin,
                bounds.yMin,
                bounds.xMax,
            )
        // Copy GeoTiff Tile to the database directory
        val tilePath = tilesFolder.resolve("$name$GEOTIFF_FILE_SUFFIX")
        tile.saveTo(tilePath)
        RaylevationTile(name, tilePath, bounds, registry).let(::addTile)
        logger.info("Added Tile $name to database")
        return name
    }

    @Synchronized
    override fun removeTile(name: String) {
        if (!lock.isLocked()) {
            throw DatabaseNotLockedException("removeTile")
        }
        lock!!.update()
        val tile = tiles.firstOrNull { it.name == name }
        if (tile == null) {
            logger.warn("Tile $name does not exist in database, skipping removal")
            return
        }
        tiles = tiles.filterNot { it.name == name }.toSet()
        index = index.delete(tile, tile.toRectangle())
        totalTilesGauge.set(tiles.size)
        Files.deleteIfExists(tile.path)
        logger.info("Removed Tile $name from database")
    }

    override fun findTileNames(bounds: TileBounds): List<String> = tiles.filter { bounds.contains(it.bounds) }.map { it.name }

    override fun <T> getMetadata(
        name: String,
        type: Class<T>,
    ): T? {
        val value = metadata[name] ?: return null
        return objectMapper.convertValue(value, type)
    }

    @Synchronized
    override fun <T> setMetadata(
        name: String,
        value: T,
    ) {
        metadata[name] = objectMapper.convertValue(value, Map::class.java)
    }

    @Synchronized
    override fun save() {
        if (!lock.isLocked()) {
            throw DatabaseNotLockedException("save")
        }
        lock!!.update()
        val indexPath = config.path.resolve(STATE_FILE_NAME)
        val state =
            RaylevationState1d0d0(
                metadata = metadata,
                tiles = tiles.map { RaylevationStateTile(it) },
            )
        objectMapper.writeValue(indexPath.toFile(), state)
    }

    private fun initDatabase(
        indexPath: Path,
        locked: Boolean,
    ) {
        if (!Files.exists(indexPath) && !locked) {
            throw FileNotFoundException(indexPath.toString())
        } else if (!Files.exists(indexPath)) {
            Files.createDirectories(config.path.resolve(TILES_FOLDER_NAME))
            objectMapper.writeValue(indexPath.toFile(), emptyRaylevationState())
        }
    }

    @Synchronized
    private fun addTile(tile: IRaylevationTile) {
        if (tiles.any { it.name == tile.name }) {
            throw IllegalArgumentException("Tile ${tile.name} already exists")
        }
        // Create new index, because the index is immutable, therefore we need to create a new one on every change
        val newTiles = tiles.toMutableSet()
        val newIndex = index.add(tile, tile.toRectangle())
        newTiles.add(tile)

        // Overwrite old index
        tiles = newTiles.toSet()
        index = newIndex

        // Observability
        totalTilesGauge.set(tiles.size)
        logger.debug("Added Tile ${tile.name} to index")
    }
}
