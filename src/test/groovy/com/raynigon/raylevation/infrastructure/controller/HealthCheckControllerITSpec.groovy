package com.raynigon.raylevation.infrastructure.controller

import com.raynigon.raylevation.helper.AbstractITSpec
import spock.lang.Unroll

class HealthCheckControllerITSpec extends AbstractITSpec {

    @Unroll
    def "health endpoint is reachable - #name"() {
        given:
        URI uri = URI.create(uriTemplate.replace("{{port}}", port.toString()))

        when:
        def result = restTemplate.getForObject(uri, Map)

        then:
        result.containsKey("status")
        result["status"] == "UP"

        where:
        name       | uriTemplate
        "actuator" | "http://localhost:{{port}}/actuator/healthcheck"
        "api"      | "http://localhost:{{port}}/api/v1/health"
    }
}
