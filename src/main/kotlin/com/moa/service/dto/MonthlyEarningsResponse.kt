package com.moa.service.dto

data class MonthlyEarningsResponse(
    val totalEarnings: Int,
    val defaultSalary: Int,
    val workedMinutes: Long,
    val standardMinutes: Long,
)
