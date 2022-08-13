package com.raynigon.raylevation.infrastructure.configuration

import org.gdal.gdal.gdal
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class GDALConfiguration {

    @PostConstruct
    fun init() {
        gdal.AllRegister()
    }
}
