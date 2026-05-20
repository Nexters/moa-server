package com.moa.service.notification

import com.moa.entity.FcmToken
import com.moa.entity.Term
import com.moa.entity.TermAgreement
import com.moa.entity.WorkPolicyVersion
import com.moa.entity.Workday
import com.moa.entity.notification.NotificationLog
import com.moa.entity.notification.NotificationSetting
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
 * 공휴일 정책 박제 테스트.
 *
 * - 정책: 공휴일에는 어떤 푸시 알림도 생성하지 않는다 (이전에는 PUBLIC_HOLIDAY 알림을 보냈음)
 * - `NotificationBatchService.generateNotificationsForDate` 의 holiday 가드 동작을 검증
 */
class NotificationBatchServiceHolidayTest {

    private val holidayCalendar = mutableSetOf<LocalDate>()
    private val workPolicies = mutableListOf<WorkPolicyVersion>()
    private val notificationLogs = mutableListOf<NotificationLog>()
    private val terms = mutableListOf<Term>()
    private val agreements = mutableListOf<TermAgreement>()
    private val settings = mutableListOf<NotificationSetting>()
    private val fcmTokens = mutableListOf<FcmToken>()

    private val publicHolidayRepo = mockk<PublicHolidayRepository>().apply {
        every { existsByDate(any()) } answers { firstArg<LocalDate>() in holidayCalendar }
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
            findMemberIdsByScheduledDateAndNotificationTypeAndMemberIdIn(any(), any(), any())
        } answers {
            val date = firstArg<LocalDate>()
            val type = secondArg<NotificationType>()
            val ids = thirdArg<Collection<Long>>()
            notificationLogs
                .filter { it.scheduledDate == date && it.notificationType == type && it.memberId in ids }
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
    fun `공휴일에는 적격한 회원이 있어도 어떠한 알림도 생성되지 않는다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L)

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).isEmpty()
    }

    @Test
    fun `공휴일에는 정책상 근무 요일이어도 출퇴근 알림이 생성되지 않는다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L, 근무요일 = Workday.WEEKDAYS)

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs.none { it.notificationType == NotificationType.CLOCK_IN }).isTrue()
        assertThat(notificationLogs.none { it.notificationType == NotificationType.CLOCK_OUT }).isTrue()
    }

    @Test
    fun `비공휴일에는 출퇴근 알림이 정상적으로 생성된다`() {
        회원_등록(id = 1L, 정책기준일 = 평일)

        sut.generateNotificationsForDate(평일)

        assertThat(notificationLogs.map { it.notificationType })
            .containsExactlyInAnyOrder(NotificationType.CLOCK_IN, NotificationType.CLOCK_OUT)
    }

    private fun 공휴일로_지정(date: LocalDate) {
        holidayCalendar += date
    }

    private fun 회원_등록(
        id: Long,
        정책기준일: LocalDate = 신정,
        근무요일: Set<Workday> = Workday.WEEKDAYS,
    ) {
        workPolicies += WorkPolicyVersion(
            memberId = id,
            effectiveFrom = 정책기준일.minusDays(30),
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(18, 0),
            workdays = 근무요일.toMutableSet(),
        )
        agreements += TermAgreement(memberId = id, termCode = TOS_CODE, agreed = true)
        settings += NotificationSetting(memberId = id)
        fcmTokens += FcmToken(memberId = id, token = "token-$id")
    }

    companion object {
        private const val TOS_CODE = "TERMS_OF_SERVICE"
        private val 신정: LocalDate = LocalDate.of(2026, 1, 1)
        private val 평일: LocalDate = LocalDate.of(2026, 3, 10)
    }
}
