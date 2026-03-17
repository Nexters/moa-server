package com.moa.entity

import java.time.LocalDate

/**
 * 일자별로 표시할 부가 이벤트를 정의하는 열거형입니다.
 */
enum class DailyEventType {
    /** 급여일에 해당하는 경우 */
    PAYDAY;

    companion object {
        /**
         * 특정 일자와 급여일 설정을 바탕으로 해당 일자에 표시할 이벤트를 판정합니다.
         *
         * 현재는 급여일([PAYDAY])만 지원하며, 추후 공휴일 등 다른 이벤트가 추가될 수 있습니다.
         *
         * @param date 이벤트를 판정할 기준 일자
         * @param paydayDay 사용자 설정 급여일
         * @return 해당 일자에 적용되는 [DailyEventType] 목록
         */
        fun resolve(date: LocalDate, paydayDay: PaydayDay): List<DailyEventType> {
            val events = mutableListOf<DailyEventType>()

            if (isPayday(date, paydayDay)) {
                events += PAYDAY
            }

            return events
        }

        private fun isPayday(date: LocalDate, paydayDay: PaydayDay): Boolean = paydayDay.isPayday(date)
    }
}
