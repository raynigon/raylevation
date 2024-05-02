package com.raynigon.raylevation.infrastructure.configuration

import jakarta.annotation.PostConstruct
import org.gdal.gdal.gdal
import org.springframework.context.annotation.Configuration

/**
 * The GDALConfiguration loads the GDAL JNI Interface and registers all drivers.
 */
@Configuration
class GDALConfiguration {

    /**
     * Loads the GDAL JNI Interface and registers all drivers
     */
    @PostConstruct
    fun init() {
        gdal.AllRegister()
    }
}
