package com.moa.service.notification

import com.moa.common.exception.NotFoundException
import com.moa.entity.DailyWorkScheduleType
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.PayrollVersionRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.calculator.CompensationCalculator
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@Service
class NotificationEarningsService(
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val payrollVersionRepository: PayrollVersionRepository,
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
    private val compensationCalculator: CompensationCalculator,
) {
    fun calculateTodayEarnings(memberId: Long, date: LocalDate): BigDecimal {
        val policy = resolveMonthlyRepresentativePolicyOrNull(memberId, date.year, date.monthValue)
            ?: throw NotFoundException()
        val payroll = resolveMonthlyRepresentativePayrollOrNull(memberId, date.year, date.monthValue)
            ?: throw NotFoundException()

        val override = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
        return compensationCalculator.calculateDailyEarnings(
            date = date,
            salaryType = payroll.salaryInputType,
            salaryAmount = payroll.salaryAmount,
            policy = policy,
            type = override?.type ?: DailyWorkScheduleType.WORK,
            clockInTime = override?.clockInTime,
            clockOutTime = override?.clockOutTime,
        )
    }

    private fun resolveMonthlyRepresentativePolicyOrNull(
        memberId: Long,
        year: Int,
        month: Int,
    ) = workPolicyVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        memberId,
        YearMonth.of(year, month).atEndOfMonth(),
    )

    private fun resolveMonthlyRepresentativePayrollOrNull(
        memberId: Long,
        year: Int,
        month: Int,
    ) = payrollVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        memberId,
        YearMonth.of(year, month).atEndOfMonth(),
    )
}
