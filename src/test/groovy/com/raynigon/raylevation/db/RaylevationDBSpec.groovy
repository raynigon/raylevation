package com.raynigon.raylevation.db

import static com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre

import com.fasterxml.jackson.databind.ObjectMapper
import com.raynigon.raylevation.db.config.DatabaseConfig
import com.raynigon.raylevation.db.exception.TileNotFoundException
import com.raynigon.raylevation.db.gdal.GDALTile
import com.raynigon.raylevation.db.tile.IRaylevationTile
import com.raynigon.raylevation.db.tile.RaylevationTile
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.helper.FileHelper
import com.raynigon.raylevation.infrastructure.configuration.JacksonConfiguration
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.NotImplementedError
import org.gdal.gdal.gdal
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class RaylevationDBSpec extends Specification {

    DatabaseConfig config = new DatabaseConfig(Path.of("src/test/resources/database/empty/"), 1)

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry()

    ObjectMapper mapper = new JacksonConfiguration().objectMapper()

    def setup() {
        gdal.AllRegister()
    }

    def "open database without tiles"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        when:
        database.open(false)

        then:
        noExceptionThrown()
    }

    def "open database with lock"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        when:
        database.open(true)

        then:
        noExceptionThrown()
        Files.exists(config.path.resolve("lock.json"))

        cleanup:
        database.close()
    }

    def "open database with one tile"() {
        given:
        DatabaseConfig config = new DatabaseConfig(Path.of("src/test/resources/database/simple/"), 1)

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        when:
        database.open(false)

        then:
        noExceptionThrown()
    }

    def "create new database with lock"() {
        given:
        Path workspace = FileHelper.createTemporaryDirectory()
        DatabaseConfig config = new DatabaseConfig(workspace, 1)

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        when:
        database.open(true)

        then:
        noExceptionThrown()
        Files.exists(config.path.resolve("index.json"))
        Files.exists(config.path.resolve("lock.json"))
        Files.exists(config.path.resolve("tiles"))
    }

    def "create new database without lock"() {
        given:
        Path workspace = FileHelper.createTemporaryDirectory()
        DatabaseConfig config = new DatabaseConfig(workspace, 1)

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        when:
        database.open(false)

        then:
        thrown(FileNotFoundException)
    }

    def "add tile to database"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        and:
        RaylevationTile tile = new RaylevationTile(
                "60.010_54.008_-9.008_1.491",
                Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"),
                new TileBounds(60.010, -9.008, 54.008, 1.491),
                new SimpleMeterRegistry()
        )

        when:
        database.addTile(tile)

        then:
        noExceptionThrown()
    }

    def "add same tile to database twice"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        and:
        RaylevationTile tile = new RaylevationTile(
                "60.010_54.008_-9.008_1.491",
                Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"),
                new TileBounds(60.010, -9.008, 54.008, 1.491),
                new SimpleMeterRegistry()
        )

        when:
        database.addTile(tile)

        and:
        database.addTile(tile)

        then:
        thrown(IllegalArgumentException)
    }

    def "lookup correct tile"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        and:
        IRaylevationTile tile0 = Mock()
        tile0.getBounds() >> new TileBounds(4, 0, 0, 2)
        IRaylevationTile tile1 = Mock()
        tile1.getBounds() >> new TileBounds(4, 2, 0, 4)

        and:
        database.addTile(tile0)
        database.addTile(tile1)

        when:
        def result = database.lookupElevation(new GeoPoint(3.0, 1.0))

        then:
        result == Metre(42)

        and:
        1 * tile0.lookupElevation(_) >> Metre(42)
    }

    def "lookup without tiles"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        when:
        database.lookupElevation(new GeoPoint(3.0, 1.0))

        then:
        thrown(TileNotFoundException)
    }

    def "sync cache - without tiles"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        when:
        database.syncCache()

        then:
        noExceptionThrown()
    }

    def "sync cache - cache one tile"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        and:
        IRaylevationTile tile0 = Mock()
        tile0.getBounds() >> new TileBounds(60.010, -9.008, 54.008, 1.491)
        tile0.getLookups() >> 1
        database.addTile(tile0)

        and:
        IRaylevationTile tile1 = Mock()
        tile1.getBounds() >> new TileBounds(54.008, 1.491, 50.008, 11.491)
        database.addTile(tile1)

        when:
        database.syncCache()

        then:
        1 * tile0.setCache(true)
        1 * tile1.setCache(false)
    }

    def "sync cache - invalidate cache for one tile"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        and:
        IRaylevationTile tile0 = Mock()
        tile0.getBounds() >> new TileBounds(60.010, -9.008, 54.008, 1.491)
        tile0.getLookups() >> 0
        tile0.getCache() >> true
        database.addTile(tile0)

        when:
        database.syncCache()

        then:
        1 * tile0.setCache(false)
    }

    def "sync cache - sync without change"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        and:
        IRaylevationTile tile0 = Mock()
        tile0.getBounds() >> new TileBounds(60.010, -9.008, 54.008, 1.491)
        tile0.getLookups() >> 1
        tile0.getCache() >> true
        database.addTile(tile0)

        and:
        IRaylevationTile tile1 = Mock()
        tile1.getBounds() >> new TileBounds(54.008, 1.491, 50.008, 11.491)
        tile0.getLookups() >> 0
        tile1.getCache() >> false
        database.addTile(tile1)

        when:
        database.syncCache()

        then:
        0 * tile0.setCache(true)
        0 * tile1.setCache(false)
    }

    def "save empty database"() {
        given:
        Path workspace = FileHelper.createTemporaryDirectory()
        DatabaseConfig config = new DatabaseConfig(workspace, 1)

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(true)

        expect:
        Files.exists(config.path.resolve("lock.json"))

        when:
        database.save()
        database.close()

        then:
        noExceptionThrown()
        Files.exists(config.path.resolve("index.json"))
        !Files.exists(config.path.resolve("lock.json"))
        Files.exists(config.path.resolve("tiles"))
    }

    def "save database with metadata"() {
        given:
        Path workspace = FileHelper.createTemporaryDirectory()
        DatabaseConfig config = new DatabaseConfig(workspace, 1)
        String randomValue = UUID.randomUUID().toString()

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(true)
        database.setMetadata("raynigon.test", ["key": randomValue])

        expect:
        Files.exists(config.path.resolve("lock.json"))

        when:
        database.save()
        database.close()

        then:
        noExceptionThrown()
        Files.exists(config.path.resolve("index.json"))
        !Files.exists(config.path.resolve("lock.json"))
        Files.exists(config.path.resolve("tiles"))

        and:
        Files.readString(config.path.resolve("index.json")).contains(randomValue)
    }

    def "save database with tile"() {
        given:
        Path workspace = FileHelper.createTemporaryDirectory()
        DatabaseConfig config = new DatabaseConfig(workspace, 1)

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(true)

        and:
        GDALTile tile = new GDALTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))

        expect:
        Files.exists(config.path.resolve("lock.json"))

        when:
        database.addTile(tile)

        and:
        database.save()
        database.close()

        then:
        noExceptionThrown()
        Files.exists(config.path.resolve("index.json"))
        !Files.exists(config.path.resolve("lock.json"))
        Files.exists(config.path.resolve("tiles"))
    }

    def "save database without lock"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(false)

        when:
        database.save()

        then:
        thrown(NotImplementedError)
    }

    def "close database without lock"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(false)

        when:
        database.close()

        then:
        noExceptionThrown()
    }

    def "add tile without lock"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(false)

        and:
        GDALTile tile = new GDALTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))

        when:
        database.addTile(tile)

        then:
        thrown(NotImplementedError)
    }

    def "add tile with missing tiles folder"() {
        given:
        Path workspace = FileHelper.createTemporaryDirectory()
        DatabaseConfig config = new DatabaseConfig(workspace, 1)

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(true)

        and:
        GDALTile tile = new GDALTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))

        when:
        Files.delete(config.path.resolve("tiles"))

        and:
        database.addTile(tile)

        then:
        noExceptionThrown()
        Files.exists(config.path.resolve("tiles"))
    }

    def "read existing metadata"() {
        given:
        DatabaseConfig config = new DatabaseConfig(Path.of("src/test/resources/database/simple/"), 1)
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(false)

        when:
        Map result = database.getMetadata("raynigon.test", Map)

        then:
        result.size() == 1
        result["key"] == "Hello World"

    }

    def "read missing metadata"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(false)

        when:
        Map result = database.getMetadata("raynigon.test", Map)

        then:
        result == null

    }
}
