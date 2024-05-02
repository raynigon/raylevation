package com.raynigon.raylevation.srtm.model

import java.nio.file.Path

/**
 * Data transfer object used for setup the srtm tiles
 */
data class OriginTile(
    val name: String,
    val splitX: Int,
    val splitY: Int,
    val etag: String = "-",
    val etagPath: Path? = null,
    val archivePath: Path? = null,
    val geoTiffPath: Path? = null,
)
