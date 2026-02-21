package com.moa.service

import com.moa.entity.NotificationStatus
import com.moa.repository.NotificationLogRepository
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
            log.info("전송된 메시지 내용입니다. \n ------------------------------------------ \n {}", message)

            var anySuccess = false
            for (token in tokens) {
                if (fcmService.send(token.token, message.title, message.body)) {
                    anySuccess = true
                }
            }

            notification.status = if (anySuccess) NotificationStatus.SENT else NotificationStatus.FAILED
        }
    }
}
