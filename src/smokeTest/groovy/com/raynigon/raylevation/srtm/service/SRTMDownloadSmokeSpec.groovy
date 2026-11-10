package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.db.geotiff.GeoTiffTile
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import com.raynigon.raylevation.srtm.model.OriginTile
import com.raynigon.raylevation.srtm.model.SRTMConfig
import groovy.json.JsonSlurper
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import spock.lang.Specification
import spock.lang.Timeout

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Smoke test which downloads real SRTM tiles from the production download host and verifies
 * that Apache SIS reads them correctly, by comparing sampled elevations against the
 * independent reference dataset from open-elevation.com.
 *
 * Not part of the regular unit test suite: run explicitly with `./gradlew smokeTest`.
 * Requires network access and the `unrar` binary to be installed locally.
 */
class SRTMDownloadSmokeSpec extends Specification {

    // The download URL template is resolved like in the application: the default comes from
    // application.yml and can be overridden with Spring's env format, e.g.
    // `export APP_RAYLEVATION_SRTM_URL=...` locally, in CI it comes from a GitHub secret.
    // An empty value (GitHub Actions passes "" for a missing secret) falls back to the default.
    private static final String URL_PROPERTY = "app.raylevation.srtm.url"
    private static final String OPEN_ELEVATION_URL = "https://api.open-elevation.com/api/v1/lookup"
    private static final int SAMPLE_COUNT = 5
    // open-elevation.com uses a different (finer) resolution reference dataset than the
    // 250m-aggregated production tiles, so only "same ballpark" is expected, not an exact match.
    private static final double TOLERANCE_METERS = 150.0d

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build()

    SRTMConfig config = new SRTMConfig(
            Path.of("workspace/smoke-test"),
            resolveSmokeTestUrl(),
            [],
            false
    )

    TileDownloadService downloadService = new TileDownloadServiceImpl(config)

    TileUnpackService unpackService = new TileUnpackServiceImpl(config)

    @Timeout(value = 600, unit = TimeUnit.SECONDS)
    def "tile #tileName downloads, unpacks and matches open-elevation.com within tolerance"() {
        given:
        Files.createDirectories(config.workspace)
        Path tileDir = config.workspace.resolve(tileName)
        Files.createDirectories(tileDir)
        OriginTile originTile = new OriginTile(tileName, splitX, splitY, "-", null, null, null)

        when: "the real production archive is downloaded and unpacked"
        OriginTile downloaded = downloadService.downloadTile(originTile, tileDir)
        OriginTile unpacked = unpackService.unpackArchive(downloaded, tileDir)
        GeoTiffTile tile = new GeoTiffTile(unpacked.geoTiffPath)

        then: "the GeoTiff metadata read by Apache SIS is coherent"
        TileBounds bounds = tile.bounds
        bounds.xMin < bounds.xMax
        bounds.yMin < bounds.yMax

        when: "carving out one production-sized sub tile, the same way TileSplitService does"
        // The downloaded file is the whole continental mosaic (e.g. 100810x28810 pixels for
        // SRTM_SE_250m) - looking up elevations directly on it would make GeoTiffTile try to
        // read the entire raster into a single array, which exceeds Java's max array size.
        // Production code never does this: it always looks up elevations on the small sub
        // tiles TileSplitService carves out first, so this mirrors that real usage.
        GeoTiffTile subTile = randomSubTile(tile, bounds, splitX, splitY, tileName)

        and: "sampling random coordinates inside the sub tile and comparing against open-elevation.com"
        List<GeoPoint> points = randomPoints(subTile.bounds, tileName)
        List<Double> reference = fetchReferenceElevations(points)

        then: "the elevation looked up in the sub tile matches the independent reference, within tolerance"
        points.eachWithIndex { GeoPoint point, int index ->
            double actual = subTile.lookupElevation(point).toSystemUnit().value.toDouble()
            double expected = reference[index]
            assert Math.abs(actual - expected) <= TOLERANCE_METERS: \
                    "Elevation mismatch at $point: expected ~${expected}m (open-elevation.com), got ${actual}m"
        }

        cleanup:
        subTile?.path?.toFile()?.delete()

        where:
        tileName       | splitX | splitY
        "SRTM_NE_250m" | 20     | 10
        "SRTM_SE_250m" | 20     | 10
        "SRTM_W_250m"  | 20     | 20
    }

    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    def "downloading the same tile twice reuses the local archive when the E-Tag is unchanged"() {
        given: "a dedicated directory, isolated from the parameterized test above"
        String tileName = "SRTM_NE_250m"
        Files.createDirectories(config.workspace)
        Path tileDir = config.workspace.resolve("${tileName}-duplicate-etag-check")
        Files.createDirectories(tileDir)
        OriginTile originTile = new OriginTile(tileName, 1, 1, "-", null, null, null)

        when: "the real production archive is downloaded for the first time"
        OriginTile first = downloadService.downloadTile(originTile, tileDir)
        FileTime firstModifiedTime = Files.getLastModifiedTime(first.archivePath)

        then: "the download succeeded and an E-Tag was recorded"
        first.archivePath.toFile().exists()
        first.etag != null

        when: "the same tile is downloaded again into the same directory"
        OriginTile second = downloadService.downloadTile(originTile, tileDir)

        then: "the real SRTM host returns the same E-Tag, so the local archive file is never rewritten"
        second.etag == first.etag
        Files.size(second.archivePath) == Files.size(first.archivePath)
        Files.getLastModifiedTime(second.archivePath) == firstModifiedTime

        when: "the reused archive is unpacked"
        OriginTile unpacked = unpackService.unpackArchive(second, tileDir)
        GeoTiffTile tile = new GeoTiffTile(unpacked.geoTiffPath)

        then: "it is still a valid, readable GeoTiff"
        TileBounds bounds = tile.bounds
        bounds.xMin < bounds.xMax
        bounds.yMin < bounds.yMax
    }

