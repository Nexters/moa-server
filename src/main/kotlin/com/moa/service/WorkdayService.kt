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
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class WorkdayService(
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val profileRepository: ProfileRepository,
    private val notificationSyncService: NotificationSyncService,
    private val earningsCalculator: EarningsCalculator,
) {

    @Transactional(readOnly = true)
    fun getMonthlyWorkdays(memberId: Long, year: Int, month: Int): List<WorkdayResponse> {
        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())

        val savedSchedulesByDate =
            dailyWorkScheduleRepository
                .findAllByMemberIdAndDateBetween(memberId, start, end)
                .associateBy { it.date }

        val monthlyPolicy = resolveMonthlyRepresentativePolicyOrNull(memberId, year, month)

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                val schedule =
                    if (monthlyPolicy == null) {
                        ResolvedSchedule(DailyWorkScheduleType.NONE, null, null)
                    } else {
                        resolveScheduleForDate(savedSchedulesByDate[date], monthlyPolicy, date)
                    }
                createWorkdayResponse(memberId, date, schedule)
            }
            .toList()
    }

    @Transactional(readOnly = true)
    fun getMonthlyEarnings(memberId: Long, year: Int, month: Int): MonthlyEarningsResponse {
        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())
        val today = LocalDate.now()
        val defaultSalary = earningsCalculator.getDefaultMonthlySalary(memberId, start) ?: 0

        val monthlyPolicy = resolveMonthlyRepresentativePolicyOrNull(memberId, year, month)
        if (monthlyPolicy == null) {
            return MonthlyEarningsResponse(
                workedEarnings = 0,
                standardSalary = defaultSalary,
                workedMinutes = 0,
                standardMinutes = 0,
            )
        }

        val policyDailyMinutes = SalaryCalculator.calculateWorkMinutes(
            monthlyPolicy.clockInTime, monthlyPolicy.clockOutTime,
        )

        val policyWorkDayOfWeeks = monthlyPolicy.workdays.map { it.dayOfWeek }.toSet()
        val workDaysInMonth = SalaryCalculator.getWorkDaysInPeriod(
            start = start,
            end = end.plusDays(1),
            workDays = policyWorkDayOfWeeks
        )

        val standardMinutes = policyDailyMinutes * workDaysInMonth

        if (start.isAfter(today)) {
            return MonthlyEarningsResponse(0, defaultSalary, 0, standardMinutes)
        }

        val lastCalculableDate = minOf(end, today)

        val savedSchedulesByDate = dailyWorkScheduleRepository
            .findAllByMemberIdAndDateBetween(memberId, start, lastCalculableDate)
            .associateBy { it.date }

        var totalEarnings = BigDecimal.ZERO
        var workedMinutes = 0L
        val now = LocalTime.now()

        var date = start
        while (!date.isAfter(lastCalculableDate)) {
            val schedule = resolveScheduleForDate(savedSchedulesByDate[date], monthlyPolicy, date)
            val status = resolveDailWorkStatus(date, schedule)
            val adjustedClockOut = resolveClockOutForEarnings(date, today, now, schedule)

            if (status == DailWorkStatusType.COMPLETED &&
                (schedule.type == DailyWorkScheduleType.WORK || schedule.type == DailyWorkScheduleType.VACATION)
                && schedule.clockIn != null && adjustedClockOut != null
            ) {
                workedMinutes += SalaryCalculator.calculateWorkMinutes(schedule.clockIn, adjustedClockOut)
            }

            val dailyEarnings = earningsCalculator.calculateDailyEarnings(
                memberId, date, monthlyPolicy, schedule.type, schedule.clockIn, adjustedClockOut,
            )
            totalEarnings = totalEarnings.add(dailyEarnings ?: BigDecimal.ZERO)

            date = date.plusDays(1)
        }

        return MonthlyEarningsResponse(
            workedEarnings = totalEarnings.toLong(),
            standardSalary = defaultSalary,
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

        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())

        val savedSchedulesByDate =
            dailyWorkScheduleRepository
                .findAllByMemberIdAndDateBetween(memberId, start, end)
                .associateBy { it.date }

        val monthlyPolicy = resolveMonthlyRepresentativePolicyOrNull(memberId, year, month)

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                val schedule =
                    if (monthlyPolicy == null) {
                        ResolvedSchedule(DailyWorkScheduleType.NONE, null, null)
                    } else {
                        resolveScheduleForDate(savedSchedulesByDate[date], monthlyPolicy, date)
                    }
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
        val schedule =
            if (policy == null) {
                ResolvedSchedule(DailyWorkScheduleType.NONE, null, null)
            } else {
                resolveScheduleForDate(saved, policy, date)
            }
        return createWorkdayResponse(memberId, date, schedule)
    }

    @Transactional
    fun upsertSchedule(memberId: Long, date: LocalDate, req: WorkdayUpsertRequest): WorkdayResponse {
        val (clockIn, clockOut) = when (req.type) {
            DailyWorkScheduleType.WORK -> {
                val clockIn = req.clockInTime ?: throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                val clockOut = req.clockOutTime ?: throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                clockIn to clockOut
            }

            DailyWorkScheduleType.VACATION -> {
                // 1. 요청에 시간이 있으면 사용, 없으면 정책 조회
                if (req.clockInTime != null && req.clockOutTime != null) {
                    req.clockInTime to req.clockOutTime
                } else {
                    val policy = workPolicyVersionRepository
                        .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, date)
                        ?: workPolicyVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                            memberId,
                            LocalDate.now()
                        ) ?: throw NotFoundException()

                    // 요청값이 하나라도 비어있으면 정책의 기본 시간을 할당
                    policy.clockInTime to policy.clockOutTime
                }
            }

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
        return createWorkdayResponse(memberId, date, schedule)
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
        return createWorkdayResponse(memberId, date, schedule)
    }

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

    private fun createWorkdayResponse(
        memberId: Long,
        date: LocalDate,
        schedule: ResolvedSchedule,
    ): WorkdayResponse {
        val events = resolveCalendarEvents(memberId, date)

        if (schedule.type == DailyWorkScheduleType.NONE) {
            return WorkdayResponse(
                date = date,
                type = DailyWorkScheduleType.NONE,
                status = DailWorkStatusType.NONE,
                events = events,
                dailyPay = 0,
            )
        }
        val policy = resolveMonthlyRepresentativePolicyOrNull(memberId, date.year, date.monthValue)
            ?: return WorkdayResponse(
                date = date,
                type = schedule.type,
                status = resolveDailWorkStatus(date, schedule),
                events = events,
                dailyPay = 0,
                clockInTime = schedule.clockIn,
                clockOutTime = schedule.clockOut,
            )
        val earnings = earningsCalculator.calculateDailyEarnings(
            memberId, date, policy, schedule.type, schedule.clockIn, schedule.clockOut,
        )
        return WorkdayResponse(
            date = date,
            type = schedule.type,
            status = resolveDailWorkStatus(date, schedule),
            events = events,
            dailyPay = earnings?.toInt() ?: 0,
            clockInTime = schedule.clockIn,
            clockOutTime = schedule.clockOut,
        )
    }

    private fun resolveMonthlyRepresentativePolicyOrNull(
        memberId: Long,
        year: Int,
        month: Int,
    ): WorkPolicyVersion? {
        val lastDayOfMonth =
            LocalDate.of(year, month, 1)
                .withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())

        return workPolicyVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                memberId,
                lastDayOfMonth,
            )
    }

    private fun resolveCalendarEvents(memberId: Long, date: LocalDate): List<CalendarEventType> {
        val events = mutableListOf<CalendarEventType>()
        val paydayDay = profileRepository.findByMemberId(memberId)?.paydayDay

        if (paydayDay != null && isPayday(date, paydayDay)) {
            events += CalendarEventType.PAYDAY
        }

        // TODO: Add holiday event resolution when holiday data is available.

        return events
    }

    private fun isPayday(date: LocalDate, paydayDay: Int): Boolean {
        return resolveEffectivePayday(date.year, date.monthValue, paydayDay) == date
    }

    // 월급일이 해당 월에 없으면 말일로 보정하고, 그 날짜가 주말이면 직전 금요일로 당긴다.
    private fun resolveEffectivePayday(year: Int, month: Int, paydayDay: Int): LocalDate {
        val baseDate = LocalDate.of(year, month, 1)
            .withDayOfMonth(minOf(paydayDay, LocalDate.of(year, month, 1).lengthOfMonth()))

        return when (baseDate.dayOfWeek) {
            DayOfWeek.SATURDAY -> baseDate.minusDays(1)
            DayOfWeek.SUNDAY -> baseDate.minusDays(2)
            else -> baseDate
        }
    }

    private fun resolveDailWorkStatus(
        date: LocalDate,
        schedule: ResolvedSchedule,
    ): DailWorkStatusType {
        if (schedule.type == DailyWorkScheduleType.NONE) return DailWorkStatusType.NONE

        val clockIn = schedule.clockIn ?: return DailWorkStatusType.NONE
        val clockOut = schedule.clockOut ?: return DailWorkStatusType.NONE
        val now = LocalDateTime.now()
        val endAt = if (clockOut.isAfter(clockIn)) {
            date.atTime(clockOut)
        } else {
            date.plusDays(1).atTime(clockOut)
        }

        return if (now.isBefore(endAt)) DailWorkStatusType.SCHEDULED else DailWorkStatusType.COMPLETED
    }

    private fun resolveClockOutForEarnings(
        targetDate: LocalDate,
        today: LocalDate,
        now: LocalTime,
        schedule: ResolvedSchedule,
    ): LocalTime? {
        if (targetDate != today ||
            (schedule.type != DailyWorkScheduleType.WORK && schedule.type != DailyWorkScheduleType.VACATION)
        ) {
            return schedule.clockOut
        }

        val clockIn = schedule.clockIn ?: return schedule.clockOut
        val clockOut = schedule.clockOut ?: return null

        if (now.isBefore(clockIn)) {
            return null
        }

        return when {
            clockOut.isAfter(clockIn) -> minOf(now, clockOut)
            else -> now
        }
    }
}

private data class ResolvedSchedule(
    val type: DailyWorkScheduleType,
    val clockIn: LocalTime?,
    val clockOut: LocalTime?,
)
