package com.moa.service.calculator

import com.moa.entity.SalaryInputType
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

/**
 * 보상 산정에 필요한 계산 공식을 제공하는 서비스입니다.
 *
 * 이 클래스는 외부 데이터를 조회하지 않고, 일급 산정과 근무 시간 환산 같은 순수 계산만 담당합니다.
 * 회원별 급여 버전 조회나 정책 선택은 [MemberEarningsService]가 맡고, 이 클래스는 계산 공식 자체를 캡슐화합니다.
 */
@Service
class CompensationCalculator {

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

        return monthlySalary.divide(BigDecimal(workDaysCount), 0, RoundingMode.HALF_UP)
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
        val minuteRate = dailyRate.divide(BigDecimal(policyWorkMinutes), 10, RoundingMode.HALF_UP)
        return minuteRate.multiply(BigDecimal(actualWorkMinutes)).setScale(0, RoundingMode.HALF_UP)
    }

    /**
     * 특정 기간 안에 포함되는 실제 근무일 수를 계산합니다.
     *
     * 월별 기준 일급이나 기준 근무 시간 합계를 계산할 때 공통으로 사용되는
     * "해당 기간 안에 몇 개의 근무 요일이 포함되는가"를 구하기 위한 헬퍼입니다.
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
