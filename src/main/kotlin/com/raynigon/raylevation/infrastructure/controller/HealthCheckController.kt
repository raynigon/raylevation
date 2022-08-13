package com.raynigon.raylevation.infrastructure.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Simple controller which provides a static response.
 * This should be used for lifeness and readyness probes in orchestrators.
 */
@RestController
@RequestMapping("/api/v1/health", "/actuator/healthcheck")
class HealthCheckController {

    /**
     * Returns always "status: UP".
     * This should be used for lifeness and readyness probes in orchestrators.
     *
     * @return a map with only one entry. The entry has the key "status" and the value "UP".
     */
    @GetMapping
    fun healthCheck() = mapOf("status" to "UP")
}
