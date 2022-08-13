package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.base.service.RaylevationDBFactory
import com.raynigon.raylevation.db.IRaylevationDB
import com.raynigon.raylevation.helper.FileHelper
import com.raynigon.raylevation.srtm.model.OriginTileConfig
import com.raynigon.raylevation.srtm.model.SRTMConfig
import com.raynigon.raylevation.base.service.RaylevationDBFactory
import com.raynigon.raylevation.db.IRaylevationDB
import com.raynigon.raylevation.helper.FileHelper
import com.raynigon.raylevation.srtm.model.OriginTileConfig
import com.raynigon.raylevation.srtm.model.SRTMConfig
import spock.lang.Specification

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
}
