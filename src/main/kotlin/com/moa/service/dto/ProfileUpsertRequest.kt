package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class ProfileUpsertRequest(
    @field:NotBlank
    val nickname: String,
    @field:NotBlank
    val workplace: String,
)
