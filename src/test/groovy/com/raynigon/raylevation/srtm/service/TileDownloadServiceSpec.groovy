package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.helper.FileHelper
import com.raynigon.raylevation.helper.SRTMWireMock
import com.raynigon.raylevation.srtm.model.OriginTile
import com.raynigon.raylevation.srtm.model.SRTMConfig
import com.raynigon.raylevation.helper.FileHelper
import com.raynigon.raylevation.helper.SRTMWireMock
import com.raynigon.raylevation.srtm.model.OriginTile
import com.raynigon.raylevation.srtm.model.SRTMConfig
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.mock.env.MockEnvironment
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path

class TileDownloadServiceSpec extends Specification {

    SRTMWireMock wireMock = new SRTMWireMock()

    SRTMConfig config

    @Subject
    TileDownloadService service

    def setup() {
        ConfigurableApplicationContext context = Mock()
        ConfigurableEnvironment environment = new MockEnvironment()
        context.getEnvironment() >> environment
        wireMock.initialize(context)
        config = new SRTMConfig(
                FileHelper.createTemporaryDirectory(),
                wireMock.url,
                [],
                false
        )
        service = new TileDownloadServiceImpl(config)
    }

    def "download new archive"() {
        given:
        OriginTile originTile = new OriginTile(
                "cea",
                1,
                1,
                "-",
                null,
                null,
                null
        )

        and:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()

        when:
        def result = service.downloadTile(originTile, target)

        then:
        result.archivePath != null
        result.archivePath.toFile().exists()
    }

    @Ignore("Not Implemented")
    def "download url returns 404"() {
        expect:
        false
    }

    @Ignore("Not Implemented")
    def "missing etag in download"() {
        expect:
        false
    }

    @Ignore("Not Implemented")
    def "missing etag in head request"() {
        expect:
        false
    }
}
