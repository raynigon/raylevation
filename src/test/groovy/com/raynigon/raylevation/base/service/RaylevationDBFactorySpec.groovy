package com.raynigon.raylevation.base.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.raynigon.raylevation.db.config.DatabaseConfig
import com.raynigon.raylevation.infrastructure.configuration.JacksonConfiguration
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.nio.file.Path

class RaylevationDBFactorySpec extends Specification {

    def "create database"() {
        given:
        DatabaseConfig config = new DatabaseConfig(Path.of("src/test/resources/database/empty/"), 1)
        MeterRegistry meterRegistry = new SimpleMeterRegistry()
        ObjectMapper mapper = new JacksonConfiguration().objectMapper()

        and:
        RaylevationDBFactory factory = new RaylevationDBFactoryImpl(config, meterRegistry, mapper)

        when:
        def result = factory.create()

        then:
        result != null
    }
}
