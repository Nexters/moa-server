package com.moa.service.dto

import io.swagger.v3.oas.annotations.media.Schema

data class OnboardingStatusResponse(
    @field:Schema(nullable = true)
    val profile: ProfileResponse?,
    @field:Schema(nullable = true)
    val payroll: PayrollResponse?,
    @field:Schema(nullable = true)
    val workPolicy: WorkPolicyResponse?,
    val hasRequiredTermsAgreed: Boolean,
)
