package com.moa.repository

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.time.LocalTime

interface NotificationLogRepository : JpaRepository<NotificationLog, Long> {
    fun findAllByScheduledDateAndScheduledTimeLessThanEqualAndStatus(
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        status: NotificationStatus,
    ): List<NotificationLog>

    fun findAllByMemberIdAndScheduledDateInAndStatus(
        memberId: Long,
        scheduledDates: Collection<LocalDate>,
        status: NotificationStatus,
    ): List<NotificationLog>

    fun findAllByMemberIdAndScheduledDateAndNotificationTypeAndStatus(
        memberId: Long,
        scheduledDate: LocalDate,
        notificationType: NotificationType,
        status: NotificationStatus,
    ): List<NotificationLog>

    fun findAllByMemberIdAndScheduledDateInAndNotificationTypeIn(
        memberId: Long,
        scheduledDates: Collection<LocalDate>,
        notificationTypes: Collection<NotificationType>,
    ): List<NotificationLog>

    @Query(
        "select n.memberId " +
                "from NotificationLog n " +
                "where n.scheduledDate = :scheduledDate and n.notificationType = :notificationType"
    )
    fun findMemberIdsByScheduledDateAndNotificationType(
        scheduledDate: LocalDate,
        notificationType: NotificationType,
    ): List<Long>
}
