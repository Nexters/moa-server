package com.moa.service

import com.moa.entity.NotificationSetting
import com.moa.repository.NotificationSettingRepository
import com.moa.service.dto.NotificationSettingResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationSettingService(
    private val notificationSettingRepository: NotificationSettingRepository,
) {

    @Transactional(readOnly = true)
    fun getSettings(memberId: Long): NotificationSettingResponse {
        val setting = notificationSettingRepository.findByMemberId(memberId)
        return NotificationSettingResponse.from(setting)
    }

    @Transactional
    fun updateWorkNotification(memberId: Long, enabled: Boolean): NotificationSettingResponse {
        val setting = getOrCreate(memberId)
        setting.workNotificationEnabled = enabled
        return NotificationSettingResponse.from(setting)
    }

    @Transactional
    fun updatePaydayNotification(memberId: Long, enabled: Boolean): NotificationSettingResponse {
        val setting = getOrCreate(memberId)
        setting.paydayNotificationEnabled = enabled
        return NotificationSettingResponse.from(setting)
    }

    @Transactional
    fun updatePromotionNotification(memberId: Long, enabled: Boolean): NotificationSettingResponse {
        val setting = getOrCreate(memberId)
        setting.promotionNotificationEnabled = enabled
        return NotificationSettingResponse.from(setting)
    }

    private fun getOrCreate(memberId: Long): NotificationSetting {
        return notificationSettingRepository.findByMemberId(memberId)
            ?: notificationSettingRepository.save(NotificationSetting(memberId = memberId))
    }
}
