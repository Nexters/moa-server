package com.moa.service.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.moa.entity.DailyWorkScheduleType
import java.time.LocalTime

data class HomeResponse(
    val workplace: String?,
    val workedEarnings: Int,
    val standardSalary: Int,
    val dailyPay: Int,
    val type: DailyWorkScheduleType,
    @JsonFormat(pattern = "HH:mm")
    val clockInTime: LocalTime,
    @JsonFormat(pattern = "HH:mm")
    val clockOutTime: LocalTime,
)
