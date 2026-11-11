package com.raynigon.raylevation.base.service

import com.raynigon.raylevation.srtm.service.SRTMService
import org.springframework.boot.ApplicationArguments
import spock.lang.Specification
import spock.lang.Subject

class CliRunnerSpec extends Specification {

    SRTMService srtmService = Mock()

    List<Integer> exitCodes = []

    @Subject
    CliRunnerImpl runner

    def setup() {
        runner = new CliRunnerImpl(srtmService, { int code -> exitCodes << code })
    }

    private ApplicationArguments argsWith(List<String> nonOptionArgs) {
        ApplicationArguments args = Mock()
        args.getNonOptionArgs() >> nonOptionArgs
        return args
    }

    def "server starts normally when only spring options are given"() {
        given:
        // Spring already strips option args (e.g. --server.port=9090) out of nonOptionArgs
        def args = argsWith([])

        when:
        runner.run(args)

        then:
        0 * srtmService.updateRaylevationDB()
        exitCodes == []
    }

    def "update-srtm runs the command and ignores spring option arguments"() {
        given:
        def args = argsWith(["update-srtm"])

        when:
        runner.run(args)

        then:
        1 * srtmService.updateRaylevationDB()
        exitCodes == [0]
    }

    def "unknown command logs the help block and exits with 127"() {
        given:
        def args = argsWith(["foo"])

        when:
        runner.run(args)

        then:
        0 * srtmService.updateRaylevationDB()
        exitCodes == [127]
    }
}
