package com.raynigon.raylevation.db

import static com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre

import tools.jackson.databind.ObjectMapper
import com.raynigon.raylevation.db.config.DatabaseConfig
import com.raynigon.raylevation.db.exception.DatabaseNotLockedException
import com.raynigon.raylevation.db.exception.TileNotFoundException
import com.raynigon.raylevation.db.geotiff.GeoTiffTile
import com.raynigon.raylevation.db.tile.IRaylevationTile
import com.raynigon.raylevation.db.tile.RaylevationTile
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.helper.FileHelper
import com.raynigon.raylevation.infrastructure.configuration.JacksonConfiguration
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class RaylevationDBSpec extends Specification {

    DatabaseConfig config = new DatabaseConfig(Path.of("src/test/resources/database/empty/"), 1)

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry()

    ObjectMapper mapper = new JacksonConfiguration().objectMapper()


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
        tile0.getName() >> "tile0"
        tile0.getBounds() >> new TileBounds(4, 0, 0, 2)
        IRaylevationTile tile1 = Mock()
        tile1.getName() >> "tile1"
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
        tile0.getName() >> "tile0"
        tile0.getBounds() >> new TileBounds(60.010, -9.008, 54.008, 1.491)
        tile0.getLookups() >> 1
        database.addTile(tile0)

        and:
        IRaylevationTile tile1 = Mock()
        tile1.getName() >> "tile1"
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
        tile0.getName() >> "tile0"
        tile0.getBounds() >> new TileBounds(60.010, -9.008, 54.008, 1.491)
        tile0.getLookups() >> 1
        tile0.getCache() >> true
        database.addTile(tile0)

        and:
        IRaylevationTile tile1 = Mock()
        tile1.getName() >> "tile1"
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
        GeoTiffTile tile = new GeoTiffTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))

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
        thrown(DatabaseNotLockedException)
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
        GeoTiffTile tile = new GeoTiffTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))

        when:
        database.addTile(tile)

        then:
        thrown(DatabaseNotLockedException)
    }

    def "remove tile without lock"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(false)

        when:
        database.removeTile("60.010_54.008_-9.008_1.491")

        then:
        thrown(DatabaseNotLockedException)
    }

    def "remove tile deletes it from index and disk"() {
        given:
        Path workspace = FileHelper.createTemporaryDirectory()
        DatabaseConfig config = new DatabaseConfig(workspace, 1)

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(true)

        and:
        GeoTiffTile tile = new GeoTiffTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))
        String name = database.addTile(tile)
        Path tilePath = config.path.resolve("tiles").resolve("${name}.tif")

        expect:
        Files.exists(tilePath)

        when:
        database.removeTile(name)

        then:
        noExceptionThrown()
        !Files.exists(tilePath)

        when:
        database.lookupElevation(new GeoPoint(57.0, -4.0))

        then:
        thrown(TileNotFoundException)
    }

    def "remove tile that does not exist is a no-op"() {
        given:
        Path workspace = FileHelper.createTemporaryDirectory()
        DatabaseConfig config = new DatabaseConfig(workspace, 1)

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(true)

        when:
        database.removeTile("does-not-exist")

        then:
        noExceptionThrown()
    }

    def "add tile with same name but different instance is rejected"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        and:
        RaylevationTile tile0 = new RaylevationTile(
                "60.010_54.008_-9.008_1.491",
                Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"),
                new TileBounds(60.010, -9.008, 54.008, 1.491),
                new SimpleMeterRegistry()
        )
        RaylevationTile tile1 = new RaylevationTile(
                "60.010_54.008_-9.008_1.491",
                Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"),
                new TileBounds(60.010, -9.008, 54.008, 1.491),
                new SimpleMeterRegistry()
        )

        when:
        database.addTile(tile0)

        and:
        database.addTile(tile1)

        then:
        thrown(IllegalArgumentException)
    }

    def "find tile names returns only tiles within bounds"() {
        given:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)

        and:
        IRaylevationTile tile0 = Mock()
        tile0.getName() >> "tile0"
        tile0.getBounds() >> new TileBounds(4, 0, 0, 2)
        IRaylevationTile tile1 = Mock()
        tile1.getName() >> "tile1"
        tile1.getBounds() >> new TileBounds(4, 2, 0, 4)

        and:
        database.addTile(tile0)
        database.addTile(tile1)

        when:
        def matching = database.findTileNames(new TileBounds(4, 0, 0, 2))

        then:
        matching == ["tile0"]

        when:
        def none = database.findTileNames(new TileBounds(-1, -10, -5, -8))

        then:
        none == []
    }

    def "add tile with missing tiles folder"() {
        given:
        Path workspace = FileHelper.createTemporaryDirectory()
        DatabaseConfig config = new DatabaseConfig(workspace, 1)

        and:
        IRaylevationDB database = new RaylevationDB(config, meterRegistry, mapper)
        database.open(true)

        and:
        GeoTiffTile tile = new GeoTiffTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))

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
