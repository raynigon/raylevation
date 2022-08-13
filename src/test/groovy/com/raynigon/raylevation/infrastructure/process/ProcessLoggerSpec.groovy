package com.raynigon.raylevation.infrastructure.process

import org.slf4j.Logger
import spock.lang.Specification

class ProcessLoggerSpec extends Specification {

    def "process logger logs stdout to info"() {
        given:
        Logger logger = Mock()

        and:
        Process process = new ProcessBuilder()
                .command("echo", "test1234\ncurrent")
                .start()
        when:
        new ProcessLogger(process, logger, "ABC").join()

        then:
        1 * logger.info({ it.contains("ABC") && it.contains("test1234") })
        1 * logger.info({ !it.contains("test1234") && it.contains("current") })
    }
}
