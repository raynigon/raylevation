package com.raynigon.raylevation.db.geotiff

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Minimal TIFF builder and tag reader for tests.
 *
 * The builder creates GeoTIFF files with properties which the production writer
 * can not produce (other data types, multiple bands, rotation, multiple images),
 * so the validation of [GeoTiffTile] can be tested without committing binary fixtures.
 */
class TiffTestFiles {

    static final int TAG_MODEL_PIXEL_SCALE = 33550
    static final int TAG_MODEL_TIEPOINT = 33922
    static final int TAG_GEO_KEY_DIRECTORY = 34735
    static final int TAG_GDAL_NODATA = 42113

    private static final short TYPE_ASCII = 2
    private static final short TYPE_SHORT = 3
    private static final short TYPE_LONG = 4
    private static final short TYPE_DOUBLE = 12

    static class Image {
        int width = 4
        int height = 4
        int bitsPerSample = 16
        // 1 = unsigned int, 2 = signed int, 3 = floating point
        int sampleFormat = 2
        int samplesPerPixel = 1
        double originX = -5.0
        double originY = 56.0
        double pixelSize = 0.5
        // if set, the georeferencing is written as ModelTransformation (row major 4x4 matrix)
        double[] modelTransformation = null
    }

    static Path write(Path target, List<Image> images) {
        def out = new ByteArrayOutputStream()
        def header = buffer(8).put('I' as char as byte).put('I' as char as byte).putShort(42 as short).putInt(8)
        out.write(header.array())
        images.eachWithIndex { Image image, int index ->
            writeImage(out, image, index == images.size() - 1)
        }
        Files.write(target, out.toByteArray())
        return target
    }

    static Path write(Path target, Image image) {
        return write(target, [image])
    }

    private static void writeImage(ByteArrayOutputStream out, Image image, boolean last) {
        int bytesPerPixel = image.samplesPerPixel * image.bitsPerSample.intdiv(8)
        int pixelBytes = image.width * image.height * bytesPerPixel
        List<Map> entries = [
                longs(256, image.width),
                longs(257, image.height),
                shorts(258, *([image.bitsPerSample] * image.samplesPerPixel)),
                shorts(259, 1),
                shorts(262, 1),
                longs(273, 0), // strip offset, filled below
                shorts(277, image.samplesPerPixel),
                longs(278, image.height),
                longs(279, pixelBytes),
                shorts(284, 1),
        ]
        if (image.samplesPerPixel > 1) {
            entries << shorts(338, *([0] * (image.samplesPerPixel - 1)))
        }
        entries << shorts(339, *([image.sampleFormat] * image.samplesPerPixel))
        if (image.modelTransformation != null) {
            entries << doubles(34264, image.modelTransformation)
        } else {
            entries << doubles(TAG_MODEL_PIXEL_SCALE, [image.pixelSize, image.pixelSize, 0.0] as double[])
            entries << doubles(TAG_MODEL_TIEPOINT, [0.0, 0.0, 0.0, image.originX, image.originY, 0.0] as double[])
        }
        // Geographic, PixelIsArea, EPSG:4326, degree
        entries << shorts(TAG_GEO_KEY_DIRECTORY, 1, 1, 0, 4, 1024, 0, 1, 2, 1025, 0, 1, 1, 2048, 0, 1, 4326, 2054, 0, 1, 9102)

        int ifdOffset = out.size()
        int offset = ifdOffset + 2 + entries.size() * 12 + 4
        entries.findAll { it.data.length > 4 }.each {
            it.offset = offset
            offset += it.data.length + (it.data.length & 1)
        }
        int pixelOffset = offset
        entries.find { it.tag == 273 }.data = buffer(4).putInt(pixelOffset).array()
        int nextIfd = last ? 0 : pixelOffset + pixelBytes + (pixelBytes & 1)

        def ifd = buffer(pixelOffset - ifdOffset)
        ifd.putShort(entries.size() as short)
        entries.each {
            ifd.putShort(it.tag as short).putShort(it.type as short).putInt(it.count as int)
            if (it.data.length > 4) {
                ifd.putInt(it.offset as int)
            } else {
                ifd.put(Arrays.copyOf(it.data as byte[], 4))
            }
        }
        ifd.putInt(nextIfd)
        entries.findAll { it.data.length > 4 }.each {
            ifd.put(it.data as byte[])
            if (it.data.length & 1) ifd.put(0 as byte)
        }
        out.write(ifd.array())
        out.write(new byte[pixelBytes + (last ? 0 : (pixelBytes & 1))])
    }

    /**
     * Reads the tags of the first image of a little endian TIFF file.
     *
     * @return tag number to the decoded values (List of Numbers, or a String for ASCII tags)
     */
    static Map<Integer, Object> readTags(Path path) {
        def data = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN)
        assert data.get(0) == ('I' as char as byte) && data.getShort(2) == 42
        int ifd = data.getInt(4)
        int count = Short.toUnsignedInt(data.getShort(ifd))
        Map<Integer, Object> tags = [:]
        for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12
            int tag = Short.toUnsignedInt(data.getShort(entry))
            int type = data.getShort(entry + 2)
            int n = data.getInt(entry + 4)
            int size = [(TYPE_ASCII as int): 1, (TYPE_SHORT as int): 2, (TYPE_LONG as int): 4, (TYPE_DOUBLE as int): 8][type] * n
            int valueOffset = size > 4 ? data.getInt(entry + 8) : entry + 8
            switch (type) {
                case TYPE_ASCII:
                    def bytes = new byte[n]
                    data.get(valueOffset, bytes)
                    tags[tag] = new String(bytes, StandardCharsets.US_ASCII).replace("\u0000", "")
                    break
                case TYPE_SHORT:
                    tags[tag] = (0..<n).collect { Short.toUnsignedInt(data.getShort(valueOffset + it * 2)) }
                    break
                case TYPE_LONG:
                    tags[tag] = (0..<n).collect { Integer.toUnsignedLong(data.getInt(valueOffset + it * 4)) }
                    break
                case TYPE_DOUBLE:
                    tags[tag] = (0..<n).collect { data.getDouble(valueOffset + it * 8) }
                    break
            }
        }
        return tags
    }

    private static Map shorts(int tag, int... values) {
        def b = buffer(values.length * 2)
        values.each { b.putShort(it as short) }
        return [tag: tag, type: TYPE_SHORT, count: values.length, data: b.array()]
    }

    private static Map longs(int tag, int... values) {
        def b = buffer(values.length * 4)
        values.each { b.putInt(it) }
        return [tag: tag, type: TYPE_LONG, count: values.length, data: b.array()]
    }

    private static Map doubles(int tag, double[] values) {
        def b = buffer(values.length * 8)
        values.each { b.putDouble(it) }
        return [tag: tag, type: TYPE_DOUBLE, count: values.length, data: b.array()]
    }

    private static ByteBuffer buffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    }
}
