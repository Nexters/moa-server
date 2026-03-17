package com.moa.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyEventTypeTest {

    @Test
    fun `급여일과 같은 날짜면 PAYDAY 이벤트를 반환한다`() {
        val result = DailyEventType.resolve(
            date = LocalDate.of(2025, 6, 25),
            paydayDay = PaydayDay(25),
        )

        assertThat(result).containsExactly(DailyEventType.PAYDAY)
    }

    @Test
    fun `급여일과 다른 날짜면 빈 이벤트 목록을 반환한다`() {
        val result = DailyEventType.resolve(
            date = LocalDate.of(2025, 6, 24),
            paydayDay = PaydayDay(25),
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `설정한 급여일이 말일보다 크면 말일을 급여일로 간주한다`() {
        val result = DailyEventType.resolve(
            date = LocalDate.of(2025, 2, 28),
            paydayDay = PaydayDay(31),
        )

        assertThat(result).containsExactly(DailyEventType.PAYDAY)
    }

    @Test
    fun `말일 보정이 적용된 달에서도 말일 이전 날짜는 급여일이 아니다`() {
        val result = DailyEventType.resolve(
            date = LocalDate.of(2025, 2, 27),
            paydayDay = PaydayDay(31),
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `설정한 급여일이 주말이면 직전 평일을 급여일로 간주한다`() {
        val result = DailyEventType.resolve(
            date = LocalDate.of(2025, 5, 30),
            paydayDay = PaydayDay(31),
        )

        assertThat(result).containsExactly(DailyEventType.PAYDAY)
    }
}
