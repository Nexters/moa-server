package com.moa.service.notification

import com.moa.entity.DailyWorkSchedule
import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.FcmToken
import com.moa.entity.notification.NotificationSetting
import com.moa.entity.notification.NotificationSettingType

/**
 * 알림 발송 적격성 판단을 위한 조회 결과를 보관하는 컨텍스트입니다.
 *
 * [NotificationEligibilityService.loadContext]에서 생성되며,
 * 배치 서비스가 회원별 적격성을 판단할 때 사용합니다.
 *
 * @param overridesMap 출퇴근 알림 전용. 월급날 알림에서는 빈 맵으로 생성됩니다.
 */
class NotificationEligibilityContext(
    private val agreementsMap: Map<Long, Set<String>>,
    private val settingsMap: Map<Long, NotificationSetting>,
    private val tokensMap: Map<Long, List<FcmToken>>,
    private val overridesMap: Map<Long, DailyWorkSchedule> = emptyMap(),
) {
    fun hasAgreedToAll(memberId: Long, requiredCodes: Set<String>): Boolean {
        val agreedCodes = agreementsMap[memberId] ?: emptySet()
        return agreedCodes.containsAll(requiredCodes)
    }

    fun isSettingEnabled(memberId: Long, type: NotificationSettingType): Boolean =
        settingsMap[memberId]?.isEnabled(type) != false

    fun hasFcmToken(memberId: Long): Boolean =
        !tokensMap[memberId].isNullOrEmpty()

    fun shouldSkipNotification(memberId: Long): Boolean {
        val overrideType = overridesMap[memberId]?.type
        return overrideType == DailyWorkScheduleType.VACATION || overrideType == DailyWorkScheduleType.NONE
    }

    fun getOverride(memberId: Long): DailyWorkSchedule? = overridesMap[memberId]
}
