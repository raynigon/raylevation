package com.raynigon.raylevation.db.geotiff

import com.raynigon.raylevation.db.exception.GeoTiffException
import com.raynigon.raylevation.db.exception.IncompatibleTileException
import com.raynigon.raylevation.db.exception.InvalidSubTileException
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre
import org.apache.sis.coverage.grid.GridExtent
import org.apache.sis.coverage.grid.PixelInCell
import org.apache.sis.referencing.CommonCRS
import org.apache.sis.referencing.operation.transform.LinearTransform
import org.apache.sis.storage.Aggregate
import org.apache.sis.storage.DataStore
import org.apache.sis.storage.DataStoreException
import org.apache.sis.storage.DataStores
import org.apache.sis.storage.GridCoverageResource
import org.apache.sis.storage.StorageConnector
import org.apache.sis.util.Utilities
import org.opengis.referencing.crs.CoordinateReferenceSystem
import java.awt.Rectangle
import java.awt.image.DataBuffer
import java.awt.image.Raster
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.measure.Quantity
import javax.measure.quantity.Length
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val SEA_LEVEL: Quantity<Length> = Metre(0)
private const val EXPECTED_BANDS = 1
private const val GDAL_PIXEL_OFFSET_TOLERANCE = 0.001
private const val ROUND_TO_NEAREST = 0.5

/**
 * The GeoTiffTile represents a GeoTiff file with exactly one band of signed 16 bit integers,
 * which provides the elevation data for the covered area in geographic WGS84 coordinates.
 *
 * The file is read with Apache SIS and is only opened while data is read from it.
 *
 * @param path    The path on the file system on which the GeoTiff file is present
 */
