package com.raynigon.raylevation.db.gdal

import com.raynigon.raylevation.db.exception.GDALException
import com.raynigon.raylevation.db.exception.IncompatibleTileException
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.helper.QuantityHelper
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import com.raynigon.raylevation.db.exception.GDALException
import com.raynigon.raylevation.db.exception.IncompatibleTileException
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.helper.QuantityHelper
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import spock.lang.Ignore
import spock.lang.Specification

import javax.measure.Quantity
import javax.measure.quantity.Length
import java.nio.file.Path
import org.gdal.gdal.gdal

import static com.raynigon.unit.api.core.units.si.SISystemUnitsConstants.Metre

class GDALTileSpec extends Specification {

    def setup() {
        gdal.AllRegister()
    }

    def "lookup(#lat, #lon) == expected"() {
        given:
        def tile = new GDALTile(Path.of(path))

        when:
        Quantity<Length> result = tile.lookupElevation(new GeoPoint(lat, lon))

        then:
        QuantityHelper.assertDelta(expected, result, 0.00001)

        where:
        lat  | lon  | expected   | path
        56.0 | -5.0 | Metre(159) | "src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"
        60.0 | -5.0 | Metre(0)   | "src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"
    }

    def "load missing geotiff"() {
        when:
        new GDALTile(Path.of("/non/existing/path/tile.tif"))

        then:
        def exception = thrown(GDALException)
        exception.errorNumber == 4
        exception.message.contains("No such file or directory")
    }

    @Ignore("No sample file exists")
    def "load geotiff with multiple bands"() {
        when:
        new GDALTile(Path.of("src/test/resources/geotiff/multi-band.tif"))

        then:
        thrown(IncompatibleTileException)
    }

    def "load geotiff with incompatible band"() {
        when:
        new GDALTile(Path.of("src/test/resources/geotiff/cea.tif"))

        then:
        thrown(IncompatibleTileException)
    }

    def "bounds are read correctly"() {
        given:
        GDALTile tile = new GDALTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))

        when:
        TileBounds bounds = tile.bounds

        then:
        bounds.getXMin() == -9.008333472519997d
        bounds.getXMax() == 1.4916665106800036d
        bounds.getYMin() == 54.00833315092001d
        bounds.getYMax() == 60.01041647465001d
    }

    def "lookup twice uses cached raster"() {
        given:
        def tile = new GDALTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))

        when:
        Quantity<Length> result0 = tile.lookupElevation(new GeoPoint(56.0, -5.0))
        Quantity<Length> result1 = tile.lookupElevation(new GeoPoint(56.0, -5.0))

        then:
        QuantityHelper.assertDelta(Metre(159), result0, 0.00001)
        QuantityHelper.assertDelta(Metre(159), result1, 0.00001)
    }
}
