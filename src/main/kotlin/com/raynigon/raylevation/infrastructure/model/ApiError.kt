package com.raynigon.raylevation.infrastructure.model

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

data class ApiError(val status: HttpStatus, val message: String, val errors: List<String>) {
    constructor(status: HttpStatus, message: String, error: String) : this(status, message, listOf(error))

    fun toResponseEntity() = ResponseEntity(this, this.status)
}
