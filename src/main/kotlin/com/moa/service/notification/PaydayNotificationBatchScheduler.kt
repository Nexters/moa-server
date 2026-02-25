package com.moa.service.notification

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class PaydayNotificationBatchScheduler(
    private val paydayNotificationBatchService: PaydayNotificationBatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    fun createPaydayNotifications() {
        log.info("{} : 월급 알림 전송 배치 실행", LocalDateTime.now())
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        paydayNotificationBatchService.generateNotificationsForDate(today)
    }
}
