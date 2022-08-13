package com.raynigon.raylevation.db.tile

import com.raynigon.raylevation.infrastructure.model.GeoPoint
import com.raynigon.raylevation.infrastructure.model.GeoPoint
import spock.lang.Specification
import spock.lang.Unroll

class TileBoundsSpec extends Specification {

    def "tile bounds from geo points"() {
        given:
        GeoPoint upperLeft = new GeoPoint(60, -90)
        GeoPoint lowerRight = new GeoPoint(-60, 90)

        when:
        def bounds = new TileBounds(upperLeft, lowerRight)

        then:
        noExceptionThrown()
        bounds.getXMax() == 90.0d
        bounds.getYMax() == 60.0d
        bounds.getXMin() == -90.0d
        bounds.getYMin() == -60.0d
    }

    def "center of tile bounds"() {
        given:
        GeoPoint upperLeft = new GeoPoint(60, -80)
        GeoPoint lowerRight = new GeoPoint(50, -50)

        and:
        TileBounds bounds = new TileBounds(upperLeft, lowerRight)

        when:
        GeoPoint center = bounds.center

        then:
        noExceptionThrown()
        center.latitude == 55.0d
        center.longitude == -65.0d
    }

    @Unroll
    def "tile bounds contains (#lat, #lon) = #expected"() {
        given:
        GeoPoint upperLeft = new GeoPoint(30, -30)
        GeoPoint lowerRight = new GeoPoint(-10, 10)

        and:
        TileBounds bounds = new TileBounds(upperLeft, lowerRight)

        when:
        boolean result = bounds.contains(new GeoPoint(lat, lon))

        then:
        result == expected

        where:
        lat   | lon   | expected
        0.0   | 0.0   | true
        20.0  | 0.0   | true
        0.0   | -20.0 | true
        20.0  | -20.0 | true
        40.0  | 0.0   | false
        0.0   | 40.0  | false
        -40.0 | 0.0   | false
        0.0   | -40.0 | false
    }
}
