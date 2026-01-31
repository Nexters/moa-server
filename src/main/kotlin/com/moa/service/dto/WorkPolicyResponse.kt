package com.moa.service.dto

import java.time.LocalDate

data class WorkPolicyResponse(
    val effectiveFrom: LocalDate,
    val days: List<DayPolicyResponse>,
)
