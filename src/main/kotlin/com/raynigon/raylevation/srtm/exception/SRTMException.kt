package com.raynigon.raylevation.srtm.exception

import java.nio.file.Path

/**
 * If an error occurs while downloading or unpacking an SRTM tile, this exception is thrown.
 */
sealed class SRTMException(
    message: String,
) : RuntimeException(message)

/**
 * If the SRTM download host responds with anything other than HTTP 200, this exception is thrown.
 */
class TileDownloadException(
    val url: String,
    val statusCode: Int,
) : SRTMException("Download of $url failed with HTTP status $statusCode")

/**
 * If unrar exits with a non-zero status code while unpacking an SRTM archive, this exception is thrown.
 */
class TileUnpackException(
    val archivePath: Path,
    val exitCode: Int,
) : SRTMException("Unpacking $archivePath failed, unrar exited with code $exitCode")
