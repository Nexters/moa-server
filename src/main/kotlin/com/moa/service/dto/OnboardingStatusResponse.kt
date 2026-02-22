package com.moa.service.dto

data class OnboardingStatusResponse(
    val profile: ProfileResponse?,
    val payroll: PayrollResponse?,
    val workPolicy: WorkPolicyResponse?,
    val hasRequiredTermsAgreed: Boolean,
)
