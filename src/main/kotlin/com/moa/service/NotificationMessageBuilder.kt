package com.moa.service

import com.moa.entity.NotificationLog
import com.moa.entity.NotificationType
import com.moa.entity.SalaryCalculator
import com.moa.entity.SalaryType
import com.moa.repository.PayrollVersionRepository
import com.moa.repository.ProfileRepository
import com.moa.repository.WorkPolicyVersionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.*

@Service
class NotificationMessageBuilder(
    private val profileRepository: ProfileRepository,
    private val payrollVersionRepository: PayrollVersionRepository,
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
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
        val dailyRate = calculateDailyRate(notification.memberId, notification.scheduledDate)
        if (dailyRate == null || dailyRate == BigDecimal.ZERO) {
            return CLOCK_OUT_FALLBACK_BODY
        }
        val formatted = NumberFormat.getNumberInstance(Locale.KOREA).format(dailyRate)
        return notification.notificationType.getBody(formatted)
    }

    private fun calculateDailyRate(memberId: Long, date: LocalDate): BigDecimal? {
        val payroll = payrollVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, date)
            ?: return null
        val policy = workPolicyVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, date)
            ?: return null

        return SalaryCalculator.calculateDailyRate(
            targetDate = date,
            salaryType = SalaryType.from(payroll.salaryInputType),
            salaryAmount = payroll.salaryAmount,
            workDays = policy.workdays.map { it.dayOfWeek }.toSet(),
        )
    }

    companion object {
        private const val CLOCK_OUT_FALLBACK_BODY = "오늘도 수고하셨어요!"
    }
}

data class NotificationMessage(val title: String, val body: String)
