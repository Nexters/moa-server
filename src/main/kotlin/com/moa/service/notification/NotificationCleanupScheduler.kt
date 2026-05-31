package com.moa.service.notification

import com.moa.service.notification.NotificationCleanupScheduler.Companion.CLEANUP_THRESHOLD_DAYS
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Cleanup 스케줄러.
 *
 * 매일 새벽 1시에 [CLEANUP_THRESHOLD_DAYS]일 이상 지난 PENDING 을
 * EXPIRED 로 만료시킨다. Dispatch 인라인 catch-up 이 정상이면 처리 건수는 0 이어야 정상이며,
 * 0 이 아니면 catch-up 미동작 가능성을 알리는 운영 신호이므로 WARN 으로 남긴다.
 */
@Component
class NotificationCleanupScheduler(
    private val notificationCleanupService: NotificationCleanupService,
    private val clock: NotificationScheduleClock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    @SchedulerLock(
        name = "cleanupExpiredNotifications",
        lockAtMostFor = "2m",
        lockAtLeastFor = "1m",
    )
    fun cleanupExpiredNotifications() {
        val threshold = clock.now().toLocalDate().minusDays(CLEANUP_THRESHOLD_DAYS)
        val expired = notificationCleanupService.expirePendingBefore(threshold)
        if (expired > 0) {
            // 정상 동작이면 0 이어야 한다.
            // dispatch/catch-up 이 왜 이들을 발송·만료하지 못했는지 디버깅 필요
            log.warn(
                "Cleanup expired {} stale PENDING notifications older than {} — investigate why dispatch left them PENDING",
                expired, threshold,
            )
        } else {
            log.info("Cleanup found no stale PENDING (threshold={})", threshold)
        }
    }

    companion object {
        private const val CLEANUP_THRESHOLD_DAYS = 7L
    }
}
