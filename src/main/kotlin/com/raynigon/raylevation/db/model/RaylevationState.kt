package com.raynigon.raylevation.db.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * The metadata for the persisted Raylevation database
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
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
    // Written by the bean itself and used as type id on read (EXISTING_PROPERTY)
    @get:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val version: String = "1.0.0"
}
