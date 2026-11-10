package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.db.geotiff.GeoTiffTile
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.srtm.model.OriginTile
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class TileSplitServiceSpec extends Specification {

    @Subject
    TileSplitService service = new TileSplitServiceImpl()


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

    def "sub tiles cover the origin tile and contain its elevation data"() {
        given:
        Path source = new File("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif").toPath()
        GeoTiffTile sourceTile = new GeoTiffTile(source)
        OriginTile originTile = new OriginTile("test", 2, 2, "-", null, null, source)

        and:
        List<TileBounds> bounds = []
        boolean sameData = true
        def callback = { GeoTiffTile subTile, OriginTile origin ->
            bounds << subTile.bounds
            def center = subTile.bounds.center
            sameData &= subTile.lookupElevation(center).value.intValue() == sourceTile.lookupElevation(center).value.intValue()
        }

        when:
        service.split(originTile, callback)

        then:
        bounds.size() == 4
        sameData
        Math.abs(bounds*.xMin.min() - sourceTile.bounds.xMin) < 1e-9
        Math.abs(bounds*.xMax.max() - sourceTile.bounds.xMax) < 1e-9
        Math.abs(bounds*.yMin.min() - sourceTile.bounds.yMin) < 1e-9
        Math.abs(bounds*.yMax.max() - sourceTile.bounds.yMax) < 1e-9
    }
}
