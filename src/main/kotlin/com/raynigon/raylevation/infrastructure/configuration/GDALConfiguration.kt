package com.raynigon.raylevation.infrastructure.configuration

import org.gdal.gdal.gdal
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

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
