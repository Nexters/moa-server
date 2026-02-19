package com.moa.service.dto

import com.moa.entity.DailyWorkScheduleType
import java.time.LocalTime

data class WorkdayUpsertRequest(
    val type: DailyWorkScheduleType,
    val clockInTime: LocalTime? = null,
    val clockOutTime: LocalTime? = null,
)
