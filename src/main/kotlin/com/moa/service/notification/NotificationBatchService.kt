package com.moa.service.notification

import com.moa.entity.*
import com.moa.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

@Service
class NotificationBatchService(
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
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

        val workdayPolicies = findWorkdayPolicies(date)
        if (workdayPolicies.isEmpty()) return

        val memberIds = workdayPolicies.map { it.memberId }
        val requiredTermCodes = findRequiredTermCodes()
        val context = loadContext(memberIds, date)

        log.info("Generating notifications for {} members on {}", memberIds.size, date)

        val notifications = workdayPolicies.mapNotNull { policy ->
            createNotificationsIfEligible(policy, date, requiredTermCodes, context)
        }.flatten()

        notificationLogRepository.saveAll(notifications)
        log.info("Created {} notification logs for {}", notifications.size, date)
    }

    private fun isAlreadyGenerated(date: LocalDate): Boolean {
        val exists = notificationLogRepository
            .existsByScheduledDateAndNotificationType(date, NotificationType.CLOCK_IN)
        if (exists) log.info("Notifications already generated for date: {}", date)
        return exists
    }

    private fun findWorkdayPolicies(date: LocalDate): List<WorkPolicyVersion> {
        val todayWorkday = Workday.from(date)
        return workPolicyVersionRepository.findLatestEffectivePoliciesPerMember(date)
            .filter { todayWorkday in it.workdays }
    }

    private fun findRequiredTermCodes(): Set<String> =
        termRepository.findAll()
            .filter { it.required }
            .map { it.code }
            .toSet()

    private fun loadContext(memberIds: List<Long>, date: LocalDate): NotificationContext {
        val agreementsMap = termAgreementRepository.findAllByMemberIdIn(memberIds)
            .filter { it.agreed }
            .groupBy { it.memberId }
            .mapValues { (_, v) -> v.map { it.termCode }.toSet() }

        val settingsMap = notificationSettingRepository.findAllByMemberIdIn(memberIds)
            .associateBy { it.memberId }

        val overridesMap = dailyWorkScheduleRepository.findAllByMemberIdInAndDate(memberIds, date)
            .associateBy { it.memberId }

        val tokensMap = fcmTokenRepository.findAllByMemberIdIn(memberIds)
            .groupBy { it.memberId }

        return NotificationContext(agreementsMap, settingsMap, overridesMap, tokensMap)
    }

    private fun createNotificationsIfEligible(
        policy: WorkPolicyVersion,
        date: LocalDate,
        requiredCodes: Set<String>,
        context: NotificationContext,
    ): List<NotificationLog>? {
        val memberId = policy.memberId

        if (!context.hasAgreedToAll(memberId, requiredCodes)) return null
        if (!context.isNotificationEnabled(memberId)) return null
        if (context.isOnVacation(memberId)) return null
        if (!context.hasFcmToken(memberId)) return null

        val override = context.getOverride(memberId)
        val clockInTime = truncateToMinute(override?.clockInTime ?: policy.clockInTime)
        val clockOutTime = truncateToMinute(override?.clockOutTime ?: policy.clockOutTime)
        val clockOutDate = if (clockOutTime < clockInTime) date.plusDays(1) else date

        return listOf(
            NotificationLog(memberId, NotificationType.CLOCK_IN, date, clockInTime),
            NotificationLog(memberId, NotificationType.CLOCK_OUT, clockOutDate, clockOutTime),
        )
    }

    private fun truncateToMinute(time: LocalTime): LocalTime =
        LocalTime.of(time.hour, time.minute)

    private class NotificationContext(
        private val agreementsMap: Map<Long, Set<String>>,
        private val settingsMap: Map<Long, NotificationSetting>,
        private val overridesMap: Map<Long, DailyWorkSchedule>,
        private val tokensMap: Map<Long, List<FcmToken>>,
    ) {
        fun hasAgreedToAll(memberId: Long, requiredCodes: Set<String>): Boolean {
            val agreedCodes = agreementsMap[memberId] ?: emptySet()
            return agreedCodes.containsAll(requiredCodes)
        }

        fun isNotificationEnabled(memberId: Long): Boolean =
            settingsMap[memberId]?.workNotificationEnabled != false

        fun isOnVacation(memberId: Long): Boolean =
            overridesMap[memberId]?.type == DailyWorkScheduleType.VACATION

        fun hasFcmToken(memberId: Long): Boolean =
            !tokensMap[memberId].isNullOrEmpty()

        fun getOverride(memberId: Long): DailyWorkSchedule? =
            overridesMap[memberId]
    }
}
