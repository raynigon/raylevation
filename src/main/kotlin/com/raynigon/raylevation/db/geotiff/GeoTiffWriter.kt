package com.raynigon.raylevation.db.geotiff

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

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

// TIFF tag values
private const val BITS_PER_SAMPLE = 16
private const val COMPRESSION_NONE = 1
private const val PHOTOMETRIC_BLACK_IS_ZERO = 1
private const val PLANAR_CHUNKY = 1
private const val SAMPLE_FORMAT_SIGNED_INT = 2

// GeoKeys for a geographic WGS84 raster with "pixel is area" semantics
private const val WGS84_CITATION = "WGS 84|"
private val WGS84_GEO_KEYS =
    listOf(
        // Header: key directory version 1, revision 1.0, number of keys
        listOf(1, 1, 0, 5),
        // GTModelTypeGeoKey = Geographic
        listOf(1024, 0, 1, 2),
        // GTRasterTypeGeoKey = PixelIsArea
        listOf(1025, 0, 1, 1),
        // GeographicTypeGeoKey = EPSG:4326
        listOf(2048, 0, 1, 4326),
        // GeogCitationGeoKey, stored in GeoAsciiParams
        listOf(2049, TAG_GEO_ASCII_PARAMS, WGS84_CITATION.length, 0),
        // GeogAngularUnitsGeoKey = degree
        listOf(2054, 0, 1, 9102),
    ).flatten().map { it.toShort() }.toShortArray()

// File layout
private const val TIFF_MAGIC: Short = 42
private const val HEADER_SIZE = 8
private const val ENTRY_SIZE = 12
private const val INLINE_VALUE_SIZE = 4
private const val BYTES_PER_SAMPLE = 2

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
    /**
     * Write the given raster as GeoTIFF file.
     *
     * @param target  The file which should be written, an existing file is overwritten
     * @param raster  The elevation values, row by row from north to south
     * @param grid    The size and georeferencing of the raster
     * @param noData  The value which marks pixels without data, null if all pixels contain data
     */
    fun write(
        target: Path,
        raster: Array<ShortArray>,
        grid: PixelGrid,
        noData: Short?,
    ) {
        require(raster.size == grid.height && raster.all { it.size == grid.width }) {
            "Raster does not match the grid size ${grid.width}x${grid.height}"
        }
        val rowSize = grid.width * BYTES_PER_SAMPLE
        val stripOffsets = Entry.longs(TAG_STRIP_OFFSETS, IntArray(grid.height))
        val entries = createEntries(grid, noData, stripOffsets)
        val pixelOffset = assignValueOffsets(entries)
        // The size of the strip offsets entry does not depend on its values, so it can be filled after the layout
        stripOffsets.data = Entry.longs(TAG_STRIP_OFFSETS, IntArray(grid.height) { pixelOffset + it * rowSize }).data

        BufferedOutputStream(Files.newOutputStream(target)).use { output ->
            writeHeader(output, entries, pixelOffset)
            writePixels(output, raster, rowSize)
        }
    }

    private fun createEntries(
        grid: PixelGrid,
        noData: Short?,
        stripOffsets: Entry,
    ): List<Entry> {
        val entries =
            mutableListOf(
                Entry.long(TAG_IMAGE_WIDTH, grid.width),
                Entry.long(TAG_IMAGE_LENGTH, grid.height),
                Entry.short(TAG_BITS_PER_SAMPLE, BITS_PER_SAMPLE),
                Entry.short(TAG_COMPRESSION, COMPRESSION_NONE),
                Entry.short(TAG_PHOTOMETRIC, PHOTOMETRIC_BLACK_IS_ZERO),
                stripOffsets,
                Entry.short(TAG_SAMPLES_PER_PIXEL, 1),
                Entry.long(TAG_ROWS_PER_STRIP, 1),
                Entry.longs(TAG_STRIP_BYTE_COUNTS, IntArray(grid.height) { grid.width * BYTES_PER_SAMPLE }),
                Entry.short(TAG_PLANAR_CONFIGURATION, PLANAR_CHUNKY),
                Entry.short(TAG_SAMPLE_FORMAT, SAMPLE_FORMAT_SIGNED_INT),
                Entry.doubles(TAG_MODEL_PIXEL_SCALE, doubleArrayOf(grid.pixelSizeX, -grid.pixelSizeY, 0.0)),
                Entry.doubles(TAG_MODEL_TIEPOINT, doubleArrayOf(0.0, 0.0, 0.0, grid.originX, grid.originY, 0.0)),
                Entry.shorts(TAG_GEO_KEY_DIRECTORY, WGS84_GEO_KEYS),
                Entry.ascii(TAG_GEO_ASCII_PARAMS, WGS84_CITATION),
            )
        if (noData != null) entries += Entry.ascii(TAG_GDAL_NODATA, noData.toString())
        return entries
    }

    /**
     * Layout: header | IFD | values which do not fit into an entry | pixel data
     *
     * @return the offset of the pixel data
     */
    private fun assignValueOffsets(entries: List<Entry>): Int {
        var offset = HEADER_SIZE + 2 + entries.size * ENTRY_SIZE + 4
        for (entry in entries.filter { it.data.size > INLINE_VALUE_SIZE }) {
            entry.valueOffset = offset
            // keep word alignment
            offset += entry.data.size + (entry.data.size and 1)
        }
        return offset
    }

    private fun writeHeader(
        output: OutputStream,
        entries: List<Entry>,
        size: Int,
    ) {
        val header = buffer(size)
        header
            .put('I'.code.toByte())
            .put('I'.code.toByte())
            .putShort(TIFF_MAGIC)
            .putInt(HEADER_SIZE)
        header.putShort(entries.size.toShort())
        for (entry in entries) {
            header.putShort(entry.tag.toShort()).putShort(entry.type).putInt(entry.count)
            if (entry.data.size > INLINE_VALUE_SIZE) {
                header.putInt(entry.valueOffset)
            } else {
                header.put(entry.data.copyOf(INLINE_VALUE_SIZE))
            }
        }
        // no further IFD
        header.putInt(0)
        for (entry in entries.filter { it.data.size > INLINE_VALUE_SIZE }) {
            header.put(entry.data)
            if (entry.data.size and 1 == 1) header.put(0)
        }
        output.write(header.array(), 0, header.position())
    }

    private fun writePixels(
        output: OutputStream,
        raster: Array<ShortArray>,
        rowSize: Int,
    ) {
        val row = buffer(rowSize)
        for (line in raster) {
            row.clear()
            row.asShortBuffer().put(line)
            output.write(row.array(), 0, rowSize)
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
            ) = Entry(tag, TYPE_SHORT, values.size, encode(values.size * Short.SIZE_BYTES) { it.asShortBuffer().put(values) })

            fun long(
                tag: Int,
                value: Int,
            ) = longs(tag, intArrayOf(value))

            fun longs(
                tag: Int,
                values: IntArray,
            ) = Entry(tag, TYPE_LONG, values.size, encode(values.size * Int.SIZE_BYTES) { it.asIntBuffer().put(values) })

            fun doubles(
                tag: Int,
                values: DoubleArray,
            ) = Entry(tag, TYPE_DOUBLE, values.size, encode(values.size * Double.SIZE_BYTES) { it.asDoubleBuffer().put(values) })

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
