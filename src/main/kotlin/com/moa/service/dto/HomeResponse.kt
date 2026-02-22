package com.moa.service.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.moa.entity.DailyWorkScheduleType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalTime

data class HomeResponse(
    @field:Schema(nullable = true)
    val workplace: String?,
    val workedEarnings: Int,
    val standardSalary: Int,
    val dailyPay: Int,
    val type: DailyWorkScheduleType,
    @field:Schema(nullable = true)
    @field:JsonFormat(pattern = "HH:mm")
    val clockInTime: LocalTime?,
    @field:Schema(nullable = true)
    @field:JsonFormat(pattern = "HH:mm")
    val clockOutTime: LocalTime?,
)
