package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.srtm.model.OriginTile
import com.raynigon.raylevation.srtm.model.SRTMConfig
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpHead
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.http.HttpHeaders
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads the Rar Archive for a given [OriginTile]
 */
interface TileDownloadService {

    /**
     * Load the remote E-Tag for a given OriginTile
     */
    fun fetchETag(tile: OriginTile): String

    /**
     * Download a given OriginTile as rar file
     */
    fun downloadTile(tile: OriginTile, target: Path): OriginTile
}

/**
 * Implements [TileDownloadService]
 */
@Service
class TileDownloadServiceImpl(
    private val config: SRTMConfig
) : TileDownloadService {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val client = HttpClientBuilder.create().build()

    companion object {
        private const val URL_PLACEHOLDER = "{{name}}"
        private const val CONTENT_FILE_SUFFIX = "_TIF.rar"
        private const val ETAG_FILE_SUFFIX = "$CONTENT_FILE_SUFFIX.etag"
    }

    init {
        require(config.url.contains(URL_PLACEHOLDER)) { "${config.url} does not contain '$URL_PLACEHOLDER'" }
    }

    override fun fetchETag(tile: OriginTile): String {
        val url = config.url.replace(URL_PLACEHOLDER, tile.name)
        return readRemoteETag(url) ?: "-"
    }

    override fun downloadTile(tile: OriginTile, target: Path): OriginTile {
        val url = config.url.replace(URL_PLACEHOLDER, tile.name)
        val etagPath = target.resolve("${tile.name}$ETAG_FILE_SUFFIX")
        val contentPath = target.resolve("${tile.name}$CONTENT_FILE_SUFFIX")
        val localETag = readLocalETag(etagPath)
        val remoteETag = readRemoteETag(url)

        // Do not download again if etag matched
        val etag = if (localETag == remoteETag && localETag != null) {
            logger.info("${tile.name} already exists in the correct version. Local: $localETag Remote: $remoteETag")
            localETag
        } else {
            downloadFile(url, contentPath, etagPath)
        }
        return tile.copy(etag = etag, etagPath = etagPath, archivePath = contentPath)
    }

    private fun readRemoteETag(url: String): String? {
        val request = HttpHead(url)
        val response = client.execute(request)
        return response.getHeader(HttpHeaders.ETAG)?.value
    }

    private fun readLocalETag(etagPath: Path): String? {
        if (!Files.exists(etagPath)) return null
        return Files.readString(etagPath, StandardCharsets.UTF_8)
    }

    private fun downloadFile(
        url: String,
        contentPath: Path,
        etagPath: Path?
    ): String {
        val request = HttpGet(url)
        val response = client.execute(request)
        // Handle response status
        if (response.code != 200) {
            TODO("raise exception due to incompatible response")
        }
        // Write Content to Disk
        FileOutputStream(contentPath.toFile()).use { fos ->
            response.entity.writeTo(fos)
            fos.flush()
        }
        logger.debug("Downloaded $url to $contentPath")
        // Handle ETag after content is written to disk
        val etag = response.getHeader(HttpHeaders.ETAG)?.value ?: "-"
        Files.writeString(etagPath, etag)
        logger.debug("Wrote ETag $etag to $etagPath")
        return etag
    }
}
