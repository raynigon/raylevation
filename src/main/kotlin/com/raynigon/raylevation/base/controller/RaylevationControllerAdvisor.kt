package com.raynigon.raylevation.base.controller

import com.raynigon.raylevation.infrastructure.model.ApiError
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus

@ControllerAdvice(assignableTypes = [RaylevationController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class RaylevationControllerAdvisor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler
    fun handleHttpMessageNotReadableException(exception: HttpMessageNotReadableException): ResponseEntity<ApiError> {
        logger.info("Given request body is not valid", exception)
        return ApiError(
            HttpStatus.BAD_REQUEST,
            "InvalidBody",
            "Given request body is not valid"
        ).toResponseEntity()
    }
}
