package com.moa.service.notification

import com.moa.entity.*
import com.moa.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Service
class PaydayNotificationBatchService(
    private val profileRepository: ProfileRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationSettingRepository: NotificationSettingRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val termRepository: TermRepository,
    private val termAgreementRepository: TermAgreementRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun generateNotificationsForDate(date: LocalDate) {
        if (isAlreadyGenerated(date)) return

        val profiles = findPaydayProfiles(date)
        if (profiles.isEmpty()) return

        val memberIds = profiles.map { it.memberId }
        val requiredTermCodes = findRequiredTermCodes()
        val context = loadContext(memberIds)

        log.info("Generating payday notifications for {} members on {}", memberIds.size, date)

        val notifications = profiles.mapNotNull { profile ->
            createNotificationIfEligible(profile.memberId, date, requiredTermCodes, context)
        }

        notificationLogRepository.saveAll(notifications)
        log.info("Created {} payday notification logs for {}", notifications.size, date)
    }

    private fun isAlreadyGenerated(date: LocalDate): Boolean {
        val exists = notificationLogRepository
            .existsByScheduledDateAndNotificationType(date, NotificationType.PAYDAY)
        if (exists) log.info("Payday notifications already generated for date: {}", date)
        return exists
    }

    private fun findPaydayProfiles(date: LocalDate): List<Profile> {
        val dayOfMonth = date.dayOfMonth
        val lastDayOfMonth = YearMonth.from(date).atEndOfMonth().dayOfMonth
        val paydayDays = if (dayOfMonth == lastDayOfMonth) {
            (dayOfMonth..31).toList()
        } else {
            listOf(dayOfMonth)
        }
        return profileRepository.findAllByPaydayDayIn(paydayDays)
    }

    private fun findRequiredTermCodes(): Set<String> =
        termRepository.findAll()
            .filter { it.required }
            .map { it.code }
            .toSet()

    private fun loadContext(memberIds: List<Long>): PaydayNotificationContext {
        val agreementsMap = termAgreementRepository.findAllByMemberIdIn(memberIds)
            .filter { it.agreed }
            .groupBy { it.memberId }
            .mapValues { (_, v) -> v.map { it.termCode }.toSet() }

        val settingsMap = notificationSettingRepository.findAllByMemberIdIn(memberIds)
            .associateBy { it.memberId }

        val tokensMap = fcmTokenRepository.findAllByMemberIdIn(memberIds)
            .groupBy { it.memberId }

        return PaydayNotificationContext(agreementsMap, settingsMap, tokensMap)
    }

    private fun createNotificationIfEligible(
        memberId: Long,
        date: LocalDate,
        requiredCodes: Set<String>,
        context: PaydayNotificationContext,
    ): NotificationLog? {
        if (!context.hasAgreedToAll(memberId, requiredCodes)) return null
        if (!context.isPaydayNotificationEnabled(memberId)) return null
        if (!context.hasFcmToken(memberId)) return null

        return NotificationLog(memberId, NotificationType.PAYDAY, date, PAYDAY_NOTIFICATION_TIME)
    }

    private class PaydayNotificationContext(
        private val agreementsMap: Map<Long, Set<String>>,
        private val settingsMap: Map<Long, NotificationSetting>,
        private val tokensMap: Map<Long, List<FcmToken>>,
    ) {
        fun hasAgreedToAll(memberId: Long, requiredCodes: Set<String>): Boolean {
            val agreedCodes = agreementsMap[memberId] ?: emptySet()
            return agreedCodes.containsAll(requiredCodes)
        }

        fun isPaydayNotificationEnabled(memberId: Long): Boolean =
            settingsMap[memberId]?.paydayNotificationEnabled != false

        fun hasFcmToken(memberId: Long): Boolean =
            !tokensMap[memberId].isNullOrEmpty()
    }

    companion object {
        private val PAYDAY_NOTIFICATION_TIME = LocalTime.of(9, 0)
    }
}
