package com.raynigon.raylevation.infrastructure.kotlin

import com.github.davidmoten.rtree.geometry.Point
import com.github.davidmoten.rtree.geometry.Rectangle
import com.github.davidmoten.rtree.geometry.internal.PointDouble
import com.github.davidmoten.rtree.geometry.internal.RectangleDouble
import com.raynigon.raylevation.db.tile.IRaylevationTile
import com.raynigon.raylevation.infrastructure.model.GeoPoint

fun GeoPoint.toPointDouble(): Point {
    return PointDouble.create(this.longitude, this.latitude)
}

fun IRaylevationTile.toRectangle(): Rectangle {
    return RectangleDouble.create(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax)
}
