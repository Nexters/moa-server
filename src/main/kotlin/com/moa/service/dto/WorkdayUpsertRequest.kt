package com.moa.service.dto

import java.time.LocalTime

data class WorkdayUpsertRequest(
    val clockInTime: LocalTime,
    val clockOutTime: LocalTime,
)
