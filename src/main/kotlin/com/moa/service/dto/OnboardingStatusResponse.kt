package com.moa.service.dto

data class OnboardingStatusResponse(
    val hasRequiredTermsAgreed: Boolean,
    val profileCompleted: Boolean,
    val payrollCompleted: Boolean,
    val workPolicyCompleted: Boolean,
)
