package com.moa.repository

import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDate
import java.time.LocalTime

/**
 * Cleanup 배치 안전망의 벌크 쿼리(`markExpiredBefore`)를 실제 H2(MySQL 모드)에서 검증한다.
 *
 * mockk fake 대신 실 DB 로 검증하는 이유: JPQL 의 status/scheduledDate 조건이나
 * 벌크 UPDATE 의 영향 범위가 틀려도 fake 는 통과시켜버린다. SQL 동작 자체가 검증 대상.
 */
@DataJpaTest
class NotificationLogRepositoryCleanupTest @Autowired constructor(
    private val notificationLogRepository: NotificationLogRepository,
    private val entityManager: EntityManager,
) {

    @Test
    fun `임계일 이전의 PENDING 은 EXPIRED 로 마킹된다`() {
        val log = save(date = 기준일.minusDays(8), type = NotificationType.CLOCK_IN, status = NotificationStatus.PENDING)

        val updated = notificationLogRepository.markExpiredBefore(기준일)
        flushAndClear()

        assertThat(updated).isEqualTo(1)
        assertThat(reload(log.id).status).isEqualTo(NotificationStatus.EXPIRED)
    }

    @Test
    fun `임계일 당일 이후의 PENDING 은 영향받지 않는다`() {
        // 경계 의미: markExpiredBefore 는 scheduledDate < threshold(strict) 만 만료시킨다.
        // 따라서 임계일 당일(==)과 그 이후(>)는 모두 영향받지 않아야 한다.
        val onThreshold = save(date = 기준일, type = NotificationType.CLOCK_IN, status = NotificationStatus.PENDING)
        val afterThreshold = save(date = 기준일.plusDays(1), type = NotificationType.CLOCK_OUT, status = NotificationStatus.PENDING)

        val updated = notificationLogRepository.markExpiredBefore(기준일)
        flushAndClear()

        assertThat(updated).isEqualTo(0)
        assertThat(reload(onThreshold.id).status).isEqualTo(NotificationStatus.PENDING)
        assertThat(reload(afterThreshold.id).status).isEqualTo(NotificationStatus.PENDING)
    }

    @Test
    fun `PENDING 이 아닌 행은 임계일 이전이라도 영향받지 않는다`() {
        val sent = save(date = 기준일.minusDays(10), type = NotificationType.PAYDAY, status = NotificationStatus.SENT)
        val failed = save(date = 기준일.minusDays(10), type = NotificationType.CLOCK_IN, status = NotificationStatus.FAILED)
        val cancelled = save(date = 기준일.minusDays(10), type = NotificationType.CLOCK_OUT, status = NotificationStatus.CANCELLED)

        val updated = notificationLogRepository.markExpiredBefore(기준일)
        flushAndClear()

        assertThat(updated).isEqualTo(0)
        assertThat(reload(sent.id).status).isEqualTo(NotificationStatus.SENT)
        assertThat(reload(failed.id).status).isEqualTo(NotificationStatus.FAILED)
        assertThat(reload(cancelled.id).status).isEqualTo(NotificationStatus.CANCELLED)
    }

    @Test
    fun `대상이 없으면 0 을 반환하고 예외 없이 종료된다`() {
        val updated = notificationLogRepository.markExpiredBefore(기준일)

        assertThat(updated).isEqualTo(0)
    }

    @Test
    fun `여러 type 이 섞여 있어도 임계일 이전 PENDING 만 마킹하고 실제 건수를 반환한다`() {
        save(date = 기준일.minusDays(8), type = NotificationType.CLOCK_IN, status = NotificationStatus.PENDING)
        save(date = 기준일.minusDays(8), type = NotificationType.CLOCK_IN, status = NotificationStatus.PENDING)
        save(date = 기준일.minusDays(9), type = NotificationType.PAYDAY, status = NotificationStatus.PENDING)
        // 제외 대상: 임계일 당일 / PENDING 아님
        save(date = 기준일, type = NotificationType.CLOCK_IN, status = NotificationStatus.PENDING)
        save(date = 기준일.minusDays(8), type = NotificationType.CLOCK_OUT, status = NotificationStatus.SENT)

        val updated = notificationLogRepository.markExpiredBefore(기준일)

        assertThat(updated).isEqualTo(3)
    }

    private fun save(
        date: LocalDate,
        type: NotificationType,
        status: NotificationStatus,
    ): NotificationLog = notificationLogRepository.save(
        NotificationLog(
            memberId = 1L,
            notificationType = type,
            scheduledDate = date,
            scheduledTime = LocalTime.of(9, 0),
            status = status,
        ),
    )

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun reload(id: Long): NotificationLog =
        notificationLogRepository.findById(id).orElseThrow()

    companion object {
        private val 기준일: LocalDate = LocalDate.of(2026, 5, 31)
    }
}
