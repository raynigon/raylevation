package com.raynigon.raylevation.infrastructure.configuration

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * The SchedulingConfiguration defines if scheduling jobs is enabled or not
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = ["app.scheduling.enable"], havingValue = "true", matchIfMissing = true)
class SchedulingConfiguration
