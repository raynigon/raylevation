package com.raynigon.raylevation.db.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.nio.file.Path

@ConstructorBinding
@ConfigurationProperties("app.raylevation.db")
data class DatabaseConfig(
    val path: Path,
    val cacheTileCount: Int
)
