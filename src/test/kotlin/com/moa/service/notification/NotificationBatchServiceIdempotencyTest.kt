package com.moa.service.notification

import com.moa.entity.FcmToken
import com.moa.entity.Term
import com.moa.entity.TermAgreement
import com.moa.entity.WorkPolicyVersion
import com.moa.entity.Workday
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationSetting
import com.moa.entity.notification.NotificationStatus
import com.moa.entity.notification.NotificationType
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.FcmTokenRepository
import com.moa.repository.NotificationLogRepository
import com.moa.repository.NotificationSettingRepository
import com.moa.repository.PublicHolidayRepository
import com.moa.repository.TermAgreementRepository
import com.moa.repository.TermRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.PublicHolidayService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * `NotificationBatchService` 멱등성 박제 테스트.
 *
 * 정책: 같은 (memberId, scheduledDate, notificationType) 페어가 이미 존재하면 재생성하지 않는다.
 * - CLOCK_IN 만 존재할 때 → CLOCK_OUT 만 새로 생성된다
 * - 둘 다 존재하면 → 아무것도 생성되지 않는다.
 * - 야간근무자의 CLOCK_OUT 은 다음날 날짜 기준으로 체크한다.
 */
class NotificationBatchServiceIdempotencyTest {

    private val workPolicies = mutableListOf<WorkPolicyVersion>()
    private val notificationLogs = mutableListOf<NotificationLog>()
    private val terms = mutableListOf<Term>()
    private val agreements = mutableListOf<TermAgreement>()
    private val settings = mutableListOf<NotificationSetting>()
    private val fcmTokens = mutableListOf<FcmToken>()

    private val publicHolidayRepo = mockk<PublicHolidayRepository>().apply {
        every { existsByDate(any()) } returns false
    }
    private val workPolicyRepo = mockk<WorkPolicyVersionRepository>().apply {
        every { findLatestEffectivePoliciesPerMember(any()) } answers {
            val date = firstArg<LocalDate>()
            workPolicies
                .filter { !it.effectiveFrom.isAfter(date) }
                .groupBy { it.memberId }
                .map { (_, versions) -> versions.maxBy { v -> v.effectiveFrom } }
        }
    }
    private val notificationLogRepo = mockk<NotificationLogRepository>().apply {
        every { saveAll(any<Iterable<NotificationLog>>()) } answers {
            val added = firstArg<Iterable<NotificationLog>>().toList()
            notificationLogs += added
            added
        }
        every {
            findMemberIdsByScheduledDateAndNotificationTypeAndStatusInAndMemberIdIn(any(), any(), any(), any())
        } answers {
            val date = firstArg<LocalDate>()
            val type = secondArg<NotificationType>()
            val statuses = thirdArg<Collection<NotificationStatus>>()
            val ids = arg<Collection<Long>>(3)
            notificationLogs
                .filter {
                    it.scheduledDate == date &&
                            it.notificationType == type &&
                            it.status in statuses &&
                            it.memberId in ids
                }
                .map { it.memberId }
                .distinct()
        }
    }
    private val termRepo = mockk<TermRepository>().apply {
        every { findAll() } answers { terms.toList() }
    }
    private val agreementRepo = mockk<TermAgreementRepository>().apply {
        every { findAllByMemberIdIn(any()) } answers {
            val ids = firstArg<Collection<Long>>()
            agreements.filter { it.memberId in ids }
        }
    }
    private val settingRepo = mockk<NotificationSettingRepository>().apply {
        every { findAllByMemberIdIn(any()) } answers {
            val ids = firstArg<Collection<Long>>()
            settings.filter { it.memberId in ids }
        }
    }
    private val fcmTokenRepo = mockk<FcmTokenRepository>().apply {
        every { findAllByMemberIdIn(any()) } answers {
            val ids = firstArg<Collection<Long>>()
            fcmTokens.filter { it.memberId in ids }
        }
    }
    private val dailyScheduleRepo = mockk<DailyWorkScheduleRepository>().apply {
        every { findAllByMemberIdInAndDate(any(), any()) } answers { emptyList() }
    }

    private val publicHolidayService = PublicHolidayService(publicHolidayRepo)
    private val eligibilityService = NotificationEligibilityService(
        termRepo, agreementRepo, settingRepo, fcmTokenRepo, dailyScheduleRepo,
    )
    private val sut = NotificationBatchService(
        workPolicyRepo, notificationLogRepo, eligibilityService, publicHolidayService,
    )

    @BeforeEach
    fun `필수 약관 한 건을 공리로 등록해둔다`() {
        terms += Term(
            code = TOS_CODE,
            title = "서비스 이용약관",
            required = true,
            contentUrl = "https://moa.example/tos",
            sortOrder = 1,
        )
    }

    @Test
    fun `같은 날 CLOCK_IN 과 CLOCK_OUT 이 둘 다 이미 있으면 아무것도 새로 생성되지 않는다`() {
        회원_등록(id = 1L)
        선존재_알림(memberId = 1L, date = 평일, type = NotificationType.CLOCK_IN, time = LocalTime.of(9, 0))
        선존재_알림(memberId = 1L, date = 평일, type = NotificationType.CLOCK_OUT, time = LocalTime.of(18, 0))

        sut.generateNotificationsForDate(평일)

        // 선존재 2건 외 새 생성 없음
        assertThat(notificationLogs).hasSize(2)
    }

