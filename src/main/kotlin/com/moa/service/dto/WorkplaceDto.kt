package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class WorkplaceDto(
    @field:NotBlank
    val name: String,
)
