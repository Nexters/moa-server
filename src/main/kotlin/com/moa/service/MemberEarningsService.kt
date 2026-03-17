package com.moa.service

import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.SalaryInputType
import com.moa.entity.WorkPolicyVersion
import com.moa.repository.PayrollVersionRepository
import com.moa.service.calculator.CompensationCalculator
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 회원 단위의 급여 계산 유스케이스를 조합하는 서비스입니다.
 *
 * 급여 계약 이력 조회와 근무 정책 해석을 바탕으로 기준 월급과 특정 일자의 소득 계산을 조립합니다.
 * 실제 계산 공식은 [com.moa.service.calculator.CompensationCalculator]에 위임하고, 이 서비스는 어떤 데이터를 기준으로 계산할지를 결정합니다.
 */
@Service
class MemberEarningsService(
    private val payrollVersionRepository: PayrollVersionRepository,
    private val compensationCalculator: CompensationCalculator,
) {
    /**
     * 지정된 날짜가 속한 월을 기준으로 회원의 기준 월급을 계산합니다.
     *
     * 주어진 날짜가 속한 달의 마지막 날을 기준으로, 해당 시점에 유효한 가장 최근의 급여 정보를 바탕으로 계산합니다.
     * 급여 유형이 연봉([com.moa.entity.SalaryInputType.ANNUAL])인 경우 12로 나눈 후 소수점 첫째 자리에서 반올림(HALF_UP)한 값을 반환하며,
     * 월급([com.moa.entity.SalaryInputType.MONTHLY])인 경우 계약된 금액을 그대로 반환합니다.
     *
     * @param memberId 회원의 고유 식별자
     * @param date 기준 날짜 (이 날짜가 속한 월의 마지막 날을 기준으로 유효한 급여 정책을 적용합니다)
     * @return 계산된 기준 월급 금액. 해당 월에 적용할 수 있는 급여 정보가 없으면 `0`을 반환합니다.
     */
    fun calculateStandardSalary(memberId: Long, date: LocalDate): BigDecimal {
        val payroll = findPayrollForMonth(memberId, date) ?: return BigDecimal.ZERO

        return when (payroll.salaryInputType) {
            SalaryInputType.ANNUAL -> payroll.salaryAmount.toBigDecimal()
                .divide(BigDecimal(12), 0, RoundingMode.HALF_UP)

            SalaryInputType.MONTHLY -> payroll.salaryAmount.toBigDecimal()
        }
    }

    /**
     * 특정 일자에 대한 회원의 일일 발생 소득을 계산합니다.
     *
     * 이 메서드는 회원의 근무 정책과 실제 출퇴근 시간을 비교하여 일일 소득을 산출합니다.
     * 다음과 같은 규칙이 적용됩니다:
     * - 근무 일정이 없는 경우([com.moa.entity.DailyWorkScheduleType.NONE]) 소득은 `0`으로 계산됩니다.
     * - 실제 출근 시간([clockInTime])과 퇴근 시간([clockOutTime])이 모두 제공된 경우, 정책상의 소정 근로 시간과
     * 실제 근로 시간을 비율로 산정하여 급여를 일할 계산합니다.
     * - 출퇴근 시간 중 하나라도 누락된 경우, 해당 일자의 기본 일급(Daily Rate)을 그대로 반환합니다.
     *
     * @param memberId 회원의 고유 식별자
     * @param date 소득을 계산할 대상 일자
     * @param policy 대상 일자에 적용되는 근무 정책 (소정 근로 시간, 근무일 등 포함)
     * @param type 대상 일자의 근무 일정 유형 (예: 근무, 휴무 등)
     * @param clockInTime 실제 출근 시간. (기록이 없을 경우 `null`)
     * @param clockOutTime 실제 퇴근 시간. (기록이 없을 경우 `null`)
     * @return 계산된 일일 소득 금액. 해당 월에 적용할 수 있는 급여 정보가 없으면 `0`을 반환합니다.
     */
    fun calculateDailyEarnings(
        memberId: Long,
        date: LocalDate,
        policy: WorkPolicyVersion,
        type: DailyWorkScheduleType,
        clockInTime: LocalTime?,
        clockOutTime: LocalTime?,
    ): BigDecimal {
        if (type == DailyWorkScheduleType.NONE) return BigDecimal.ZERO

        val payroll = findPayrollForMonth(memberId, date) ?: return BigDecimal.ZERO

        val dailyRate = compensationCalculator.calculateDailyRate(
            targetDate = date,
            salaryType = payroll.salaryInputType,
            salaryAmount = payroll.salaryAmount,
            workDays = policy.workdays.map { it.dayOfWeek }.toSet(),
        )
        if (dailyRate == BigDecimal.ZERO) return dailyRate

        if (clockInTime != null && clockOutTime != null) {
            val policyMinutes = compensationCalculator.calculateWorkMinutes(
                policy.clockInTime, policy.clockOutTime,
            )
            val actualMinutes = compensationCalculator.calculateWorkMinutes(clockInTime, clockOutTime)
            return compensationCalculator.calculateEarnings(dailyRate, policyMinutes, actualMinutes)
        }

        return dailyRate
    }

    /**
     * 월 집계 응답의 `standardMinutes`를 계산합니다.
     *
     * 기준 근무 시간은 정책의 일일 소정 근로 시간과 해당 기간의 근무일 수를 곱해 산출합니다.
     * 계산 공식 자체는 [CompensationCalculator]에 위임하고, 이 메서드는 월 집계 용어에 맞는 진입점을 제공합니다.
     */
    fun calculateStandardMinutes(
        policy: WorkPolicyVersion,
        start: LocalDate,
        endInclusive: LocalDate,
    ): Long {
        val standardDailyMinutes = compensationCalculator.calculateWorkMinutes(
            policy.clockInTime, policy.clockOutTime,
        )
        val standardWorkDaysCount = compensationCalculator.getWorkDaysInPeriod(
            start = start,
            end = endInclusive.plusDays(1),
            workDays = policy.workdays.map { it.dayOfWeek }.toSet(),
        )
        return standardDailyMinutes * standardWorkDaysCount
    }

    /**
     * 월 집계 응답의 `workedMinutes`를 계산합니다.
     *
     * `WorkdayService`가 근무 시간 계산 공식을 직접 알지 않도록,
     * 실제 출퇴근 시각을 분 단위 근무 시간으로 환산하는 책임을 이 서비스로 모읍니다.
     */
    fun calculateWorkedMinutes(clockInTime: LocalTime, clockOutTime: LocalTime): Long =
        compensationCalculator.calculateWorkMinutes(clockInTime, clockOutTime)

    /**
     * 월 기준 급여 버전 조회 규칙을 한 곳으로 고정하기 위한 헬퍼입니다.
     *
     * 월급 계산과 일소득 계산이 모두 같은 기준일을 사용해야 하므로,
     * "해당 월 말일 시점에 유효한 최신 급여"라는 규칙을 중복 없이 재사용합니다.
     */
    private fun findPayrollForMonth(memberId: Long, date: LocalDate) =
        payrollVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            memberId, YearMonth.from(date).atEndOfMonth(),
        )
}
