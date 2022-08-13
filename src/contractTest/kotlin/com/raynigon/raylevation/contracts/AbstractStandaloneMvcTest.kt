package com.raynigon.raylevation.contracts

import com.raynigon.raylevation.infrastructure.configuration.JacksonConfiguration
import com.raynigon.raylevation.infrastructure.controller.FallbackControllerAdvisor
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.core.convert.converter.Converter
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.format.support.FormattingConversionService
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.SpringValidatorAdapter
import javax.validation.Validation

/**
 * Base class for StandaloneMvc Tests, with correct message converter,
 * ControllerAdvices and no-op spring security.
 * Note that the StandaloneMvc cannot perform post processing on beans,
 * which in turn means that it's not possible to use bean validation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractStandaloneMvcTest<T> {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    private val conversionService: FormattingConversionService = DefaultFormattingConversionService()

    abstract val controller: T

    open val additionalControllerAdvices: Array<Any> = arrayOf()

    open val additionalConverters: Array<Converter<*, *>> = arrayOf()

    @BeforeAll
    open fun setup() {
        val messageConverter = MappingJackson2HttpMessageConverter()
        messageConverter.objectMapper = JacksonConfiguration().objectMapper()

        additionalConverters.forEach { conversionService.addConverter(it) }

        val standaloneMockMvcBuilder = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(
                FallbackControllerAdvisor(),
                *additionalControllerAdvices
            )
            .setConversionService(conversionService)
            .setValidator(SpringValidatorAdapter(validator))
            .setMessageConverters(messageConverter)

        RestAssuredMockMvc.standaloneSetup(standaloneMockMvcBuilder)
    }
}
