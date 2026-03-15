package com.moa.service.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.moa.entity.DailyEventType
import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.DailyWorkStatusType
import java.time.LocalDate
import java.time.LocalTime

data class WorkdayResponse(
    val date: LocalDate,
    val type: DailyWorkScheduleType,
    val status: DailyWorkStatusType,
    val events: List<DailyEventType> = emptyList(),
    val dailyPay: Int,
    @field:JsonFormat(pattern = "HH:mm")
    val clockInTime: LocalTime? = null,
    @field:JsonFormat(pattern = "HH:mm")
    val clockOutTime: LocalTime? = null,
)
