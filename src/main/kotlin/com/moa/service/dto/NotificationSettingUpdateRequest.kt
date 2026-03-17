package com.moa.service.dto

import com.moa.entity.notification.NotificationSettingType

data class NotificationSettingUpdateRequest(
    val type: NotificationSettingType,
    val checked: Boolean,
)
