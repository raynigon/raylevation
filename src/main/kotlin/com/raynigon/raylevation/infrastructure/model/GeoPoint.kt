package com.raynigon.raylevation.infrastructure.model

import com.github.davidmoten.geo.GeoHash
import org.slf4j.LoggerFactory

/**
 * Data Transfer Object for Geo Coordinates
 * @param latitude the latitude, which must be between -90 and +90, inclusive
 * @param longitude the longitude, which must be between -180° and +180° inclusive
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(LATITUDE_RANGE.contains(latitude)) { "Latitude $latitude is not in range $LATITUDE_RANGE" }
        require(LONGITUDE_RANGE.contains(longitude)) { "Longitude $longitude is not in range $LONGITUDE_RANGE" }
        /*
         * We need to check if the latitude & longitude combination is (0,0)
         * If this is the case a warning should be logged.
         * Since this is a valid GeoPoint an error/exception would be inadequate
         */
        if (ZERO_RANGE.contains(latitude) && ZERO_RANGE.contains(longitude)) {
            logger.warn("The location ($latitude, $longitude) might not be initialized correctly")
        }
    }

    companion object {
        val LATITUDE_RANGE = -90.0..90.0
        val LONGITUDE_RANGE = -180.0..180.0
        private val ZERO_RANGE = -0.001..0.001
        private val logger = LoggerFactory.getLogger(GeoPoint::class.java)
    }

    val geoHash: String by lazy {
        GeoHash.encodeHash(latitude, longitude)
    }
}
