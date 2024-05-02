package com.raynigon.raylevation.infrastructure.controller

import com.raynigon.raylevation.infrastructure.model.ApiError
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.context.request.WebRequest
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * This is a generic ControllerAdvice that handles all common MVC exceptions and formats them
 * as [ApiError].
 *
 * These are mostly a relict of accidental complexity: we don't want them in our project,
 * but they could happen because we use HTTP. They are not of interest to our clients and
 * don't require further documentation.
 */
@Suppress("UNCHECKED_CAST")
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class FallbackControllerAdvisor : ResponseEntityExceptionHandler() {
    private val structuredLogger = LoggerFactory.getLogger(this::class.java)

    // wraps responses from ResponseEntityExceptionHandler in our ApiError format
    override fun handleExceptionInternal(
        ex: java.lang.Exception,
        body: Any?,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> = ApiError(
        super.handleExceptionInternal(ex, body, headers, status, request)!!.statusCode.let { HttpStatus.valueOf(it.value()) },
        body.toString(),
        emptyList()
    ).toResponseEntity() as ResponseEntity<Any>

    /**
     * Handle all generic [Exception] by generating an [ApiError] with Status [HttpStatus.INTERNAL_SERVER_ERROR].
     */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: Exception): ResponseEntity<ApiError> {
        structuredLogger.error("An unexpected ${exception::class.simpleName} occurred", exception)
        return ApiError(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "UnexpectedException",
            "An unexpected Exception occurred" // do not expose information here
        ).toResponseEntity()
    }

    /**
     * Handle a [AccessDeniedException] by generating an [ApiError] with Status [HttpStatus.FORBIDDEN].
     */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleAccessDeniedException(exception: AccessDeniedException): ResponseEntity<ApiError> {
        structuredLogger.warn("Access was forbidden", exception)
        return ApiError(
            HttpStatus.FORBIDDEN,
            "AccessDenied",
            "Access to the resource is forbidden"
        ).toResponseEntity()
    }

    /**
     * Handle a [ResponseStatusException] by using the given status and reason phrase to generate an [ApiError].
     */
    @ExceptionHandler
    fun handleResponseStatusException(exception: ResponseStatusException): ResponseEntity<ApiError> {
        structuredLogger.info("A ResponseStatusException occurred", exception)
        return ApiError(
            exception.statusCode,
            exception.statusCode.toString(),
            "An unspecified Error occurred" // do not expose information here
        ).toResponseEntity()
    }
}
