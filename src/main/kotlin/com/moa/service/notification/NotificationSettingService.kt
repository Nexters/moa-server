package com.moa.service.notification

import com.moa.entity.NotificationSetting
import com.moa.entity.NotificationSettingType
import com.moa.entity.Term
import com.moa.entity.TermAgreement
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
        if (type == NotificationSettingType.MARKETING) {
            val marketingTermCode = termAgreementRepository.findByMemberIdAndTermCode(memberId, Term.MARKETING)
            if (marketingTermCode != null) {
                marketingTermCode.agreed = checked
            } else {
                termAgreementRepository.save(
                    TermAgreement(
                        memberId = memberId,
                        termCode = Term.MARKETING,
                        agreed = checked,
                    )
                )
            }
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
}
