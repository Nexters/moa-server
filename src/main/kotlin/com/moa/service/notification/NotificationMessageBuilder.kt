package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationType
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

@Service
class NotificationMessageBuilder(
    private val notificationEarningsService: NotificationEarningsService,
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
        val earnings = notificationEarningsService.calculateTodayEarnings(
            notification.memberId,
            notification.scheduledDate,
        )
        if (earnings == BigDecimal.ZERO) {
            return CLOCK_OUT_FALLBACK_BODY
        }
        val formatted = NumberFormat.getNumberInstance(Locale.KOREA)
            .format(earnings.setScale(0, RoundingMode.HALF_UP))
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
