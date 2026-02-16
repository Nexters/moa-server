package com.moa.service.dto

import com.moa.entity.NotificationSetting

data class NotificationSettingResponse(
    val workNotificationEnabled: Boolean,
    val paydayNotificationEnabled: Boolean,
    val promotionNotificationEnabled: Boolean,
) {
    companion object {
        fun from(setting: NotificationSetting?) = NotificationSettingResponse(
            workNotificationEnabled = setting?.workNotificationEnabled ?: false,
            paydayNotificationEnabled = setting?.paydayNotificationEnabled ?: false,
            promotionNotificationEnabled = setting?.promotionNotificationEnabled ?: false,
        )
    }
}
