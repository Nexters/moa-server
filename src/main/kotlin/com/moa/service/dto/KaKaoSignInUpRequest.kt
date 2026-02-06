package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class KaKaoSignInUpRequest(
    @field:NotBlank
    val idToken: String,
    val fcmDeviceToken: String? = null,
) {
}
