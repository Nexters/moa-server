package com.moa.service.dto

import java.time.LocalTime

data class WorkdayEditRequest(
    val clockOutTime: LocalTime,
)
