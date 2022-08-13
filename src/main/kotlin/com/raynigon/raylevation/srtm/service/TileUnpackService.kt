package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.infrastructure.process.ProcessLogger
import com.raynigon.raylevation.srtm.model.OriginTile
import com.raynigon.raylevation.srtm.model.SRTMConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.name

/**
 * The TileUnpackService decompresses the downloaded rar archive for a given origin file.
 */
interface TileUnpackService {

    /**
     * Decompresses the downloaded rar archive for a given tile
     * @param tile    The origin tile for which the archive should be decompressed
     * @param target  The target path where the decompressed data should be written to
     * @return A new instance of an [OriginTile] which contains the path to the decompressed archive
     */
    fun unpackArchive(tile: OriginTile, target: Path): OriginTile
}

/**
 * Implementation of [TileUnpackService]
 */
@Service
@EnableConfigurationProperties(SRTMConfig::class)
class TileUnpackServiceImpl(private val config: SRTMConfig) : TileUnpackService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun unpackArchive(tile: OriginTile, target: Path): OriginTile {
        // Return if the rar file was already extracted
        findGeoTiff(target)?.let {
            logger.info("${tile.name} was already unpacked, using existing GeoTiff: $it")
            return tile.copy(geoTiffPath = it)
        }
        // GeoTiff was not extracted
        val process = ProcessBuilder()
            .directory(target.toFile())
            .command("unrar", "e", tile.archivePath!!.toAbsolutePath().toString())
            .start()
        ProcessLogger(process, logger, "UNRAR: ")
        if (process.waitFor() != 0) {
            TODO("Raise exception, unrar failed")
        }
        val geoTiff = findGeoTiff(target) ?: error("No GeoTiff in archive")
        return if (!config.saveDiskSpace) {
            tile.copy(geoTiffPath = geoTiff)
        } else {
            tile.archivePath.let(Files::delete)
            tile.etagPath?.let(Files::delete)
            tile.copy(geoTiffPath = geoTiff, archivePath = null, etagPath = null)
        }
    }

    private fun findGeoTiff(target: Path): Path? {
        return Files.list(target)
            .collect(Collectors.toList())
            .firstOrNull { it.name.endsWith(".tif") }
    }
}
