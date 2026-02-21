package com.moa.service.notification

import com.moa.entity.NotificationStatus
import com.moa.repository.NotificationLogRepository
import com.moa.service.FcmService
import com.moa.service.FcmTokenService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

@Service
class NotificationDispatchService(
    private val notificationLogRepository: NotificationLogRepository,
    private val fcmTokenService: FcmTokenService,
    private val fcmService: FcmService,
    private val notificationMessageBuilder: NotificationMessageBuilder,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processNotifications(date: LocalDate, currentTime: LocalTime) {
        val pendingLogs = notificationLogRepository
            .findAllByScheduledDateAndScheduledTimeLessThanEqualAndStatus(
                scheduledDate = date,
                scheduledTime = currentTime,
                status = NotificationStatus.PENDING,
            )

        if (pendingLogs.isEmpty()) return

        log.info("Dispatching {} pending notifications", pendingLogs.size)

        for (notification in pendingLogs) {
            val tokens = fcmTokenService.getTokensByMemberId(notification.memberId)

            if (tokens.isEmpty()) {
                notification.status = NotificationStatus.FAILED
                log.warn("No FCM tokens for member {}, marking as FAILED", notification.memberId)
                continue
            }

            val message = notificationMessageBuilder.buildMessage(notification)
            val data = mapOf(
                "title" to message.title,
                "body" to message.body,
                "type" to message.type.name,
            )

            var anySuccess = false
            for (token in tokens) {
                if (fcmService.send(token.token, data)) {
                    anySuccess = true
                }
            }

            notification.status = if (anySuccess) NotificationStatus.SENT else NotificationStatus.FAILED
        }
    }
}
