package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.NotFoundException
import com.moa.entity.*
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.ProfileRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.dto.*
import com.moa.service.notification.NotificationSyncService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Service
class WorkdayService(
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val profileRepository: ProfileRepository,
    private val notificationSyncService: NotificationSyncService,
    private val memberEarningsService: MemberEarningsService,
) {

    @Transactional(readOnly = true)
    fun getMonthlyWorkdays(memberId: Long, year: Int, month: Int): List<WorkdayResponse> {
        val (start, end) = resolveMonthRange(year, month)

        val savedSchedulesByDate =
            dailyWorkScheduleRepository
                .findAllByMemberIdAndDateBetween(memberId, start, end)
                .associateBy { it.date }

        val monthlyPolicy = resolveMonthlyRepresentativePolicyOrNull(memberId, year, month)
        val paydayDay = resolvePaydayDay(memberId)

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                val schedule = resolveSchedule(savedSchedulesByDate[date], monthlyPolicy, date)
                createWorkdayResponse(memberId, date, schedule, monthlyPolicy, paydayDay)
            }
            .toList()
    }

    @Transactional(readOnly = true)
    fun getMonthlyEarnings(memberId: Long, year: Int, month: Int): MonthlyEarningsResponse {
        val (start, end) = resolveMonthRange(year, month)
        val today = LocalDate.now()
        val standardSalary = memberEarningsService.calculateStandardSalary(memberId, start).toLong()

        val monthlyPolicy = resolveMonthlyRepresentativePolicyOrNull(memberId, year, month)
        if (monthlyPolicy == null) {
            return MonthlyEarningsResponse(
                workedEarnings = 0,
                standardSalary = standardSalary,
                workedMinutes = 0,
                standardMinutes = 0,
            )
        }

        val standardMinutes = memberEarningsService.calculateStandardMinutes(monthlyPolicy, start, end)

        if (start.isAfter(today)) {
            return MonthlyEarningsResponse(0, standardSalary, 0, standardMinutes)
        }

        val lastCalculableDate = minOf(end, today)

        val savedSchedulesByDate = dailyWorkScheduleRepository
            .findAllByMemberIdAndDateBetween(memberId, start, lastCalculableDate)
            .associateBy { it.date }

        var workedEarnings = 0L
        var workedMinutes = 0L
        var date = start
        while (!date.isAfter(lastCalculableDate)) {
            val schedule = resolveSchedule(savedSchedulesByDate[date], monthlyPolicy, date)
            val status = DailyWorkStatusType.resolve(
                date = date,
                scheduleType = schedule.type,
                clockIn = schedule.clockIn,
                clockOut = schedule.clockOut,
            )
            val completedWork = resolveCompletedWorkForSettlement(schedule, status)

            if (completedWork != null) {
                workedMinutes += memberEarningsService.calculateWorkedMinutes(
                    completedWork.clockIn,
                    completedWork.clockOut,
                )
                val dailyEarnings = memberEarningsService.calculateDailyEarnings(
                    memberId,
                    date,
                    monthlyPolicy,
                    completedWork.type,
                    completedWork.clockIn,
                    completedWork.clockOut,
                )
                workedEarnings += dailyEarnings.toLong()
            }

            date = date.plusDays(1)
        }

        return MonthlyEarningsResponse(
            workedEarnings = workedEarnings,
            standardSalary = standardSalary,
            workedMinutes = workedMinutes,
            standardMinutes = standardMinutes,
        )
    }

    @Transactional(readOnly = true)
    fun getMonthlySchedules(
        memberId: Long,
        year: Int,
        month: Int,
    ): List<MonthlyWorkdayResponse> {

        val (start, end) = resolveMonthRange(year, month)

        val savedSchedulesByDate =
            dailyWorkScheduleRepository
                .findAllByMemberIdAndDateBetween(memberId, start, end)
                .associateBy { it.date }

        val monthlyPolicy = resolveMonthlyRepresentativePolicyOrNull(memberId, year, month)

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                val schedule = resolveSchedule(savedSchedulesByDate[date], monthlyPolicy, date)
                MonthlyWorkdayResponse(date = date, type = schedule.type)
            }
            .toList()
    }

    @Transactional(readOnly = true)
    fun getSchedule(
        memberId: Long,
        date: LocalDate,
    ): WorkdayResponse {
        val saved = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
        val policy = resolveMonthlyRepresentativePolicyOrNull(memberId, date.year, date.monthValue)
        val schedule = resolveSchedule(saved, policy, date)
        return createWorkdayResponse(memberId, date, schedule, policy, resolvePaydayDay(memberId))
    }

    @Transactional
    fun upsertSchedule(memberId: Long, date: LocalDate, req: WorkdayUpsertRequest): WorkdayResponse {
        val (clockIn, clockOut) = when (req.type) {
            DailyWorkScheduleType.WORK -> {
                val clockIn = req.clockInTime ?: throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                val clockOut = req.clockOutTime ?: throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                clockIn to clockOut
            }

            DailyWorkScheduleType.VACATION -> resolveVacationTimes(memberId, date, req)

            DailyWorkScheduleType.NONE -> throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
        }

        val workSchedule = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
            ?.apply {
                this.type = req.type
                this.clockInTime = clockIn
                this.clockOutTime = clockOut
            }
            ?: DailyWorkSchedule(
                memberId = memberId,
                date = date,
                type = req.type,
                clockInTime = clockIn,
                clockOutTime = clockOut,
            )

        val savedSchedule = dailyWorkScheduleRepository.save(workSchedule)

        notificationSyncService.syncNotifications(
            memberId, date, savedSchedule.type, savedSchedule.clockInTime, savedSchedule.clockOutTime,
        )

        val schedule = ResolvedSchedule(savedSchedule.type, savedSchedule.clockInTime, savedSchedule.clockOutTime)
        return createWorkdayResponse(
            memberId,
            date,
            schedule,
            resolveMonthlyRepresentativePolicyOrNull(memberId, date.year, date.monthValue),
            resolvePaydayDay(memberId),
        )
    }

    @Transactional
    fun patchClockOut(memberId: Long, date: LocalDate, req: WorkdayEditRequest): WorkdayResponse {
        val workSchedule = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
            ?.also {
                if (it.type == DailyWorkScheduleType.VACATION) {
                    throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                }
            }
            ?: run {
                val policy = workPolicyVersionRepository
                    .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, date)
                    ?: throw NotFoundException()
                DailyWorkSchedule(
                    memberId = memberId,
                    date = date,
                    type = DailyWorkScheduleType.WORK,
                    clockInTime = policy.clockInTime,
                    clockOutTime = policy.clockOutTime,
                )
            }

        workSchedule.clockOutTime = req.clockOutTime

        val savedSchedule = dailyWorkScheduleRepository.save(workSchedule)

        notificationSyncService.syncNotifications(
            memberId, date, DailyWorkScheduleType.WORK, savedSchedule.clockInTime, savedSchedule.clockOutTime,
        )

        val schedule = ResolvedSchedule(savedSchedule.type, savedSchedule.clockInTime, savedSchedule.clockOutTime)
        return createWorkdayResponse(
            memberId,
            date,
            schedule,
            resolveMonthlyRepresentativePolicyOrNull(memberId, date.year, date.monthValue),
            resolvePaydayDay(memberId),
        )
    }

    /**
     * 저장된 스케줄과 정책 기본값 중 어떤 값을 응답에 반영할지 한 곳에서 결정하기 위한 헬퍼입니다.
     *
     * 월간 조회, 단건 조회, 월간 집계가 모두 같은 해석 규칙을 쓰도록 하여
     * "저장된 값이 있으면 우선, 없으면 정책으로 보완" 규칙의 중복을 줄입니다.
     */
    private fun resolveScheduleForDate(
        saved: DailyWorkSchedule?,
        policy: WorkPolicyVersion,
        date: LocalDate,
    ): ResolvedSchedule {
        if (saved != null) {
            return ResolvedSchedule(saved.type, saved.clockInTime, saved.clockOutTime)
        }
        val isWorkday = policy.workdays.any { it.dayOfWeek == date.dayOfWeek }
        return if (isWorkday) {
            ResolvedSchedule(DailyWorkScheduleType.WORK, policy.clockInTime, policy.clockOutTime)
        } else {
            ResolvedSchedule(DailyWorkScheduleType.NONE, null, null)
        }
    }

    /**
     * 근무일 화면 응답 생성 규칙을 한 곳에 모아 두기 위한 헬퍼입니다.
     *
     * 상태 계산, 이벤트 계산, 표시용 일급 계산이 여러 API에서 동일하게 동작해야 하므로
     * 응답 조립 로직을 서비스 메서드마다 반복하지 않도록 분리했습니다.
     */
    private fun createWorkdayResponse(
        memberId: Long,
        date: LocalDate,
        schedule: ResolvedSchedule,
        policy: WorkPolicyVersion?,
        paydayDay: PaydayDay,
    ): WorkdayResponse {
        val events = DailyEventType.resolve(date, paydayDay)
        val status = DailyWorkStatusType.resolve(
            date = date,
            scheduleType = schedule.type,
            clockIn = schedule.clockIn,
            clockOut = schedule.clockOut,
        )

        if (schedule.type == DailyWorkScheduleType.NONE) {
            return WorkdayResponse(
                date = date,
                type = DailyWorkScheduleType.NONE,
                status = status,
                events = events,
                dailyPay = 0,
            )
        }
        val dailyPay = resolveDisplayedDailyPay(memberId, date, schedule, policy)

        return WorkdayResponse(
            date = date,
            type = schedule.type,
            status = status,
            events = events,
            dailyPay = dailyPay,
            clockInTime = schedule.clockIn,
            clockOutTime = schedule.clockOut,
        )
    }

    /**
     * 정책이 없는 경우를 포함해 스케줄 해석 진입점을 단순화하기 위한 헬퍼입니다.
     *
     * 호출부가 매번 null 정책 분기를 직접 처리하지 않게 해서
     * 월간 조회와 단건 조회의 흐름을 더 짧고 동일한 형태로 유지합니다.
     */
    private fun resolveSchedule(
        saved: DailyWorkSchedule?,
        policy: WorkPolicyVersion?,
        date: LocalDate,
    ): ResolvedSchedule {
        if (policy == null) {
            return ResolvedSchedule(DailyWorkScheduleType.NONE, null, null)
        }
        return resolveScheduleForDate(saved, policy, date)
    }

    /**
     * 월 시작일과 마지막 날 계산을 한 곳으로 모아 날짜 범위 표현을 통일하기 위한 헬퍼입니다.
     *
     * 같은 계산이 여러 메서드에 흩어지면 사소한 수정에도 중복 변경이 필요하므로
     * 월 단위 조회에서 공통으로 사용하는 범위 생성을 분리했습니다.
     */
    private fun resolveMonthRange(year: Int, month: Int): Pair<LocalDate, LocalDate> {
        val start = LocalDate.of(year, month, 1)
        return start to start.withDayOfMonth(start.lengthOfMonth())
    }

    /**
     * 화면에 보여줄 `dailyPay` 계산 책임을 월 정산 로직과 분리하기 위한 헬퍼입니다.
     *
     * 같은 계산기를 사용하더라도 화면 표시는 "그날 보여줄 금액"이고,
     * 월 집계는 "완료된 근무만 합산한 확정 소득"이므로 의도를 코드 레벨에서 구분합니다.
     */
    private fun resolveDisplayedDailyPay(
        memberId: Long,
        date: LocalDate,
        schedule: ResolvedSchedule,
        policy: WorkPolicyVersion?,
    ): Int {
        if (policy == null) return 0

        return memberEarningsService.calculateDailyEarnings(
            memberId, date, policy, schedule.type, schedule.clockIn, schedule.clockOut,
        ).toInt()
    }

    /**
     * 월 소득 집계에 포함 가능한 근무만 선별하기 위한 헬퍼입니다.
     *
     * 완료 여부, 근무 유형, 출퇴근 시간 존재 여부를 한 번에 확인해
     * 정산 대상 판정 규칙이 루프 본문에 흩어지지 않도록 분리했습니다.
     */
    private fun resolveCompletedWorkForSettlement(
        schedule: ResolvedSchedule,
        status: DailyWorkStatusType,
    ): CompletedWork? {
        if (status != DailyWorkStatusType.COMPLETED) return null
        if (schedule.type != DailyWorkScheduleType.WORK && schedule.type != DailyWorkScheduleType.VACATION) {
            return null
        }

        val clockIn = schedule.clockIn ?: return null
        val clockOut = schedule.clockOut ?: return null
        return CompletedWork(schedule.type, clockIn, clockOut)
    }

    /**
     * 휴무 입력 시 사용할 시간을 결정하는 규칙을 `upsertSchedule`에서 분리하기 위한 헬퍼입니다.
     *
     * 요청 시간이 모두 있으면 그대로 사용하고, 없으면 정책 시간으로 보완하는 규칙을 묶어
     * 상위 메서드가 "어떤 타입의 스케줄을 저장하는가"에만 집중하게 합니다.
     */
    private fun resolveVacationTimes(
        memberId: Long,
        date: LocalDate,
        req: WorkdayUpsertRequest,
    ): Pair<LocalTime, LocalTime> {
        if (req.clockInTime != null && req.clockOutTime != null) {
            val clockIn = req.clockInTime
            val clockOut = req.clockOutTime
            return clockIn to clockOut
        }

        val policy = workPolicyVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, date)
            ?: workPolicyVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                memberId,
                LocalDate.now()
            )
            ?: throw NotFoundException()

        return policy.clockInTime to policy.clockOutTime
    }

    /**
     * 특정 월을 대표하는 정책 버전을 조회하는 규칙을 명시적으로 드러내기 위한 헬퍼입니다.
     *
     * 이 서비스는 월말 기준으로 그 달에 적용되는 최신 정책을 사용하므로,
     * 조회 기준일 계산과 리포지토리 호출을 한 곳에 묶어 의미를 고정합니다.
     */
    private fun resolveMonthlyRepresentativePolicyOrNull(
        memberId: Long,
        year: Int,
        month: Int,
    ): WorkPolicyVersion? {
        val lastDayOfMonth = YearMonth.of(year, month).atEndOfMonth()

        return workPolicyVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                memberId,
                lastDayOfMonth,
            )
    }

    private fun resolvePaydayDay(memberId: Long): PaydayDay =
        profileRepository.findByMemberId(memberId)?.paydayDay ?: throw NotFoundException()
}

private data class ResolvedSchedule(
    val type: DailyWorkScheduleType,
    val clockIn: LocalTime?,
    val clockOut: LocalTime?,
)

private data class CompletedWork(
    val type: DailyWorkScheduleType,
    val clockIn: LocalTime,
    val clockOut: LocalTime,
)
