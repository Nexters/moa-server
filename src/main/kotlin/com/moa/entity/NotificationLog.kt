package com.moa.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(
    indexes = [
        Index(
            name = "idx_notification_log_schedule",
            columnList = "scheduledDate, scheduledTime, status",
        )
    ]
)
class NotificationLog(
    @Column(nullable = false)
    val memberId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val notificationType: NotificationType,

    @Column(nullable = false)
    var scheduledDate: LocalDate,

    @Column(nullable = false)
    var scheduledTime: LocalTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: NotificationStatus = NotificationStatus.PENDING,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
