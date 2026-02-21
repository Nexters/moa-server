package com.moa.entity

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class SalaryType {
    YEARLY,
    MONTHLY;

    companion object {
        fun from(inputType: SalaryInputType): SalaryType = when (inputType) {
            SalaryInputType.ANNUAL -> YEARLY
            SalaryInputType.MONTHLY -> MONTHLY
        }
    }
}

object SalaryCalculator {

    fun calculateDailyRate(
        targetDate: LocalDate,
        salaryType: SalaryType,
        salaryAmount: Long,
        workDays: Set<DayOfWeek>
    ): BigDecimal {
        val monthlySalary = when (salaryType) {
            SalaryType.YEARLY -> salaryAmount.toBigDecimal().divide(BigDecimal(12), 0, RoundingMode.HALF_UP)
            SalaryType.MONTHLY -> salaryAmount.toBigDecimal()
        }

        val yearMonth = YearMonth.from(targetDate)
        val periodStart = yearMonth.atDay(1)
        val periodEnd = yearMonth.atEndOfMonth().plusDays(1) // exclusive

        val workDaysCount = getWorkDaysInPeriod(periodStart, periodEnd, workDays)

        if (workDaysCount == 0) return BigDecimal.ZERO

        return monthlySalary.divide(BigDecimal(workDaysCount), 0, RoundingMode.HALF_UP)
    }

    fun calculateWorkMinutes(clockIn: LocalTime, clockOut: LocalTime): Long {
        val minutes = Duration.between(clockIn, clockOut).toMinutes()
        return if (minutes <= 0) minutes + 24 * 60 else minutes
    }

    fun calculateEarnings(
        dailyRate: BigDecimal,
        policyWorkMinutes: Long,
        actualWorkMinutes: Long,
    ): BigDecimal {
        if (policyWorkMinutes <= 0) return dailyRate
        val minuteRate = dailyRate.divide(BigDecimal(policyWorkMinutes), 10, RoundingMode.HALF_UP)
        return minuteRate.multiply(BigDecimal(actualWorkMinutes)).setScale(0, RoundingMode.HALF_UP)
    }

    private fun getWorkDaysInPeriod(
        start: LocalDate,
        end: LocalDate,
        workDays: Set<DayOfWeek>
    ): Int {
        var count = 0
        var current = start
        while (current.isBefore(end)) {
            if (workDays.contains(current.dayOfWeek)) count++
            current = current.plusDays(1)
        }
        return count
    }
}