    @Test
    fun `CLOCK_IN 만 존재하고 CLOCK_OUT 이 누락된 상태라면 CLOCK_OUT 만 새로 생성된다`() {
        회원_등록(id = 1L)
        선존재_알림(memberId = 1L, date = 평일, type = NotificationType.CLOCK_IN, time = LocalTime.of(9, 0))

        sut.generateNotificationsForDate(평일)

        val byType = notificationLogs.groupingBy { it.notificationType }.eachCount()
        assertThat(byType[NotificationType.CLOCK_IN]).isEqualTo(1)
        assertThat(byType[NotificationType.CLOCK_OUT]).isEqualTo(1)
    }

    @Test
    fun `CLOCK_OUT 만 존재하고 CLOCK_IN 이 누락된 상태라면 CLOCK_IN 만 새로 생성된다`() {
        회원_등록(id = 1L)
        선존재_알림(memberId = 1L, date = 평일, type = NotificationType.CLOCK_OUT, time = LocalTime.of(18, 0))

        sut.generateNotificationsForDate(평일)

        val byType = notificationLogs.groupingBy { it.notificationType }.eachCount()
        assertThat(byType[NotificationType.CLOCK_IN]).isEqualTo(1)
        assertThat(byType[NotificationType.CLOCK_OUT]).isEqualTo(1)
    }

    @Test
    fun `야간근무자의 CLOCK_OUT 멱등성은 다음날 날짜 기준으로 체크된다`() {
        // 야간근무: 22시 출근 → 06시 퇴근 (다음날)
        회원_등록(id = 1L, 출근시각 = LocalTime.of(22, 0), 퇴근시각 = LocalTime.of(6, 0))
        선존재_알림(
            memberId = 1L,
            date = 평일.plusDays(1),
            type = NotificationType.CLOCK_OUT,
            time = LocalTime.of(6, 0),
        )

        sut.generateNotificationsForDate(평일)

        val byType = notificationLogs.groupingBy { it.notificationType }.eachCount()
        // 어제 등록된 CLOCK_OUT (다음날=평일+1) 1건 + 오늘 새로 만든 CLOCK_IN 1건
        assertThat(byType[NotificationType.CLOCK_IN]).isEqualTo(1)
        assertThat(byType[NotificationType.CLOCK_OUT]).isEqualTo(1)
    }

    @Test
    fun `CANCELLED 상태의 행은 absent 로 간주되어 같은 type 의 알림이 재생성된다`() {
        // 시나리오: 어제 휴가 등록 → 오늘 CLOCK_IN/OUT 이 CANCELLED 됨
        //          어제 휴가 취소 → 오늘 자정 배치 시 CLOCK_IN/OUT 재생성되어야 함
        회원_등록(id = 1L)
        선존재_알림(
            memberId = 1L, date = 평일, type = NotificationType.CLOCK_IN,
            time = LocalTime.of(9, 0), status = NotificationStatus.CANCELLED,
        )
        선존재_알림(
            memberId = 1L, date = 평일, type = NotificationType.CLOCK_OUT,
            time = LocalTime.of(18, 0), status = NotificationStatus.CANCELLED,
        )

        sut.generateNotificationsForDate(평일)

        val pending = notificationLogs.filter { it.status == NotificationStatus.PENDING }
        assertThat(pending.map { it.notificationType })
            .containsExactlyInAnyOrder(NotificationType.CLOCK_IN, NotificationType.CLOCK_OUT)
    }

    @Test
    fun `EXPIRED 상태의 행도 absent 로 간주되어 재생성된다`() {
        회원_등록(id = 1L)
        선존재_알림(
            memberId = 1L, date = 평일, type = NotificationType.CLOCK_IN,
            time = LocalTime.of(9, 0), status = NotificationStatus.EXPIRED,
        )

        sut.generateNotificationsForDate(평일)

        val pending = notificationLogs.filter { it.status == NotificationStatus.PENDING }
        assertThat(pending.map { it.notificationType })
            .containsExactlyInAnyOrder(NotificationType.CLOCK_IN, NotificationType.CLOCK_OUT)
    }

    private fun 선존재_알림(
        memberId: Long,
        date: LocalDate,
        type: NotificationType,
        time: LocalTime,
        status: NotificationStatus = NotificationStatus.PENDING,
    ) {
        notificationLogs += NotificationLog(
            memberId = memberId,
            notificationType = type,
            scheduledDate = date,
            scheduledTime = time,
            status = status,
        )
    }

    private fun 회원_등록(
        id: Long,
        근무요일: Set<Workday> = Workday.WEEKDAYS,
        출근시각: LocalTime = LocalTime.of(9, 0),
        퇴근시각: LocalTime = LocalTime.of(18, 0),
    ) {
        workPolicies += WorkPolicyVersion(
            memberId = id,
            effectiveFrom = 평일.minusDays(30),
            clockInTime = 출근시각,
            clockOutTime = 퇴근시각,
            workdays = 근무요일.toMutableSet(),
        )
        agreements += TermAgreement(memberId = id, termCode = TOS_CODE, agreed = true)
        settings += NotificationSetting(memberId = id)
        fcmTokens += FcmToken(memberId = id, token = "token-$id")
    }

    companion object {
        private const val TOS_CODE = "TERMS_OF_SERVICE"
        private val 평일: LocalDate = LocalDate.of(2026, 3, 10)
    }
}
