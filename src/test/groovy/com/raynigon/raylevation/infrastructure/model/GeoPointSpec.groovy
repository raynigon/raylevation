package com.raynigon.raylevation.infrastructure.model

import nl.altindag.log.LogCaptor
import spock.lang.Specification
import spock.lang.Unroll

class GeoPointSpec extends Specification {

    @Unroll
    def "log warning for (#latitude, #longitude)"() {
        given:
        LogCaptor logCaptor = LogCaptor.forClass(GeoPoint.class)

        when:
        new GeoPoint(latitude, longitude)

        then:
        logCaptor.getWarnLogs().size() == count

        where:
        latitude | longitude | count
        0.001    | 0.001     | 1
        0.01     | 0.01      | 0
        0.1      | 0.1       | 0
        0.0      | 0.0       | 1
        -1.0     | -1.0      | 0
    }

    @Unroll
    def "create valid location (#latitude, #longitude)"() {
        when:
        new GeoPoint(latitude, longitude)

        then:
        noExceptionThrown()

        where:
        latitude | longitude
        0.0      | 0.0
        -90.0    | 0.0
        90.0     | 0.0
        0.0      | -180.0
        0.0      | 180.0
        45.0     | 45.0
        -45.0    | -45.0
    }

    @Unroll
    def "create invalid location (#latitude, #longitude)"() {
        when:
        new GeoPoint(latitude, longitude)

        then:
        thrown(IllegalArgumentException)

        where:
        latitude                 | longitude
        -90.1d                   | 0.0d
        90.1d                    | 0.0d
        Double.MAX_VALUE * -1.0d | 0.0d
        Double.MAX_VALUE         | 0.0d
        Double.NEGATIVE_INFINITY | 0.0d
        Double.POSITIVE_INFINITY | 0.0d
        Double.NaN               | 0.0d
        0.0d                     | -180.1d
        0.0d                     | 180.1d
        0.0d                     | Double.MAX_VALUE * -1.0d
        0.0d                     | Double.MAX_VALUE
        0.0d                     | Double.NEGATIVE_INFINITY
        0.0d                     | Double.POSITIVE_INFINITY
        0.0d                     | Double.NaN
    }
}
