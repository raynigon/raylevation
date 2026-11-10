package com.raynigon.raylevation.db.geotiff

import java.io.BufferedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes single band, signed 16 bit GeoTIFF files in geographic WGS84 coordinates.
 *
 * The produced files use the same layout GDAL used for the tiles of the raylevation database:
 * classic little endian TIFF, uncompressed, one strip per row, georeferenced by
 * `ModelPixelScale` and `ModelTiepoint` and with the no data value stored in the `GDAL_NODATA` tag.
 *
 * Apache SIS provides a GeoTIFF writer as well, but it does not write the `GDAL_NODATA` tag,
 * which is required to distinguish missing data (sea level) from real elevation values.
 */
internal object GeoTiffWriter {
    // TIFF field types
    private const val TYPE_ASCII: Short = 2
    private const val TYPE_SHORT: Short = 3
    private const val TYPE_LONG: Short = 4
    private const val TYPE_DOUBLE: Short = 12

    // TIFF tags
    private const val TAG_IMAGE_WIDTH = 256
    private const val TAG_IMAGE_LENGTH = 257
    private const val TAG_BITS_PER_SAMPLE = 258
    private const val TAG_COMPRESSION = 259
    private const val TAG_PHOTOMETRIC = 262
    private const val TAG_STRIP_OFFSETS = 273
    private const val TAG_SAMPLES_PER_PIXEL = 277
    private const val TAG_ROWS_PER_STRIP = 278
    private const val TAG_STRIP_BYTE_COUNTS = 279
    private const val TAG_PLANAR_CONFIGURATION = 284
    private const val TAG_SAMPLE_FORMAT = 339
    private const val TAG_MODEL_PIXEL_SCALE = 33550
    private const val TAG_MODEL_TIEPOINT = 33922
    private const val TAG_GEO_KEY_DIRECTORY = 34735
    private const val TAG_GEO_ASCII_PARAMS = 34737
    private const val TAG_GDAL_NODATA = 42113

    // GeoKeys for a geographic WGS84 raster with "pixel is area" semantics
    private const val KEY_GT_MODEL_TYPE = 1024
    private const val KEY_GT_RASTER_TYPE = 1025
    private const val KEY_GEOGRAPHIC_TYPE = 2048
    private const val KEY_GEOG_CITATION = 2049
    private const val KEY_GEOG_ANGULAR_UNITS = 2054
    private const val MODEL_TYPE_GEOGRAPHIC = 2
    private const val RASTER_PIXEL_IS_AREA = 1
    private const val EPSG_WGS84 = 4326
    private const val ANGULAR_UNIT_DEGREE = 9102
    private const val WGS84_CITATION = "WGS 84|"

    private const val HEADER_SIZE = 8
    private const val ENTRY_SIZE = 12
    private const val BYTES_PER_SAMPLE = 2

