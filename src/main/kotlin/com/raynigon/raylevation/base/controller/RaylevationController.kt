package com.raynigon.raylevation.base.controller

import com.raynigon.raylevation.base.dto.ElevationRequest
import com.raynigon.raylevation.base.dto.ElevationResponse
import com.raynigon.raylevation.base.dto.ElevationResult
import com.raynigon.raylevation.base.service.LocationsParser
import com.raynigon.raylevation.base.service.RaylevationService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The RaylevationController contains all available endpoints
 * to request elevation for GeoPoints.
 *
 * @param parser  The parser used to convert a query string to GeoPoints.
 * @param service The [RaylevationService] instance used to lookup the elevation for the GeoPoints
 */
@RestController
@RequestMapping("/api/v1/lookup")
class RaylevationController(
    private val parser: LocationsParser,
    private val service: RaylevationService
) {

    /**
     * Elevation request endpoint for query string requests
     * @param locationsParam the locations as pipe seperated list of coordinates
     * @return A [ElevationResponse] containing the elevation for each given geo point,
     * or an error if no elevation data could be found
     */
    @GetMapping
    fun getElevation(@RequestParam("locations", defaultValue = "") locationsParam: String): ElevationResponse =
        parser.parse(locationsParam)
            .let(service::lookup)
            .map { ElevationResult(it.point, it.elevation, it.error) }
            .let(::ElevationResponse)

    /**
     * Elevation request endpoint for post requests
     * @param body the locations as json formatted objects
     * @return A [ElevationResponse] containing the elevation for each given geo point,
     * or an error if no elevation data could be found
     */
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun getElevation(@RequestBody body: ElevationRequest): ElevationResponse =
        body.locations.let(service::lookup)
            .map { ElevationResult(it.point, it.elevation, it.error) }
            .let(::ElevationResponse)
}
