package com.moa.service.notification

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NotificationCleanupSchedulerTest {

    private val cleanupService = mockk<NotificationCleanupService>()
    private val clock = mockk<NotificationScheduleClock>()
    private val sut = NotificationCleanupScheduler(cleanupService, clock)

    @Test
    fun `오늘로부터 7일 이전을 threshold 로 cleanup 서비스에 위임한다`() {
        every { clock.now() } returns LocalDateTime.of(2026, 5, 31, 1, 0)
        every { cleanupService.expirePendingBefore(any()) } returns 0

        sut.cleanupExpiredNotifications()

        verify(exactly = 1) {
            cleanupService.expirePendingBefore(LocalDate.of(2026, 5, 24))
        }
    }

    @Test
    fun `처리 건수가 0 이 아니어도 예외 없이 정상 종료된다`() {
        every { clock.now() } returns LocalDateTime.of(2026, 5, 31, 1, 0)
        every { cleanupService.expirePendingBefore(any()) } returns 5

        sut.cleanupExpiredNotifications()

        verify { cleanupService.expirePendingBefore(LocalDate.of(2026, 5, 24)) }
    }
}
