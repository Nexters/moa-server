package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * `NotificationTtlPolicy` 단위 테스트 — 알림 유형별 TTL 경계값.
 *
 * - CLOCK_IN: scheduledTime + 30분
 * - CLOCK_OUT, PAYDAY: scheduledDate 의 23:59:59.999999999 (당일 자정)
 */
class NotificationTtlPolicyTest {

    private val clock = mockk<NotificationScheduleClock>()
    private val sut = NotificationTtlPolicy(clock)

    @Test
    fun `CLOCK_IN은 예약 시각 + 30분 직전이면 만료가 아니다`() {
        val now = LocalDateTime.of(2026, 5, 27, 9, 29, 59)
        every { clock.now() } returns now

        val notification = log(NotificationType.CLOCK_IN, LocalDate.of(2026, 5, 27), LocalTime.of(9, 0))

        assertThat(sut.isExpired(notification)).isFalse()
    }

    @Test
    fun `CLOCK_IN은 예약 시각 + 30분 정각이면 만료가 아니다`() {
        val now = LocalDateTime.of(2026, 5, 27, 9, 30)
        every { clock.now() } returns now

        val notification = log(NotificationType.CLOCK_IN, LocalDate.of(2026, 5, 27), LocalTime.of(9, 0))

        assertThat(sut.isExpired(notification)).isFalse()
    }

    @Test
    fun `CLOCK_IN은 예약 시각 + 30분 1초 초과면 만료다`() {
        val now = LocalDateTime.of(2026, 5, 27, 9, 30, 1)
        every { clock.now() } returns now

        val notification = log(NotificationType.CLOCK_IN, LocalDate.of(2026, 5, 27), LocalTime.of(9, 0))

        assertThat(sut.isExpired(notification)).isTrue()
    }

    @Test
    fun `CLOCK_OUT은 당일 자정 직전이면 만료가 아니다`() {
        val now = LocalDateTime.of(2026, 5, 27, 23, 59, 59)
        every { clock.now() } returns now

        val notification = log(NotificationType.CLOCK_OUT, LocalDate.of(2026, 5, 27), LocalTime.of(18, 0))

        assertThat(sut.isExpired(notification)).isFalse()
    }

    @Test
    fun `CLOCK_OUT은 다음날 자정이 지나면 만료다`() {
        val now = LocalDateTime.of(2026, 5, 28, 0, 0, 1)
        every { clock.now() } returns now

        val notification = log(NotificationType.CLOCK_OUT, LocalDate.of(2026, 5, 27), LocalTime.of(18, 0))

        assertThat(sut.isExpired(notification)).isTrue()
    }

    @Test
    fun `PAYDAY는 당일 자정 직전이면 만료가 아니다`() {
        val now = LocalDateTime.of(2026, 5, 27, 23, 30)
        every { clock.now() } returns now

        val notification = log(NotificationType.PAYDAY, LocalDate.of(2026, 5, 27), LocalTime.of(9, 0))

        assertThat(sut.isExpired(notification)).isFalse()
    }

    @Test
    fun `PAYDAY는 다음날이면 만료다`() {
        val now = LocalDateTime.of(2026, 5, 28, 0, 0, 1)
        every { clock.now() } returns now

        val notification = log(NotificationType.PAYDAY, LocalDate.of(2026, 5, 27), LocalTime.of(9, 0))

        assertThat(sut.isExpired(notification)).isTrue()
    }

    @Test
    fun `CLOCK_OUT은 당일 LocalTime_MAX 정각이면 만료가 아니다 (정밀 경계)`() {
        val date = LocalDate.of(2026, 5, 27)
        val deadline = LocalDateTime.of(date, LocalTime.MAX)
        every { clock.now() } returns deadline

        val notification = log(NotificationType.CLOCK_OUT, date, LocalTime.of(18, 0))

        assertThat(sut.isExpired(notification)).isFalse()
    }

    @Test
    fun `CLOCK_OUT은 당일 LocalTime_MAX 보다 1 나노초만 넘어도 만료다 (정밀 경계)`() {
        val date = LocalDate.of(2026, 5, 27)
        val now = LocalDateTime.of(date, LocalTime.MAX).plusNanos(1)
        every { clock.now() } returns now

        val notification = log(NotificationType.CLOCK_OUT, date, LocalTime.of(18, 0))

        assertThat(sut.isExpired(notification)).isTrue()
    }

    private fun log(type: NotificationType, date: LocalDate, time: LocalTime) = NotificationLog(
        memberId = 1L,
        notificationType = type,
        scheduledDate = date,
        scheduledTime = time,
        status = NotificationStatus.PENDING,
    )
}
