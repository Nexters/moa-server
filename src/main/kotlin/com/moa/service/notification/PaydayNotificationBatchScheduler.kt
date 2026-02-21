package com.moa.service.notification

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class PaydayNotificationBatchScheduler(
    private val paydayNotificationBatchService: PaydayNotificationBatchService,
) {
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    fun createPaydayNotifications() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        paydayNotificationBatchService.generateNotificationsForDate(today)
    }
}
