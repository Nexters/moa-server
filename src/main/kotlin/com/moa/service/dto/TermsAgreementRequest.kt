package com.moa.service.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class TermsAgreementRequest(
    @field:NotEmpty
    @field:Valid
    val agreements: List<TermAgreementUpsertDto>,
)
