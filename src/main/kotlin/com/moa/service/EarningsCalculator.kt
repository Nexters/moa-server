package com.moa.service

import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.SalaryCalculator
import com.moa.entity.SalaryType
import com.moa.entity.WorkPolicyVersion
import com.moa.repository.PayrollVersionRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Service
class EarningsCalculator(
    private val payrollVersionRepository: PayrollVersionRepository,
) {
    fun getDefaultMonthlySalary(memberId: Long, date: LocalDate): Int? {
        val lastDayOfMonth = YearMonth.from(date).atEndOfMonth()
        val payroll = payrollVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                memberId, lastDayOfMonth,
            ) ?: return null

        return when (SalaryType.from(payroll.salaryInputType)) {
            SalaryType.YEARLY -> payroll.salaryAmount.toBigDecimal()
                .divide(BigDecimal(12), 0, RoundingMode.HALF_UP).toInt()
            SalaryType.MONTHLY -> payroll.salaryAmount.toInt()
        }
    }

    fun calculateDailyEarnings(
        memberId: Long,
        date: LocalDate,
        policy: WorkPolicyVersion,
        type: DailyWorkScheduleType,
        clockInTime: LocalTime?,
        clockOutTime: LocalTime?,
    ): BigDecimal? {
        if (type == DailyWorkScheduleType.NONE) return BigDecimal.ZERO

        val lastDayOfMonth = YearMonth.from(date).atEndOfMonth()
        val payroll = payrollVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                memberId, lastDayOfMonth,
            ) ?: return null

        val dailyRate = SalaryCalculator.calculateDailyRate(
            targetDate = date,
            salaryType = SalaryType.from(payroll.salaryInputType),
            salaryAmount = payroll.salaryAmount,
            workDays = policy.workdays.map { it.dayOfWeek }.toSet(),
        )
        if (dailyRate == BigDecimal.ZERO) return dailyRate

        // 유급 휴가는 기본 일급 반환
        if (type == DailyWorkScheduleType.VACATION) return dailyRate

        if (clockInTime != null && clockOutTime != null) {
            val policyMinutes = SalaryCalculator.calculateWorkMinutes(
                policy.clockInTime, policy.clockOutTime,
            )
            val actualMinutes = SalaryCalculator.calculateWorkMinutes(clockInTime, clockOutTime)
            return SalaryCalculator.calculateEarnings(dailyRate, policyMinutes, actualMinutes)
        }

        return dailyRate
    }
}
