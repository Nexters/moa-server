package com.moa.service.notification

import com.moa.entity.WorkPolicyVersion
import com.moa.entity.Workday
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationSettingType
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.entity.notification.WorkScheduleTime
import com.moa.repository.NotificationLogRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.PublicHolidayService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class NotificationBatchService(
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationEligibilityService: NotificationEligibilityService,
    private val publicHolidayService: PublicHolidayService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun generateNotificationsForDate(date: LocalDate) {
        if (publicHolidayService.isHoliday(date)) return
        generateWorkNotifications(date)
    }

    private fun generateWorkNotifications(date: LocalDate) {
        val workdayPolicies = findWorkdayPolicies(date)
        if (workdayPolicies.isEmpty()) return

        val workdayMemberIds = workdayPolicies.map { it.memberId }
        val existing = loadExistingWorkNotifications(date, workdayMemberIds)

        val requiredTermCodes = notificationEligibilityService.findRequiredTermCodes()
        val context = notificationEligibilityService.loadContext(workdayMemberIds, date)

        log.info("Generating notifications for {} members on {}", workdayMemberIds.size, date)

        val notifications = workdayPolicies.mapNotNull { policy ->
            createNotificationsIfEligible(policy, date, requiredTermCodes, context, existing)
        }.flatten()

        if (notifications.isEmpty()) {
            log.info("No new notification logs to create for {} (all already generated)", date)
            return
        }

        notificationLogRepository.saveAll(notifications)
        log.info("Created {} notification logs for {}", notifications.size, date)
    }

    private fun loadExistingWorkNotifications(
        date: LocalDate,
        memberIds: List<Long>,
    ): ExistingWorkNotifications {
        val clockIn = activeMemberIdsFor(date, NotificationType.CLOCK_IN, memberIds)
        val clockOutToday = activeMemberIdsFor(date, NotificationType.CLOCK_OUT, memberIds)
        val clockOutTomorrow = activeMemberIdsFor(date.plusDays(1), NotificationType.CLOCK_OUT, memberIds)
        return ExistingWorkNotifications(clockIn, clockOutToday, clockOutTomorrow)
    }

    private fun activeMemberIdsFor(
        date: LocalDate,
        type: NotificationType,
        memberIds: List<Long>,
    ): Set<Long> = notificationLogRepository
        .findMemberIdsByScheduledDateAndNotificationTypeAndStatusInAndMemberIdIn(
            date, type, NotificationStatus.ACTIVE_STATUSES, memberIds,
        ).toSet()

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
        existing: ExistingWorkNotifications,
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
        val clockOutDate = schedule.clockOutDate(date)
        val clockOutExisting =
            if (clockOutDate == date) existing.clockOutToday else existing.clockOutTomorrow

        val result = mutableListOf<NotificationLog>()
        if (memberId !in existing.clockIn) {
            result.add(NotificationLog(memberId, NotificationType.CLOCK_IN, date, schedule.clockInTime))
        }
        if (memberId !in clockOutExisting) {
            result.add(NotificationLog(memberId, NotificationType.CLOCK_OUT, clockOutDate, schedule.clockOutTime))
        }
        return result.ifEmpty { null }
    }

    private data class ExistingWorkNotifications(
        val clockIn: Set<Long>,
        val clockOutToday: Set<Long>,
        val clockOutTomorrow: Set<Long>,
    )
}
