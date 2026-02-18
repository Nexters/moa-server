package com.moa.service.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class PaydayUpdateRequest(
    val paydayDay: Int,
)
