package com.moa.service.notification

import com.moa.common.exception.NotFoundException
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class NotificationMessageBuilderTest {

    private val earningsService = mockk<NotificationEarningsService>()
    private val sut = NotificationMessageBuilder(earningsService)

    @Test
    fun `CLOCK_IN 알림은 정적 본문을 반환하고 fallback 사용하지 않는다`() {
        val result = sut.buildMessage(notificationLog(NotificationType.CLOCK_IN), emptySet())

        assertThat(result.fallbackUsed).isFalse()
        assertThat(result.message.body).isEqualTo(NotificationType.CLOCK_IN.body)
    }

    @Test
    fun `PAYDAY 알림은 정적 본문을 반환하고 fallback 사용하지 않는다`() {
        val result = sut.buildMessage(notificationLog(NotificationType.PAYDAY), emptySet())

        assertThat(result.fallbackUsed).isFalse()
        assertThat(result.message.body).isEqualTo(NotificationType.PAYDAY.body)
    }

    @Test
    fun `CLOCK_OUT earnings 계산 성공 시 포맷된 금액 본문을 반환한다`() {
        every { earningsService.calculateTodayEarnings(any(), any(), any()) } returns BigDecimal("123456")

        val result = sut.buildMessage(notificationLog(NotificationType.CLOCK_OUT), emptySet())

        assertThat(result.fallbackUsed).isFalse()
        assertThat(result.message.body).contains("123,456")
    }

    @Test
    fun `CLOCK_OUT earnings 가 0원이면 zero_earnings fallback을 반환한다`() {
        every { earningsService.calculateTodayEarnings(any(), any(), any()) } returns BigDecimal.ZERO

        val result = sut.buildMessage(notificationLog(NotificationType.CLOCK_OUT), emptySet())

        assertThat(result.fallbackUsed).isTrue()
        assertThat(result.fallbackReason).isEqualTo(NotificationMessageBuilder.REASON_ZERO_EARNINGS)
        assertThat(result.message.body).isEqualTo("오늘도 수고하셨어요!")
    }

    @Test
    fun `CLOCK_OUT earnings 계산이 예외를 던지면 earnings_error fallback을 반환한다`() {
        every { earningsService.calculateTodayEarnings(any(), any(), any()) } throws NotFoundException()

        val result = sut.buildMessage(notificationLog(NotificationType.CLOCK_OUT), emptySet())

        assertThat(result.fallbackUsed).isTrue()
        assertThat(result.fallbackReason).isEqualTo(NotificationMessageBuilder.REASON_EARNINGS_ERROR)
        assertThat(result.message.body).isEqualTo("오늘도 수고하셨어요!")
    }

    private fun notificationLog(type: NotificationType) = NotificationLog(
        memberId = 1L,
        notificationType = type,
        scheduledDate = LocalDate.of(2026, 5, 26),
        scheduledTime = LocalTime.NOON,
    )
}
