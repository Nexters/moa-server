package com.moa.service.dto

import com.moa.entity.NotificationSettingType

data class NotificationSettingUpdateRequest(
    val type: NotificationSettingType,
    val checked: Boolean,
)
