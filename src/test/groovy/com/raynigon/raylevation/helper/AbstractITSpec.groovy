package com.raynigon.raylevation.helper


import com.raynigon.raylevation.infrastructure.configuration.JacksonConfiguration
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

@AutoConfigureMetrics
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.scheduling.enable=false")
@ContextConfiguration(initializers = [])
abstract class AbstractITSpec extends Specification {


    @LocalServerPort
    private int port

    protected RestTemplate restTemplate = new RestTemplate()

    def setup() {
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory())
        restTemplate.getMessageConverters().forEach { conv ->
            if (conv instanceof MappingJackson2HttpMessageConverter) {
                conv.objectMapper = new JacksonConfiguration().objectMapper()
            }
        }
    }

    protected int getPort() {
        return port
    }

    protected URI apiUri(String path) {
        return URI.create("http://localhost:$port/api$path")
    }
}