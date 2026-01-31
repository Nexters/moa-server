package com.moa.service.dto

data class TermsAgreementsResponse(
    val agreements: List<TermAgreementDto>,
    val hasRequiredTermsAgreed: Boolean,
)
