package com.moa.service.dto

data class CalendarResponse(
    val earnings: MonthlyEarningsResponse,
    val schedules: List<WorkdayResponse>,
)
