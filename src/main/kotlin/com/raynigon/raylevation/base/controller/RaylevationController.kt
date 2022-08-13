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

@RestController
@RequestMapping("/api/v1/lookup")
class RaylevationController(
    private val parser: LocationsParser,
    private val service: RaylevationService
) {

    @GetMapping
    fun getElevation(@RequestParam("locations", defaultValue = "") locationsParam: String): ElevationResponse =
        parser.parse(locationsParam)
            .let(service::lookup)
            .map { ElevationResult(it.point, it.elevation, it.error) }
            .let(::ElevationResponse)

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun getElevation(@RequestBody body: ElevationRequest): ElevationResponse =
        body.locations.let(service::lookup)
            .map { ElevationResult(it.point, it.elevation, it.error) }
            .let(::ElevationResponse)
}
