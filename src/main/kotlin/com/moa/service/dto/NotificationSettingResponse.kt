package com.moa.service.dto

import com.moa.entity.NotificationSetting
import com.moa.entity.NotificationSettingType

data class NotificationSettingResponse(
    val type: NotificationSettingType,
    val category: String,
    val title: String,
    val checked: Boolean,
) {
    companion object {
        fun from(setting: NotificationSetting): List<NotificationSettingResponse> {
            return NotificationSettingType.entries.map { type ->
                NotificationSettingResponse(
                    type = type,
                    category = type.category,
                    title = type.title,
                    checked = setting.isEnabled(type),
                )
            }
        }

        fun from(marketingAgreed: Boolean): List<NotificationSettingResponse> {
            return NotificationSettingType.entries.map { type ->
                NotificationSettingResponse(
                    type = type,
                    category = type.category,
                    title = type.title,
                    checked = if (type == NotificationSettingType.MARKETING) marketingAgreed else true,
                )
            }
        }
    }
}
