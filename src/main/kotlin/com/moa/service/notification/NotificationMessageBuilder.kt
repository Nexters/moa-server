package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationType
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.*

@Service
class NotificationMessageBuilder(
    private val notificationEarningsService: NotificationEarningsService,
) {

    fun buildMessage(
        notification: NotificationLog,
        publicHolidays: Set<LocalDate>,
    ): NotificationMessage {
        val title = notification.notificationType.title
        val body = when (notification.notificationType) {
            NotificationType.CLOCK_IN -> notification.notificationType.body
            NotificationType.CLOCK_OUT -> buildClockOutBody(notification, publicHolidays)
            NotificationType.PAYDAY -> notification.notificationType.body
            NotificationType.PUBLIC_HOLIDAY -> notification.notificationType.body
        }
        return NotificationMessage(title, body, notification.notificationType)
    }

    private fun buildClockOutBody(
        notification: NotificationLog,
        publicHolidays: Set<LocalDate>,
    ): String {
        val earnings = notificationEarningsService.calculateTodayEarnings(
            notification.memberId,
            notification.scheduledDate,
            publicHolidays,
        )
        if (earnings == BigDecimal.ZERO) {
            return CLOCK_OUT_FALLBACK_BODY
        }
        val formatted = NumberFormat.getNumberInstance(Locale.KOREA).format(earnings)
        return notification.notificationType.getBody(formatted)
    }

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
