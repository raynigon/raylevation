package com.raynigon.raylevation.db.geotiff

import com.raynigon.raylevation.db.tile.TileBounds

/**
 * Size and georeferencing of a north up raster in geographic WGS84 coordinates.
 *
 * @param width       Number of pixels per row
 * @param height      Number of rows
 * @param originX     Longitude of the upper left corner of the upper left pixel
 * @param originY     Latitude of the upper left corner of the upper left pixel
 * @param pixelSizeX  Width of one pixel in degrees, positive (west to east)
 * @param pixelSizeY  Height of one pixel in degrees, negative (north to south)
 */
internal data class PixelGrid(
    val width: Int,
    val height: Int,
    val originX: Double,
    val originY: Double,
    val pixelSizeX: Double,
    val pixelSizeY: Double,
) {
    /**
     * The area covered by this grid, computed in the same way as GDAL does from the geo transform
     */
    val bounds: TileBounds
        get() = TileBounds(originY, originX, originY + height * pixelSizeY, originX + width * pixelSizeX)

    /**
     * The grid of a pixel window inside this grid
     *
     * @param x               Column of the upper left pixel of the window
     * @param y               Row of the upper left pixel of the window
     * @param windowWidth     Number of pixels per row of the window
     * @param windowHeight    Number of rows of the window
     * @return The [PixelGrid] of the window
     */
    fun window(
        x: Int,
        y: Int,
        windowWidth: Int,
        windowHeight: Int,
    ): PixelGrid =
        copy(
            width = windowWidth,
            height = windowHeight,
            originX = originX + x * pixelSizeX,
            originY = originY + y * pixelSizeY,
        )
}
