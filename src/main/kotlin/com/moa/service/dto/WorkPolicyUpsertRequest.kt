package com.moa.service.dto

import com.moa.entity.Workday
import jakarta.validation.constraints.NotEmpty
import java.time.LocalTime

data class WorkPolicyUpsertRequest(
    @field:NotEmpty
    val workdays: Set<Workday>,

    val clockInTime: LocalTime,
    val clockOutTime: LocalTime,
)
