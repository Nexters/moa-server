package com.moa.service.notification

import com.moa.entity.PaydayDay
import com.moa.entity.Profile
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationSettingType
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.repository.NotificationLogRepository
import com.moa.repository.ProfileRepository
import com.moa.service.PublicHolidayService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

@Service
class PaydayNotificationBatchService(
    private val profileRepository: ProfileRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationEligibilityService: NotificationEligibilityService,
    private val publicHolidayService: PublicHolidayService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun generateNotificationsForDate(date: LocalDate) {
        val profiles = findPaydayProfiles(date)
        if (profiles.isEmpty()) return

        val profileMemberIds = profiles.map { it.memberId }
        val alreadyGeneratedMemberIds = notificationLogRepository
            .findMemberIdsByScheduledDateAndNotificationTypeAndStatusInAndMemberIdIn(
                date, NotificationType.PAYDAY, NotificationStatus.ACTIVE_STATUSES, profileMemberIds,
            )
            .toSet()

        val targetProfiles = profiles.filter { it.memberId !in alreadyGeneratedMemberIds }
        if (targetProfiles.isEmpty()) return

        val memberIds = targetProfiles.map { it.memberId }
        val requiredTermCodes = notificationEligibilityService.findRequiredTermCodes()
        val context = notificationEligibilityService.loadContext(memberIds)

        log.info("Generating payday notifications for {} members on {}", memberIds.size, date)

        val notifications = targetProfiles.mapNotNull { profile ->
            createNotificationIfEligible(profile.memberId, date, requiredTermCodes, context)
        }

        notificationLogRepository.saveAll(notifications)
        log.info("Created {} payday notification logs for {}", notifications.size, date)
    }

    private fun findPaydayProfiles(date: LocalDate): List<Profile> {
        val publicHolidays = publicHolidayService.getHolidayDatesForPaydayResolution(date)
        val candidatePaydayDays = PaydayDay.resolvingTo(date, publicHolidays)
            .map { it.value }

        if (candidatePaydayDays.isEmpty()) {
            return emptyList()
        }

        return profileRepository.findAllByPaydayDay_ValueIn(candidatePaydayDays)
    }

    private fun createNotificationIfEligible(
        memberId: Long,
        date: LocalDate,
        requiredCodes: Set<String>,
        context: NotificationEligibilityContext,
    ): NotificationLog? {
        if (!context.hasAgreedToAll(memberId, requiredCodes)) return null
        if (!context.isSettingEnabled(memberId, NotificationSettingType.PAYDAY)) return null
        if (!context.hasFcmToken(memberId)) return null

        return NotificationLog(memberId, NotificationType.PAYDAY, date, PAYDAY_NOTIFICATION_TIME)
    }

    companion object {
        private val PAYDAY_NOTIFICATION_TIME = LocalTime.of(9, 0)
    }
}
