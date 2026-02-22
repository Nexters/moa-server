package com.moa.service.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.moa.entity.DailyWorkScheduleType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime

data class WorkdayResponse(
    val date: LocalDate,
    val type: DailyWorkScheduleType,
    val dailyPay: Int,
    @field:Schema(nullable = true)
    @field:JsonFormat(pattern = "HH:mm")
    val clockInTime: LocalTime? = null,
    @field:Schema(nullable = true)
    @field:JsonFormat(pattern = "HH:mm")
    val clockOutTime: LocalTime? = null,
)
