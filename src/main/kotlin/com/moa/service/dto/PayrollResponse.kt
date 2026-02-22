package com.moa.service.dto

import com.moa.entity.SalaryInputType

data class PayrollResponse(
    val salaryInputType: SalaryInputType,
    val salaryAmount: Long,
)