    /**
     * Write the given raster as GeoTIFF file.
     *
     * @param target      The file which should be written, an existing file is overwritten
     * @param raster      The elevation values, row by row from north to south, all rows have the same length
     * @param originX     Longitude of the upper left corner of the upper left pixel
     * @param originY     Latitude of the upper left corner of the upper left pixel
     * @param pixelSizeX  Width of one pixel in degrees, positive
     * @param pixelSizeY  Height of one pixel in degrees, positive
     * @param noData      The value which marks pixels without data, null if all pixels contain data
     */
    fun write(
        target: Path,
        raster: Array<ShortArray>,
        originX: Double,
        originY: Double,
        pixelSizeX: Double,
        pixelSizeY: Double,
        noData: Short?,
    ) {
        require(raster.isNotEmpty() && raster[0].isNotEmpty()) { "Raster must not be empty" }
        val width = raster[0].size
        val height = raster.size
        require(raster.all { it.size == width }) { "All raster rows must have the same length" }

        val geoKeys =
            shortArrayOf(
                // Header: version 1, revision 1.0, number of keys
                1,
                1,
                0,
                5,
                KEY_GT_MODEL_TYPE.toShort(),
                0,
                1,
                MODEL_TYPE_GEOGRAPHIC.toShort(),
                KEY_GT_RASTER_TYPE.toShort(),
                0,
                1,
                RASTER_PIXEL_IS_AREA.toShort(),
                KEY_GEOGRAPHIC_TYPE.toShort(),
                0,
                1,
                EPSG_WGS84.toShort(),
                KEY_GEOG_CITATION.toShort(),
                TAG_GEO_ASCII_PARAMS.toShort(),
                WGS84_CITATION.length.toShort(),
                0,
                KEY_GEOG_ANGULAR_UNITS.toShort(),
                0,
                1,
                ANGULAR_UNIT_DEGREE.toShort(),
            )
        // The strip offsets are known once the layout is computed, the size of the entry does not change
        val stripOffsets = Entry.longs(TAG_STRIP_OFFSETS, IntArray(height))
        val entries =
            mutableListOf(
                Entry.long(TAG_IMAGE_WIDTH, width),
                Entry.long(TAG_IMAGE_LENGTH, height),
                Entry.short(TAG_BITS_PER_SAMPLE, 16),
                Entry.short(TAG_COMPRESSION, 1),
                Entry.short(TAG_PHOTOMETRIC, 1),
                stripOffsets,
                Entry.short(TAG_SAMPLES_PER_PIXEL, 1),
                Entry.long(TAG_ROWS_PER_STRIP, 1),
                Entry.longs(TAG_STRIP_BYTE_COUNTS, IntArray(height) { width * BYTES_PER_SAMPLE }),
                Entry.short(TAG_PLANAR_CONFIGURATION, 1),
                // Sample format 2 = signed integer
                Entry.short(TAG_SAMPLE_FORMAT, 2),
                Entry.doubles(TAG_MODEL_PIXEL_SCALE, doubleArrayOf(pixelSizeX, pixelSizeY, 0.0)),
                Entry.doubles(TAG_MODEL_TIEPOINT, doubleArrayOf(0.0, 0.0, 0.0, originX, originY, 0.0)),
                Entry.shorts(TAG_GEO_KEY_DIRECTORY, geoKeys),
                Entry.ascii(TAG_GEO_ASCII_PARAMS, WGS84_CITATION),
            )
        if (noData != null) entries += Entry.ascii(TAG_GDAL_NODATA, noData.toString())

        // Layout: header | IFD | out of line values | pixel data
        val ifdSize = 2 + entries.size * ENTRY_SIZE + 4
        var offset = HEADER_SIZE + ifdSize
        for (entry in entries) {
            if (entry.data.size > 4) {
                entry.valueOffset = offset
                offset += entry.data.size + (entry.data.size and 1) // keep word alignment
            }
        }
        val pixelOffset = offset
        val rowSize = width * BYTES_PER_SAMPLE
        stripOffsets.data = Entry.longs(TAG_STRIP_OFFSETS, IntArray(height) { pixelOffset + it * rowSize }).data

        BufferedOutputStream(Files.newOutputStream(target)).use { output ->
            val header = buffer(pixelOffset)
            header
                .put('I'.code.toByte())
                .put('I'.code.toByte())
                .putShort(42)
                .putInt(HEADER_SIZE)
            header.putShort(entries.size.toShort())
            for (entry in entries) {
                header.putShort(entry.tag.toShort()).putShort(entry.type).putInt(entry.count)
                if (entry.data.size > 4) {
                    header.putInt(entry.valueOffset)
                } else {
                    header.put(entry.data.copyOf(4))
                }
            }
            header.putInt(0) // no further IFD
            for (entry in entries.filter { it.data.size > 4 }) {
                header.put(entry.data)
                if (entry.data.size and 1 == 1) header.put(0)
            }
            output.write(header.array(), 0, header.position())

            val row = buffer(rowSize)
            for (line in raster) {
                row.clear()
                row.asShortBuffer().put(line)
                output.write(row.array(), 0, rowSize)
            }
        }
    }

    private fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    /**
     * A single IFD entry, [data] contains the encoded values.
     */
    private class Entry(
        val tag: Int,
        val type: Short,
        val count: Int,
        var data: ByteArray,
    ) {
        var valueOffset: Int = 0

        companion object {
            fun short(
                tag: Int,
                value: Int,
            ) = shorts(tag, shortArrayOf(value.toShort()))

            fun shorts(
                tag: Int,
                values: ShortArray,
            ) = Entry(tag, TYPE_SHORT, values.size, encode(values.size * 2) { it.asShortBuffer().put(values) })

            fun long(
                tag: Int,
                value: Int,
            ) = longs(tag, intArrayOf(value))

            fun longs(
                tag: Int,
                values: IntArray,
            ) = Entry(tag, TYPE_LONG, values.size, encode(values.size * 4) { it.asIntBuffer().put(values) })

            fun doubles(
                tag: Int,
                values: DoubleArray,
            ) = Entry(tag, TYPE_DOUBLE, values.size, encode(values.size * 8) { it.asDoubleBuffer().put(values) })

            fun ascii(
                tag: Int,
                value: String,
            ): Entry {
                val bytes = (value + "\u0000").toByteArray(StandardCharsets.US_ASCII)
                return Entry(tag, TYPE_ASCII, bytes.size, bytes)
            }

            private fun encode(
                size: Int,
                writer: (ByteBuffer) -> Unit,
            ): ByteArray {
                val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
                writer(buffer)
                return buffer.array()
            }
        }
    }
}
