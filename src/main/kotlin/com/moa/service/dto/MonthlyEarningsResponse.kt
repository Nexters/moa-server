package com.moa.service.dto

data class MonthlyEarningsResponse(
    val workedEarnings: Long,
    val standardSalary: Long,
    val workedMinutes: Long,
    val standardMinutes: Long,
)
