package com.moa.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class NotificationBatchScheduler(
    private val notificationBatchService: NotificationBatchService,
) {
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun createDailyNotifications() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        notificationBatchService.generateNotificationsForDate(today)
    }
}
