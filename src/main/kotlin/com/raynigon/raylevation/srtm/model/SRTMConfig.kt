package com.raynigon.raylevation.srtm.model

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.nio.file.Path

/**
 * Data transfer object containing the configuration properties of the SRTM download module.
 *
 * @param workspace       The directory to which the SRTM data can be downloaded to
 * @param url             The base url from which the tiles should be downloaded
 * @param tiles           The list of tiles to download
 * @param saveDiskSpace   The Disk Space saving mode deleted all files which are no longer needed. This causes a new download on each run.
 */
@ConfigurationProperties("app.raylevation.srtm")
data class SRTMConfig(
    val workspace: Path,
    val url: String,
    @NestedConfigurationProperty
    val tiles: List<OriginTileConfig>,
    val saveDiskSpace: Boolean = false
)

/**
 * Data transfer object containing the tile metadata
 *
 * @param name    The name of the tile to be downlaoded
 * @param splitX  The number of horizontal segments in which this tile should be split to
 * @param splitY  The number of vertical segments in which this tile should be split to
 */
data class OriginTileConfig(
    val name: String,
    val splitX: Int,
    val splitY: Int
)
