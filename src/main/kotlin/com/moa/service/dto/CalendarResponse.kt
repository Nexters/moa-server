package com.moa.service.dto

import java.time.LocalDate

data class CalendarResponse(
    val earnings: MonthlyEarningsResponse,
    val schedules: List<WorkdayResponse>,
    val joinedAt: LocalDate,
)
