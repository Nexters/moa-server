package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.entity.NotificationSetting
import com.moa.entity.NotificationSettingType
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
    fun getSettings(memberId: Long): List<NotificationSettingResponse> {
        val setting = notificationSettingRepository.findByMemberId(memberId)
        if (setting != null) {
            return NotificationSettingResponse.from(setting)
        }
        // Setting 지연생성 때문에 지저분한 코드..
        return NotificationSettingResponse.from(isMarketingAgreed(memberId))
    }

    @Transactional
    fun updateSetting(
        memberId: Long,
        type: NotificationSettingType,
        checked: Boolean
    ): List<NotificationSettingResponse> {
        if (type == NotificationSettingType.MARKETING && checked) {
            validateMarketingAgreed(memberId)
        }
        val setting = getOrCreate(memberId)
        setting.update(type, checked)
        return NotificationSettingResponse.from(setting)
    }

    private fun getOrCreate(memberId: Long): NotificationSetting {
        return notificationSettingRepository.findByMemberId(memberId)
            ?: notificationSettingRepository.save(
                NotificationSetting(
                    memberId = memberId,
                    marketingNotificationEnabled = isMarketingAgreed(memberId),
                )
            )
    }

    private fun isMarketingAgreed(memberId: Long): Boolean {
        return termAgreementRepository.findByMemberIdAndTermCode(memberId, Term.MARKETING)?.agreed ?: false
    }

    private fun validateMarketingAgreed(memberId: Long) {
        if (!isMarketingAgreed(memberId)) {
            throw BadRequestException(ErrorCode.REQUIRED_MARKETING_TERM)
        }
    }
}
