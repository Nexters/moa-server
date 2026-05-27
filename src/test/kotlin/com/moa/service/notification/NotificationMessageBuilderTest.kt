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
    fun `CLOCK_IN 알림은 정적 본문을 반환하고 Normal 결과를 만든다`() {
        val result = sut.buildMessage(notificationLog(NotificationType.CLOCK_IN), emptySet())

        assertThat(result).isInstanceOf(NotificationMessageBuildResult.Normal::class.java)
        assertThat(result.message.body).isEqualTo(NotificationType.CLOCK_IN.body)
    }

    @Test
    fun `PAYDAY 알림은 정적 본문을 반환하고 Normal 결과를 만든다`() {
        val result = sut.buildMessage(notificationLog(NotificationType.PAYDAY), emptySet())

        assertThat(result).isInstanceOf(NotificationMessageBuildResult.Normal::class.java)
        assertThat(result.message.body).isEqualTo(NotificationType.PAYDAY.body)
    }

    @Test
    fun `CLOCK_OUT earnings 계산 성공 시 포맷된 금액 본문의 Normal 결과를 만든다`() {
        every { earningsService.calculateTodayEarnings(any(), any(), any()) } returns BigDecimal("123456")

        val result = sut.buildMessage(notificationLog(NotificationType.CLOCK_OUT), emptySet())

        assertThat(result).isInstanceOf(NotificationMessageBuildResult.Normal::class.java)
        assertThat(result.message.body).contains("123,456")
    }

    @Test
    fun `CLOCK_OUT earnings 가 0원이면 zero_earnings reason의 Fallback 결과를 만든다`() {
        every { earningsService.calculateTodayEarnings(any(), any(), any()) } returns BigDecimal.ZERO

        val result = sut.buildMessage(notificationLog(NotificationType.CLOCK_OUT), emptySet())

        assertThat(result).isInstanceOf(NotificationMessageBuildResult.Fallback::class.java)
        val fallback = result as NotificationMessageBuildResult.Fallback
        assertThat(fallback.reason).isEqualTo(NotificationMessageBuilder.REASON_ZERO_EARNINGS)
        assertThat(fallback.message.body).isEqualTo("오늘도 수고하셨어요!")
    }

    @Test
    fun `CLOCK_OUT earnings 계산이 예외를 던지면 earnings_error reason의 Fallback 결과를 만든다`() {
        every { earningsService.calculateTodayEarnings(any(), any(), any()) } throws NotFoundException()

        val result = sut.buildMessage(notificationLog(NotificationType.CLOCK_OUT), emptySet())

        assertThat(result).isInstanceOf(NotificationMessageBuildResult.Fallback::class.java)
        val fallback = result as NotificationMessageBuildResult.Fallback
        assertThat(fallback.reason).isEqualTo(NotificationMessageBuilder.REASON_EARNINGS_ERROR)
        assertThat(fallback.message.body).isEqualTo("오늘도 수고하셨어요!")
    }

    @Test
    fun `CLOCK_OUT earnings 계산이 InterruptedException을 던지면 그대로 전파하고 인터럽트 플래그를 복원한다`() {
        every { earningsService.calculateTodayEarnings(any(), any(), any()) } throws InterruptedException("cancel")

        try {
            assertThat(Thread.interrupted()).isFalse()  // 시작 시 클린한 상태 보장
            org.junit.jupiter.api.assertThrows<InterruptedException> {
                sut.buildMessage(notificationLog(NotificationType.CLOCK_OUT), emptySet())
            }
            assertThat(Thread.currentThread().isInterrupted).isTrue()
        } finally {
            Thread.interrupted()  // 다른 테스트에 영향 안 가도록 클리어
        }
    }

    private fun notificationLog(type: NotificationType) = NotificationLog(
        memberId = 1L,
        notificationType = type,
        scheduledDate = LocalDate.of(2026, 5, 26),
        scheduledTime = LocalTime.NOON,
    )
}
