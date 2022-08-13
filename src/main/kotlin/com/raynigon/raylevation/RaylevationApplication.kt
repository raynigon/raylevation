package com.raynigon.raylevation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Application object used by Spring Boot
 */
@SpringBootApplication
class RaylevationApplication

/**
 * Spring Boot Entry Method
 * @param args    The command line arguments passed on application start
 */
fun main(args: Array<String>) {
    runApplication<RaylevationApplication>(*args)
}
