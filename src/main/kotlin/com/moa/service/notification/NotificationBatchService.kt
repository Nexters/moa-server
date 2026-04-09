package com.moa.service.notification

import com.moa.entity.WorkPolicyVersion
import com.moa.entity.Workday
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationSettingType
import com.moa.entity.notification.NotificationType
import com.moa.entity.notification.WorkScheduleTime
import com.moa.repository.NotificationLogRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.PublicHolidayService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

@Service
class NotificationBatchService(
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationEligibilityService: NotificationEligibilityService,
    private val publicHolidayService: PublicHolidayService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val PUBLIC_HOLIDAY_NOTIFICATION_TIME = LocalTime.of(9, 0)
    }

    @Transactional
    fun generateNotificationsForDate(date: LocalDate) {
        if (publicHolidayService.isHoliday(date)) {
            generateHolidayNotifications(date)
        } else {
            generateWorkNotifications(date)
        }
    }

    private fun generateHolidayNotifications(date: LocalDate) {
        val allPolicies = workPolicyVersionRepository.findLatestEffectivePoliciesPerMember(date)
        if (allPolicies.isEmpty()) return

        val allMemberIds = allPolicies.map { it.memberId }
        val alreadyGeneratedMemberIds = notificationLogRepository
            .findMemberIdsByScheduledDateAndNotificationTypeAndMemberIdIn(date, NotificationType.PUBLIC_HOLIDAY, allMemberIds)
            .toSet()

        val targetPolicies = allPolicies.filter { it.memberId !in alreadyGeneratedMemberIds }
        if (targetPolicies.isEmpty()) return

        val memberIds = targetPolicies.map { it.memberId }
        val requiredTermCodes = notificationEligibilityService.findRequiredTermCodes()
        val context = notificationEligibilityService.loadContext(memberIds, date)

        log.info("Generating public holiday notifications for {} members on {}", memberIds.size, date)

        val notifications = targetPolicies.mapNotNull { policy ->
            createHolidayNotificationIfEligible(policy.memberId, date, requiredTermCodes, context)
        }

        notificationLogRepository.saveAll(notifications)
        log.info("Created {} public holiday notification logs for {}", notifications.size, date)
    }

    private fun createHolidayNotificationIfEligible(
        memberId: Long,
        date: LocalDate,
        requiredCodes: Set<String>,
        context: NotificationEligibilityContext,
    ): NotificationLog? {
        if (!context.hasAgreedToAll(memberId, requiredCodes)) return null
        if (!context.isSettingEnabled(memberId, NotificationSettingType.WORK)) return null
        if (!context.hasFcmToken(memberId)) return null

        return NotificationLog(memberId, NotificationType.PUBLIC_HOLIDAY, date, PUBLIC_HOLIDAY_NOTIFICATION_TIME)
    }

    private fun generateWorkNotifications(date: LocalDate) {
        val workdayPolicies = findWorkdayPolicies(date)
        if (workdayPolicies.isEmpty()) return

        val workdayMemberIds = workdayPolicies.map { it.memberId }
        val alreadyGeneratedMemberIds = notificationLogRepository
            .findMemberIdsByScheduledDateAndNotificationTypeAndMemberIdIn(date, NotificationType.CLOCK_IN, workdayMemberIds)
            .toSet()

        val targetPolicies = workdayPolicies.filter { it.memberId !in alreadyGeneratedMemberIds }
        if (targetPolicies.isEmpty()) return

        val memberIds = targetPolicies.map { it.memberId }
        val requiredTermCodes = notificationEligibilityService.findRequiredTermCodes()
        val context = notificationEligibilityService.loadContext(memberIds, date)

        log.info("Generating notifications for {} members on {}", memberIds.size, date)

        val notifications = targetPolicies.mapNotNull { policy ->
            createNotificationsIfEligible(policy, date, requiredTermCodes, context)
        }.flatten()

        notificationLogRepository.saveAll(notifications)
        log.info("Created {} notification logs for {}", notifications.size, date)
    }

    private fun findWorkdayPolicies(date: LocalDate): List<WorkPolicyVersion> {
        val todayWorkday = Workday.from(date)
        return workPolicyVersionRepository.findLatestEffectivePoliciesPerMember(date)
            .filter { todayWorkday in it.workdays }
    }

    private fun createNotificationsIfEligible(
        policy: WorkPolicyVersion,
        date: LocalDate,
        requiredCodes: Set<String>,
        context: NotificationEligibilityContext,
    ): List<NotificationLog>? {
        val memberId = policy.memberId

        if (!context.hasAgreedToAll(memberId, requiredCodes)) return null
        if (!context.isSettingEnabled(memberId, NotificationSettingType.WORK)) return null
        if (context.shouldSkipNotification(memberId)) return null
        if (!context.hasFcmToken(memberId)) return null

        val override = context.getOverride(memberId)
        val schedule = WorkScheduleTime.of(
            clockIn = override?.clockInTime ?: policy.clockInTime,
            clockOut = override?.clockOutTime ?: policy.clockOutTime,
        )

        return listOf(
            NotificationLog(memberId, NotificationType.CLOCK_IN, date, schedule.clockInTime),
            NotificationLog(memberId, NotificationType.CLOCK_OUT, schedule.clockOutDate(date), schedule.clockOutTime),
        )
    }
}
