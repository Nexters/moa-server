package com.moa.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 사용자가 설정한 급여일을 표현하는 Value Object 입니다.
 *
 * 급여일은 1일부터 31일 사이의 값만 허용하며,
 * 실제 급여일 계산 시 월말 보정 및 주말 보정 규칙을 함께 제공합니다.
 */
@Embeddable
data class PaydayDay(
    @Column(nullable = false)
    var value: Int = 25,
) {
    init {
        require(value in 1..31) { "paydayDay must be between 1 and 31" }
    }

    /**
     * 특정 연월에 적용되는 실제 급여일을 계산합니다.
     *
     * 설정한 급여일이 해당 월에 없으면 말일로 보정하고,
     * 보정된 날짜가 주말이면 직전 금요일로 당깁니다.
     *
     * @param year 급여일을 계산할 연도
     * @param month 급여일을 계산할 월
     * @return 실제 적용되는 급여일
     */
    fun resolveEffectiveDate(year: Int, month: Int): LocalDate {
        val yearMonth = YearMonth.of(year, month)
        val baseDate = yearMonth.atDay(minOf(value, yearMonth.lengthOfMonth()))

        return when (baseDate.dayOfWeek) {
            DayOfWeek.SATURDAY -> baseDate.minusDays(1)
            DayOfWeek.SUNDAY -> baseDate.minusDays(2)
            else -> baseDate
        }
    }

    /**
     * 특정 날짜가 이 급여일 설정의 실제 급여일인지 판정합니다.
     *
     * @param date 판정할 날짜
     * @return 해당 날짜가 실제 급여일이면 `true`
     */
    fun isPayday(date: LocalDate): Boolean =
        resolveEffectiveDate(date.year, date.monthValue) == date

    companion object {
        /**
         * 특정 날짜에 실제 급여일로 귀결되는 모든 급여일 설정 값을 반환합니다.
         *
         * @param date 기준 날짜
         * @return 해당 날짜를 실제 급여일로 가지는 [PaydayDay] 집합
         */
        fun resolvingTo(date: LocalDate): Set<PaydayDay> =
            (1..31).map(::PaydayDay).filterTo(mutableSetOf()) { it.isPayday(date) }
    }
}
