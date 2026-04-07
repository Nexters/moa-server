package com.moa.service.dto

import java.time.LocalDate

data class PublicHolidayCreateRequest(
    val date: LocalDate,
    val name: String,
)
