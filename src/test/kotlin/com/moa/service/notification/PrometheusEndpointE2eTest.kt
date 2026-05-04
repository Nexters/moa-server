package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.repository.NotificationLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.LocalTime

/**
 * End-to-End 테스트.
 *
 * 통합 테스트(`NotificationDispatchIntegrationTest`)와의 차이:
 * - 통합: `MeterRegistry` 객체를 직접 주입받아 메트릭 값을 in-process로 단언.
 * - E2E: 실제 HTTP 서버(Random Port)를 띄우고 `TestRestTemplate`으로 `/actuator/prometheus`
 *   를 호출해 **응답 본문 텍스트**를 검증. 운영에서 Grafana Alloy가 scrape하는 그대로의
 *   계약(노출 형식 + Prometheus exposition format + 라벨 직렬화)을 회귀로 잠근다.
 *
 * 즉, 단위→통합→E2E 로 갈수록:
 *   - 격리도는 낮아지고
 *   - 시스템 경계까지 검증 범위가 늘어나고
 *   - 실행 비용은 커진다.
 * 그래서 **단위는 분기마다, 통합은 핵심 흐름당 1개, E2E는 외부 계약당 1개**가 일반적인 비율.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.endpoints.access.default=read_only",
        "management.prometheus.metrics.export.enabled=true",
    ],
)
class PrometheusEndpointE2eTest @Autowired constructor(
    @LocalServerPort private val port: Int,
    private val notificationDispatchService: NotificationDispatchService,
    private val notificationLogRepository: NotificationLogRepository,
) {

    private val httpClient = RestClient.create()

    @AfterEach
    fun cleanup() {
        notificationLogRepository.deleteAll()
    }

    @Test
    fun `E2E - dispatch 후 actuator prometheus 엔드포인트가 신규 메트릭을 텍스트로 노출한다`() {
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

        val body = httpClient.get()
            .uri("http://localhost:$port/actuator/prometheus")
            .retrieve()
            .body(String::class.java) ?: ""

        // 1) Counter 라인 노출 (Prometheus는 점을 underscore로 변환하고 카운터에 _total 접미사 부착)
        assertThat(body).contains("moa_notification_dispatch_pending_total")
        assertThat(body).contains("moa_notification_dispatch_failed_total")

        // 2) Timer (Spring Boot는 summary 형태 + p50/p95/p99 quantile)
        assertThat(body).contains("moa_notification_dispatch_seconds")
        assertThat(body).contains("quantile=\"0.95\"")

        // 3) 분기별 태그가 그대로 직렬화되어 보이는지 (notification_type, reason)
        assertThat(body).contains("notification_type=\"CLOCK_IN\"")
        assertThat(body).contains("reason=\"no_token\"")

        // 4) common tag — 운영 PromQL의 `deploy_color`, `app_version` 라벨 분리에 필수
        assertThat(body).contains("application=\"moa\"")
        assertThat(body).contains("deploy_color=")
        assertThat(body).contains("app_version=")
    }

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2026, 5, 1)
        private val NOON: LocalTime = LocalTime.NOON
    }
}
