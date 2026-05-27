package com.moa.service.notification

import com.moa.entity.FcmToken
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.repository.FcmTokenRepository
import com.moa.repository.NotificationLogRepository
import com.moa.service.FcmService
import com.moa.service.PublicHolidayService
import com.moa.service.dto.FcmRequest
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 비즈니스 메트릭 단위 테스트.
 */
class NotificationDispatchServiceMetricsTest {

    private val notificationLogs = mutableListOf<NotificationLog>()
    private val fcmTokens = mutableListOf<FcmToken>()

    private val notificationLogRepo = mockk<NotificationLogRepository>().apply {
        every {
            findAllByScheduledDateAndScheduledTimeLessThanEqualAndStatus(any(), any(), any())
        } answers {
            val date = firstArg<LocalDate>()
            val time = secondArg<LocalTime>()
            val status = thirdArg<NotificationStatus>()
            notificationLogs.filter {
                it.scheduledDate == date && !it.scheduledTime.isAfter(time) && it.status == status
            }
        }
        every { saveAll(any<Iterable<NotificationLog>>()) } answers {
            firstArg<Iterable<NotificationLog>>().toList()
        }
    }
    private val fcmTokenRepo = mockk<FcmTokenRepository>().apply {
        every { findAllByMemberIdIn(any()) } answers {
            val ids = firstArg<Collection<Long>>()
            fcmTokens.filter { it.memberId in ids }
        }
    }
    private val fcmService = mockk<FcmService>().apply {
        every { sendEach(any()) } answers {
            val reqs = firstArg<List<FcmRequest>>()
            List(reqs.size) { true }
        }
    }
    private val publicHolidayService = mockk<PublicHolidayService>().apply {
        every { getHolidayDatesForMonth(any(), any()) } returns emptySet()
    }
    private val messageBuilder = mockk<NotificationMessageBuilder>().apply {
        every { buildMessage(any(), any()) } answers {
            val n = firstArg<NotificationLog>()
            NotificationMessageBuildResult(
                NotificationMessage("title", "body", n.notificationType),
                fallbackUsed = false,
            )
        }
    }
    private val registry = SimpleMeterRegistry()

    private val sut = NotificationDispatchService(
        notificationLogRepo,
        fcmTokenRepo,
        fcmService,
        messageBuilder,
        publicHolidayService,
        registry,
    )

    @BeforeEach
    fun reset() {
        notificationLogs.clear()
        fcmTokens.clear()
    }

    @Test
    fun `토큰 없는 회원의 알림은 failed_no_token 카운터를 증가시킨다`() {
        notificationLogs += notificationLog(memberId = 1L, type = NotificationType.CLOCK_IN)

        sut.processNotifications(TODAY, NOON)

        val attempts = registry.find("moa.notification.dispatch.attempts")
            .tag("notification_type", "CLOCK_IN").counter()
        val failed = registry.find("moa.notification.dispatch.failed")
            .tag("notification_type", "CLOCK_IN").tag("reason", "no_token").counter()

        assertThat(attempts?.count()).isEqualTo(1.0)
        assertThat(failed?.count()).isEqualTo(1.0)
    }

    @Test
    fun `메시지 빌드 실패는 failed 카운터를 reason=build 태그로 증가시킨다`() {
        notificationLogs += notificationLog(memberId = 1L, type = NotificationType.CLOCK_OUT)
        fcmTokens += FcmToken(memberId = 1L, token = "tok-1")
        every { messageBuilder.buildMessage(any(), any()) } throws IllegalStateException("boom")

        sut.processNotifications(TODAY, NOON)

        val failed = registry.find("moa.notification.dispatch.failed")
            .tag("notification_type", "CLOCK_OUT").tag("reason", "build").counter()
        assertThat(failed?.count()).isEqualTo(1.0)
    }

    @Test
    fun `fallback 메시지로 발송된 알림은 fallback 카운터를 증가시키고 정상 발송된다`() {
        notificationLogs += notificationLog(memberId = 1L, type = NotificationType.CLOCK_OUT)
        fcmTokens += FcmToken(memberId = 1L, token = "tok-1")
        every { messageBuilder.buildMessage(any(), any()) } answers {
            val n = firstArg<NotificationLog>()
            NotificationMessageBuildResult(
                NotificationMessage("title", "오늘도 수고하셨어요!", n.notificationType),
                fallbackUsed = true,
                fallbackReason = NotificationMessageBuilder.REASON_EARNINGS_ERROR,
            )
        }

        sut.processNotifications(TODAY, NOON)

        val fallback = registry.find("moa.notification.dispatch.fallback")
            .tag("notification_type", "CLOCK_OUT").tag("reason", "earnings_error").counter()
        val failed = registry.find("moa.notification.dispatch.failed")
            .tag("notification_type", "CLOCK_OUT").counter()
        assertThat(fallback?.count()).isEqualTo(1.0)
        assertThat(failed).isNull()
        assertThat(notificationLogs[0].status).isEqualTo(NotificationStatus.SENT)
    }

    @Test
    fun `FCM 결과가 false인 알림은 failed 카운터를 reason=fcm 태그로 증가시킨다`() {
        notificationLogs += notificationLog(memberId = 1L, type = NotificationType.PAYDAY)
        fcmTokens += FcmToken(memberId = 1L, token = "tok-1")
        every { fcmService.sendEach(any()) } answers {
            val reqs = firstArg<List<FcmRequest>>()
            List(reqs.size) { false }
        }

        sut.processNotifications(TODAY, NOON)

        val failed = registry.find("moa.notification.dispatch.failed")
            .tag("notification_type", "PAYDAY").tag("reason", "fcm").counter()
        assertThat(failed?.count()).isEqualTo(1.0)
    }

    private fun notificationLog(memberId: Long, type: NotificationType) = NotificationLog(
        memberId = memberId,
        notificationType = type,
        scheduledDate = TODAY,
        scheduledTime = NOON.minusHours(1),
        status = NotificationStatus.PENDING,
    )

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2026, 5, 1)
        private val NOON: LocalTime = LocalTime.NOON
    }
}
