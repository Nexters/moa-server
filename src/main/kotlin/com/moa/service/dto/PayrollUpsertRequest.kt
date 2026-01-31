package com.moa.service.dto

import com.moa.entity.SalaryInputType
import java.time.LocalDate

data class PayrollUpsertRequest(
    val effectiveFrom: LocalDate,
    val salaryInputType: SalaryInputType,
    val salaryAmount: Long,
    // "없으면 25일 고정"을 DTO에서 null 허용으로 처리
    val paydayDay: Int? = null,
)
