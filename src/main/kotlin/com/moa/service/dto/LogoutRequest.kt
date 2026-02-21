package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class LogoutRequest(
    @field:NotBlank
    val fcmDeviceToken: String,
)
