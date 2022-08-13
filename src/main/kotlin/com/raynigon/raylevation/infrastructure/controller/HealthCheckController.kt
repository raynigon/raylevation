package com.raynigon.raylevation.infrastructure.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/health", "/actuator/healthcheck")
class HealthCheckController {

    @GetMapping
    fun healthCheck() = mapOf("status" to "UP")
}
