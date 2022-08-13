package com.raynigon.raylevation.base.service

import com.raynigon.raylevation.infrastructure.model.GeoPoint
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.text.ParseException

class LocationsParserSpec extends Specification {

    @Subject
    LocationsParser parser = new LocationsParserImpl()

    @Unroll
    def "parse valid locations - '#input'"() {
        given:
        false

        when:
        def result = parser.parse(input)

        then:
        result == expected

        where:
        input               | expected
        ""                  | []
        "1,2"               | [new GeoPoint(1, 2)]
        "1.1,2"             | [new GeoPoint(1.1, 2)]
        "1,2.1"             | [new GeoPoint(1, 2.1)]
        "1.1,2.2"           | [new GeoPoint(1.1, 2.2)]
        "-1.1,2.2"          | [new GeoPoint(-1.1, 2.2)]
        "1.1,-2.2"          | [new GeoPoint(1.1, -2.2)]
        "-1.1,-2.2"         | [new GeoPoint(-1.1, -2.2)]
        "1,2|3,4"           | [new GeoPoint(1, 2), new GeoPoint(3, 4)]
        "1.4,2.3|3.2,4.1"   | [new GeoPoint(1.4, 2.3), new GeoPoint(3.2, 4.1)]
        "-1.4,2.3|-3.2,4.1" | [new GeoPoint(-1.4, 2.3), new GeoPoint(-3.2, 4.1)]
        "1.4,-2.3|3.2,-4.1" | [new GeoPoint(1.4, -2.3), new GeoPoint(3.2, -4.1)]
    }

    @Unroll
    def "parse invalid locations - '#input'"() {
        given:
        false

        when:
        parser.parse(input)

        then:
        def exception = thrown(ParseException)
        exception.errorOffset == offset
        exception.message == message

        where:
        input     | offset | message
        " "       | 0      | "Unexpected input ' ' at position 0"
        "|"       | 0      | "Unexpected input '' at position 0"
        "1"       | 0      | "Unexpected input '1' at position 0"
        "2"       | 0      | "Unexpected input '2' at position 0"
        "1,"      | 0      | "Unexpected input '1,' at position 0"
        "12"      | 0      | "Unexpected input '12' at position 0"
        "1,2|"    | 0      | "Unexpected input '' at position 0"
        "123"     | 0      | "Unexpected input '123' at position 0"
        "A,B"     | 0      | "Unexpected input 'A,B' at position 0"
        "1,B"     | 0      | "Unexpected input '1,B' at position 0"
        "-"       | 0      | "Unexpected input '-' at position 0"
        "-1"      | 0      | "Unexpected input '-1' at position 0"
        "-1,"     | 0      | "Unexpected input '-1,' at position 0"
        "-1,2|3," | 5      | "Unexpected input '3,' at position 5"
    }
}