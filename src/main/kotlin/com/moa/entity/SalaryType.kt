package com.moa.entity

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

/**
 * 급여 산정 방식을 정의하는 열거형입니다.
 */
enum class SalaryType {
    /** 연봉 기반 산정 방식 */
    YEARLY,

    /** 월급 기반 산정 방식 */
    MONTHLY;

    companion object {
        /**
         * 입력된 급여 유형([SalaryInputType])을 내부 처리용 급여 산정 방식([SalaryType])으로 변환합니다.
         *
         * @param inputType 외부에서 입력된 급여 유형 (예: ANNUAL, MONTHLY)
         * @return 매핑된 [SalaryType] 인스턴스
         */
        fun from(inputType: SalaryInputType): SalaryType = when (inputType) {
            SalaryInputType.ANNUAL -> YEARLY
            SalaryInputType.MONTHLY -> MONTHLY
        }
    }
}

/**
 * 급여, 일급, 근로 시간 등을 계산하는 순수 유틸리티 객체입니다.
 * * 외부 상태를 가지지 않으며, 제공된 파라미터만을 기반으로 계산을 수행합니다.
 */
object SalaryCalculator {

    /**
     * 특정 일자가 속한 달의 일일 급여(일급)를 계산합니다.
     *
     * 이 메서드는 직원의 월 기본급을 해당 월의 '총 소정 근로일수'로 나누어 일급을 산출합니다.
     * 연봉([SalaryType.YEARLY])인 경우 금액을 12로 나누어 월 기본급을 먼저 구합니다.
     * 최종 산출된 일급은 소수점 첫째 자리에서 반올림([RoundingMode.HALF_UP]) 처리됩니다.
     *
     * @param targetDate 기준 일자 (이 일자가 속한 월을 기준으로 총 근로일수를 계산합니다)
     * @param salaryType 급여 산정 방식 (연봉 또는 월급)
     * @param salaryAmount 책정된 급여 금액
     * @param workDays 직원의 주간 근무 요일 집합 (예: 월, 수, 금)
     * @return 계산된 일급 금액. 해당 월에 근무일이 전혀 없는 경우 `0`을 반환합니다.
     */
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

    /**
     * 출근 시간과 퇴근 시간 사이의 총 근무 시간을 분(minute) 단위로 계산합니다.
     *
     * 퇴근 시간이 출근 시간보다 빠르거나 같은 경우, 익일 퇴근(야간 교대근무 등)으로 간주하여
     * 자동으로 24시간(1440분)을 더하여 계산합니다.
     *
     * @param clockIn 출근 시간
     * @param clockOut 퇴근 시간
     * @return 총 근무 시간 (분 단위)
     */
    fun calculateWorkMinutes(clockIn: LocalTime, clockOut: LocalTime): Long {
        val minutes = Duration.between(clockIn, clockOut).toMinutes()
        return if (minutes <= 0) minutes + 24 * 60 else minutes
    }

    /**
     * 일급과 실제 근무 시간을 바탕으로 최종 발생 소득을 계산합니다.
     *
     * 소정 근로 시간 대비 실제 근로 시간의 비율을 일급에 곱하여 산출합니다.
     * 계산의 정확도를 위해 분당 급여율 산출 시 소수점 10자리까지 유지하며,
     * 최종 결과값에서 소수점 첫째 자리에서 반올림([RoundingMode.HALF_UP])하여 정수 단위로 맞춥니다.
     *
     * @param dailyRate 산정된 기준 일급
     * @param policyWorkMinutes 정책상 소정 근로 시간 (분 단위)
     * @param actualWorkMinutes 실제 근로 시간 (분 단위)
     * @return 계산된 최종 소득 금액. 정책상 근로 시간이 0 이하로 잘못 설정된 경우, 보정 없이 기준 일급을 그대로 반환합니다.
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
     * 특정 기간 내에 포함된 실제 근무일(조건에 맞는 요일)의 총 일수를 계산합니다.
     *
     * @param start 계산 시작 일자 (포함)
     * @param end 계산 종료 일자 (제외, Exclusive)
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
