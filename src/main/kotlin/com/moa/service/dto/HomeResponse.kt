package com.moa.service.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalTime

data class HomeResponse(
    val workplace: String?,
    val workedEarnings: Int,
    val standardSalary: Int,
    val dailyPay: Int,
    @JsonFormat(pattern = "HH:mm")
    val clockInTime: LocalTime,
    @JsonFormat(pattern = "HH:mm")
    val clockOutTime: LocalTime,
)
