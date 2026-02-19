package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class FcmTokenRequest(
    @field:NotBlank
    val token: String,
)
