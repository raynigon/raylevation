package com.raynigon.raylevation.db.geotiff

import static com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre

import com.raynigon.raylevation.db.exception.GeoTiffException
import com.raynigon.raylevation.db.exception.IncompatibleTileException
import com.raynigon.raylevation.db.exception.InvalidSubTileException
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.helper.QuantityHelper
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import javax.measure.Quantity
import javax.measure.quantity.Length
import java.nio.file.Files
import java.nio.file.Path

class GeoTiffTileSpec extends Specification {

    static final String TILE_PATH = "src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"

    // Pixel size of the test tile in degrees (7.5 arc seconds)
    static final double PIXEL = 0.00208333333

    @Shared
    GeoTiffTile sourceTile = new GeoTiffTile(Path.of(TILE_PATH))

    def "lookup(#lat, #lon) == expected"() {
        given:
        def tile = new GeoTiffTile(Path.of(path))

        when:
        Quantity<Length> result = tile.lookupElevation(new GeoPoint(lat, lon))

        then:
        QuantityHelper.assertDelta(expected, result, 0.00001)

        where:
        lat  | lon  | expected   | path
        56.0 | -5.0 | Metre(159) | TILE_PATH
        60.0 | -5.0 | Metre(0)   | TILE_PATH
    }

    def "load missing geotiff"() {
        when:
        new GeoTiffTile(Path.of("/non/existing/path/tile.tif"))

        then:
        def exception = thrown(GeoTiffException)
        exception.path == Path.of("/non/existing/path/tile.tif")
        exception.message.contains("No such file or directory")
    }

    def "load invalid geotiff"() {
        given:
        def file = Files.createTempFile("invalid", ".tif")
        file.toFile().deleteOnExit()
        Files.writeString(file, "this is not a tiff file")

        when:
        new GeoTiffTile(file)

        then:
        thrown(GeoTiffException)
    }

    @Ignore("No sample file exists")
    def "load geotiff with multiple bands"() {
        when:
        new GeoTiffTile(Path.of("src/test/resources/geotiff/multi-band.tif"))

        then:
        thrown(IncompatibleTileException)
    }

    def "load geotiff with incompatible band or projection"() {
        when:
        new GeoTiffTile(Path.of("src/test/resources/geotiff/cea.tif"))

        then:
        thrown(IncompatibleTileException)
    }

    def "bounds are read correctly"() {
        given:
        GeoTiffTile tile = new GeoTiffTile(Path.of(TILE_PATH))

        when:
        TileBounds bounds = tile.bounds

        then:
        bounds.getXMin() == -9.008333472519997d
        bounds.getXMax() == 1.4916665106800036d
        bounds.getYMin() == 54.00833315092001d
        bounds.getYMax() == 60.01041647465001d
    }

    def "lookup twice uses cached raster"() {
        given:
        def tile = new GeoTiffTile(Path.of(TILE_PATH))

        when:
        Quantity<Length> result0 = tile.lookupElevation(new GeoPoint(56.0, -5.0))
        Quantity<Length> result1 = tile.lookupElevation(new GeoPoint(56.0, -5.0))

        then:
        QuantityHelper.assertDelta(Metre(159), result0, 0.00001)
        QuantityHelper.assertDelta(Metre(159), result1, 0.00001)
    }

    def "lookup on the lower right corner"() {
        given:
        def bounds = sourceTile.bounds

        when:
        def result = sourceTile.lookupElevation(new GeoPoint(bounds.yMin, bounds.xMax))

        then:
        noExceptionThrown()
        result != null
    }

    def "database tile created by GDAL is compatible"() {
        given:
        def path = new File("src/test/resources/database/simple/tiles").listFiles().first().toPath()

        when:
        def tile = new GeoTiffTile(path)

        then:
        tile.bounds == sourceTile.bounds
        QuantityHelper.assertDelta(Metre(159), tile.lookupElevation(new GeoPoint(56.0, -5.0)), 0.00001)
    }

    def "sub tile is aligned to full pixels like gdal_translate"() {
        given:
        def bounds = sourceTile.bounds
        // Request a window which is not aligned to the pixel grid
        def requested = new TileBounds(
                bounds.yMax - 100.3 * PIXEL,
                bounds.xMin + 200.2 * PIXEL,
                bounds.yMax - 150.6 * PIXEL,
                bounds.xMin + 260.7 * PIXEL,
        )

        when:
        def subTile = sourceTile.subTile(requested)

        then: "offset is rounded down, size is rounded to the nearest pixel (60.5 -> 61, 50.3 -> 50)"
        Math.abs(subTile.bounds.xMin - (bounds.xMin + 200 * PIXEL)) < 1e-9
        Math.abs(subTile.bounds.yMax - (bounds.yMax - 100 * PIXEL)) < 1e-9
        Math.abs(subTile.bounds.xMax - (bounds.xMin + 261 * PIXEL)) < 1e-9
        Math.abs(subTile.bounds.yMin - (bounds.yMax - 150 * PIXEL)) < 1e-9

        cleanup:
        subTile?.path?.toFile()?.delete()
    }

    def "sub tile contains the same elevation data as the source tile"() {
        given:
        def bounds = new TileBounds(57.0, -6.0, 55.0, -4.0)

        when:
        def subTile = sourceTile.subTile(bounds)

        then:
        subTile.bounds.contains(new GeoPoint(56.0, -5.0))
        // Compare a grid of sample points, including land and sea (no data) pixels
        def points = (0..<20).collectMany { y ->
            (0..<20).collect { x -> new GeoPoint(56.95 - y * 0.1, -5.95 + x * 0.1) }
        }
        points.every { point ->
            subTile.lookupElevation(point).value.intValue() == sourceTile.lookupElevation(point).value.intValue()
        }
        QuantityHelper.assertDelta(Metre(159), subTile.lookupElevation(new GeoPoint(56.0, -5.0)), 0.00001)

        cleanup:
        subTile?.path?.toFile()?.delete()
    }

    def "sub tile keeps the no data value"() {
        given: "an area in the north sea, which has no data"
        def bounds = new TileBounds(60.0, -5.5, 59.5, -4.5)

        when:
        def subTile = sourceTile.subTile(bounds)

        then:
        QuantityHelper.assertDelta(Metre(0), subTile.lookupElevation(new GeoPoint(59.8, -5.0)), 0.00001)

        and: "the GDAL_NODATA tag is written, so the file stays compatible with GDAL"
        new String(Files.readAllBytes(subTile.path), "US-ASCII").contains("-32768")

        cleanup:
        subTile?.path?.toFile()?.delete()
    }

    def "sub tile of a sub tile"() {
        given:
        def subTile = sourceTile.subTile(new TileBounds(57.0, -6.0, 55.0, -4.0))

        when:
        def subSubTile = subTile.subTile(new TileBounds(56.5, -5.5, 55.5, -4.5))

        then:
        QuantityHelper.assertDelta(Metre(159), subSubTile.lookupElevation(new GeoPoint(56.0, -5.0)), 0.00001)

        cleanup:
        subTile?.path?.toFile()?.delete()
        subSubTile?.path?.toFile()?.delete()
    }

    def "sub tile outside of the tile bounds"() {
        when:
        sourceTile.subTile(new TileBounds(70.0, -6.0, 55.0, -4.0))

        then:
        thrown(InvalidSubTileException)
    }

    def "save tile to another location"() {
        given:
        def target = Files.createTempDirectory("geotiff").resolve("copy.tif")

        when:
        def copy = sourceTile.saveTo(target)

        then:
        copy.path == target
        copy.bounds == sourceTile.bounds

        cleanup:
        Files.deleteIfExists(target)
    }
}
