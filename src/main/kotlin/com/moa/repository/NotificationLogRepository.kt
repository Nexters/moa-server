package com.moa.repository

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.time.LocalTime

interface NotificationLogRepository : JpaRepository<NotificationLog, Long> {
    fun findAllByScheduledDateAndScheduledTimeLessThanEqualAndStatus(
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        status: NotificationStatus,
    ): List<NotificationLog>

    fun findAllByScheduledDateLessThanAndStatus(
        scheduledDate: LocalDate,
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
        "select distinct n.memberId " +
                "from NotificationLog n " +
                "where n.scheduledDate = :scheduledDate " +
                "and n.notificationType = :notificationType " +
                "and n.memberId in :memberIds " +
                "and n.status in :statuses"
    )
    fun findMemberIdsByScheduledDateAndNotificationTypeAndStatusInAndMemberIdIn(
        scheduledDate: LocalDate,
        notificationType: NotificationType,
        statuses: Collection<NotificationStatus>,
        memberIds: Collection<Long>,
    ): List<Long>

    /**
     * Cleanup — [threshold] 이전(미만)에 예약됐는데 여전히 PENDING 인 알림을 일괄 EXPIRED 로 마킹한다.
     *
     * @return 마킹된 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update NotificationLog n " +
                "set n.status = com.moa.entity.notification.NotificationStatus.EXPIRED " +
                "where n.status = com.moa.entity.notification.NotificationStatus.PENDING " +
                "and n.scheduledDate < :threshold"
    )
    fun markExpiredBefore(threshold: LocalDate): Int
}
