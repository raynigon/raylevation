package com.raynigon.raylevation.infrastructure.kotlin

import com.github.davidmoten.rtree.geometry.Point
import com.github.davidmoten.rtree.geometry.Rectangle
import com.github.davidmoten.rtree.geometry.internal.PointDouble
import com.github.davidmoten.rtree.geometry.internal.RectangleDouble
import com.raynigon.raylevation.db.tile.IRaylevationTile
import com.raynigon.raylevation.infrastructure.model.GeoPoint

/**
 * Create a [Point] in a kartesian system for this [GeoPoint].
 * The x value is the longitude and the y value is the latitude
 *
 * @return the [Point] representing the [GeoPoint] in a kartesian system
 */
fun GeoPoint.toPointDouble(): Point {
    return PointDouble.create(this.longitude, this.latitude)
}

/**
 * Create a bounding [Rectangle] for this tile.
 * The x values are the longitudes and the y values are the latitudes
 *
 * @return the bounding [Rectangle] for this tile
 */
fun IRaylevationTile.toRectangle(): Rectangle {
    return RectangleDouble.create(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax)
}