class GeoTiffTile(
    val path: Path,
) {
    private val metadata: Metadata = readAndValidateMetadata()
    private val grid: PixelGrid = metadata.grid
    private val noDataValue: Short? = metadata.noDataValue
    private var rasterCache: Array<ShortArray>? = null

    val bounds: TileBounds by lazy { grid.bounds }

    private val raster: Array<ShortArray>
        get() {
            val tmpRasterCache = rasterCache
            if (tmpRasterCache != null) return tmpRasterCache
            val result = readPixelWindow(0, 0, grid.width, grid.height)
            rasterCache = result
            return result
        }

    /**
     * Looks up the elevation on a given [GeoPoint] in this tile.
     * If the [GeoPoint] is not in the bounds of this tile, the behaviour is undefined.
     * Make sure to check that the given [GeoPoint] is in bounds of this tile.
     *
     * @param point   The [GeoPoint] for which the elevation should be looked up
     * @return The elevation as [Length] [Quantity], which can be converted to any [Length] Unit
     */
    fun lookupElevation(point: GeoPoint): Quantity<Length> {
        // Convert the coordinate to pixel / line, the tile is always in WGS84 and north up.
        // Clamp to the raster, because points on the lower/right border belong to the last pixel.
        val xpix = min(((point.longitude - grid.originX) / grid.pixelSizeX).toInt(), grid.width - 1)
        val ylin = min(((point.latitude - grid.originY) / grid.pixelSizeY).toInt(), grid.height - 1)

        val elevation = raster[ylin][xpix]

        // if no data exists we know the point is on sea level
        if (elevation == noDataValue) {
            return SEA_LEVEL
        }
        return Metre(elevation)
    }

    /**
     *  Save the current tile at the destination location
     *  @param destination     the path on which the new tile should be stored
     *  @return the new [GeoTiffTile] which was created from the current tile
     */
    fun saveTo(destination: Path): GeoTiffTile {
        Files.copy(path, destination)
        return GeoTiffTile(destination)
    }

    /**
     * Create a new [GeoTiffTile] covering the area defined by the given [TileBounds].
     * The area is aligned to full pixels in the same way as `gdal_translate -projWin` does.
     *
     * @param bounds  The [TileBounds] defining the sub area covered by this tile
     * @return The new [GeoTiffTile] stored in a temporary file, which contains the defined area
     */
    fun subTile(bounds: TileBounds): GeoTiffTile {
        if (!this.bounds.contains(bounds)) {
            throw InvalidSubTileException(path, this.bounds, bounds)
        }
        // Pixel window as computed by gdal_translate for -projWin with nearest neighbour resampling:
        // the offset is rounded down with a small tolerance, the size is rounded to the nearest pixel
        val xOff = floor((bounds.xMin - grid.originX) / grid.pixelSizeX + GDAL_PIXEL_OFFSET_TOLERANCE).toInt()
        val yOff = floor((bounds.yMax - grid.originY) / grid.pixelSizeY + GDAL_PIXEL_OFFSET_TOLERANCE).toInt()
        val xSize = floor((bounds.xMax - bounds.xMin) / grid.pixelSizeX + ROUND_TO_NEAREST).toInt()
        val ySize = floor((bounds.yMin - bounds.yMax) / grid.pixelSizeY + ROUND_TO_NEAREST).toInt()
        val x0 = max(xOff, 0)
        val y0 = max(yOff, 0)
        val x1 = min(xOff + xSize, grid.width)
        val y1 = min(yOff + ySize, grid.height)
        if (x1 <= x0 || y1 <= y0) {
            throw InvalidSubTileException(path, this.bounds, bounds)
        }

        val target = File.createTempFile("geotiffsubtile", ".tif")
        target.deleteOnExit()
        GeoTiffWriter.write(
            target.toPath(),
            readPixelWindow(x0, y0, x1 - x0, y1 - y0),
            grid.window(x0, y0, x1 - x0, y1 - y0),
            noDataValue,
        )
        return GeoTiffTile(target.toPath())
    }

    private fun readAndValidateMetadata(): Metadata =
        withResource { resource ->
            val sampleDimensions = resource.sampleDimensions
            if (sampleDimensions.size != EXPECTED_BANDS) {
                throw IncompatibleTileException(path, "has too many Raster Bands", sampleDimensions.size, EXPECTED_BANDS)
            }
            val gridGeometry = resource.gridGeometry
            // No reprojection is supported, the pixel math in lookupElevation relies on longitude first
            if (!isLongitudeFirstWGS84(gridGeometry.coordinateReferenceSystem)) {
                throw IncompatibleTileException(
                    "Tile ($path) has an incompatible coordinate reference system: " +
                        "${gridGeometry.coordinateReferenceSystem.name}. Expected: WGS 84",
                )
            }
            // Reading a single pixel is enough to determine the data type of the band
            val firstPixel = GridExtent(null, longArrayOf(0, 0), longArrayOf(0, 0), true)
            val dataType = readRasterCovering(resource, firstPixel).dataType
            if (dataType != DataBuffer.TYPE_SHORT) {
                throw IncompatibleTileException(path, "has an incompatible data type on Band 1", dataType, DataBuffer.TYPE_SHORT)
            }
            val gridToCRS =
                gridGeometry.getGridToCRS(PixelInCell.CELL_CORNER) as? LinearTransform
                    ?: throw IncompatibleTileException("Tile ($path) has a non linear grid to CRS transformation")
            val matrix = gridToCRS.matrix
            if (matrix.getElement(0, 1) != 0.0 || matrix.getElement(1, 0) != 0.0) {
                throw IncompatibleTileException("Tile ($path) is rotated, only north up rasters are supported")
            }
            val grid =
                PixelGrid(
                    width = Math.toIntExact(gridGeometry.extent.getSize(0)),
                    height = Math.toIntExact(gridGeometry.extent.getSize(1)),
                    originX = matrix.getElement(0, 2),
                    originY = matrix.getElement(1, 2),
                    pixelSizeX = matrix.getElement(0, 0),
                    pixelSizeY = matrix.getElement(1, 1),
                )
            // Apache SIS exposes the GDAL_NODATA value as background of the sample dimension
            val noDataValue =
                sampleDimensions[0]
                    .background
                    .map { it.toShort() }
                    .orElse(null)
            Metadata(grid, noDataValue)
        }

    private fun isLongitudeFirstWGS84(crs: CoordinateReferenceSystem?): Boolean =
        crs != null && Utilities.equalsIgnoreMetadata(CommonCRS.WGS84.normalizedGeographic(), crs)

    private fun readPixelWindow(
        x: Int,
        y: Int,
        windowWidth: Int,
        windowHeight: Int,
    ): Array<ShortArray> =
        withResource { resource ->
            val extent =
                GridExtent(
                    null,
                    longArrayOf(x.toLong(), y.toLong()),
                    longArrayOf((x + windowWidth - 1).toLong(), (y + windowHeight - 1).toLong()),
                    true,
                )
            val raster = readRasterCovering(resource, extent)
            Array(windowHeight) { row ->
                val line = ShortArray(windowWidth)
                raster.data.getDataElements(raster.offsetX, raster.offsetY + row, windowWidth, 1, line)
                line
            }
        }

    private fun readRasterCovering(
        resource: GridCoverageResource,
        extent: GridExtent,
    ): WindowRaster {
        val subGrid =
            resource.gridGeometry
                .derive()
                .subgrid(extent)
                .build()
        val coverage = resource.read(subGrid).forConvertedValues(false)
        // Apache SIS may read a slightly larger area (aligned to the strips of the file),
        // the offsets point to the requested area in the returned raster
        val readExtent = coverage.gridGeometry.extent
        val image = coverage.render(null)
        val offsetX = image.minX + Math.toIntExact(extent.getLow(0) - readExtent.getLow(0))
        val offsetY = image.minY + Math.toIntExact(extent.getLow(1) - readExtent.getLow(1))
        val rectangle =
            Rectangle(
                offsetX,
                offsetY,
                Math.toIntExact(extent.getSize(0)),
                Math.toIntExact(extent.getSize(1)),
            )
        return WindowRaster(image.getData(rectangle), offsetX, offsetY, image.sampleModel.dataType)
    }

    private fun <T> withResource(action: (GridCoverageResource) -> T): T =
        try {
            openStore().use { store -> action(findResource(store)) }
        } catch (exception: DataStoreException) {
            throw GeoTiffException(path, exception)
        } catch (exception: IOException) {
            throw GeoTiffException(path, exception)
        }

    private fun openStore(): DataStore {
        if (!Files.isRegularFile(path)) {
            throw GeoTiffException(path, "No such file or directory")
        }
        return DataStores.open(StorageConnector(path))
    }

    private fun findResource(store: DataStore): GridCoverageResource =
        when (store) {
            is GridCoverageResource -> store
            is Aggregate ->
                store.components().filterIsInstance<GridCoverageResource>().singleOrNull()
                    ?: throw IncompatibleTileException(path, "has an incompatible number of images", store.components().size, 1)
            else -> throw GeoTiffException(path, "Unsupported data store ${store.javaClass.simpleName}")
        }

    private class Metadata(
        val grid: PixelGrid,
        val noDataValue: Short?,
    )

    private class WindowRaster(
        val data: Raster,
        val offsetX: Int,
        val offsetY: Int,
        val dataType: Int,
    )
}
