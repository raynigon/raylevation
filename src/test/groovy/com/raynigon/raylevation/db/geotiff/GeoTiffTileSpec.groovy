package com.raynigon.raylevation.db.geotiff

import static com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre

import com.raynigon.raylevation.db.exception.GeoTiffException
import com.raynigon.raylevation.db.exception.IncompatibleTileException
import com.raynigon.raylevation.db.exception.InvalidSubTileException
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.helper.QuantityHelper
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import javax.measure.Quantity
import javax.measure.quantity.Length
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

import static com.raynigon.raylevation.db.geotiff.TiffTestFiles.TAG_GDAL_NODATA

class GeoTiffTileSpec extends Specification {

    static final String TILE_PATH = "src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"

    // Pixel size of the test tile in degrees (7.5 arc seconds)
    static final double PIXEL = 0.00208333333

    @TempDir
    Path tempDir

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

    def "load geotiff with incompatible coordinate reference system"() {
        when:
        new GeoTiffTile(Path.of("src/test/resources/geotiff/cea.tif"))

        then:
        def exception = thrown(IncompatibleTileException)
        exception.message.contains("incompatible coordinate reference system")
    }

    def "load geotiff with #description"() {
        given:
        def file = TiffTestFiles.write(tempDir.resolve("incompatible.tif"), images)

        when:
        new GeoTiffTile(file)

        then:
        def exception = thrown(IncompatibleTileException)
        exception.message.contains(message)

        where:
        description         | images                                                        | message
        "multiple bands"    | [new TiffTestFiles.Image(samplesPerPixel: 2)]                 | "has too many Raster Bands"
        "float32 data type" | [new TiffTestFiles.Image(bitsPerSample: 32, sampleFormat: 3)] | "has an incompatible data type"
        "int32 data type"   | [new TiffTestFiles.Image(bitsPerSample: 32, sampleFormat: 2)] | "has an incompatible data type"
        "rotated raster"    | [new TiffTestFiles.Image(modelTransformation: rotated(0.01d, 0.0d))] | "is rotated"
        "sheared raster"    | [new TiffTestFiles.Image(modelTransformation: rotated(0.0d, 0.01d))] | "is rotated"
        "multiple images"   | [new TiffTestFiles.Image(), new TiffTestFiles.Image()]        | "has an incompatible number of images"
    }

    def "file of another data store type is rejected"() {
        given: "a WKT file, which Apache SIS opens with its WKT store"
        def file = tempDir.resolve("wgs84.prj")
        Files.writeString(file, 'GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],' +
                'PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]')

        when:
        new GeoTiffTile(file)

        then:
        def exception = thrown(GeoTiffException)
        exception.message.contains("Unsupported data store")
    }

