package com.moa.service.notification

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.repository.NotificationLogRepository
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.LocalTime

/**
 * End-to-End 테스트.
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
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@Import(PrometheusEndpointE2eTest.NoopSchedulerConfig::class)
class PrometheusEndpointE2eTest @Autowired constructor(
    @LocalServerPort private val port: Int,
    private val notificationDispatchService: NotificationDispatchService,
    private val notificationLogRepository: NotificationLogRepository,
    private val meterRegistry: MeterRegistry,
    private val applicationContext: ApplicationContext,
    private val dispatchScheduler: NotificationDispatchScheduler,
) {

    private val httpClient = RestClient.create()

    @AfterEach
    fun cleanup() {
        notificationLogRepository.deleteAll()
        meterRegistry.clear()
    }

    @Test
    fun `Scheduler 빈이 진짜로 mock으로 교체됐는지 — bean override 검증`() {
        val schedulerBeans = applicationContext.getBeansOfType(NotificationDispatchScheduler::class.java)
        assertThat(schedulerBeans).hasSize(1)
        assertThat(schedulerBeans.keys).containsExactly("notificationDispatchScheduler")

        dispatchScheduler.dispatchPendingNotifications()
        assertThat(meterRegistry.find("moa.notification.dispatch").timer())
            .`as`("mock scheduler 호출은 production processNotifications 를 안 부르므로 Timer 등록도 안 됨")
            .isNull()
    }

    @Test
    fun `E2E - dispatch 후 actuator prometheus 엔드포인트가 신규 메트릭을 텍스트로 노출한다`() {
        // PAYDAY 는 TTL 정책상 당일 자정까지 not expired 이므로 시각 의존성을 줄이기 위해 사용.
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
        assertThat(body).contains("notification_type=\"PAYDAY\"")
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
     * NotificationDispatchScheduler 빈을 noop mock으로 *교체*(override) 한다.
     *
     * 중요: `@Bean` 의 *name* 을 component-scanned 빈과 *동일하게*
     * (`notificationDispatchScheduler`) 지정해야 진짜 교체가 일어난다.
     *
     * 다른 이름으로 정의하고 `@Primary` 만 붙이면 두 빈이 *공존* 하고,
     * component-scanned 빈이 그대로 살아있어 `@Scheduled` cron이 여전히
     * 등록된다 — Spring 의 `ScheduledAnnotationBeanPostProcessor` 는
     * 컨텍스트 안의 *모든* 빈을 스캔하기 때문이고, `@Primary` 는
     * *주입 우선순위* 만 결정할 뿐 빈 등록 자체에 개입하지 않는다.
     *
     * `spring.main.allow-bean-definition-overriding=true` 가 properties 에
     * 활성화돼야 같은 이름의 빈 재정의가 허용된다.
     */
    @TestConfiguration
    class NoopSchedulerConfig {
        @Bean("notificationDispatchScheduler")
        fun noopDispatchScheduler(): NotificationDispatchScheduler =
            mockk(relaxed = true)
    }

}
