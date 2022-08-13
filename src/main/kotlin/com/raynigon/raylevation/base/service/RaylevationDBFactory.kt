package com.raynigon.raylevation.base.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.raynigon.raylevation.db.IRaylevationDB
import com.raynigon.raylevation.db.RaylevationDB
import com.raynigon.raylevation.db.config.DatabaseConfig
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.nio.file.Files

/**
 * The RaylevationDBFactory provides methods to open the [IRaylevationDB] used
 * in the current context.
 */
interface RaylevationDBFactory {

    /**
     * Create the [IRaylevationDB] if not existing and return it in a read only state
     */
    fun create(): IRaylevationDB

    /**
     * Create the [IRaylevationDB] if not existing and return it in a read/write state
     */
    fun createLocked(): IRaylevationDB
}

/**
 * Implementation of [RaylevationDBFactory]
 */
@Service
@EnableConfigurationProperties(DatabaseConfig::class)
class RaylevationDBFactoryImpl(
    private val databaseConfig: DatabaseConfig,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper
) : RaylevationDBFactory {

    override fun create(): IRaylevationDB {
        // If the Database does not exist yet, we need to create it in locked mode
        if (!Files.exists(databaseConfig.path.resolve(RaylevationDB.STATE_FILE_NAME))) {
            createLocked().close()
        }
        val database = RaylevationDB(databaseConfig, meterRegistry, objectMapper)
        database.open()
        return database
    }

    override fun createLocked(): IRaylevationDB {
        val database = RaylevationDB(databaseConfig, meterRegistry, objectMapper)
        database.open(locked = true)
        return database
    }
}
