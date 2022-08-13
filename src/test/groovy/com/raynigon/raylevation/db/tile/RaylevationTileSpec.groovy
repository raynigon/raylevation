package com.raynigon.raylevation.db.tile

import com.raynigon.raylevation.db.exception.LookupOutOfBoundsException
import com.raynigon.raylevation.helper.QuantityHelper
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import com.raynigon.raylevation.db.exception.LookupOutOfBoundsException
import com.raynigon.raylevation.helper.QuantityHelper
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path

import static com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre

class RaylevationTileSpec extends Specification {

    def "lookup out of bounds throws exception"() {
        given:
        RaylevationTile tile = new RaylevationTile(
                "tile",
                Path.of("tile.tif"),
                new TileBounds(1, 0, 0, 1),
                new SimpleMeterRegistry()
        )

        when:
        tile.lookupElevation(new GeoPoint(2, 2))

        then:
        thrown(LookupOutOfBoundsException)
    }

    def "lookup value without cache"() {
        given:
        RaylevationTile tile = new RaylevationTile(
                "60.010_54.008_-9.008_1.491",
                Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"),
                new TileBounds(60.010, -9.008, 54.008, 1.491),
                new SimpleMeterRegistry()
        )

        when:
        def result = tile.lookupElevation(new GeoPoint(56.0, -5.0))

        then:
        !tile.getCache()

        and:
        QuantityHelper.assertDelta(Metre(159), result, 0.00001)
    }

    def "lookup value with cache"() {
        given:
        RaylevationTile tile = new RaylevationTile(
                "60.010_54.008_-9.008_1.491",
                Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"),
                new TileBounds(60.010, -9.008, 54.008, 1.491),
                new SimpleMeterRegistry()
        )

        when:
        tile.setCache(true)

        and:
        def result = tile.lookupElevation(new GeoPoint(56.0, -5.0))

        then:
        tile.getCache()

        and:
        QuantityHelper.assertDelta(Metre(159), result, 0.00001)
    }

    def "lookup value increments counters"() {
        given:
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry()

        and:
        RaylevationTile tile = new RaylevationTile(
                "60.010_54.008_-9.008_1.491",
                Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"),
                new TileBounds(60.010, -9.008, 54.008, 1.491),
                meterRegistry
        )

        when:
        def result = tile.lookupElevation(new GeoPoint(56.0, -5.0))

        then:
        QuantityHelper.assertDelta(Metre(159), result, 0.00001)

        and: "Tile Lookup property was incremented"
        tile.lookups == 1

        and: "Meter Registry counter was incremented"
        meterRegistry.find("app.raylevation.db.tile.lookups").counter().count() == 1.0d
    }

    @Unroll
    def "cache management, initial=#initial, change=#change"() {
        given:
        RaylevationTile tile = new RaylevationTile(
                "60.010_54.008_-9.008_1.491",
                Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"),
                new TileBounds(60.010, -9.008, 54.008, 1.491),
                new SimpleMeterRegistry()
        )

        and: "Initial cache state"
        tile.setCache(initial)

        when:
        tile.setCache(change)

        then:
        tile.getCache() == expected

        where:
        initial | change | expected
        false   | false  | false
        true    | true   | true
        false   | true   | true
        true    | false  | false
    }
}
