package com.moa.service.notification

import com.moa.repository.NotificationLogRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Cleanup 안전망 서비스.
 *
 * Dispatch 인라인 catch-up 이 어떤 이유로 누락했을 때를 대비해, [threshold] 이전에 예약됐는데
 * 여전히 PENDING 인 알림을 일괄 EXPIRED 로 만료시킨다. 정상 동작에서는 처리 건수가 0 이어야 정상.
 */
@Service
class NotificationCleanupService(
    private val notificationLogRepository: NotificationLogRepository,
    private val meterRegistry: MeterRegistry,
) {
    @Transactional
    fun expirePendingBefore(threshold: LocalDate): Int {
        val expired = notificationLogRepository.markExpiredBefore(threshold)
        if (expired > 0) {
            cleanupExpiredCounter().increment(expired.toDouble())
        }
        return expired
    }

    private fun cleanupExpiredCounter(): Counter = Counter.builder(METRIC_CLEANUP_EXPIRED)
        .description("Cleanup 안전망이 만료시킨 잔류 PENDING 알림 수 (정상 동작에서는 0)")
        .register(meterRegistry)

    companion object {
        const val METRIC_CLEANUP_EXPIRED = "moa.notification.cleanup.expired"
    }
}
