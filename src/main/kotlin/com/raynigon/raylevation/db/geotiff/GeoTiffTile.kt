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
    companion object {
        private val SEA_LEVEL: Quantity<Length> = Metre(0)
        private const val EXPECTED_BANDS = 1

        /**
         * Tolerance used by GDAL (gdal_translate -projWin) to round a window offset to full pixels
         */
        private const val PIXEL_OFFSET_TOLERANCE = 0.001
    }

    private val metadata: Metadata = readMetadata()
    private val width = metadata.width
    private val height = metadata.height
    private val originX = metadata.originX
    private val originY = metadata.originY
    private val pixelSizeX = metadata.pixelSizeX
    private val pixelSizeY = metadata.pixelSizeY
    private val noDataValue = metadata.noDataValue
    private var rasterCache: Array<ShortArray>? = null

    /**
     * Read and validate the structure and georeferencing of the GeoTiff file
     */
    private fun readMetadata(): Metadata =
        withResource { resource ->
            val sampleDimensions = resource.sampleDimensions
            if (sampleDimensions.size != EXPECTED_BANDS) {
                throw IncompatibleTileException(path, "has too many Raster Bands", sampleDimensions.size, EXPECTED_BANDS)
            }
            val gridGeometry = resource.gridGeometry
            if (!isWGS84(gridGeometry.coordinateReferenceSystem)) {
                throw IncompatibleTileException(
                    "Tile ($path) has an incompatible coordinate reference system: " +
                        "${gridGeometry.coordinateReferenceSystem.name}. Expected: WGS 84",
                )
            }
            // Reading a single pixel is enough to determine the data type of the band
            val firstPixel = GridExtent(null, longArrayOf(0, 0), longArrayOf(0, 0), true)
            val dataType = readRaster(resource, firstPixel).dataType
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
            Metadata(
                width = Math.toIntExact(gridGeometry.extent.getSize(0)),
                height = Math.toIntExact(gridGeometry.extent.getSize(1)),
                originX = matrix.getElement(0, 2),
                originY = matrix.getElement(1, 2),
                pixelSizeX = matrix.getElement(0, 0),
                pixelSizeY = matrix.getElement(1, 1),
                // Apache SIS exposes the GDAL_NODATA value as background of the sample dimension
                noDataValue =
                    sampleDimensions[0]
                        .background
                        .map { it.toShort() }
                        .orElse(null),
            )
        }

    val bounds: TileBounds by lazy {
        TileBounds(originY, originX, originY + height * pixelSizeY, originX + width * pixelSizeX)
    }

    private val raster: Array<ShortArray>
        get() {
            val tmpRasterCache = rasterCache
            if (tmpRasterCache != null) return tmpRasterCache
            val result = readWindow(0, 0, width, height)
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
        val xpix = min(((point.longitude - originX) / pixelSizeX).toInt(), width - 1)
        val ylin = min(((point.latitude - originY) / pixelSizeY).toInt(), height - 1)

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
        // Pixel window as computed by gdal_translate for -projWin with nearest neighbour resampling
        val xOff = floor((bounds.xMin - originX) / pixelSizeX + PIXEL_OFFSET_TOLERANCE).toInt()
        val yOff = floor((bounds.yMax - originY) / pixelSizeY + PIXEL_OFFSET_TOLERANCE).toInt()
        val xSize = floor((bounds.xMax - bounds.xMin) / pixelSizeX + 0.5).toInt()
        val ySize = floor((bounds.yMin - bounds.yMax) / pixelSizeY + 0.5).toInt()
        val x0 = max(xOff, 0)
        val y0 = max(yOff, 0)
        val x1 = min(xOff + xSize, width)
        val y1 = min(yOff + ySize, height)
        if (x1 <= x0 || y1 <= y0) {
            throw InvalidSubTileException(path, this.bounds, bounds)
        }

        val target = File.createTempFile("geotiffsubtile", ".tif")
        target.deleteOnExit()
        GeoTiffWriter.write(
            target.toPath(),
            readWindow(x0, y0, x1 - x0, y1 - y0),
            originX + x0 * pixelSizeX,
            originY + y0 * pixelSizeY,
            pixelSizeX,
            -pixelSizeY,
            noDataValue,
        )
        return GeoTiffTile(target.toPath())
    }

    /**
     * Checks for geographic WGS84 with longitude first, as defined by GeoTIFF.
     * No reprojection is supported, the pixel math in [lookupElevation] relies on this axis order.
     */
    private fun isWGS84(crs: CoordinateReferenceSystem?): Boolean =
        crs != null && Utilities.equalsIgnoreMetadata(CommonCRS.WGS84.normalizedGeographic(), crs)

    /**
     * Read the given pixel window, row by row
     */
    private fun readWindow(
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
            val raster = readRaster(resource, extent)
            Array(windowHeight) { row ->
                val line = ShortArray(windowWidth)
                raster.data.getDataElements(raster.offsetX, raster.offsetY + row, windowWidth, 1, line)
                line
            }
        }

    /**
     * Read the raster covering at least the given extent.
     * Apache SIS may read a slightly larger area (aligned to the strips of the file),
     * the offsets point to the requested area in the returned raster.
     */
    private fun readRaster(
        resource: GridCoverageResource,
        extent: GridExtent,
    ): WindowRaster {
        val subGrid =
            resource.gridGeometry
                .derive()
                .subgrid(extent)
                .build()
        val coverage = resource.read(subGrid).forConvertedValues(false)
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
        val width: Int,
        val height: Int,
        val originX: Double,
        val originY: Double,
        val pixelSizeX: Double,
        val pixelSizeY: Double,
        val noDataValue: Short?,
    )

    private class WindowRaster(
        val data: Raster,
        val offsetX: Int,
        val offsetY: Int,
        val dataType: Int,
    )
}
