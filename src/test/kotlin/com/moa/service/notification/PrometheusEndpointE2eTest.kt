package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.repository.NotificationLogRepository
import io.mockk.mockk
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.LocalTime

/**
 * End-to-End 테스트.
 *
 * 통합 테스트(`NotificationDispatchIntegrationTest`)와의 차이:
 * - 통합: `MeterRegistry` 객체를 직접 주입받아 메트릭 값을 in-process로 단언.
 * - E2E: 실제 HTTP 서버(Random Port)를 띄우고 `RestClient`로 `/actuator/prometheus`
 *   를 호출해 **응답 본문 텍스트**를 검증. 운영에서 Grafana Alloy가 scrape하는 그대로의
 *   계약(노출 형식 + Prometheus exposition format + 라벨 직렬화)을 회귀로 잠근다.
 *
 * 즉, 단위→통합→E2E 로 갈수록:
 *   - 격리도는 낮아지고
 *   - 시스템 경계까지 검증 범위가 늘어나고
 *   - 실행 비용은 커진다.
 * 그래서 **단위는 분기마다, 통합은 핵심 흐름당 1개, E2E는 외부 계약당 1개**가 일반적인 비율.
 *
 * NotificationDispatchScheduler 빈을 mock으로 교체한 이유:
 *   `@SpringBootTest`는 컴포넌트 스캔을 모두 수행해서 Scheduler가 cron으로 살아있다.
 *   그 cron이 분 경계에서 실제 `processNotifications`를 호출하면, 본 테스트가
 *   명시 호출을 안 하고도 Timer가 노출되어 false positive로 통과할 수 있다.
 *   noop mock으로 교체해 *명시 호출만 측정*되도록 격리.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.endpoints.access.default=read_only",
        "management.prometheus.metrics.export.enabled=true",
    ],
)
@Import(PrometheusEndpointE2eTest.NoopSchedulerConfig::class)
class PrometheusEndpointE2eTest @Autowired constructor(
    @LocalServerPort private val port: Int,
    private val notificationDispatchService: NotificationDispatchService,
    private val notificationLogRepository: NotificationLogRepository,
    private val meterRegistry: MeterRegistry,
) {

    private val httpClient = RestClient.create()

    @AfterEach
    fun cleanup() {
        notificationLogRepository.deleteAll()
        meterRegistry.clear()
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
        assertThat(body).contains("moa_notification_dispatch_attempts_total")
        assertThat(body).contains("moa_notification_dispatch_failed_total")

        // 2) Timer — histogram 모드라 bucket 시리즈가 노출되어야 한다.
        //    server-side `histogram_quantile()`로 정확한 multi-instance/multi-color 집계가 가능.
        //    summary(percentiles)와 달리 deploy_color 합산 시 수학적으로 valid.
        assertThat(body).contains("moa_notification_dispatch_seconds_bucket")

        // 3) 분기별 태그가 그대로 직렬화되어 보이는지 (notification_type, reason)
        assertThat(body).contains("notification_type=\"CLOCK_IN\"")
        assertThat(body).contains("reason=\"no_token\"")

        // 4) common tag — 비즈니스 메트릭 라인에 *실제로* 부착되었는지 확인.
        //    `body.contains("application=\"moa\"")`만 단언하면 JVM/HTTP 등 무관한 메트릭에도
        //    common tag가 붙어있어 가짜 통과 가능. 비즈니스 라인만 추출해 그 라인이 모두
        //    common tag를 가지는지(`allSatisfy`) 확인해야 customizer 회귀를 잡을 수 있다.
        val notificationMetricLines = metricLines(body, "moa_notification_dispatch_attempts_total") +
            metricLines(body, "moa_notification_dispatch_failed_total") +
            metricLines(body, "moa_notification_dispatch_seconds")
        assertThat(notificationMetricLines).isNotEmpty
        assertThat(notificationMetricLines).allSatisfy { line ->
            assertThat(line).contains("application=\"moa\"")
            assertThat(line).contains("deploy_color=")
            assertThat(line).contains("app_version=")
        }
    }

    private fun metricLines(body: String, metricPrefix: String): List<String> =
        body.lineSequence()
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .filter { line -> line.startsWith(metricPrefix) }
            .toList()

    /**
     * NotificationDispatchScheduler를 noop mock으로 교체하는 테스트 컨피그.
     * Scheduler 빈은 살아있되 cron이 호출하는 메서드가 무력화되어, 본 테스트의
     * 명시 호출만 Timer/Counter에 반영된다.
     */
    @TestConfiguration
    class NoopSchedulerConfig {
        @Bean
        @Primary
        fun noopDispatchScheduler(): NotificationDispatchScheduler =
            mockk(relaxed = true)
    }

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2026, 5, 1)
        private val NOON: LocalTime = LocalTime.NOON
    }
}
