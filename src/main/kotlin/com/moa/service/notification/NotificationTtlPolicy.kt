package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationType
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 알림 유형별 TTL정책.
 *
 * TTL의 정의: 알림이 `scheduledTime` 기준으로 N분/시간 이내에 발송되지 않으면
 * 의미를 잃는다고 간주하는 시간 임계값. 정상 동작에서는 트리거되지 않으며,
 * 오로지 서버 다운/장애 등 비정상 복구 시나리오에서만 동작한다.
 *
 * 임계값 결정 기준
 * - CLOCK_IN: 30분 — 출근 시각이 메시지 가치의 전부. 30분 지나면 노이즈.
 * - CLOCK_OUT: 당일 자정 — 일당 정보가 당일까지 가치 있음.
 * - PAYDAY: 당일 자정 — 월급날이라는 사실은 하루 종일 의미 있음.
 */
@Component
class NotificationTtlPolicy(
    private val clock: NotificationScheduleClock,
) {
    fun isExpired(notification: NotificationLog, now: LocalDateTime = clock.now()): Boolean {
        val deadline = deadlineFor(notification)
        return now.isAfter(deadline)
    }

    private fun deadlineFor(notification: NotificationLog): LocalDateTime {
        val scheduledAt = notification.scheduledDate.atTime(notification.scheduledTime)
        return when (notification.notificationType) {
            NotificationType.CLOCK_IN -> scheduledAt.plus(CLOCK_IN_TTL)
            NotificationType.CLOCK_OUT, NotificationType.PAYDAY ->
                notification.scheduledDate.atTime(LocalTime.MAX)
        }
    }

    companion object {
        val CLOCK_IN_TTL: Duration = Duration.ofMinutes(30)
    }
}
