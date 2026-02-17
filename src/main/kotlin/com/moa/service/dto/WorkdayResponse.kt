package com.moa.service.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDate
import java.time.LocalTime

data class WorkdayResponse(
    val date: LocalDate,
    @JsonFormat(pattern = "HH:mm")
    val clockInTime: LocalTime,
    @JsonFormat(pattern = "HH:mm")
    val clockOutTime: LocalTime,
)
