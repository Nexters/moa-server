package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.entity.NotificationSetting
import com.moa.entity.Term
import com.moa.repository.NotificationSettingRepository
import com.moa.repository.TermAgreementRepository
import com.moa.service.dto.NotificationSettingResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationSettingService(
    private val notificationSettingRepository: NotificationSettingRepository,
    private val termAgreementRepository: TermAgreementRepository,
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

        if (enabled) {
            validateMarketingAgreed(memberId)
        }

        setting.promotionNotificationEnabled = enabled

        return NotificationSettingResponse.from(setting)
    }

    private fun getOrCreate(memberId: Long): NotificationSetting {
        return notificationSettingRepository.findByMemberId(memberId)
            ?: notificationSettingRepository.save(NotificationSetting(memberId = memberId))
    }

    private fun validateMarketingAgreed(memberId: Long) {
        val agreement = termAgreementRepository.findByMemberIdAndTermCode(memberId, Term.MARKETING)

        if (agreement == null || !agreement.agreed) {
            throw BadRequestException(ErrorCode.REQUIRED_MARKETING_TERM)
        }
    }
}
