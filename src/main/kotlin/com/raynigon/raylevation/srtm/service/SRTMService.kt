package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.base.service.RaylevationDBFactory
import com.raynigon.raylevation.db.IRaylevationDB
import com.raynigon.raylevation.db.geotiff.GeoTiffTile
import com.raynigon.raylevation.srtm.model.OriginTile
import com.raynigon.raylevation.srtm.model.SRTMConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

/**
 * Update the [IRaylevationDB] for this context with the latest SRTM tiles.
 */
interface SRTMService {
    /**
     * Update the [IRaylevationDB] for this context with the latest SRTM tiles.
     */
    fun updateRaylevationDB()
}

/**
 * Implementation of the [SRTMService]
 */
@Service
@EnableConfigurationProperties(SRTMConfig::class)
class SRTMServiceImpl(
    private val config: SRTMConfig,
    private val factory: RaylevationDBFactory,
    private val downloadService: TileDownloadService,
    private val unpackService: TileUnpackService,
    private val splitService: TileSplitService,
) : SRTMService {
    companion object {
        const val METADATA_KEY_TILES = "raylevation.srtm.tiles"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val workspace = config.workspace
    private val tiles: List<OriginTile> = config.tiles.map { OriginTile(it.name, it.splitX, it.splitY) }

    override fun updateRaylevationDB() {
        val database = factory.createLocked()
        var tileState =
            database.getMetadata(METADATA_KEY_TILES, OriginTilesState::class.java)
                ?: OriginTilesState()
        Files.createDirectories(workspace)

        for (originTile in tiles) {
            if (isUpToDate(originTile, tileState)) {
                logger.info("${originTile.name} is up to date and does not need an update")
                continue
            }
            val previousState = tileState.getState(originTile)
            logger.debug("Start processing for tile ${originTile.name}")
            var tile = originTile
            val tileDir = createTileDirectory(tile, workspace)
            logger.debug("Created working directory $tileDir")
            // Download Tile
            tile = downloadService.downloadTile(tile, tileDir)
            logger.debug("Downloaded tile archive to ${tile.archivePath}")
            // Unpack Tile
            tile = unpackService.unpackArchive(tile, tileDir)
            logger.debug("Unpacked tile to ${tile.geoTiffPath}")
            // Remove sub tiles from a previous version of this origin tile, if any
            if (previousState != null) {
                removeOldSubTiles(tile, previousState, database)
            }
            // Split Tile
            val subTileNames = mutableListOf<String>()
            splitService.split(tile) { subTile, _ -> subTileNames.add(database.addTile(subTile)) }
            logger.debug("Added ${subTileNames.size} subTiles to database for origin ${tile.name}")
            // Update Metadata
            tileState = tileState.updateTile(tile, subTileNames)
            database.setMetadata(METADATA_KEY_TILES, tileState)
            // Save Database
            database.save()
            if (config.saveDiskSpace) {
                tile.geoTiffPath?.let(Files::delete)
                tile.archivePath?.let(Files::delete)
                tile.etagPath?.let(Files::delete)
            }
        }
        database.close()
    }

    private fun isUpToDate(
        tile: OriginTile,
        tileState: OriginTilesState,
    ): Boolean {
        val state = tileState.getState(tile) ?: return false
        if (state.etag != downloadService.fetchETag(tile)) return false
        return state.splitX == tile.splitX && state.splitY == tile.splitY
    }

    private fun removeOldSubTiles(
        tile: OriginTile,
        previous: OriginTileState,
        database: IRaylevationDB,
    ) {
        val staleNames =
            previous.subTiles.ifEmpty {
                logger.warn(
                    "Origin tile ${tile.name} has no recorded sub tiles (legacy metadata), " +
                        "falling back to a bounds lookup",
                )
                database.findTileNames(GeoTiffTile(tile.geoTiffPath!!).bounds)
            }
        if (staleNames.isEmpty()) {
            logger.warn("No existing sub tiles found for origin tile ${tile.name}, nothing to remove")
        }
        staleNames.forEach(database::removeTile)
    }

    private fun createTileDirectory(
        tile: OriginTile,
        target: Path,
    ): Path {
        val tileDir = target.resolve(tile.name)
        Files.createDirectories(tileDir)
        return tileDir
    }
}

/**
 * Temporary container for the setup of the SRTM tiles
 */
data class OriginTilesState(
    val tiles: List<OriginTileState> = emptyList(),
) {
    /**
     * Create or update the given origin tile.
     *
     * @param tile        The [OriginTile] to create or update
     * @param subTiles    The names of the database tiles which were created for this [OriginTile]
     * @return The new [OriginTileState] containing the updated [OriginTile]
     */
    fun updateTile(
        tile: OriginTile,
        subTiles: List<String>,
    ): OriginTilesState {
        val newTiles = tiles.filter { it.name != tile.name }.toMutableList()
        newTiles.add(OriginTileState(tile.name, tile.etag, tile.splitX, tile.splitY, subTiles))
        return OriginTilesState(newTiles.toList())
    }

    /**
     *  Returns the stored state for a given [OriginTile]
     *
     *  @param tile    The [OriginTile] for which the state should be queried
     *  @return The [OriginTileState] of the given [OriginTile] or null if no state was found
     */
    fun getState(tile: OriginTile): OriginTileState? = tiles.firstOrNull { it.name == tile.name }
}

/**
 * Temporary container for the setup of the SRTM tiles
 *
 * @param splitX      The horizontal split count used to create [subTiles], 0 if unknown (legacy metadata)
 * @param splitY      The vertical split count used to create [subTiles], 0 if unknown (legacy metadata)
 * @param subTiles    The names of the database tiles which were created for this origin tile,
 *                     empty if unknown (legacy metadata)
 */
data class OriginTileState(
    val name: String,
    val etag: String,
    val splitX: Int = 0,
    val splitY: Int = 0,
    val subTiles: List<String> = emptyList(),
)
