package com.moa.service.dto

data class MonthlyEarningsResponse(
    val workedEarnings: Int,
    val standardSalary: Int,
    val workedMinutes: Long,
    val standardMinutes: Long,
)
