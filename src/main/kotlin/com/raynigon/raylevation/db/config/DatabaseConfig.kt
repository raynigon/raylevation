package com.raynigon.raylevation.db.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.nio.file.Path

/**
 * Data transfer object containing the configuration properties
 * of the raylevation database for this application context.
 */
@ConstructorBinding
@ConfigurationProperties("app.raylevation.db")
data class DatabaseConfig(
    val path: Path,
    val cacheTileCount: Int
)
