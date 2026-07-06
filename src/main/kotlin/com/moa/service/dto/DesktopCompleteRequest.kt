package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class DesktopCompleteRequest(
    @field:NotBlank
    val exchangeCode: String,
)
