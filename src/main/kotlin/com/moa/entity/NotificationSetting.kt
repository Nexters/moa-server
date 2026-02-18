package com.moa.entity

import jakarta.persistence.*

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["memberId"])])
class NotificationSetting(
    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    var workNotificationEnabled: Boolean = true,

    @Column(nullable = false)
    var paydayNotificationEnabled: Boolean = true,

    @Column(nullable = false)
    var marketingNotificationEnabled: Boolean = true,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    fun isEnabled(type: NotificationSettingType): Boolean = when (type) {
        NotificationSettingType.WORK -> workNotificationEnabled
        NotificationSettingType.PAYDAY -> paydayNotificationEnabled
        NotificationSettingType.MARKETING -> marketingNotificationEnabled
    }

    fun update(type: NotificationSettingType, checked: Boolean) {
        when (type) {
            NotificationSettingType.WORK -> workNotificationEnabled = checked
            NotificationSettingType.PAYDAY -> paydayNotificationEnabled = checked
            NotificationSettingType.MARKETING -> marketingNotificationEnabled = checked
        }
    }
}
