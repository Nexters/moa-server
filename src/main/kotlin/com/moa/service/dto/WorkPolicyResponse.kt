package com.moa.service.dto

import com.moa.entity.Workday
import java.time.LocalDate
import java.time.LocalTime

data class WorkPolicyResponse(
    val effectiveFrom: LocalDate,
    val workdays: List<Workday>,
    val clockInTime: LocalTime,
    val clockOutTime: LocalTime,
)
