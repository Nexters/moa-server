package com.moa.service.notification

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Component
class NotificationDispatchScheduler(
    private val notificationDispatchService: NotificationDispatchService,
) {
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @SchedulerLock(
        name = "dispatchNotifications",
        lockAtMostFor = "55s",
        lockAtLeastFor = "30s",
    )
    fun dispatchPendingNotifications() {
        val now = LocalTime.now(ZoneId.of("Asia/Seoul"))
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        notificationDispatchService.processNotifications(today, LocalTime.of(now.hour, now.minute))
    }
}
