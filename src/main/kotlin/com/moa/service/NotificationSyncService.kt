package com.moa.service

import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.NotificationLog
import com.moa.entity.NotificationStatus
import com.moa.entity.NotificationType
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

    @Transactional
    fun syncNotifications(
        memberId: Long,
        date: LocalDate,
        type: DailyWorkScheduleType,
        clockInTime: LocalTime?,
        clockOutTime: LocalTime?,
    ) {
        val pendingLogs = notificationLogRepository
            .findAllByMemberIdAndScheduledDateAndStatus(memberId, date, NotificationStatus.PENDING)

        if (pendingLogs.isEmpty()) {
            restoreIfCancelled(memberId, date, type, clockInTime, clockOutTime)
            return
        }

        if (type == DailyWorkScheduleType.VACATION) {
            pendingLogs.forEach { it.status = NotificationStatus.CANCELLED }
            log.info(
                "Cancelled {} pending notifications for member {} on {} (vacation)",
                pendingLogs.size, memberId, date,
            )
            return
        }

        for (pendingLog in pendingLogs) {
            when (pendingLog.notificationType) {
                NotificationType.CLOCK_IN -> {
                    clockInTime?.let { pendingLog.scheduledTime = truncateToMinute(it) }
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
            }
        }
        log.info("Synced pending notifications for member {} on {}", memberId, date)
    }

    private fun restoreIfCancelled(
        memberId: Long,
        date: LocalDate,
        type: DailyWorkScheduleType,
        clockInTime: LocalTime?,
        clockOutTime: LocalTime?,
    ) {
        if (type != DailyWorkScheduleType.WORK) return
        if (clockInTime == null || clockOutTime == null) return

        val hasCancelled = notificationLogRepository
            .existsByMemberIdAndScheduledDateAndStatus(memberId, date, NotificationStatus.CANCELLED)
        if (!hasCancelled) return

        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        val logs = mutableListOf<NotificationLog>()
        val truncatedIn = truncateToMinute(clockInTime)
        val truncatedOut = truncateToMinute(clockOutTime)
        val isMidnightCrossing = truncatedOut < truncatedIn

        if (date.atTime(truncatedIn).isAfter(now)) {
            logs.add(NotificationLog(memberId, NotificationType.CLOCK_IN, date, truncatedIn))
        }

        val clockOutDate = if (isMidnightCrossing) date.plusDays(1) else date
        if (clockOutDate.atTime(truncatedOut).isAfter(now)) {
            logs.add(NotificationLog(memberId, NotificationType.CLOCK_OUT, clockOutDate, truncatedOut))
        }

        if (logs.isNotEmpty()) {
            notificationLogRepository.saveAll(logs)
            log.info(
                "Restored {} notifications for member {} on {} (vacation -> work)",
                logs.size, memberId, date,
            )
        }
    }

    private fun truncateToMinute(time: LocalTime): LocalTime =
        LocalTime.of(time.hour, time.minute)
}
