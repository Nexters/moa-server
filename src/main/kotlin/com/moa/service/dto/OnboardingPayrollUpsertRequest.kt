package com.moa.service.dto

import com.moa.entity.SalaryInputType

data class OnboardingPayrollUpsertRequest(
    val salaryInputType: SalaryInputType,
    val salaryAmount: Long,
)