    private static GeoTiffTile randomSubTile(GeoTiffTile tile, TileBounds bounds, int splitX, int splitY, String tileName) {
        // Mirrors the cell math in TileSplitServiceImpl.split(), picking one cell instead of all of them.
        Random random = new Random(tileName.hashCode())
        int x = 1 + random.nextInt(splitX)
        int y = 1 + random.nextInt(splitY)
        double cx = (bounds.xMax - bounds.xMin) / splitX
        double cy = (bounds.yMax - bounds.yMin) / splitY
        double x1 = bounds.xMin + cx * (x - 1)
        double x2 = bounds.xMax - cx * (splitX - x)
        double y1 = bounds.yMin + cy * (y - 1)
        double y2 = bounds.yMax - cy * (splitY - y)
        return tile.subTile(new TileBounds(y2, x1, y1, x2))
    }

    private static List<GeoPoint> randomPoints(TileBounds bounds, String tileName) {
        // Seeded per tile so a local smoke test run is reproducible/debuggable,
        // while still sampling across the whole tile.
        Random random = new Random(tileName.hashCode())
        (1..SAMPLE_COUNT).collect {
            double latitude = bounds.yMin + random.nextDouble() * (bounds.yMax - bounds.yMin)
            double longitude = bounds.xMin + random.nextDouble() * (bounds.xMax - bounds.xMin)
            new GeoPoint(latitude, longitude)
        }
    }

    private static String resolveSmokeTestUrl() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean()
        yaml.resources = new ClassPathResource("application.yml")
        Properties defaults = yaml.object

        // StandardEnvironment maps app.raylevation.srtm.url to APP_RAYLEVATION_SRTM_URL,
        // system properties and environment variables take precedence over application.yml
        StandardEnvironment environment = new StandardEnvironment()
        environment.propertySources.addLast(new PropertiesPropertySource("application.yml", defaults))

        String url = environment.getProperty(URL_PROPERTY)?.trim() ?: defaults?.getProperty(URL_PROPERTY)?.trim()
        if (!url) {
            throw new IllegalStateException("No SRTM download URL found: $URL_PROPERTY is missing in application.yml")
        }
        return url
    }

    private List<Double> fetchReferenceElevations(List<GeoPoint> points) {
        // Batch all points for a tile into a single request to stay polite to the free API.
        String locations = points.collect { "${it.latitude},${it.longitude}" }.join("|")
        URI uri = URI.create("${OPEN_ELEVATION_URL}?locations=${URLEncoder.encode(locations, "UTF-8")}")
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assert response.statusCode() == 200: "open-elevation.com returned HTTP ${response.statusCode()}: ${response.body()}"
        def json = new JsonSlurper().parseText(response.body())
        return json.results.collect { ((Number) it.elevation).doubleValue() }
    }
}
