package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.repository.FcmTokenRepository
import com.moa.repository.NotificationLogRepository
import com.moa.service.FcmService
import com.moa.service.PublicHolidayService
import com.moa.service.dto.FcmRequest
import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Service
class NotificationDispatchService(
    private val notificationLogRepository: NotificationLogRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val fcmService: FcmService,
    private val notificationMessageBuilder: NotificationMessageBuilder,
    private val publicHolidayService: PublicHolidayService,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun pendingCounter(type: String): Counter = Counter.builder(METRIC_PENDING)
        .description("발송 시도된 알림 수")
        .tag("notification_type", type)
        .register(meterRegistry)

    private fun failedCounter(type: String, reason: String): Counter = Counter.builder(METRIC_FAILED)
        .description("발송 실패한 알림 수")
        .tag("notification_type", type)
        .tag("reason", reason)
        .register(meterRegistry)

    @Timed(value = "moa.notification.dispatch", percentiles = [0.5, 0.95, 0.99])
    fun processNotifications(date: LocalDate, currentTime: LocalTime) {
        val pendingLogs = notificationLogRepository
            .findAllByScheduledDateAndScheduledTimeLessThanEqualAndStatus(
                scheduledDate = date,
                scheduledTime = currentTime,
                status = NotificationStatus.PENDING,
            )
        if (pendingLogs.isEmpty()) return

        log.info("Dispatching {} pending notifications", pendingLogs.size)
        pendingLogs.groupingBy { it.notificationType.name }.eachCount()
            .forEach { (type, n) -> pendingCounter(type).increment(n.toDouble()) }

        val memberIds = pendingLogs.map { it.memberId }.distinct()
        val tokensByMemberId = fcmTokenRepository.findAllByMemberIdIn(memberIds)
            .groupBy { it.memberId }

        val holidaysByMonth = pendingLogs
            .map { YearMonth.from(it.scheduledDate) }
            .distinct()
            .associateWith { publicHolidayService.getHolidayDatesForMonth(it.year, it.monthValue) }

        val dispatchItems = mutableListOf<DispatchItem>()
        for (notification in pendingLogs) {
            val typeName = notification.notificationType.name
            val tokens = tokensByMemberId[notification.memberId].orEmpty()
            if (tokens.isEmpty()) {
                notification.status = NotificationStatus.FAILED
                failedCounter(typeName, REASON_NO_TOKEN).increment()
                log.warn("No FCM tokens for member {}, marking as FAILED", notification.memberId)
                continue
            }
            try {
                val publicHolidays = holidaysByMonth[YearMonth.from(notification.scheduledDate)] ?: emptySet()
                val data = notificationMessageBuilder.buildMessage(notification, publicHolidays).toData()
                tokens.forEach { dispatchItems.add(DispatchItem(notification, it.token, data)) }
            } catch (e: Exception) {
                notification.status = NotificationStatus.FAILED
                failedCounter(typeName, REASON_BUILD).increment()
                log.error(
                    "Failed to build message for notification {}, member {}",
                    notification.id,
                    notification.memberId,
                    e
                )
            }
        }

        if (dispatchItems.isNotEmpty()) {
            val results = fcmService.sendEach(dispatchItems.map { FcmRequest(it.token, it.data) })
            results.forEachIndexed { i, success ->
                if (success) dispatchItems[i].notification.status = NotificationStatus.SENT
            }
            pendingLogs.filter { it.status == NotificationStatus.PENDING }
                .forEach {
                    it.status = NotificationStatus.FAILED
                    failedCounter(it.notificationType.name, REASON_FCM).increment()
                }
        }

        notificationLogRepository.saveAll(pendingLogs)
    }

    companion object {
        private const val METRIC_PENDING = "moa.notification.dispatch.pending"
        private const val METRIC_FAILED = "moa.notification.dispatch.failed"
        private const val REASON_NO_TOKEN = "no_token"
        private const val REASON_BUILD = "build"
        private const val REASON_FCM = "fcm"
    }
}

private data class DispatchItem(
    val notification: NotificationLog,
    val token: String,
    val data: Map<String, String>,
)
