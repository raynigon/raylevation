package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.base.service.RaylevationDBFactory
import com.raynigon.raylevation.db.IRaylevationDB
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
            } else if (tileState.contains(originTile)) {
                TODO("Origin Tile changed, tile updates are not yet implemented")
            }
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
            // Split Tile
            splitService.split(tile) { subTile, _ -> database.addTile(subTile) }
            logger.debug("Add subTiles to database for origin ${tile.name}")
            // Update Metadata
            tileState = tileState.updateTile(tile)
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
        if (!tileState.contains(tile)) return false
        val stateETag = tileState.getETag(tile)
        val remoteETag = downloadService.fetchETag(tile)
        return stateETag == remoteETag
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
     * @param tile    The [OriginTile] to create or update
     * @return The new [OriginTileState] containing the updated [OriginTile]
     */
    fun updateTile(tile: OriginTile): OriginTilesState {
        val newTiles = tiles.filter { it.name != tile.name }.toMutableList()
        newTiles.add(OriginTileState(tile.name, tile.etag))
        return OriginTilesState(newTiles.toList())
    }

    /**
     *  Checks if a given [OriginTile] is present
     *
     *  @param tile    The [OriginTile] which should be checked
     *  @return True if the tile exists, false if not
     */
    fun contains(tile: OriginTile): Boolean {
        return tiles.any { it.name == tile.name }
    }

    /**
     *  Returns the E-Tag for a given [OriginTile]
     *
     *  @param tile    The [OriginTile] for which the E-Tag should be queried
     *  @return The E-Tag of the given [OriginTile] or null if no E-Tag was found
     */
    fun getETag(tile: OriginTile): String? {
        return tiles.filter { it.name == tile.name }.map { it.etag }.firstOrNull()
    }
}

/**
 * Temporary container for the setup of the SRTM tiles
 */
data class OriginTileState(
    val name: String,
    val etag: String,
)
