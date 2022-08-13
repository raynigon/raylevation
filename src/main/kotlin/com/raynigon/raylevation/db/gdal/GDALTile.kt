package com.raynigon.raylevation.db.gdal

import com.raynigon.raylevation.db.exception.GDALException
import com.raynigon.raylevation.db.exception.IncompatibleTileException
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre
import org.gdal.gdal.Dataset
import org.gdal.gdal.TranslateOptions
import org.gdal.gdal.gdal
import org.gdal.gdalconst.gdalconstConstants.GDT_Int16
import org.gdal.osr.CoordinateTransformation
import org.gdal.osr.SpatialReference
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Vector
import javax.measure.Quantity
import javax.measure.quantity.Length
import kotlin.io.path.absolutePathString

class GDALTile(val path: Path) {

    companion object {
        private const val EPSG_WGS84 = 4326
        private val SEA_LEVEL: Quantity<Length> = Metre(0)
        private const val ELEVATION_BAND = 1
    }

    private val dataset: Dataset = gdal.Open(path.absolutePathString()) ?: throw GDALException()
    private val coordinateTransform: CoordinateTransformation
    private val geoTransform: DoubleArray
    private val geoTransformInv: DoubleArray
    private var rasterCache: Array<ShortArray>? = null

    /**
     * Init the transformations, etc.
     * Logic transferred from https://stackoverflow.com/questions/13439357/extract-point-from-raster-in-gdal
     */
    init {
        if (dataset.GetRasterCount() != 1)
            throw IncompatibleTileException(path, "has too many Raster Bands", dataset.GetRasterCount(), 1)
        val bandDataType = dataset.GetRasterBand(ELEVATION_BAND).dataType
        if (bandDataType != GDT_Int16)
            throw IncompatibleTileException(path, "has an incompatible data type on Band 1", bandDataType, GDT_Int16)

        val spatialReferenceRaster = SpatialReference(dataset.GetProjection())
        val spatialReference = SpatialReference()
        spatialReference.ImportFromEPSG(EPSG_WGS84)

        // coordinate transformation
        coordinateTransform = CoordinateTransformation(spatialReference, spatialReferenceRaster)
        geoTransform = dataset.GetGeoTransform()
        val dev = geoTransform[1] * geoTransform[5] - geoTransform[2] * geoTransform[4]
        geoTransformInv = doubleArrayOf(
            geoTransform[0],
            geoTransform[5] / dev,
            -geoTransform[2] / dev,
            geoTransform[3],
            -geoTransform[4] / dev,
            geoTransform[1] / dev
        )
    }

    val bounds: TileBounds by lazy {
        val ulx = this.geoTransform[0]
        val xRes = this.geoTransform[1]
        val uly = this.geoTransform[3]
        val yRes = this.geoTransform[5]
        val lrx = ulx + (dataset.GetRasterXSize() * xRes)
        val lry = uly + (dataset.GetRasterYSize() * yRes)
        TileBounds(uly, ulx, lry, lrx)
    }

    private val raster: Array<ShortArray>
        get() {
            val tmpRasterCache = rasterCache
            if (tmpRasterCache != null) return tmpRasterCache
            val band1 = dataset.GetRasterBand(ELEVATION_BAND)
            val result = Array(band1.ySize) { ShortArray(0) }
            for (i in 0 until band1.ySize) {
                val line = ShortArray(band1.xSize)
                band1.ReadRaster(0, i, band1.xSize, 1, line)
                result[i] = line
            }
            rasterCache = result
            return result
        }

    private val noDataValue: Short
        get() {
            val array = Array(1) { 0.0 }
            dataset.GetRasterBand(1).GetNoDataValue(array)
            return array[0].toInt().toShort()
        }

    fun lookupElevation(point: GeoPoint): Quantity<Length> {
        // get coordinate of the raster
        val result = coordinateTransform.TransformPoint(point.longitude, point.latitude, 0.0)
        val xGeo = result[0]
        val yGeo = result[1]

        // convert it to pixel / line on band
        val varU = xGeo - geoTransformInv[0]
        val varV = yGeo - geoTransformInv[3]
        /* FIXME The conversion to int is probably a bad idea,
         *   something like centering on a cell should be used.
         *   Currently I have no idea how this could work
         */
        val xpix = (geoTransformInv[1] * varU + geoTransformInv[2] * varV).toInt()
        val ylin = (geoTransformInv[4] * varU + geoTransformInv[5] * varV).toInt()

        // look the value up
        val elevation = raster[ylin][xpix]

        // if no data exists we know the point is on sea level
        if (elevation == noDataValue)
            return SEA_LEVEL
        return Metre(elevation)
    }

    /**
     *  Save the current GDAL Tile at the destination location
     *  @param destination     the path on which the new GDAL Tile should be stored
     *  @return the new [GDALTile] which was created from the current GDAL Tile
     */
    fun saveTo(destination: Path): GDALTile {
        Files.copy(path, destination)
        return GDALTile(destination)
    }

    fun subTile(bounds: TileBounds): GDALTile {
        if (!this.bounds.contains(bounds)) {
            TODO("Raise Exception which indicates the boundaries do not match")
        }
        val target = File.createTempFile("gdalsubtile", ".tif")
        target.deleteOnExit()
        val options = createTranslateOptions(bounds)
        gdal.Translate(target.absolutePath, dataset, options)
        return GDALTile(target.toPath())
    }

    private fun createTranslateOptions(bounds: TileBounds) = TranslateOptions(
        Vector<Any>(
            listOf(
                "-of", "GTiff",
                "-projWin",
                bounds.xMin.toString(),
                bounds.yMax.toString(),
                bounds.xMax.toString(),
                bounds.yMin.toString()
            )
        )
    )
}
