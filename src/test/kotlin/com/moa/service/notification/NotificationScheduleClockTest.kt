package com.moa.service.notification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class NotificationScheduleClockTest {

    private val sut = NotificationScheduleClock()

    @Test
    fun `now 는 Asia_Seoul timezone 기준 현재 시각을 반환한다`() {
        val before = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        val now = sut.now()
        val after = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

        assertThat(now).isBetween(before, after)
    }

    @Test
    fun `명시한 now 보다 1분 미래인 스케줄은 future 로 판정한다`() {
        val now = LocalDateTime.of(2026, 5, 22, 13, 30)
        val date = LocalDate.of(2026, 5, 22)
        val time = LocalTime.of(13, 31)

        assertThat(sut.isFutureSchedule(date, time, now)).isTrue()
    }

    @Test
    fun `명시한 now 보다 1분 과거인 스케줄은 future 가 아니다`() {
        val now = LocalDateTime.of(2026, 5, 22, 13, 30)
        val date = LocalDate.of(2026, 5, 22)
        val time = LocalTime.of(13, 29)

        assertThat(sut.isFutureSchedule(date, time, now)).isFalse()
    }

    @Test
    fun `명시한 now 와 정확히 같은 시각은 future 가 아니다`() {
        // isAfter 는 strict 비교라 동일 시각은 false 가 맞다.
        val now = LocalDateTime.of(2026, 5, 22, 13, 30)
        val date = LocalDate.of(2026, 5, 22)
        val time = LocalTime.of(13, 30)

        assertThat(sut.isFutureSchedule(date, time, now)).isFalse()
    }

    @Test
    fun `자정 직전 now 에서 다음날 00시 00분 스케줄은 future 로 판정한다`() {
        val now = LocalDateTime.of(2026, 5, 22, 23, 59)
        val nextDay = LocalDate.of(2026, 5, 23)
        val midnight = LocalTime.of(0, 0)

        assertThat(sut.isFutureSchedule(nextDay, midnight, now)).isTrue()
    }
}
