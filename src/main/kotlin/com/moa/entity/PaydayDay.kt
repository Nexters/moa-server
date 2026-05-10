package com.moa.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 사용자가 설정한 급여일을 표현하는 값 객체입니다.
 *
 * 급여일은 1일부터 31일 사이의 값만 허용하며,
 * 실제 급여일 계산 시 월말 보정과 휴일 보정 규칙을 함께 제공합니다.
 */
@Embeddable
data class PaydayDay(
    @Column(name = "payday_day", nullable = false)
    var value: Int = 25,
) {
    init {
        require(value in 1..31) { "paydayDay must be between 1 and 31" }
    }

    /**
     * 특정 연월에 적용되는 실제 급여일을 계산합니다.
     *
     * 설정한 급여일이 해당 월에 없으면 말일로 보정하고,
     * 보정된 날짜가 주말 또는 공휴일이면 직전 영업일로 당깁니다.
     *
     * @param year 급여일을 계산할 연도
     * @param month 급여일을 계산할 월
     * @param publicHolidays 해당 연월의 급여일 보정에 사용할 공휴일 날짜 집합
     * @return 실제 적용되는 급여일
     */
    fun resolveEffectiveDate(
        year: Int,
        month: Int,
        publicHolidays: Set<LocalDate> = emptySet(),
    ): LocalDate {
        val yearMonth = YearMonth.of(year, month)
        var date = yearMonth.atDay(minOf(value, yearMonth.lengthOfMonth()))

        while (date.isHoliday(publicHolidays)) {
            date = date.minusDays(1)
        }

        return date
    }

    /**
     * 특정 날짜가 이 급여일 설정의 실제 급여일인지 판정합니다.
     *
     * @param date 판정할 날짜
     * @param publicHolidays 급여일 보정에 필요한 공휴일 날짜 집합.
     * date가 속한 달과 다음 달의 공휴일을 포함해야 합니다.
     * @return 해당 날짜가 실제 급여일이면 `true`
     */
    fun isPayday(
        date: LocalDate,
        publicHolidays: Set<LocalDate> = emptySet(),
    ): Boolean {
        val currentMonth = YearMonth.from(date)
        val candidateMonths = setOf(currentMonth, currentMonth.plusMonths(1))

        return candidateMonths.any {
            resolveEffectiveDate(it.year, it.monthValue, publicHolidays) == date
        }
    }

    companion object {
        /**
         * 특정 날짜에 실제 급여일로 귀결되는 모든 급여일 설정 값을 반환합니다.
         *
         * @param date 기준 날짜
         * @param publicHolidays 급여일 보정에 필요한 공휴일 날짜 집합.
         * date가 속한 달과 다음 달의 공휴일을 포함해야 합니다.
         * @return 해당 날짜를 실제 급여일로 가지는 [PaydayDay] 집합
         */
        fun resolvingTo(
            date: LocalDate,
            publicHolidays: Set<LocalDate> = emptySet(),
        ): Set<PaydayDay> {
            val currentMonth = YearMonth.from(date)
            val candidateMonths = setOf(currentMonth, currentMonth.plusMonths(1))

            return (1..31).map(::PaydayDay).filterTo(mutableSetOf()) { paydayDay ->
                candidateMonths.any {
                    paydayDay.resolveEffectiveDate(it.year, it.monthValue, publicHolidays) == date
                }
            }
        }
    }

    private fun LocalDate.isHoliday(publicHolidays: Set<LocalDate>): Boolean =
        dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY || this in publicHolidays
}
