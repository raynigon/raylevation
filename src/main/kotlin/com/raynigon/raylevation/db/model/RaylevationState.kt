package com.raynigon.raylevation.db.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * The metadata for the persisted Raylevation database
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "version",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = RaylevationState1d0d0::class, name = "1.0.0"),
)
sealed interface IRaylevationState {
    val metadata: Map<String, Any>
    val tiles: List<RaylevationStateTile>
}

/**
 * Generates a new and empty [IRaylevationState] in the current format version
 */
fun emptyRaylevationState(): IRaylevationState =
    RaylevationState1d0d0(
        mapOf(),
        emptyList(),
    )

/**
 * Version 1.0.0 of the raylevation database state
 */
class RaylevationState1d0d0(
    override val metadata: Map<String, Any>,
    override val tiles: List<RaylevationStateTile>,
) : IRaylevationState {
    val version: String = "1.0.0"
}
