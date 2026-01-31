package com.moa.service.dto

import jakarta.validation.constraints.NotBlank

data class TermAgreementUpsertDto(
    @field:NotBlank
    val code: String,
    val agreed: Boolean,
)
