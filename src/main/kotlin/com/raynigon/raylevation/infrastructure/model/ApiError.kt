package com.raynigon.raylevation.infrastructure.model

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity

/**
 * A generic data transfer object, which represents an error.
 * This should only be returned if the status code is greater than 399.
 *
 * @param status  The HTTP Status of this error
 * @param message Reason Phrase of the HTTP Status or descriptive error message
 * @param errors  Detailed error descriptions for each occurred error
 */
data class ApiError(val status: HttpStatusCode, val message: String, val errors: List<String>) {
    constructor(status: HttpStatusCode, message: String, error: String) : this(status, message, listOf(error))

    /**
     * Generate a [ResponseEntity] from this API Error
     *
     * @return A [ResponseEntity] containing this object as body and the [status] as HTTP response status
     */
    fun toResponseEntity() = ResponseEntity(this, this.status)
}
