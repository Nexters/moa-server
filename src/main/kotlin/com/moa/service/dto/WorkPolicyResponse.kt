package com.moa.service.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.moa.entity.Workday
import java.time.LocalDate
import java.time.LocalTime

data class WorkPolicyResponse(
    val workdays: List<Workday>,
    @JsonFormat(pattern = "HH:mm")
    val clockInTime: LocalTime,
    @JsonFormat(pattern = "HH:mm")
    val clockOutTime: LocalTime,
)
