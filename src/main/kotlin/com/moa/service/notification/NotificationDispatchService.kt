package com.moa.service.notification

import com.moa.entity.NotificationLog
import com.moa.entity.NotificationStatus
import com.moa.repository.FcmTokenRepository
import com.moa.repository.NotificationLogRepository
import com.moa.service.FcmService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime

@Service
class NotificationDispatchService(
    private val notificationLogRepository: NotificationLogRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val fcmService: FcmService,
    private val notificationMessageBuilder: NotificationMessageBuilder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processNotifications(date: LocalDate, currentTime: LocalTime) {
        val pendingLogs = notificationLogRepository
            .findAllByScheduledDateAndScheduledTimeLessThanEqualAndStatus(
                scheduledDate = date,
                scheduledTime = currentTime,
                status = NotificationStatus.PENDING,
            )
        if (pendingLogs.isEmpty()) return

        log.info("Dispatching {} pending notifications", pendingLogs.size)

        val memberIds = pendingLogs.map { it.memberId }.distinct()
        val tokensByMemberId = fcmTokenRepository.findAllByMemberIdIn(memberIds)
            .groupBy { it.memberId }

        data class DispatchItem(
            val notification: NotificationLog,
            val token: String,
            val data: Map<String, String>,
        )

        val dispatchItems = mutableListOf<DispatchItem>()
        for (notification in pendingLogs) {
            val tokens = tokensByMemberId[notification.memberId].orEmpty()
            if (tokens.isEmpty()) {
                notification.status = NotificationStatus.FAILED
                log.warn("No FCM tokens for member {}, marking as FAILED", notification.memberId)
                continue
            }
            val data = notificationMessageBuilder.buildMessage(notification).toData()
            tokens.forEach { dispatchItems.add(DispatchItem(notification, it.token, data)) }
        }

        if (dispatchItems.isNotEmpty()) {
            val results = fcmService.sendEach(dispatchItems.map { it.token to it.data })
            results.forEachIndexed { i, success ->
                if (success) dispatchItems[i].notification.status = NotificationStatus.SENT
            }
            pendingLogs.filter { it.status == NotificationStatus.PENDING }
                .forEach { it.status = NotificationStatus.FAILED }
        }

        notificationLogRepository.saveAll(pendingLogs)
    }
}
