package com.raynigon.raylevation.db.geotiff

import com.raynigon.raylevation.infrastructure.model.GeoPoint
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static com.raynigon.raylevation.db.geotiff.TiffTestFiles.TAG_GDAL_NODATA
import static com.raynigon.raylevation.db.geotiff.TiffTestFiles.TAG_GEO_KEY_DIRECTORY
import static com.raynigon.raylevation.db.geotiff.TiffTestFiles.TAG_MODEL_PIXEL_SCALE
import static com.raynigon.raylevation.db.geotiff.TiffTestFiles.TAG_MODEL_TIEPOINT

class GeoTiffWriterSpec extends Specification {

    @TempDir
    Path tempDir

    // odd width, so rows are not word aligned
    PixelGrid grid = new PixelGrid(3, 5, -5.0d, 56.0d, 0.25d, -0.125d)

    short[][] raster = [
            [Short.MIN_VALUE, Short.MAX_VALUE, 0],
            [-1, 1, -2],
            [100, -100, 159],
            [32000, -32000, 7],
            [Short.MIN_VALUE, Short.MIN_VALUE, 42],
    ] as short[][]

    def "written tile can be read again with identical values and bounds (noData=#noData)"() {
        given:
        def target = tempDir.resolve("tile.tif")

        when:
        GeoTiffWriter.INSTANCE.write(target, raster, grid, noData)
        def tile = new GeoTiffTile(target)

        then:
        tile.bounds == grid.bounds
        (0..<grid.height).every { y ->
            (0..<grid.width).every { x ->
                short value = raster[y][x]
                def expected = value == noData ? 0 : value
                tile.lookupElevation(pixelCenter(x, y)).value.intValue() == expected
            }
        }

        where:
        noData << [Short.MIN_VALUE, null]
    }

    def "without no data value the minimum value is returned as elevation"() {
        given:
        def target = tempDir.resolve("tile.tif")
        GeoTiffWriter.INSTANCE.write(target, raster, grid, null)

        expect:
        new GeoTiffTile(target).lookupElevation(pixelCenter(0, 0)).value.intValue() == Short.MIN_VALUE
    }

    def "geo tags are written for the grid"() {
        given:
        def target = tempDir.resolve("tile.tif")

        when:
        GeoTiffWriter.INSTANCE.write(target, raster, grid, (short) -32768)
        def tags = TiffTestFiles.readTags(target)

        then:
        tags[256] == [3L]
        tags[257] == [5L]
        tags[TAG_MODEL_PIXEL_SCALE] == [0.25d, 0.125d, 0.0d]
        tags[TAG_MODEL_TIEPOINT] == [0.0d, 0.0d, 0.0d, -5.0d, 56.0d, 0.0d]
        // GeographicTypeGeoKey = EPSG:4326
        tags[TAG_GEO_KEY_DIRECTORY].collate(4).find { it[0] == 2048 } == [2048, 0, 1, 4326]
        tags[TAG_GDAL_NODATA] == "-32768"
    }

    def "no GDAL_NODATA tag is written without no data value"() {
        given:
        def target = tempDir.resolve("tile.tif")

        when:
        GeoTiffWriter.INSTANCE.write(target, raster, grid, null)

        then:
        !TiffTestFiles.readTags(target).containsKey(TAG_GDAL_NODATA)
    }

    def "single pixel tile"() {
        given:
        def target = tempDir.resolve("pixel.tif")
        def pixel = new PixelGrid(1, 1, 10.0d, 50.0d, 1.0d, -1.0d)

        when:
        GeoTiffWriter.INSTANCE.write(target, [[1234] as short[]] as short[][], pixel, null)
        def tile = new GeoTiffTile(target)

        then:
        tile.bounds == pixel.bounds
        tile.lookupElevation(new GeoPoint(49.5d, 10.5d)).value.intValue() == 1234
    }

    def "raster which does not match the grid is rejected"() {
        when:
        GeoTiffWriter.INSTANCE.write(tempDir.resolve("tile.tif"), invalid as short[][], grid, null)

        then:
        thrown(IllegalArgumentException)

        where:
        invalid << [
                [[1, 2, 3]],                                              // too few rows
                [[1, 2], [1, 2], [1, 2], [1, 2], [1, 2]],                 // too few columns
                [[1, 2, 3], [1, 2, 3], [1, 2, 3], [1, 2, 3], [1, 2, 3, 4]], // one row too long
        ]
    }

    def "existing file is overwritten"() {
        given:
        def target = tempDir.resolve("tile.tif")
        Files.write(target, new byte[100_000])

        when:
        GeoTiffWriter.INSTANCE.write(target, raster, grid, null)

        then:
        Files.size(target) < 100_000
        new GeoTiffTile(target).bounds == grid.bounds
    }

    private GeoPoint pixelCenter(int x, int y) {
        return new GeoPoint(
                grid.originY + (y + 0.5d) * grid.pixelSizeY,
                grid.originX + (x + 0.5d) * grid.pixelSizeX,
        )
    }
}
