package com.moa.service.calculator

import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.SalaryInputType
import com.moa.entity.WorkPolicyVersion
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

/**
 * 보상 산정에 필요한 계산 공식을 담당하는 서비스입니다.
 *
 * 외부 데이터를 조회하지 않고, 전달받은 급여 금액과 근무 정보만으로
 * 기준 월급 환산, 기준 일급 산정, 근무 시간 비례 소득 계산을 수행합니다.
 */
@Service
class CompensationCalculator {
    companion object {
        private const val MONEY_SCALE = 10
    }

    /**
     * 주어진 기간의 기준 근무 시간을 계산합니다.
     *
     * 정책의 일일 소정 근로 시간을 구한 뒤, 해당 기간에 포함되는 근무일 수를 곱해 기준 근무 시간을 산출합니다.
     *
     * @param policy 집계 대상 기간에 적용되는 근무 정책
     * @param start 계산 시작 일자. 포함 기준입니다.
     * @param endInclusive 계산 종료 일자. 포함 기준입니다.
     * @return 계산된 기준 근무 시간의 총합입니다.
     */
    fun calculateStandardMinutes(
        policy: WorkPolicyVersion,
        start: LocalDate,
        endInclusive: LocalDate,
    ): Long {
        val standardDailyMinutes = calculateWorkMinutes(policy.clockInTime, policy.clockOutTime)
        val standardWorkDaysCount = getWorkDaysInPeriod(
            start = start,
            end = endInclusive.plusDays(1),
            workDays = policy.workdays.map { it.dayOfWeek }.toSet(),
        )
        return standardDailyMinutes * standardWorkDaysCount
    }

    /**
     * 출근 시간과 퇴근 시간 사이의 총 근무 시간을 분(minute) 단위로 계산합니다.
     *
     * 퇴근 시간이 출근 시간보다 빠른 경우에만 익일 퇴근으로 간주하여 24시간을 더합니다.
     * 시각이 같으면 근무 시간은 0분으로 계산합니다.
     *
     * @param clockIn 출근 시간
     * @param clockOut 퇴근 시간
     * @return 총 근무 시간 (분 단위)
     */
    fun calculateWorkMinutes(clockIn: LocalTime, clockOut: LocalTime): Long {
        val minutes = Duration.between(clockIn, clockOut).toMinutes()
        return if (minutes < 0) minutes + 24 * 60 else minutes
    }

    /**
     * 입력된 급여 정보를 기준 월급으로 환산합니다.
     *
     * 급여 유형이 연봉([com.moa.entity.SalaryInputType.ANNUAL])인 경우 12로 나눈 후 소수점 첫째 자리에서 반올림(HALF_UP)한 값을 반환하며,
     * 월급([com.moa.entity.SalaryInputType.MONTHLY])인 경우 계약된 금액을 그대로 반환합니다.
     *
     * @param salaryType 급여 산정 방식
     * @param salaryAmount 책정된 급여 금액
     * @return 계산된 기준 월급 금액입니다.
     */
    fun calculateStandardSalary(
        salaryType: SalaryInputType,
        salaryAmount: Long,
    ): BigDecimal {
        return when (salaryType) {
            SalaryInputType.ANNUAL -> salaryAmount.toBigDecimal().divide(BigDecimal(12), 0, RoundingMode.HALF_UP)
            SalaryInputType.MONTHLY -> salaryAmount.toBigDecimal()
        }
    }

    /**
     * 특정 일자에 대한 회원의 일일 발생 소득을 계산합니다.
     *
     * 전달받은 급여 정보와 근무 정책을 기준으로 해당 날짜의 일급을 구한 뒤,
     * 실제 출퇴근 기록이 있으면 소정 근로 시간 대비 실제 근로 시간을 반영해 최종 금액을 계산합니다.
     * 근무 일정이 없는 경우([com.moa.entity.DailyWorkScheduleType.NONE])에는 `0`을 반환하고,
     * 출퇴근 시간 중 하나라도 누락된 경우에는 기본 일급을 그대로 반환합니다.
     *
     * @param date 소득을 계산할 대상 일자
     * @param salaryType 급여 산정 방식
     * @param salaryAmount 책정된 급여 금액
     * @param policy 대상 일자에 적용되는 근무 정책
     * @param type 대상 일자의 근무 일정 유형
     * @param clockInTime 실제 출근 시간. 기록이 없으면 `null`입니다.
     * @param clockOutTime 실제 퇴근 시간. 기록이 없으면 `null`입니다.
     * @return 계산된 일일 소득 금액입니다.
     */
    fun calculateDailyEarnings(
        date: LocalDate,
        salaryType: SalaryInputType,
        salaryAmount: Long,
        policy: WorkPolicyVersion,
        type: DailyWorkScheduleType,
        clockInTime: LocalTime?,
        clockOutTime: LocalTime?,
    ): BigDecimal {
        if (type == DailyWorkScheduleType.NONE) return BigDecimal.ZERO

        val dailyRate = calculateDailyRate(
            targetDate = date,
            salaryType = salaryType,
            salaryAmount = salaryAmount,
            workDays = policy.workdays.map { it.dayOfWeek }.toSet(),
        )
        if (dailyRate == BigDecimal.ZERO) return dailyRate

        if (clockInTime != null && clockOutTime != null) {
            val policyMinutes = calculateWorkMinutes(policy.clockInTime, policy.clockOutTime)
            val actualMinutes = calculateWorkMinutes(clockInTime, clockOutTime)
            return calculateEarnings(dailyRate, policyMinutes, actualMinutes)
        }

        return dailyRate
    }

