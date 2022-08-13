package com.raynigon.raylevation.base.service

import com.raynigon.raylevation.infrastructure.model.GeoPoint
import org.springframework.stereotype.Service
import java.text.ParseException

/**
 * The location parser consumes a string and produces a list of GeoPoints.
 *
 * The input String should be formatted according to this EBNF syntax:
 * ```
 * INPUT            = GEO_POINT, {"|", GEO_POINT};
 * GEO_POINT        = FLOATING_POINT, ",", FLOATING_POINT;
 * FLOATING_POINT   = ["-"], DIGIT, {DIGIT}, ["."], {DIGIT};
 * DIGIT            = "0" |"1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9";
 * ```
 */
interface LocationsParser {

    fun parse(input: String): List<GeoPoint>
}

@Service
class LocationsParserImpl : LocationsParser {

    override fun parse(input: String): List<GeoPoint> {
        if (input.isEmpty()) return emptyList()
        return input.split('|')
            .map { toGeoPoint(it, input) }
    }

    private fun toGeoPoint(point: String, totalInput: String): GeoPoint {
        val parts = point.split(",").mapNotNull { it.toDoubleOrNull() }
        if (parts.size != 2) {
            val index = totalInput.indexOf(point)
            throw ParseException("Unexpected input '$point' at position $index", index)
        }
        return GeoPoint(parts[0], parts[1])
    }
}
