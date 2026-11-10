package com.raynigon.raylevation.db.exception

import com.raynigon.raylevation.db.tile.TileBounds
import spock.lang.Specification

import java.nio.file.Path

class RaylevationExceptionSpec extends Specification {

    Path path = Path.of("tiles/tile.tif")

    def "GeoTiffException uses the message of the cause"() {
        given:
        def cause = new IOException("disk on fire")

        when:
        def exception = new GeoTiffException(path, cause)

        then:
        exception.path == path
        exception.cause.is(cause)
        exception.message == "Unable to read GeoTiff (tiles/tile.tif): disk on fire"
    }

    def "GeoTiffException falls back to the class name if the cause has no message"() {
        when:
        def exception = new GeoTiffException(path, new IllegalStateException())

        then:
        exception.message == "Unable to read GeoTiff (tiles/tile.tif): IllegalStateException"
    }

    def "GeoTiffException with message only has no cause"() {
        when:
        def exception = new GeoTiffException(path, "No such file or directory", null)

        then:
        exception.cause == null
        exception.message.endsWith("No such file or directory")
    }

    def "InvalidSubTileException contains path, tile bounds and requested bounds"() {
        given:
        def bounds = new TileBounds(2.0d, 0.0d, 0.0d, 2.0d)
        def requested = new TileBounds(3.0d, 0.0d, 1.0d, 1.0d)

        when:
        def exception = new InvalidSubTileException(path, bounds, requested)

        then:
        exception.path == path
        exception.bounds == bounds
        exception.requested == requested
        exception.message == "Tile ($path) with bounds $bounds does not cover the requested area $requested"
    }
}
