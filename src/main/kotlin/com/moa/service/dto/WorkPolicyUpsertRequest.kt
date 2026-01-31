package com.moa.service.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate

data class WorkPolicyUpsertRequest(
    val effectiveFrom: LocalDate,

    @field:NotEmpty
    @field:Valid
    val days: List<DayPolicyDto>,
)
