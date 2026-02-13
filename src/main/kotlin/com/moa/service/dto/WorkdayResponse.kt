package com.moa.service.dto

import java.time.LocalDate
import java.time.LocalTime

data class WorkdayResponse(
    val date: LocalDate,
    val clockInTime: LocalTime,
    val clockOutTime: LocalTime,
)
