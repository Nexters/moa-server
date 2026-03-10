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

/**
 * 직원의 급여 및 일일 소득을 계산하는 서비스입니다.
 * * 직원의 급여 계약 이력과 근무 정책을 기반으로 월 기본급과 특정 일자의 발생 소득을 계산합니다.
 */
@Service
class EarningsCalculator(
    private val payrollVersionRepository: PayrollVersionRepository,
) {
    /**
     * 지정된 날짜가 속한 월을 기준으로 직원의 기본 월급을 계산합니다.
     *
     * 주어진 날짜가 속한 달의 마지막 날을 기준으로, 해당 시점에 유효한 가장 최근의 급여 정보를 바탕으로 계산합니다.
     * 급여 유형이 연봉([SalaryType.YEARLY])인 경우 12로 나눈 후 소수점 첫째 자리에서 반올림(HALF_UP)한 값을 반환하며,
     * 월급([SalaryType.MONTHLY])인 경우 계약된 금액을 그대로 반환합니다.
     *
     * @param memberId 직원의 고유 식별자
     * @param date 기준 날짜 (이 날짜가 속한 월의 마지막 날을 기준으로 유효한 급여 정책을 적용합니다)
     * @return 계산된 기본 월급 금액. 적용할 수 있는 급여 정보가 존재하지 않으면 `null`을 반환합니다.
     */
    fun getDefaultMonthlySalary(memberId: Long, date: LocalDate): Long? {
        val lastDayOfMonth = YearMonth.from(date).atEndOfMonth()
        val payroll = payrollVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                memberId, lastDayOfMonth,
            ) ?: return null

        return when (SalaryType.from(payroll.salaryInputType)) {
            SalaryType.YEARLY -> payroll.salaryAmount.toBigDecimal()
                .divide(BigDecimal(12), 0, RoundingMode.HALF_UP).toLong()

            SalaryType.MONTHLY -> payroll.salaryAmount
        }
    }

    /**
     * 특정 일자에 대한 직원의 일일 발생 소득을 계산합니다.
     *
     * 이 메서드는 직원의 근무 정책과 실제 출퇴근 시간을 비교하여 일일 소득을 산출합니다.
     * 다음과 같은 규칙이 적용됩니다:
     * - 근무 일정이 없는 경우([DailyWorkScheduleType.NONE]) 소득은 `0`으로 계산됩니다.
     * - 실제 출근 시간([clockInTime])과 퇴근 시간([clockOutTime])이 모두 제공된 경우, 정책상의 소정 근로 시간과
     * 실제 근로 시간을 비율로 산정하여 급여를 일할 계산합니다.
     * - 출퇴근 시간 중 하나라도 누락된 경우, 해당 일자의 기본 일급(Daily Rate)을 그대로 반환합니다.
     *
     * @param memberId 직원의 고유 식별자
     * @param date 소득을 계산할 대상 일자
     * @param policy 대상 일자에 적용되는 근무 정책 (소정 근로 시간, 근무일 등 포함)
     * @param type 대상 일자의 근무 일정 유형 (예: 근무, 휴무 등)
     * @param clockInTime 실제 출근 시간. (기록이 없을 경우 `null`)
     * @param clockOutTime 실제 퇴근 시간. (기록이 없을 경우 `null`)
     * @return 계산된 일일 소득 금액. 적용할 수 있는 급여 정보가 존재하지 않으면 `null`을 반환합니다.
     */
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
