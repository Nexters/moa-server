package com.moa.service.notification

import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.NotificationLog
import com.moa.entity.NotificationType
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.ProfileRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.EarningsCalculator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.*

@Service
class NotificationMessageBuilder(
    private val profileRepository: ProfileRepository,
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
    private val earningsCalculator: EarningsCalculator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun buildMessage(notification: NotificationLog): NotificationMessage {
        val title = notification.notificationType.title
        val body = when (notification.notificationType) {
            NotificationType.CLOCK_IN -> notification.notificationType.body
            NotificationType.CLOCK_OUT -> buildClockOutBody(notification)
        }
        return NotificationMessage(title, body)
    }

    private fun buildClockOutBody(notification: NotificationLog): String {
        val earnings = calculateTodayEarnings(notification.memberId, notification.scheduledDate)
        if (earnings == null || earnings == BigDecimal.ZERO) {
            return CLOCK_OUT_FALLBACK_BODY
        }
        val formatted = NumberFormat.getNumberInstance(Locale.KOREA).format(earnings)
        return notification.notificationType.getBody(formatted)
    }

    private fun calculateTodayEarnings(memberId: Long, date: LocalDate): BigDecimal? {
        val lastDayOfMonth = YearMonth.from(date).atEndOfMonth()
        val policy = workPolicyVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                memberId, lastDayOfMonth,
            ) ?: return null

        val override = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
        return earningsCalculator.calculateDailyEarnings(
            memberId = memberId,
            date = date,
            policy = policy,
            type = override?.type ?: DailyWorkScheduleType.WORK,
            clockInTime = override?.clockInTime,
            clockOutTime = override?.clockOutTime,
        )
    }

    companion object {
        private const val CLOCK_OUT_FALLBACK_BODY = "오늘도 수고하셨어요!"
    }
}

data class NotificationMessage(val title: String, val body: String)
