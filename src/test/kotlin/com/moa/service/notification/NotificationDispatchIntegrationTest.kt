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
        val today = LocalDate.now()
        notificationLogRepository.save(
            NotificationLog(
                memberId = 1L,
                notificationType = NotificationType.PAYDAY,
                scheduledDate = today,
                scheduledTime = LocalTime.of(9, 0),
                status = NotificationStatus.PENDING,
            )
        )

        notificationDispatchService.processNotifications(today, LocalTime.of(12, 0))

        val attempts = meterRegistry.find("moa.notification.dispatch.attempts")
            .tag("notification_type", "PAYDAY").counter()
        val failed = meterRegistry.find("moa.notification.dispatch.failed")
            .tag("notification_type", "PAYDAY").tag("reason", "no_token").counter()

        assertThat(attempts?.count()).isEqualTo(1.0)
        assertThat(failed?.count()).isEqualTo(1.0)

        // common tag 부착 검증 — `MetricsConfig`의 `MeterRegistryCustomizer`가
        // 모든 신규 메트릭에 application/deploy_color/app_version 을 자동으로 붙이는지 확인.
        //
        // 키 존재만으론 부족하다 — `app_version=""` 처럼 *값이 비어있는* 회귀를 못 잡는다.
        // 값까지 검증 + 두 메트릭 간 공통 태그 일관성까지 비교해서 customizer가 *부분만*
        // 적용되는 깨진 상태를 잡는다.
        val attemptsTags = attempts?.id?.tags.orEmpty().associate { it.key to it.value }
        val failedTags = failed?.id?.tags.orEmpty().associate { it.key to it.value }

        // 분기 태그 — 비즈니스 코드가 직접 붙인 태그가 정확히 흘러갔는지
        assertThat(attemptsTags["notification_type"]).isEqualTo("PAYDAY")
        assertThat(failedTags["notification_type"]).isEqualTo("PAYDAY")
        assertThat(failedTags["reason"]).isEqualTo("no_token")

        // common tag — 값이 비어있지 않은지
        assertThat(attemptsTags["application"]).isNotNull().isNotBlank()
        assertThat(attemptsTags["deploy_color"]).isNotNull().isNotBlank()
        assertThat(attemptsTags["app_version"]).isNotNull().isNotBlank()

        // common tag 일관성 — customizer가 두 메트릭에 *같은 값*을 붙였는지
        assertThat(failedTags["application"]).isEqualTo(attemptsTags["application"])
        assertThat(failedTags["deploy_color"]).isEqualTo(attemptsTags["deploy_color"])
        assertThat(failedTags["app_version"]).isEqualTo(attemptsTags["app_version"])
    }
}
