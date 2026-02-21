package com.moa.service.dto

import com.moa.entity.DailyWorkScheduleType
import java.time.LocalDate

data class MonthlyWorkdayResponse(
    val date: LocalDate,
    val type: DailyWorkScheduleType,
)
