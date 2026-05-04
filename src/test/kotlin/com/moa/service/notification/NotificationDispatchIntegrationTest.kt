package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.repository.NotificationLogRepository
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.LocalTime

/**
 * 통합 테스트
 *
 * 단위 테스트(`NotificationDispatchServiceMetricsTest`)와 비교:
 * - 단위: `SimpleMeterRegistry` + MockK Fake → 빠르고 격리됨, 그러나 Spring 자동 구성을
 *   거치지 않아 common tag(`application/deploy_color/app_version`)가 부착되지 않음.
 * - 통합: 실제 Spring 컨텍스트 + 실제 `PrometheusMeterRegistry` + 실제 H2 → 자동 구성된
 *   `MeterRegistryCustomizer`까지 포함해 메트릭이 운영에서 보일 모습 그대로 검증.
 *
 */
@SpringBootTest
class NotificationDispatchIntegrationTest @Autowired constructor(
    private val notificationDispatchService: NotificationDispatchService,
    private val notificationLogRepository: NotificationLogRepository,
    private val meterRegistry: MeterRegistry,
) {

    @AfterEach
    fun cleanup() {
        notificationLogRepository.deleteAll()
    }

    @Test
    fun `통합 - dispatch 호출 후 PrometheusMeterRegistry에 비즈니스 메트릭과 common tag가 함께 기록된다`() {
        notificationLogRepository.save(
            NotificationLog(
                memberId = 1L,
                notificationType = NotificationType.CLOCK_IN,
                scheduledDate = TODAY,
                scheduledTime = NOON.minusHours(1),
                status = NotificationStatus.PENDING,
            )
        )

        notificationDispatchService.processNotifications(TODAY, NOON)

        val pending = meterRegistry.find("moa.notification.dispatch.pending")
            .tag("notification_type", "CLOCK_IN").counter()
        val failed = meterRegistry.find("moa.notification.dispatch.failed")
            .tag("notification_type", "CLOCK_IN").tag("reason", "no_token").counter()

        assertThat(pending?.count()).isEqualTo(1.0)
        assertThat(failed?.count()).isEqualTo(1.0)

        // common tag 부착 검증 — `MetricsConfig`의 `MeterRegistryCustomizer`가
        // 모든 신규 메트릭에 application/deploy_color/app_version 을 자동으로 붙이는지 확인.
        val tagKeys = pending?.id?.tags?.map { it.key }.orEmpty()
        assertThat(tagKeys).contains("application", "deploy_color", "app_version", "notification_type")
    }

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2026, 5, 1)
        private val NOON: LocalTime = LocalTime.NOON
    }
}
