package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.base.service.RaylevationDBFactory
import com.raynigon.raylevation.db.IRaylevationDB
import com.raynigon.raylevation.db.geotiff.GeoTiffTile
import com.raynigon.raylevation.helper.FileHelper
import com.raynigon.raylevation.srtm.model.OriginTile
import com.raynigon.raylevation.srtm.model.OriginTileConfig
import com.raynigon.raylevation.srtm.model.SRTMConfig
import spock.lang.Specification

import java.nio.file.Path

class SRTMServiceSpec extends Specification {

    SRTMConfig config = new SRTMConfig(
            FileHelper.createTemporaryDirectory(),
            "-",
            [
                    new OriginTileConfig("A", 1, 1),
                    new OriginTileConfig("B", 1, 1),
                    new OriginTileConfig("C", 1, 1),
            ],
            false
    )

    RaylevationDBFactory dbFactory = Mock()

    TileDownloadService downloadService = Mock()

    TileUnpackService unpackService = Mock()

    TileSplitService splitService = Mock()

    SRTMService service = new SRTMServiceImpl(
            config,
            dbFactory,
            downloadService,
            unpackService,
            splitService
    )

    def "all three tiles are added to the database"() {
        given:
        IRaylevationDB database = Mock(IRaylevationDB)

        when:
        service.updateRaylevationDB()

        then:
        1 * dbFactory.createLocked() >> database
        3 * downloadService.downloadTile(_, _) >> { origin, _ -> origin }
        3 * unpackService.unpackArchive(_, _) >> { origin, _ -> origin }
        3 * splitService.split(_, _)
    }

    def "origin tile with changed etag removes old sub tiles and adds the new ones"() {
        given:
        SRTMConfig config = new SRTMConfig(
                FileHelper.createTemporaryDirectory(),
                "-",
                [new OriginTileConfig("A", 1, 1)],
                false
        )
        SRTMService service = new SRTMServiceImpl(config, dbFactory, downloadService, unpackService, splitService)
        IRaylevationDB database = Mock(IRaylevationDB)
        OriginTilesState previousState = new OriginTilesState([
                new OriginTileState("A", "old-etag", 1, 1, ["sub-a-1"])
        ])

        when:
        service.updateRaylevationDB()

        then:
        1 * dbFactory.createLocked() >> database
        1 * database.getMetadata(_, _) >> previousState
        1 * downloadService.fetchETag(_) >> "new-etag"
        1 * downloadService.downloadTile(_, _) >> { origin, _ -> origin }
        1 * unpackService.unpackArchive(_, _) >> { origin, _ -> origin }
        1 * database.removeTile("sub-a-1")
        1 * splitService.split(_, _) >> { tile, callback ->
            GeoTiffTile subTile = new GeoTiffTile(Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif"))
            callback.invoke(subTile, tile)
        }
        1 * database.addTile(_) >> "sub-a-2"
        1 * database.setMetadata(_, { OriginTilesState state ->
            state.tiles.find { it.name == "A" }?.subTiles == ["sub-a-2"]
        })
        1 * database.save()
        1 * database.close()
    }

    def "origin tile with changed split count triggers an update even though the etag is unchanged"() {
        given:
        SRTMConfig config = new SRTMConfig(
                FileHelper.createTemporaryDirectory(),
                "-",
                [new OriginTileConfig("A", 2, 2)],
                false
        )
        SRTMService service = new SRTMServiceImpl(config, dbFactory, downloadService, unpackService, splitService)
        IRaylevationDB database = Mock(IRaylevationDB)
        OriginTilesState previousState = new OriginTilesState([
                new OriginTileState("A", "same-etag", 1, 1, ["sub-a-1"])
        ])

        when:
        service.updateRaylevationDB()

        then:
        1 * dbFactory.createLocked() >> database
        1 * database.getMetadata(_, _) >> previousState
        1 * downloadService.fetchETag(_) >> "same-etag"
        1 * downloadService.downloadTile(_, _) >> { origin, _ -> origin }
        1 * unpackService.unpackArchive(_, _) >> { origin, _ -> origin }
        1 * database.removeTile("sub-a-1")
        1 * splitService.split(_, _)
        1 * database.save()
    }

    def "legacy origin tile state without subTiles falls back to a bounds lookup"() {
        given:
        SRTMConfig config = new SRTMConfig(
                FileHelper.createTemporaryDirectory(),
                "-",
                [new OriginTileConfig("A", 1, 1)],
                false
        )
        SRTMService service = new SRTMServiceImpl(config, dbFactory, downloadService, unpackService, splitService)
        IRaylevationDB database = Mock(IRaylevationDB)
        OriginTilesState previousState = new OriginTilesState([
                new OriginTileState("A", "old-etag", 0, 0, [])
        ])
        Path geoTiffPath = Path.of("src/test/resources/geotiff/60.010_54.008_-9.008_1.491.tif")

        when:
        service.updateRaylevationDB()

        then:
        1 * dbFactory.createLocked() >> database
        1 * database.getMetadata(_, _) >> previousState
        1 * downloadService.fetchETag(_) >> "new-etag"
        1 * downloadService.downloadTile(_, _) >> { origin, _ -> origin }
        1 * unpackService.unpackArchive(_, _) >> { origin, _ ->
            new OriginTile(origin.name, origin.splitX, origin.splitY, origin.etag, origin.etagPath, origin.archivePath, geoTiffPath)
        }
        1 * database.findTileNames(_) >> ["legacy-sub-1"]
        1 * database.removeTile("legacy-sub-1")
        1 * splitService.split(_, _)
        1 * database.save()
    }
}
