package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.helper.FileHelper
import com.raynigon.raylevation.srtm.model.OriginTile
import com.raynigon.raylevation.srtm.model.SRTMConfig
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Ignore
class TileUnpackServiceSpec extends Specification {

    @Subject
    TileUnpackService service = new TileUnpackServiceImpl(
            new SRTMConfig(
                    FileHelper.createTemporaryDirectory(),
                    "-",
                    [],
                    false
            )
    )

    def "unpack valid rar archive"() {
        given:
        File sourceFile = File.createTempFile("archive", ".rar")
        sourceFile.deleteOnExit()
        Path source = sourceFile.toPath()
        Files.copy(this.class.getResourceAsStream("/archive/cea.rar"), source, StandardCopyOption.REPLACE_EXISTING)

        and:
        OriginTile originTile = new OriginTile(
                "cea",
                1,
                1,
                "-",
                null,
                source,
                null
        )

        and:
        File targetFile = File.createTempDir()
        targetFile.deleteOnExit()
        Path target = targetFile.toPath()

        when:
        def result = service.unpackArchive(originTile, target)

        then:
        result.geoTiffPath != null
        result.geoTiffPath.toFile().exists()
    }

    @Ignore("Not Implemented")
    def "unpack invalid archive"() {
        expect:
        false
    }

    @Ignore("Not Implemented")
    def "unpack archive without tif"() {
        expect:
        false
    }
}
