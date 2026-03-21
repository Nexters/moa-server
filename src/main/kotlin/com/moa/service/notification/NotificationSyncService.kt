package com.moa.service.notification

import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.repository.NotificationLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Service
class NotificationSyncService(
    private val notificationLogRepository: NotificationLogRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val workNotificationTypes = listOf(NotificationType.CLOCK_IN, NotificationType.CLOCK_OUT)

    @Transactional
    fun syncNotifications(
        memberId: Long,
        date: LocalDate,
        type: DailyWorkScheduleType,
        clockInTime: LocalTime?,
        clockOutTime: LocalTime?,
    ) {
        val relatedDates = listOf(date, date.plusDays(1))
        val pendingLogs = notificationLogRepository
            .findAllByMemberIdAndScheduledDateInAndStatus(memberId, relatedDates, NotificationStatus.PENDING)
            .filter { it.notificationType != NotificationType.PAYDAY }

        if (pendingLogs.isEmpty()) {
            ensureUpcomingWorkNotifications(memberId, date, type, clockInTime, clockOutTime)
            return
        }

        if (type == DailyWorkScheduleType.VACATION || type == DailyWorkScheduleType.NONE) {
            pendingLogs.forEach { it.status = NotificationStatus.CANCELLED }
            log.info(
                "Cancelled {} pending notifications for member {} on {} ({})",
                pendingLogs.size, memberId, date, type,
            )
            return
        }

        for (pendingLog in pendingLogs) {
            when (pendingLog.notificationType) {
                NotificationType.CLOCK_IN -> {
                    clockInTime?.let { pendingLog.scheduledTime = truncateToMinute(it) }
                    pendingLog.scheduledDate = date
                }

                NotificationType.CLOCK_OUT -> {
                    clockOutTime?.let {
                        val truncated = truncateToMinute(it)
                        val clockInTruncated = clockInTime?.let(::truncateToMinute)
                        if (clockInTruncated != null && truncated < clockInTruncated) {
                            pendingLog.scheduledDate = date.plusDays(1)
                        } else {
                            pendingLog.scheduledDate = date
                        }
                        pendingLog.scheduledTime = truncated
                    }
                }

                NotificationType.PAYDAY -> Unit
            }
        }
        log.info("Synced pending notifications for member {} on {}", memberId, date)
    }

    private fun ensureUpcomingWorkNotifications(
        memberId: Long,
        date: LocalDate,
        type: DailyWorkScheduleType,
        clockInTime: LocalTime?,
        clockOutTime: LocalTime?,
    ) {
        if (type != DailyWorkScheduleType.WORK) return
        if (clockInTime == null || clockOutTime == null) return

        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        val logs = mutableListOf<NotificationLog>()
        val truncatedIn = truncateToMinute(clockInTime)
        val truncatedOut = truncateToMinute(clockOutTime)
        val isMidnightCrossing = truncatedOut < truncatedIn
        val relatedDates = listOf(date, date.plusDays(1))
        val existingLogs = notificationLogRepository
            .findAllByMemberIdAndScheduledDateInAndNotificationTypeIn(
                memberId,
                relatedDates,
                workNotificationTypes,
            )

        if (date.atTime(truncatedIn).isAfter(now) && existingLogs.none {
                it.notificationType == NotificationType.CLOCK_IN &&
                    it.scheduledDate == date &&
                    it.status != NotificationStatus.CANCELLED
            }
        ) {
            logs.add(NotificationLog(memberId, NotificationType.CLOCK_IN, date, truncatedIn))
        }

        val clockOutDate = if (isMidnightCrossing) date.plusDays(1) else date
        if (clockOutDate.atTime(truncatedOut).isAfter(now) && existingLogs.none {
                it.notificationType == NotificationType.CLOCK_OUT &&
                    it.scheduledDate == clockOutDate &&
                    it.status != NotificationStatus.CANCELLED
            }
        ) {
            logs.add(NotificationLog(memberId, NotificationType.CLOCK_OUT, clockOutDate, truncatedOut))
        }

        if (logs.isNotEmpty()) {
            notificationLogRepository.saveAll(logs)
            log.info(
                "Ensured {} upcoming work notifications for member {} on {}",
                logs.size, memberId, date,
            )
        }
    }

    private fun truncateToMinute(time: LocalTime): LocalTime =
        LocalTime.of(time.hour, time.minute)
}
