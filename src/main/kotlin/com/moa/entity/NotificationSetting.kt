package com.moa.entity

import jakarta.persistence.*

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["memberId"])])
class NotificationSetting(
    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    var workNotificationEnabled: Boolean = false,

    @Column(nullable = false)
    var paydayNotificationEnabled: Boolean = false,

    @Column(nullable = false)
    var promotionNotificationEnabled: Boolean = false,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
