package com.moa.service.notification

import com.moa.entity.DailyWorkSchedule
import com.moa.entity.DailyWorkScheduleType
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
 * 고전파(Classicist) 스타일 단위 테스트.
 *
 * - 모든 **out-of-process** 의존성(JPA Repository)만 Fake로 대체한다.
 * - `PublicHolidayService`, `NotificationEligibilityService` 는 **실물**을 사용한다.
 * - 검증은 오직 최종 상태(`notificationLogs` 리스트의 내용)로만 한다 — `verify` 금지.
 *
 * Fake 저장소(`holidayCalendar`, `workPolicies`, …)가 프로덕션의 MySQL 테이블 역할을 한다.
 */
class NotificationBatchServiceHolidayTest {

    private val holidayCalendar = mutableSetOf<LocalDate>()
    private val workPolicies = mutableListOf<WorkPolicyVersion>()
    private val notificationLogs = mutableListOf<NotificationLog>()
    private val terms = mutableListOf<Term>()
    private val agreements = mutableListOf<TermAgreement>()
    private val settings = mutableListOf<NotificationSetting>()
    private val fcmTokens = mutableListOf<FcmToken>()
    private val dailySchedules = mutableListOf<DailyWorkSchedule>()

    // ─── Repository = 자료구조의 얇은 어댑터 (Fake 역할) ──────────────────────
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
        every { findAllByMemberIdInAndDate(any(), any()) } answers {
            val ids = firstArg<Collection<Long>>()
            val date = secondArg<LocalDate>()
            dailySchedules.filter { it.memberId in ids && it.date == date }
        }
    }

    // ─── 실물 도메인 서비스 ───────────────────────────────────────────────────
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

    // ═════════════════════════════════════════════════════════════════════════
    // 1. 행복 경로
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `공휴일에 세팅·약관·토큰을 모두 갖춘 회원은 09시 PUBLIC_HOLIDAY 로그를 PENDING 상태로 받는다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L)

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).hasSize(1)
        val log = notificationLogs.first()
        assertThat(log.memberId).isEqualTo(1L)
        assertThat(log.notificationType).isEqualTo(NotificationType.PUBLIC_HOLIDAY)
        assertThat(log.scheduledDate).isEqualTo(신정)
        assertThat(log.scheduledTime).isEqualTo(LocalTime.of(9, 0))
        assertThat(log.status).isEqualTo(NotificationStatus.PENDING)
    }

    @Test
    fun `적격 회원이 여러 명이면 각자 한 건씩 생성된다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L)
        회원_등록(id = 2L)
        회원_등록(id = 3L)

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs.map { it.memberId }).containsExactlyInAnyOrder(1L, 2L, 3L)
        assertThat(notificationLogs).allMatch { it.notificationType == NotificationType.PUBLIC_HOLIDAY }
    }

    @Test
    fun `비공휴일에는 PUBLIC_HOLIDAY 로그가 생성되지 않는다`() {
        val 평일 = LocalDate.of(2026, 3, 10)
        회원_등록(id = 1L, 정책기준일 = 평일)

        sut.generateNotificationsForDate(평일)

        assertThat(notificationLogs.none { it.notificationType == NotificationType.PUBLIC_HOLIDAY }).isTrue()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. 제외 조건 (알림을 받지 말아야 하는 회원)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `공휴일이어도 WORK 알림을 꺼둔 회원에게는 로그가 생성되지 않는다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L, 알림_켜짐 = false)

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).isEmpty()
    }

    @Test
    fun `공휴일이어도 필수 약관에 동의하지 않은 회원에게는 로그가 생성되지 않는다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L)
        agreements.clear() // 약관 동의 철회

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).isEmpty()
    }

    @Test
    fun `공휴일이어도 FCM 토큰이 없는 회원에게는 로그가 생성되지 않는다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L)
        fcmTokens.clear()

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).isEmpty()
    }

    @Test
    fun `공휴일이어도 정책의 effectiveFrom이 미래인 회원은 알림을 받지 않는다`() {
        공휴일로_지정(신정)
        workPolicies += WorkPolicyVersion(
            memberId = 1L,
            effectiveFrom = 신정.plusDays(1), // 정책이 내일부터 유효
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(18, 0),
            workdays = Workday.WEEKDAYS.toMutableSet(),
        )
        agreements += TermAgreement(memberId = 1L, termCode = TOS_CODE, agreed = true)
        settings += NotificationSetting(memberId = 1L)
        fcmTokens += FcmToken(memberId = 1L, token = "t-1")

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).isEmpty()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. 멱등성 (배치가 두 번 돌아도 중복 생성되지 않는다)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `같은 공휴일에 두 번 실행해도 회원당 PUBLIC_HOLIDAY 로그는 1건만 존재한다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L)

        sut.generateNotificationsForDate(신정)
        sut.generateNotificationsForDate(신정)

        assertThat(
            notificationLogs.count { it.memberId == 1L && it.notificationType == NotificationType.PUBLIC_HOLIDAY },
        ).isEqualTo(1)
    }

    @Test
    fun `공휴일에 일부 회원만 이미 로그가 있으면 나머지 회원에게만 신규 로그가 생성된다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L)
        회원_등록(id = 2L)
        notificationLogs += NotificationLog(
            memberId = 1L,
            notificationType = NotificationType.PUBLIC_HOLIDAY,
            scheduledDate = 신정,
            scheduledTime = LocalTime.of(9, 0),
        )

        sut.generateNotificationsForDate(신정)

        val holidayLogs = notificationLogs.filter { it.notificationType == NotificationType.PUBLIC_HOLIDAY }
        assertThat(holidayLogs.map { it.memberId }).containsExactlyInAnyOrder(1L, 2L)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. 현재 구현의 의도를 박제하는 "문서화 테스트"
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `NotificationSetting row가 아예 없으면 기본 활성화로 간주되어 공휴일 알림이 생성된다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L)
        settings.clear() // isSettingEnabled 의 "null != false → true" 분기 검증

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).hasSize(1)
    }

    @Test
    fun `공휴일이 정책상 비근무 요일이어도 PUBLIC_HOLIDAY 로그가 생성된다`() {
        // PUBLIC_HOLIDAY 경로는 workdays 를 보지 않는다 (CLOCK_IN 경로와의 의도된 차이)
        공휴일로_지정(신정)
        회원_등록(id = 1L, 근무요일 = setOf(Workday.MON))

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).hasSize(1)
    }

    @Test
    fun `공휴일에 휴가가 등록되어 있어도 PUBLIC_HOLIDAY 로그가 생성된다`() {
        // PUBLIC_HOLIDAY 경로는 shouldSkipNotification 을 호출하지 않는다 (CLOCK_IN 경로와의 의도된 차이)
        공휴일로_지정(신정)
        회원_등록(id = 1L)
        dailySchedules += DailyWorkSchedule(
            memberId = 1L,
            date = 신정,
            type = DailyWorkScheduleType.VACATION,
            clockInTime = LocalTime.of(10, 0),
            clockOutTime = LocalTime.of(16, 0),
        )

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).hasSize(1)
        assertThat(notificationLogs.first().notificationType).isEqualTo(NotificationType.PUBLIC_HOLIDAY)
    }

    @Test
    fun `FCM 토큰이 여러 개여도 회원당 로그는 1건만 생성된다`() {
        공휴일로_지정(신정)
        회원_등록(id = 1L)
        fcmTokens += FcmToken(memberId = 1L, token = "token-1-second-device")

        sut.generateNotificationsForDate(신정)

        assertThat(notificationLogs).hasSize(1)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 도메인 언어 DSL
    // ═════════════════════════════════════════════════════════════════════════

    private fun 공휴일로_지정(date: LocalDate) {
        holidayCalendar += date
    }

    private fun 회원_등록(
        id: Long,
        알림_켜짐: Boolean = true,
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
        settings += NotificationSetting(memberId = id, workNotificationEnabled = 알림_켜짐)
        fcmTokens += FcmToken(memberId = id, token = "token-$id")
    }

    companion object {
        private const val TOS_CODE = "TERMS_OF_SERVICE"
        private val 신정: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
