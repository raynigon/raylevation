package com.raynigon.raylevation

import com.raynigon.raylevation.helper.AbstractITSpec
import com.raynigon.raylevation.helper.AbstractITSpec

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class SpringDocITSpec extends AbstractITSpec {

    def "write openapi to file"() {
        given:
        Path directory = Path.of("build/docs/openapi/")
        Path fullPath = directory.resolve("raylevation.json")

        when:
        def result = restTemplate.getForObject(URI.create("http://localhost:$port/v3/api-docs"), String.class)

        and:
        Files.createDirectories(directory)
        Files.write(fullPath, [result], StandardCharsets.UTF_8)

        then:
        Files.exists(fullPath)
    }
}
