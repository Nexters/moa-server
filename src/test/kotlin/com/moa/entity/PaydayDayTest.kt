package com.moa.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PaydayDayTest {

    @Test
    fun `급여일은 1일부터 31일까지의 값만 허용한다`() {
        assertThatThrownBy { PaydayDay(0) }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy { PaydayDay(32) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `설정한 급여일이 해당 월에 없으면 말일로 보정한다`() {
        val result = PaydayDay(31).resolveEffectiveDate(2025, 2)

        assertThat(result).isEqualTo(LocalDate.of(2025, 2, 28))
    }

    @Test
    fun `실제 급여일이 주말이면 직전 금요일로 당긴다`() {
        val result = PaydayDay(31).resolveEffectiveDate(2025, 5)

        assertThat(result).isEqualTo(LocalDate.of(2025, 5, 30))
    }

    @Test
    fun `특정 날짜가 실제 급여일인지 판정할 수 있다`() {
        val paydayDay = PaydayDay(25)

        assertThat(paydayDay.isPayday(LocalDate.of(2025, 6, 25))).isTrue()
        assertThat(paydayDay.isPayday(LocalDate.of(2025, 6, 24))).isFalse()
    }
}
