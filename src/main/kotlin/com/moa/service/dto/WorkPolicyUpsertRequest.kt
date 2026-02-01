package com.moa.service.dto

import com.moa.entity.Workday
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate
import java.time.LocalTime

data class WorkPolicyUpsertRequest(
    val effectiveFrom: LocalDate,

    @field:NotEmpty
    val workdays: Set<Workday>,

    val clockInTime: LocalTime,
    val clockOutTime: LocalTime,
    val breakStartTime: LocalTime,
    val breakEndTime: LocalTime,
)
