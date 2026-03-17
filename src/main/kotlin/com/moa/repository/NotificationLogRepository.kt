package com.moa.repository

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.time.LocalTime

interface NotificationLogRepository : JpaRepository<NotificationLog, Long> {
    fun findAllByScheduledDateAndScheduledTimeLessThanEqualAndStatus(
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        status: NotificationStatus,
    ): List<NotificationLog>

    fun findAllByMemberIdAndScheduledDateAndStatus(
        memberId: Long,
        scheduledDate: LocalDate,
        status: NotificationStatus,
    ): List<NotificationLog>

    fun existsByMemberIdAndScheduledDateAndStatus(
        memberId: Long,
        scheduledDate: LocalDate,
        status: NotificationStatus,
    ): Boolean

    fun existsByScheduledDateAndNotificationType(
        scheduledDate: LocalDate,
        notificationType: NotificationType,
    ): Boolean
}
