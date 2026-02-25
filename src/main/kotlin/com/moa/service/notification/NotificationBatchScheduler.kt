package com.moa.service.notification

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class NotificationBatchScheduler(
    private val notificationBatchService: NotificationBatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun createDailyNotifications() {
        log.info("{} : 출퇴근 알림 전송 배치 실행", LocalDateTime.now())
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        notificationBatchService.generateNotificationsForDate(today)
    }
}