    /**
     * 특정 일자가 속한 달의 기준 일급을 계산합니다.
     *
     * 월급 또는 연봉으로 입력된 금액을 월 기준 금액으로 환산한 뒤,
     * 해당 월의 총 소정 근로일수로 나누어 일급을 산출합니다.
     *
     * @param targetDate 기준 일자 (이 일자가 속한 월을 기준으로 총 근로일수를 계산합니다)
     * @param salaryType 급여 산정 방식 (연봉 또는 월급)
     * @param salaryAmount 책정된 급여 금액
     * @param workDays 주간 근무 요일 집합 (예: 월, 수, 금)
     * @return 계산된 기준 일급 금액. 해당 월에 근무일이 전혀 없는 경우 `0`을 반환합니다.
     */
    fun calculateDailyRate(
        targetDate: LocalDate,
        salaryType: SalaryInputType,
        salaryAmount: Long,
        workDays: Set<DayOfWeek>
    ): BigDecimal {
        val monthlySalary = when (salaryType) {
            SalaryInputType.ANNUAL -> salaryAmount.toBigDecimal().divide(BigDecimal(12), 0, RoundingMode.HALF_UP)
            SalaryInputType.MONTHLY -> salaryAmount.toBigDecimal()
        }

        val yearMonth = YearMonth.from(targetDate)
        val periodStart = yearMonth.atDay(1)
        val periodEnd = yearMonth.atEndOfMonth().plusDays(1)

        val workDaysCount = getWorkDaysInPeriod(periodStart, periodEnd, workDays)

        if (workDaysCount == 0) return BigDecimal.ZERO

        return monthlySalary.divide(BigDecimal(workDaysCount), MONEY_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * 기준 일급과 실제 근무 시간을 바탕으로 최종 발생 소득을 계산합니다.
     *
     * 기준 일급을 정책상 소정 근로 시간으로 나눈 분당 단가를 구한 뒤,
     * 실제 근무 시간에 비례하도록 곱하여 최종 금액을 산출합니다.
     *
     * @param dailyRate 산정된 기준 일급
     * @param policyWorkMinutes 정책상 소정 근로 시간 (분 단위)
     * @param actualWorkMinutes 실제 근로 시간 (분 단위)
     * @return 계산된 최종 소득 금액. 정책상 근로 시간이 0 이하이면 기준 일급을 그대로 반환합니다.
     */
    fun calculateEarnings(
        dailyRate: BigDecimal,
        policyWorkMinutes: Long,
        actualWorkMinutes: Long,
    ): BigDecimal {
        if (policyWorkMinutes <= 0) return dailyRate
        val minuteRate = dailyRate.divide(BigDecimal(policyWorkMinutes), MONEY_SCALE, RoundingMode.HALF_UP)
        return minuteRate.multiply(BigDecimal(actualWorkMinutes))
    }

    /**
     * 특정 기간 안에 포함되는 실제 근무일 수를 계산합니다.
     *
     * 기준 일급이나 기준 근무 시간 합계를 계산할 때 사용하는 근무일 수 계산 로직입니다.
     * 시작일은 포함하고 종료일은 제외하는 반개구간 규칙을 사용합니다.
     *
     * @param start 계산 시작 일자 (포함)
     * @param end 계산 종료 일자 (제외)
     * @param workDays 포함시킬 근무 요일 집합
     * @return 기간 내 포함된 근무일의 총 개수
     */
    fun getWorkDaysInPeriod(
        start: LocalDate,
        end: LocalDate,
        workDays: Set<DayOfWeek>
    ): Int = generateSequence(start) { it.plusDays(1) }
        .takeWhile { it.isBefore(end) }
        .count { it.dayOfWeek in workDays }
}
