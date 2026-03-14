package com.moa.service.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.moa.entity.DailWorkStatusType
import com.moa.entity.DailyWorkScheduleType
import java.time.LocalTime

data class HomeResponse(
    val workplace: String?,
    val workedEarnings: Long,
    val standardSalary: Long,
    val dailyPay: Int,
    val type: DailyWorkScheduleType,
    val status: DailWorkStatusType,
    @field:JsonFormat(pattern = "HH:mm")
    val clockInTime: LocalTime?,
    @field:JsonFormat(pattern = "HH:mm")
    val clockOutTime: LocalTime?,
)
