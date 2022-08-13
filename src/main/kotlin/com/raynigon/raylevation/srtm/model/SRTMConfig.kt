package com.raynigon.raylevation.srtm.model

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.nio.file.Path

@ConstructorBinding
@ConfigurationProperties("app.raylevation.srtm")
data class SRTMConfig(
    val workspace: Path,
    val url: String,
    @NestedConfigurationProperty
    val tiles: List<OriginTileConfig>,
    val saveDiskSpace: Boolean = false
)

data class OriginTileConfig(
    val name: String,
    val splitX: Int,
    val splitY: Int
)
