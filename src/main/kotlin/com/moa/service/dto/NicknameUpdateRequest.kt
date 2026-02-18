package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class NicknameUpdateRequest(
    @field:NotBlank
    val nickname: String,
)
