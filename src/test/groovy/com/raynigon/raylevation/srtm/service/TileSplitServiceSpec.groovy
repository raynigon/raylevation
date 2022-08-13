package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.srtm.model.OriginTile
import com.raynigon.raylevation.srtm.model.OriginTile
import org.gdal.gdal.gdal
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class TileSplitServiceSpec extends Specification {

    @Subject
    TileSplitService service = new TileSplitServiceImpl()

    def setup() {
        gdal.AllRegister()
    }

    def "tile is split to 4 parts (2x2)"() {
        given:
        Path source = new File("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif").toPath()

        and:
        OriginTile originTile = new OriginTile(
                "cea",
                2,
                2,
                "-",
                null,
                null,
                source
        )

        and:
        AtomicInteger calls = new AtomicInteger(0)
        def callback = { a, b -> calls.incrementAndGet() }

        when:
        service.split(originTile, callback)

        then:
        calls.get() == 4
    }
}
