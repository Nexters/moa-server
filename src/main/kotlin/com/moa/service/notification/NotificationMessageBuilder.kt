package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.*

@Service
class NotificationMessageBuilder(
    private val notificationEarningsService: NotificationEarningsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun buildMessage(
        notification: NotificationLog,
        publicHolidays: Set<LocalDate>,
    ): NotificationMessageBuildResult {
        val title = notification.notificationType.title
        return when (notification.notificationType) {
            NotificationType.CLOCK_IN, NotificationType.PAYDAY ->
                NotificationMessageBuildResult(
                    NotificationMessage(title, notification.notificationType.body, notification.notificationType),
                    fallbackUsed = false,
                )

            NotificationType.CLOCK_OUT -> buildClockOutResult(notification, publicHolidays)
        }
    }

    private fun buildClockOutResult(
        notification: NotificationLog,
        publicHolidays: Set<LocalDate>,
    ): NotificationMessageBuildResult {
        val earnings = try {
            notificationEarningsService.calculateTodayEarnings(
                notification.memberId,
                notification.scheduledDate,
                publicHolidays,
            )
        } catch (e: Exception) {
            log.error(
                "Fallback message used — earnings calculation failed for notification {}, member {}",
                notification.id,
                notification.memberId,
                e,
            )
            return fallbackResult(notification, REASON_EARNINGS_ERROR)
        }

        if (earnings == BigDecimal.ZERO) {
            log.warn(
                "Fallback message used — zero earnings for notification {}, member {}",
                notification.id,
                notification.memberId,
            )
            return fallbackResult(notification, REASON_ZERO_EARNINGS)
        }

        val formatted = NumberFormat.getNumberInstance(Locale.KOREA)
            .format(earnings.setScale(0, RoundingMode.HALF_UP))
        return NotificationMessageBuildResult(
            NotificationMessage(
                notification.notificationType.title,
                notification.notificationType.getBody(formatted),
                notification.notificationType,
            ),
            fallbackUsed = false,
        )
    }

    private fun fallbackResult(notification: NotificationLog, reason: String): NotificationMessageBuildResult =
        NotificationMessageBuildResult(
            NotificationMessage(
                notification.notificationType.title,
                CLOCK_OUT_FALLBACK_BODY,
                notification.notificationType,
            ),
            fallbackUsed = true,
            fallbackReason = reason,
        )

    companion object {
        private const val CLOCK_OUT_FALLBACK_BODY = "오늘도 수고하셨어요!"
        const val REASON_ZERO_EARNINGS = "zero_earnings"
        const val REASON_EARNINGS_ERROR = "earnings_error"
    }
}

data class NotificationMessage(val title: String, val body: String, val type: NotificationType) {
    fun toData(): Map<String, String> = mapOf(
        "title" to title,
        "body" to body,
        "type" to type.name,
    )
}

data class NotificationMessageBuildResult(
    val message: NotificationMessage,
    val fallbackUsed: Boolean,
    val fallbackReason: String? = null,
)
