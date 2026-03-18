package com.moa.service.notification

import com.moa.common.exception.NotFoundException
import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationType
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.PayrollVersionRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.calculator.CompensationCalculator
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.*

@Service
class NotificationMessageBuilder(
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val payrollVersionRepository: PayrollVersionRepository,
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
    private val compensationCalculator: CompensationCalculator,
) {

    fun buildMessage(notification: NotificationLog): NotificationMessage {
        val title = notification.notificationType.title
        val body = when (notification.notificationType) {
            NotificationType.CLOCK_IN -> notification.notificationType.body
            NotificationType.CLOCK_OUT -> buildClockOutBody(notification)
            NotificationType.PAYDAY -> notification.notificationType.body
        }
        return NotificationMessage(title, body, notification.notificationType)
    }

    private fun buildClockOutBody(notification: NotificationLog): String {
        val earnings = calculateTodayEarnings(notification.memberId, notification.scheduledDate)
        if (earnings == BigDecimal.ZERO) {
            return CLOCK_OUT_FALLBACK_BODY
        }
        val formatted = NumberFormat.getNumberInstance(Locale.KOREA).format(earnings)
        return notification.notificationType.getBody(formatted)
    }

    private fun calculateTodayEarnings(memberId: Long, date: LocalDate): BigDecimal {
        val policy = resolveMonthlyRepresentativePolicyOrNull(memberId, date.year, date.monthValue) ?: throw NotFoundException()
        val payroll = resolveMonthlyRepresentativePayrollOrNull(memberId, date.year, date.monthValue) ?: throw NotFoundException()

        val override = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
        return compensationCalculator.calculateDailyEarnings(
            date = date,
            salaryType = payroll.salaryInputType,
            salaryAmount = payroll.salaryAmount,
            policy = policy,
            type = override?.type ?: DailyWorkScheduleType.WORK,
            clockInTime = override?.clockInTime,
            clockOutTime = override?.clockOutTime,
        )
    }

    private fun resolveMonthlyRepresentativePolicyOrNull(
        memberId: Long,
        year: Int,
        month: Int,
    ) = workPolicyVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        memberId,
        YearMonth.of(year, month).atEndOfMonth(),
    )

    private fun resolveMonthlyRepresentativePayrollOrNull(
        memberId: Long,
        year: Int,
        month: Int,
    ) = payrollVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        memberId,
        YearMonth.of(year, month).atEndOfMonth(),
    )

    companion object {
        private const val CLOCK_OUT_FALLBACK_BODY = "오늘도 수고하셨어요!"
    }
}

data class NotificationMessage(val title: String, val body: String, val type: NotificationType) {
    fun toData(): Map<String, String> = mapOf(
        "title" to title,
        "body" to body,
        "type" to type.name,
    )
}
