package com.moa.entity

import java.time.LocalDate

/**
 * 일자별로 표시할 부가 이벤트를 정의하는 열거형입니다.
 */
enum class DailyEventType {
    /** 급여일에 해당하는 경우 */
    PAYDAY,

    /** 공휴일에 해당하는 경우 */
    PUBLIC_HOLIDAY;

    companion object {
        /**
         * 특정 일자와 급여일 설정, 공휴일 목록을 바탕으로 해당 일자에 표시할 이벤트를 판정합니다.
         *
         * @param date 이벤트를 판정할 기준 일자
         * @param paydayDay 사용자 설정 급여일
         * @param publicHolidays 해당 월의 공휴일 날짜 집합
         * @return 해당 일자에 적용되는 [DailyEventType] 목록
         */
        fun resolve(
            date: LocalDate,
            paydayDay: PaydayDay,
            publicHolidays: Set<LocalDate>,
        ): List<DailyEventType> {
            val events = mutableListOf<DailyEventType>()

            if (paydayDay.isPayday(date)) {
                events += PAYDAY
            }
            if (date in publicHolidays) {
                events += PUBLIC_HOLIDAY
            }

            return events
        }
    }
}
