package com.raynigon.raylevation.infrastructure.controller

import com.raynigon.raylevation.helper.AbstractITSpec
import kotlin.io.AccessDeniedException
import nl.altindag.log.LogCaptor
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.server.ResponseStatusException
import spock.lang.Ignore

@Ignore("Too many side effects")
class FallbackControllerAdvisorITSpec extends AbstractITSpec {

    LogCaptor logCaptor = LogCaptor.forClass(FallbackControllerAdvisor.class)

    @Override
    def setup() {
        logCaptor.clearLogs()
    }

    def "generic exception gets handled"() {
        when:
        restTemplate.getForObject(apiUri("/test/fca/Exception"), Void)

        then:
        thrown(HttpServerErrorException.InternalServerError)

        and:
        logCaptor.errorLogs.size() == 1
        logCaptor.errorLogs.any { it.contains("An unexpected") && it.contains("occurred") }
    }

    def "access denied exception gets handled"() {
        when:
        restTemplate.getForObject(apiUri("/test/fca/AccessDeniedException"), Void)

        then:
        thrown(HttpClientErrorException.Forbidden)

        and:
        logCaptor.warnLogs.size() == 1
        logCaptor.warnLogs.any { it.contains("Access was forbidden") }
    }

    def "response status exception gets handled"() {
        when:
        restTemplate.getForObject(apiUri("/test/fca/ResponseStatusException"), Void)

        then:
        thrown(HttpClientErrorException.TooManyRequests)

        and:
        logCaptor.infoLogs.size() == 1
        logCaptor.infoLogs.any { it.contains("A ResponseStatusException occurred") }
    }

    @RestController
    @RequestMapping("/api/test/fca")
    static class ExceptionController {

        @GetMapping("Exception")
        void getException() {
            throw new Exception()
        }

        @GetMapping("AccessDeniedException")
        void getAccessDeniedException() {
            throw new AccessDeniedException(new File("file"), null, null)
        }

        @GetMapping("ResponseStatusException")
        void getResponseStatusException() {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)
        }
    }
}
