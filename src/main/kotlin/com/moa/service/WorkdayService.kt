package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.NotFoundException
import com.moa.entity.*
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.PayrollVersionRepository
import com.moa.repository.ProfileRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.calculator.CompensationCalculator
import com.moa.service.dto.*
import com.moa.service.notification.NotificationSyncService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Service
class WorkdayService(
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
    private val payrollVersionRepository: PayrollVersionRepository,
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val profileRepository: ProfileRepository,
    private val notificationSyncService: NotificationSyncService,
    private val compensationCalculator: CompensationCalculator,
    private val publicHolidayService: PublicHolidayService,
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
        val publicHolidays = publicHolidayService.getHolidayDatesForMonth(year, month)

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                val schedule = resolveSchedule(savedSchedulesByDate[date], monthlyPolicy, date, publicHolidays)
                createWorkdayResponse(memberId, date, schedule, monthlyPolicy, paydayDay, publicHolidays)
            }
            .toList()
    }

    @Transactional(readOnly = true)
    fun getMonthlyEarnings(memberId: Long, year: Int, month: Int): MonthlyEarningsResponse {
        val (start, end) = resolveMonthRange(year, month)
        val today = LocalDate.now()
        val standardSalary = calculateStandardSalary(memberId, start).toLong()
        val publicHolidays = publicHolidayService.getHolidayDatesForMonth(year, month)

        val monthlyPolicy = resolveMonthlyRepresentativePolicyOrNull(memberId, year, month)
        if (monthlyPolicy == null) {
            return MonthlyEarningsResponse(
                workedEarnings = 0,
                standardSalary = standardSalary,
                workedMinutes = 0,
                standardMinutes = 0,
            )
        }

        val standardMinutes = compensationCalculator.calculateStandardMinutes(
            monthlyPolicy, start, end, publicHolidays,
        )

        if (start.isAfter(today)) {
            return MonthlyEarningsResponse(0, standardSalary, 0, standardMinutes)
        }

        val lastCalculableDate = minOf(end, today)

        val savedSchedulesByDate = dailyWorkScheduleRepository
            .findAllByMemberIdAndDateBetween(memberId, start, lastCalculableDate)
            .associateBy { it.date }

        var workedEarnings = BigDecimal.ZERO
        var workedMinutes = 0L
        var date = start
        while (!date.isAfter(lastCalculableDate)) {
            val schedule = resolveSchedule(savedSchedulesByDate[date], monthlyPolicy, date, publicHolidays)
            val status = DailyWorkStatusType.resolve(
                date = date,
                scheduleType = schedule.type,
                clockIn = schedule.clockIn,
                clockOut = schedule.clockOut,
            )
            val completedWork = resolveCompletedWorkForSettlement(schedule, status)

            if (completedWork != null) {
                workedMinutes += compensationCalculator.calculateWorkMinutes(
                    completedWork.clockIn,
                    completedWork.clockOut,
                )
                workedEarnings = workedEarnings.add(calculateDailyEarnings(
                    memberId,
                    date,
                    monthlyPolicy,
                    completedWork.type,
                    completedWork.clockIn,
                    completedWork.clockOut,
                    publicHolidays,
                ))
            }

            date = date.plusDays(1)
        }

        return MonthlyEarningsResponse(
            workedEarnings = workedEarnings.setScale(0, RoundingMode.HALF_UP).toLong(),
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
        val publicHolidays = publicHolidayService.getHolidayDatesForMonth(year, month)

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                val schedule = resolveSchedule(savedSchedulesByDate[date], monthlyPolicy, date, publicHolidays)
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
        val publicHolidays = if (publicHolidayService.isHoliday(date)) setOf(date) else emptySet()
        val schedule = resolveSchedule(saved, policy, date, publicHolidays)
        return createWorkdayResponse(memberId, date, schedule, policy, resolvePaydayDay(memberId), publicHolidays)
    }

    @Transactional
    fun upsertSchedule(memberId: Long, date: LocalDate, req: WorkdayUpsertRequest): WorkdayResponse {
        val existingSchedule = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
        val (clockIn, clockOut) = when (req.type) {
            DailyWorkScheduleType.WORK -> {
                val clockIn = req.clockInTime ?: throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                val clockOut = req.clockOutTime ?: throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                clockIn to clockOut
            }

            DailyWorkScheduleType.VACATION -> {
                val policy = resolveMonthlyRepresentativePolicyOrNull(memberId, date.year, date.monthValue)
                    ?: throw NotFoundException()
                resolveVacationTimes(date, req, existingSchedule, policy)
            }

            DailyWorkScheduleType.NONE -> null to null
        }

        val workSchedule = existingSchedule
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
        val publicHolidays = if (publicHolidayService.isHoliday(date)) setOf(date) else emptySet()
        return createWorkdayResponse(
            memberId,
            date,
            schedule,
            resolveMonthlyRepresentativePolicyOrNull(memberId, date.year, date.monthValue),
            resolvePaydayDay(memberId),
            publicHolidays,
        )
    }

    @Transactional
    fun patchClockOut(memberId: Long, date: LocalDate, req: WorkdayEditRequest): WorkdayResponse {
        val workSchedule = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
            ?.also {
                if (it.type == DailyWorkScheduleType.VACATION || it.type == DailyWorkScheduleType.NONE) {
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
        val publicHolidays = if (publicHolidayService.isHoliday(date)) setOf(date) else emptySet()
        return createWorkdayResponse(
            memberId,
            date,
            schedule,
            resolveMonthlyRepresentativePolicyOrNull(memberId, date.year, date.monthValue),
            resolvePaydayDay(memberId),
            publicHolidays,
        )
    }

    private fun resolveMonthRange(year: Int, month: Int): Pair<LocalDate, LocalDate> {
        val start = LocalDate.of(year, month, 1)
        return start to start.withDayOfMonth(start.lengthOfMonth())
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

    /**
     * 특정 월을 대표하는 급여 버전을 조회하는 규칙을 명시적으로 드러내기 위한 헬퍼입니다.
     *
     * 이 서비스는 월말 기준으로 그 달에 적용되는 최신 급여를 사용하므로,
     * 조회 기준일 계산과 리포지토리 호출을 한 곳에 묶어 의미를 고정합니다.
     */
    private fun resolveMonthlyRepresentativePayrollOrNull(
        memberId: Long,
        year: Int,
        month: Int,
    ) = payrollVersionRepository
        .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            memberId,
            YearMonth.of(year, month).atEndOfMonth(),
        )

    private fun resolvePaydayDay(memberId: Long): PaydayDay =
        profileRepository.findByMemberId(memberId)?.paydayDay ?: throw NotFoundException()

    private fun resolveSchedule(
        saved: DailyWorkSchedule?,
        policy: WorkPolicyVersion?,
        date: LocalDate,
        publicHolidays: Set<LocalDate> = emptySet(),
    ): ResolvedSchedule {
        if (policy == null) {
            return ResolvedSchedule(DailyWorkScheduleType.NONE, null, null)
        }
        return resolveScheduleForDate(saved, policy, date, publicHolidays)
    }

    private fun resolveScheduleForDate(
        saved: DailyWorkSchedule?,
        policy: WorkPolicyVersion,
        date: LocalDate,
        publicHolidays: Set<LocalDate> = emptySet(),
    ): ResolvedSchedule {
        if (saved != null) {
            return ResolvedSchedule(saved.type, saved.clockInTime, saved.clockOutTime)
        }
        val isWorkday = policy.workdays.any { it.dayOfWeek == date.dayOfWeek }
        if (date in publicHolidays && isWorkday) {
            return ResolvedSchedule(DailyWorkScheduleType.NONE, null, null)
        }
        return if (isWorkday) {
            ResolvedSchedule(DailyWorkScheduleType.WORK, policy.clockInTime, policy.clockOutTime)
        } else {
            ResolvedSchedule(DailyWorkScheduleType.NONE, null, null)
        }
    }

    private fun resolveVacationTimes(
        date: LocalDate,
        req: WorkdayUpsertRequest,
        savedSchedule: DailyWorkSchedule?,
        policy: WorkPolicyVersion,
    ): Pair<LocalTime, LocalTime> {
        if (req.clockInTime != null && req.clockOutTime != null) {
            val clockIn = req.clockInTime
            val clockOut = req.clockOutTime
            return clockIn to clockOut
        }

        val schedule = resolveSchedule(savedSchedule, policy, date)
        if (schedule.clockIn != null && schedule.clockOut != null) {
            return schedule.clockIn to schedule.clockOut
        }

        return policy.clockInTime to policy.clockOutTime
    }

    private fun createWorkdayResponse(
        memberId: Long,
        date: LocalDate,
        schedule: ResolvedSchedule,
        policy: WorkPolicyVersion?,
        paydayDay: PaydayDay,
        publicHolidays: Set<LocalDate> = emptySet(),
    ): WorkdayResponse {
        val events = DailyEventType.resolve(date, paydayDay, publicHolidays)
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
        val dailyPay = resolveDisplayedDailyPay(memberId, date, schedule, policy, publicHolidays)

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

    private fun resolveDisplayedDailyPay(
        memberId: Long,
        date: LocalDate,
        schedule: ResolvedSchedule,
        policy: WorkPolicyVersion?,
        publicHolidays: Set<LocalDate> = emptySet(),
    ): Int {
        if (policy == null) return 0

        return calculateDailyEarnings(
            memberId, date, policy, schedule.type, schedule.clockIn, schedule.clockOut, publicHolidays,
        ).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    /**
     * 해당 월에 적용할 급여 정보를 조회한 뒤 기준 월급을 계산합니다.
     *
     * 급여 정보가 없으면 `0`을 반환합니다.
     */
    private fun calculateStandardSalary(memberId: Long, date: LocalDate) =
        resolveMonthlyRepresentativePayrollOrNull(memberId, date.year, date.monthValue)?.let {
            compensationCalculator.calculateStandardSalary(it.salaryInputType, it.salaryAmount)
        } ?: BigDecimal.ZERO

    /**
     * 해당 월에 적용할 급여 정보를 조회한 뒤 일일 소득 계산에 필요한 값만 계산기로 전달합니다.
     *
     * 급여 정보가 없으면 `0`을 반환합니다.
     */
    private fun calculateDailyEarnings(
        memberId: Long,
        date: LocalDate,
        policy: WorkPolicyVersion,
        type: DailyWorkScheduleType,
        clockInTime: LocalTime?,
        clockOutTime: LocalTime?,
        publicHolidays: Set<LocalDate> = emptySet(),
    ) = resolveMonthlyRepresentativePayrollOrNull(memberId, date.year, date.monthValue)?.let {
        compensationCalculator.calculateDailyEarnings(
            date = date,
            salaryType = it.salaryInputType,
            salaryAmount = it.salaryAmount,
            policy = policy,
            type = type,
            clockInTime = clockInTime,
            clockOutTime = clockOutTime,
            publicHolidays = publicHolidays,
        )
    } ?: BigDecimal.ZERO

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
