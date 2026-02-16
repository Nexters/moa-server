package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class OnboardingProfileUpsertRequest(
    @field:NotBlank
    val nickname: String,
)
