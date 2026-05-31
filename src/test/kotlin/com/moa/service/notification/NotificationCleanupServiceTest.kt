package com.moa.service.notification

import com.moa.repository.NotificationLogRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * `NotificationCleanupService` 단위 테스트.
 *
 * 책임: 벌크 EXPIRED 마킹을 수행하고, **실제 마킹된 건수**(쿼리 반환값)만큼 cleanup 전용 카운터를 올린 뒤
 * 그 건수를 반환한다. 메트릭이 "사전 집계값"이 아닌 "실제 UPDATE 건수"여야 dispatch catch-up 과의 경쟁에서
 * 0건 처리하고도 메트릭만 오르는 과계상을 막을 수 있다.
 */
class NotificationCleanupServiceTest {

    private val repository = mockk<NotificationLogRepository>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private val sut = NotificationCleanupService(repository, meterRegistry)

    @Test
    fun `실제 마킹된 건수만큼 cleanup 카운터를 올리고 그 건수를 반환한다`() {
        every { repository.markExpiredBefore(threshold) } returns 3

        val result = sut.expirePendingBefore(threshold)

        assertThat(result).isEqualTo(3)
        assertThat(cleanupExpiredCount()).isEqualTo(3.0)
    }

    @Test
    fun `마킹된 건수가 0 이면 카운터를 올리지 않고 0 을 반환한다`() {
        every { repository.markExpiredBefore(threshold) } returns 0

        val result = sut.expirePendingBefore(threshold)

        assertThat(result).isEqualTo(0)
        assertThat(meterRegistry.find(NotificationCleanupService.METRIC_CLEANUP_EXPIRED).counters()).isEmpty()
    }

    @Test
    fun `경쟁으로 실제 UPDATE 가 0건이면 메트릭도 증가하지 않는다`() {
        // dispatch catch-up 이 같은 오래된 PENDING 을 먼저 EXPIRED 로 바꾼 경우:
        // markExpiredBefore 는 0 을 돌려주고, 메트릭은 실제 건수 기반이라 과계상되지 않는다.
        every { repository.markExpiredBefore(threshold) } returns 0

        sut.expirePendingBefore(threshold)

        verify(exactly = 1) { repository.markExpiredBefore(threshold) }
        assertThat(cleanupExpiredCount()).isEqualTo(0.0)
    }

    private fun cleanupExpiredCount(): Double =
        meterRegistry.find(NotificationCleanupService.METRIC_CLEANUP_EXPIRED)
            .counter()
            ?.count() ?: 0.0

    companion object {
        private val threshold: LocalDate = LocalDate.of(2026, 5, 24)
    }
}
