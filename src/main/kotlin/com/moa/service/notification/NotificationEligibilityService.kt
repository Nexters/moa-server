package com.moa.service.notification

import com.moa.repository.*
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 알림 발송 적격성 판단에 필요한 데이터를 로딩하는 서비스입니다.
 *
 * 적격성 조건:
 * - 필수 약관 전체 동의 여부
 * - 알림 유형별 수신 설정 활성화 여부 (출퇴근 / 월급날)
 * - 유효한 FCM 토큰 보유 여부
 * - 알림 발송 제외 스케줄 여부 (휴가, 미근무)
 *
 * [NotificationBatchService], [PaydayNotificationBatchService]에서 공통으로 사용합니다.
 */
@Service
class NotificationEligibilityService(
    private val termRepository: TermRepository,
    private val termAgreementRepository: TermAgreementRepository,
    private val notificationSettingRepository: NotificationSettingRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
) {
    fun findRequiredTermCodes(): Set<String> =
        termRepository.findAll()
            .filter { it.required }
            .map { it.code }
            .toSet()

    fun loadContext(
        memberIds: List<Long>,
        date: LocalDate? = null,
    ): NotificationEligibilityContext {
        val agreementsMap = termAgreementRepository.findAllByMemberIdIn(memberIds)
            .filter { it.agreed }
            .groupBy { it.memberId }
            .mapValues { (_, v) -> v.map { it.termCode }.toSet() }

        val settingsMap = notificationSettingRepository.findAllByMemberIdIn(memberIds)
            .associateBy { it.memberId }

        val tokensMap = fcmTokenRepository.findAllByMemberIdIn(memberIds)
            .groupBy { it.memberId }

        val overridesMap = date?.let {
            dailyWorkScheduleRepository.findAllByMemberIdInAndDate(memberIds, it)
                .associateBy { schedule -> schedule.memberId }
        } ?: emptyMap()

        return NotificationEligibilityContext(agreementsMap, settingsMap, tokensMap, overridesMap)
    }
}
