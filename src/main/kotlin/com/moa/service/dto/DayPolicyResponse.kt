package com.moa.service.dto

import com.moa.entity.Workday
import java.time.LocalTime

data class DayPolicyResponse(
    val workday: Workday,
    val clockInTime: LocalTime,
    val clockOutTime: LocalTime,
    val breakStartTime: LocalTime,
    val breakEndTime: LocalTime,
)
