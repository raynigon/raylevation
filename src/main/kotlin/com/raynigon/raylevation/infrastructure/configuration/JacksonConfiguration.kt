package com.raynigon.raylevation.infrastructure.configuration

import com.raynigon.unit.api.jackson.UnitApiModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.text.SimpleDateFormat

/**
 * Configures the [ObjectMapper] used in the Application Context
 */
@Configuration
class JacksonConfiguration {
    /**
     * Configures the [ObjectMapper] used in the Application Context.
     * Java time support is built into Jackson 3 and does not need a separate module.
     *
     * @return A configured [JsonMapper] instance
     */
    @Bean
    fun objectMapper(): JsonMapper =
        JsonMapper
            .builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
            .disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .enable(EnumFeature.READ_ENUMS_USING_TO_STRING)
            .addModule(KotlinModule.Builder().build())
            .addModule(UnitApiModule())
            .defaultDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
            .build()
}
