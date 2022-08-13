package com.raynigon.raylevation.srtm.service

import com.raynigon.raylevation.db.gdal.GDALTile
import com.raynigon.raylevation.db.tile.TileBounds
import com.raynigon.raylevation.srtm.model.OriginTile
import org.springframework.stereotype.Service
import java.nio.file.Files

interface TileSplitService {

    fun split(tile: OriginTile, callback: (GDALTile, OriginTile) -> Unit)
}

@Service
class TileSplitServiceImpl : TileSplitService {

    override fun split(tile: OriginTile, callback: (GDALTile, OriginTile) -> Unit) {
        val gdalTile = GDALTile(tile.geoTiffPath!!)
        val bounds = gdalTile.bounds
        val cx = (bounds.xMax - bounds.xMin) / tile.splitX
        val cy = (bounds.yMax - bounds.yMin) / tile.splitY
        for (y in 1..tile.splitY) {
            for (x in 1..tile.splitX) {
                val x1 = bounds.xMin + cx * (x - 1)
                val x2 = bounds.xMax - cx * (tile.splitX - x)
                val y1 = bounds.yMin + cy * (y - 1)
                val y2 = bounds.yMax - cy * (tile.splitY - y)
                val subBounds = TileBounds(y2, x1, y1, x2)
                val subTile = gdalTile.subTile(subBounds)
                callback(subTile, tile)
                // Delete SubTile after if was added to the database
                Files.delete(subTile.path)
            }
        }
    }
}