    def "test file builder produces a valid tile for the default image"() {
        given: "guards the incompatible tile cases above against false positives"
        def file = TiffTestFiles.write(tempDir.resolve("valid.tif"), new TiffTestFiles.Image())

        when:
        def tile = new GeoTiffTile(file)

        then:
        tile.bounds == new TileBounds(56.0d, -5.0d, 54.0d, -3.0d)
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

    def "lookup twice uses cached raster and the file is not needed any more"() {
        given:
        def file = tempDir.resolve("tile.tif")
        Files.copy(Path.of(TILE_PATH), file)
        def tile = new GeoTiffTile(file)

        when:
        Quantity<Length> result0 = tile.lookupElevation(new GeoPoint(56.0, -5.0))
        Files.delete(file)
        Quantity<Length> result1 = tile.lookupElevation(new GeoPoint(56.0, -5.0))

        then:
        QuantityHelper.assertDelta(Metre(159), result0, 0.00001)
        QuantityHelper.assertDelta(Metre(159), result1, 0.00001)
    }

    def "raster is only read on the first lookup"() {
        given:
        def file = tempDir.resolve("tile.tif")
        Files.copy(Path.of(TILE_PATH), file)
        def tile = new GeoTiffTile(file)
        Files.delete(file)

        when:
        tile.lookupElevation(new GeoPoint(56.0, -5.0))

        then:
        thrown(GeoTiffException)
    }

    def "tile without no data value returns the raw value"() {
        given:
        def file = tempDir.resolve("no-data.tif")
        def grid = new PixelGrid(2, 1, 0.0d, 1.0d, 1.0d, -1.0d)
        GeoTiffWriter.INSTANCE.write(file, [[-32768, 12] as short[]] as short[][], grid, null)
        def tile = new GeoTiffTile(file)

        expect:
        tile.lookupElevation(new GeoPoint(0.5d, 0.5d)).value.intValue() == -32768
        tile.lookupElevation(new GeoPoint(0.5d, 1.5d)).value.intValue() == 12
    }

    def "sub tile of a tile without no data value has no GDAL_NODATA tag"() {
        given:
        def file = tempDir.resolve("no-data.tif")
        def grid = new PixelGrid(4, 4, 0.0d, 4.0d, 1.0d, -1.0d)
        def raster = (0..<4).collect { y -> (0..<4).collect { x -> (y * 4 + x) as short } as short[] } as short[][]
        GeoTiffWriter.INSTANCE.write(file, raster, grid, null)

        when:
        def subTile = new GeoTiffTile(file).subTile(new TileBounds(3.0d, 1.0d, 1.0d, 3.0d))

        then:
        !TiffTestFiles.readTags(subTile.path).containsKey(TAG_GDAL_NODATA)
        subTile.bounds == new TileBounds(3.0d, 1.0d, 1.0d, 3.0d)
        subTile.lookupElevation(new GeoPoint(2.5d, 1.5d)).value.intValue() == 5
        subTile.lookupElevation(new GeoPoint(1.5d, 2.5d)).value.intValue() == 10

        cleanup:
        subTile?.path?.toFile()?.delete()
    }

    def "lookup on the lower right corner"() {
        given:
        def bounds = sourceTile.bounds

        when:
        def result = sourceTile.lookupElevation(new GeoPoint(bounds.yMin, bounds.xMax))

        then: "the point belongs to the last pixel"
        result == sourceTile.lookupElevation(new GeoPoint(bounds.yMin + PIXEL / 2, bounds.xMax - PIXEL / 2))
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
        TiffTestFiles.readTags(subTile.path)[TAG_GDAL_NODATA] == "-32768"

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

    def "sub tile outside of the tile bounds (#side)"() {
        given:
        def b = sourceTile.bounds
        def requested = new TileBounds(b.yMax + dyMax, b.xMin + dxMin, b.yMin + dyMin, b.xMax + dxMax)

        when:
        sourceTile.subTile(requested)

        then:
        def exception = thrown(InvalidSubTileException)
        exception.bounds == sourceTile.bounds
        exception.requested == requested

        where:
        side    | dyMax | dxMin | dyMin | dxMax
        "north" | 0.1d  | 0.0d  | 0.0d  | 0.0d
        "west"  | 0.0d  | -0.1d | 0.0d  | 0.0d
        "south" | 0.0d  | 0.0d  | -0.1d | 0.0d
        "east"  | 0.0d  | 0.0d  | 0.0d  | 0.1d
    }

    def "sub tile smaller than half a pixel is rejected (#direction)"() {
        given:
        def b = sourceTile.bounds
        def requested = new TileBounds(b.yMax - 10 * PIXEL, b.xMin + 10 * PIXEL, b.yMax - (10 + height) * PIXEL, b.xMin + (10 + width) * PIXEL)

        when:
        sourceTile.subTile(requested)

        then:
        thrown(InvalidSubTileException)

        where:
        direction    | width | height
        "vertical"   | 10.0d | 0.3d
        "horizontal" | 0.3d  | 10.0d
    }

    def "sub tile covering the whole tile equals the tile"() {
        when:
        def subTile = sourceTile.subTile(sourceTile.bounds)

        then:
        subTile.bounds == sourceTile.bounds
        def b = sourceTile.bounds
        (0..<10).every { y ->
            (0..<10).every { x ->
                def point = new GeoPoint(b.yMax - (y + 0.5) * (b.yMax - b.yMin) / 10, b.xMin + (x + 0.5) * (b.xMax - b.xMin) / 10)
                subTile.lookupElevation(point) == sourceTile.lookupElevation(point)
            }
        }

        cleanup:
        subTile?.path?.toFile()?.delete()
    }

    def "sub tile at the lower right corner ends exactly at the tile edge"() {
        given: "a window of 10.6 pixels, which is rounded to 11 pixels"
        def b = sourceTile.bounds
        def requested = new TileBounds(b.yMin + 10.6 * PIXEL, b.xMax - 10.6 * PIXEL, b.yMin, b.xMax)

        when:
        def subTile = sourceTile.subTile(requested)

        then:
        Math.abs(subTile.bounds.xMax - b.xMax) < 1e-9
        Math.abs(subTile.bounds.yMin - b.yMin) < 1e-9
        subTile.lookupElevation(new GeoPoint(b.yMin, b.xMax)) == sourceTile.lookupElevation(new GeoPoint(b.yMin, b.xMax))

        cleanup:
        subTile?.path?.toFile()?.delete()
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

    def "save tile does not overwrite an existing file"() {
        given:
        def target = tempDir.resolve("existing.tif")
        Files.writeString(target, "existing")

        when:
        sourceTile.saveTo(target)

        then:
        thrown(FileAlreadyExistsException)
        Files.readString(target) == "existing"
    }

    private static double[] rotated(double shearX, double shearY) {
        // 0.5 degree pixels with small rotation / shear terms
        return [0.5d, shearX, 0.0d, -5.0d,
                shearY, -0.5d, 0.0d, 56.0d,
                0.0d, 0.0d, 0.0d, 0.0d,
                0.0d, 0.0d, 0.0d, 1.0d] as double[]
    }
}
