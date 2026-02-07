package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class AppleSignInUpRequest(
    @field:NotBlank
    val idToken: String,
    val fcmDeviceToken: String? = null,
)
