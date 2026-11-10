package com.raynigon.raylevation.db.geotiff

import com.raynigon.raylevation.db.tile.TileBounds
import spock.lang.Specification

class PixelGridSpec extends Specification {

    PixelGrid grid = new PixelGrid(40, 20, -10.0d, 60.0d, 0.25d, -0.5d)

    def "bounds are computed from origin, size and pixel size"() {
        expect:
        grid.bounds == new TileBounds(60.0d, -10.0d, 50.0d, 0.0d)
    }

    def "window shifts the origin by full pixels and keeps the pixel size"() {
        when:
        def window = grid.window(4, 2, 8, 6)

        then:
        window.width == 8
        window.height == 6
        window.originX == -9.0d
        window.originY == 59.0d
        window.pixelSizeX == grid.pixelSizeX
        window.pixelSizeY == grid.pixelSizeY
        window.bounds == new TileBounds(59.0d, -9.0d, 56.0d, -7.0d)
    }

    def "window covering the whole grid equals the grid"() {
        expect:
        grid.window(0, 0, grid.width, grid.height) == grid
    }

    def "window of the last pixel ends at the lower right corner of the grid"() {
        when:
        def window = grid.window(grid.width - 1, grid.height - 1, 1, 1)

        then:
        window.bounds.xMax == grid.bounds.xMax
        window.bounds.yMin == grid.bounds.yMin
    }
}
