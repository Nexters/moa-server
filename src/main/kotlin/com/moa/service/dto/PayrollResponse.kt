package com.moa.service.dto

import com.moa.entity.SalaryInputType
import java.time.LocalDate

data class PayrollResponse(
    val salaryInputType: SalaryInputType,
    val salaryAmount: Long,
)
