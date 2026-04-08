package com.moa.service.notification

import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.entity.notification.WorkScheduleTime
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
        val pendingLogs = findPendingWorkNotifications(memberId, date)

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
                    clockInTime?.let {
                        pendingLog.scheduledTime = LocalTime.of(it.hour, it.minute)
                    }
                    pendingLog.scheduledDate = date
                }

                NotificationType.CLOCK_OUT -> {
                    if (clockInTime != null && clockOutTime != null) {
                        val schedule = WorkScheduleTime.of(clockInTime, clockOutTime)
                        pendingLog.scheduledDate = schedule.clockOutDate(date)
                        pendingLog.scheduledTime = schedule.clockOutTime
                    } else if (clockOutTime != null) {
                        pendingLog.scheduledTime = LocalTime.of(clockOutTime.hour, clockOutTime.minute)
                        pendingLog.scheduledDate = date
                    }
                }

                NotificationType.PAYDAY -> Unit
                NotificationType.PUBLIC_HOLIDAY -> Unit
            }
        }
        log.info("Synced pending notifications for member {} on {}", memberId, date)
    }

    private fun findPendingWorkNotifications(memberId: Long, date: LocalDate): List<NotificationLog> {
        val sameDayLogs = notificationLogRepository
            .findAllByMemberIdAndScheduledDateInAndStatus(memberId, listOf(date), NotificationStatus.PENDING)
            .filter { it.notificationType != NotificationType.PAYDAY && it.notificationType != NotificationType.PUBLIC_HOLIDAY }
        val nextDayClockOutLogs = notificationLogRepository
            .findAllByMemberIdAndScheduledDateAndNotificationTypeAndStatus(
                memberId,
                date.plusDays(1),
                NotificationType.CLOCK_OUT,
                NotificationStatus.PENDING,
            )
        return sameDayLogs + nextDayClockOutLogs
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
        val schedule = WorkScheduleTime.of(clockInTime, clockOutTime)
        val clockOutDate = schedule.clockOutDate(date)
        val relatedDates = listOf(date, date.plusDays(1))
        val existingLogs = notificationLogRepository
            .findAllByMemberIdAndScheduledDateInAndNotificationTypeIn(
                memberId,
                relatedDates,
                workNotificationTypes,
            )

        val logs = mutableListOf<NotificationLog>()

        if (date.atTime(schedule.clockInTime).isAfter(now) && existingLogs.none {
                it.notificationType == NotificationType.CLOCK_IN &&
                    it.scheduledDate == date &&
                    it.status != NotificationStatus.CANCELLED
            }
        ) {
            logs.add(NotificationLog(memberId, NotificationType.CLOCK_IN, date, schedule.clockInTime))
        }

        if (clockOutDate.atTime(schedule.clockOutTime).isAfter(now) && existingLogs.none {
                it.notificationType == NotificationType.CLOCK_OUT &&
                    it.scheduledDate == clockOutDate &&
                    it.status != NotificationStatus.CANCELLED
            }
        ) {
            logs.add(NotificationLog(memberId, NotificationType.CLOCK_OUT, clockOutDate, schedule.clockOutTime))
        }

        if (logs.isNotEmpty()) {
            notificationLogRepository.saveAll(logs)
            log.info(
                "Ensured {} upcoming work notifications for member {} on {}",
                logs.size, memberId, date,
            )
        }
    }

}
